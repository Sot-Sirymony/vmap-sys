package com.visionmapping.service.oauth;

import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.dto.response.SignInAnswer;
import com.visionmapping.dto.response.TwoFactorChallengeResponse;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.service.twofactor.SignInChallengeService;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ExternalIdentity;
import com.visionmapping.entity.OAuthHandoff;
import com.visionmapping.entity.enums.IdentityProvider;
import com.visionmapping.entity.enums.OAuthHandoffKind;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.ExternalIdentityRepository;
import com.visionmapping.repository.OAuthHandoffRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.AuthService;
import com.visionmapping.service.AuthSessionService;
import com.visionmapping.service.oauth.GoogleSignInService.VerifiedGoogleSignIn;
import com.visionmapping.service.support.LoginAttemptLimiter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V14 (BRD 14.0.0, GA-FR-14 to GA-FR-20, GA-FR-23 to GA-FR-25, GA-SEC-06,
 * GA-SEC-07): what a verified Google answer does to accounts.
 *
 * <ol>
 *   <li><b>Already linked</b> (the Google {@code sub} is known): that account
 *       gets a 60-second sign-in code.</li>
 *   <li><b>Email used by an account</b>: nothing is linked. A 10-minute link
 *       code lets the person prove the account's password once (GA-SEC-06).</li>
 *   <li><b>New email</b>: an account is created with a password nobody knows,
 *       linked, and given a sign-in code.</li>
 * </ol>
 *
 * The codes travel in the URL fragment; only their SHA-256 is kept, and each
 * works once. Access and refresh tokens are only ever in a POST answer.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GoogleAccountService {

    static final Duration SIGN_IN_CODE_LIFETIME = Duration.ofSeconds(60);
    static final Duration LINK_CODE_LIFETIME = Duration.ofMinutes(10);
    static final int MAX_NAME_LENGTH = 150;
    static final int MAX_EMAIL_LENGTH = 180;
    public static final String SIGN_IN_EXPIRED = "This sign-in link has expired. Please try again.";
    public static final String LINK_EXPIRED = "This link has expired. Please sign in with Google again.";
    public static final String WRONG_PASSWORD = "That password is not correct.";

    private final AppUserRepository appUserRepository;
    private final ExternalIdentityRepository identityRepository;
    private final OAuthHandoffRepository handoffRepository;
    private final AuthSessionService authSessionService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final PasswordEncoder passwordEncoder;
    private final SecurityEventService securityEvents;
    private final SignInChallengeService signInChallenges;
    private final Clock clock;
    private final com.visionmapping.service.admin.InvitationService invitations;

    /**
     * GA-FR-14 to GA-FR-17, GA-FR-19: decides the outcome and returns the URL
     * fragment for the frontend, without the leading "#".
     */
    String outcome(VerifiedGoogleSignIn verified) {
        GoogleIdentity google = verified.identity();
        Optional<ExternalIdentity> linked = identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, google.subject());
        if (linked.isPresent()) {
            ExternalIdentity identity = linked.get();
            AppUser user = activeOrRefuse(identity.getUser());
            if (!identity.getLastEmail().equalsIgnoreCase(google.email())) {
                identity.setLastEmail(google.email());
            }
            return fragment("signed-in", newCode(OAuthHandoffKind.SIGN_IN, user, google.subject(), verified), verified);
        }
        String email = google.email().toLowerCase(Locale.ROOT);
        Optional<AppUser> existing = appUserRepository.findByEmail(email);
        if (existing.isPresent()) {
            AppUser user = activeOrRefuse(existing.get());
            // V23 (UM-FR-26): an invited person who has not chosen a password yet. Google proved they own the
            // address, as the invitation email would have, so it is linked without a password.
            if (identityRepository.findByUser_IdAndProvider(user.getId(), IdentityProvider.GOOGLE).isEmpty()
                    && invitations.acceptWithGoogle(user)) {
                linkIdentity(user, google, "invitation");
                return fragment("signed-in", newCode(OAuthHandoffKind.SIGN_IN, user, google.subject(), verified), verified);
            }
            return fragment("link", newCode(OAuthHandoffKind.LINK, user, google.subject(), verified), verified);
        }
        AppUser created = createAccount(google, email);
        return fragment("signed-in", newCode(OAuthHandoffKind.SIGN_IN, created, google.subject(), verified), verified);
    }

    /** GA-FR-20: turns a sign-in code into the normal sign-in answer, once. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public SignInAnswer exchange(@Nullable String code, @Nullable String deviceToken) {
        Optional<OAuthHandoff> open = findOpen(code, OAuthHandoffKind.SIGN_IN);
        // Read before markUsed, which clears the persistence context.
        Long userId = open.map(handoff -> handoff.getUser().getId()).orElse(null);
        boolean remember = open.map(OAuthHandoff::isRememberMe).orElse(false);
        String returnPath = open.map(OAuthHandoff::getReturnPath).orElse("/");
        if (open.isEmpty() || handoffRepository.markUsed(open.get().getId(), clock.instant()) != 1) {
            // GA-FR-34: counted per network towards the security alert.
            securityEvents.record(SecurityEventType.GOOGLE_SIGN_IN_FAILED, SecurityOutcome.DENIED, null, null, "exchange");
            throw new BusinessRuleException(SIGN_IN_EXPIRED);
        }
        // markUsed clears the persistence context, so the account is read again.
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new BusinessRuleException(SIGN_IN_EXPIRED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            securityEvents.recordFor(user.getId(), SecurityEventType.GOOGLE_SIGN_IN_FAILED, SecurityOutcome.DENIED, "unavailable");
            throw new BusinessRuleException(SIGN_IN_EXPIRED);
        }
        identityRepository.findByUser_IdAndProvider(user.getId(), IdentityProvider.GOOGLE)
                .ifPresent(identity -> identity.setLastUsedAt(clock.instant()));
        // V21 (TF-FR-18, D-21-3): with 2FA on, Google is only the first factor.
        Optional<TwoFactorChallengeResponse> challenge = signInChallenges.start(user, remember, returnPath,
                SignInOrigin.GOOGLE, deviceToken);
        if (challenge.isPresent()) {
            return challenge.get();
        }
        securityEvents.recordFor(user.getId(), SecurityEventType.GOOGLE_SIGN_IN_SUCCEEDED, SecurityOutcome.SUCCESS,
                remember ? "remember me" : "this tab only");
        return AuthService.response(authSessionService.open(user, remember));
    }

    /**
     * GA-FR-23, GA-FR-24, GA-SEC-06: links Google to an existing account once
     * its password is confirmed. The same attempt limits as password sign-in
     * apply, and a wrong password counts as a failed sign-in. The link code
     * stays usable until it expires, so the person can try again.
     */
    @Transactional(noRollbackFor = {BusinessRuleException.class, TooManyRequestsException.class})
    public SignInAnswer link(@Nullable String linkToken, String password, @Nullable String clientIp,
                             @Nullable String deviceToken) {
        OAuthHandoff handoff = findOpen(linkToken, OAuthHandoffKind.LINK)
                .orElseThrow(() -> new BusinessRuleException(LINK_EXPIRED));
        AppUser user = handoff.getUser();
        String email = user.getEmail();
        try {
            loginAttemptLimiter.checkAllowed(email, clientIp);
        } catch (TooManyRequestsException locked) {
            securityEvents.recordForEmail(email, null, SecurityEventType.LOGIN_LOCKED, SecurityOutcome.DENIED, "google link");
            throw locked;
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(LINK_EXPIRED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            securityEvents.recordForEmail(email, user.getId(), SecurityEventType.LOGIN_FAILED, SecurityOutcome.DENIED, "google link");
            throw new BusinessRuleException(WRONG_PASSWORD);
        }
        String subject = handoff.getProviderSubject();
        boolean remember = handoff.isRememberMe();
        String returnPath = handoff.getReturnPath();
        Long userId = user.getId();
        boolean alreadyLinked = identityRepository.findByProviderAndSubject(IdentityProvider.GOOGLE, subject).isPresent()
                || identityRepository.findByUser_IdAndProvider(userId, IdentityProvider.GOOGLE).isPresent();
        if (alreadyLinked || handoffRepository.markUsed(handoff.getId(), clock.instant()) != 1) {
            throw new BusinessRuleException(LINK_EXPIRED);
        }
        // markUsed clears the persistence context, so the account is read again.
        user = appUserRepository.findById(userId).orElseThrow(() -> new BusinessRuleException(LINK_EXPIRED));
        Instant now = clock.instant();
        identityRepository.save(ExternalIdentity.builder()
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .subject(subject)
                .emailAtLink(email)
                .lastEmail(email)
                .linkedAt(now)
                .lastUsedAt(now)
                .build());
        securityEvents.recordFor(userId, SecurityEventType.GOOGLE_LINKED, SecurityOutcome.SUCCESS, "password confirmed");
        // V21 (TF-FR-18, TF-FR-16): the link is saved; with 2FA on, the second step still comes before a session,
        // and the failure count is reset only after it.
        Optional<TwoFactorChallengeResponse> challenge = signInChallenges.start(user, remember, returnPath,
                SignInOrigin.GOOGLE, deviceToken);
        if (challenge.isPresent()) {
            return challenge.get();
        }
        loginAttemptLimiter.recordSuccess(email);
        return AuthService.response(authSessionService.open(user, remember));
    }

    /** GA-FR-17: a disabled account is never signed in or linked through Google. */
    private static AppUser activeOrRefuse(AppUser user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new GoogleSignInException(GoogleSignInFailure.UNAVAILABLE, "The account is not active");
        }
        return user;
    }

    /**
     * GA-FR-16, GA-FR-25: role USER, active, the name from Google (or the part
     * of the email before "@"), and a password hash of a random 256-bit secret
     * that is never shown, so no password check can succeed.
     */
    private AppUser createAccount(GoogleIdentity google, String email) {
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new GoogleSignInException(GoogleSignInFailure.UNAVAILABLE, "The Google email is too long for an account");
        }
        AppUser user = appUserRepository.save(AppUser.builder()
                .fullName(fullName(google, email))
                .email(email)
                .passwordHash(passwordEncoder.encode(OAuthValues.random(OAuthValues.RANDOM_BYTES)))
                .passwordSet(false)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        Instant now = clock.instant();
        identityRepository.save(ExternalIdentity.builder()
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .subject(google.subject())
                .emailAtLink(google.email())
                .lastEmail(google.email())
                .linkedAt(now)
                .build());
        securityEvents.recordFor(user.getId(), SecurityEventType.REGISTERED, SecurityOutcome.SUCCESS, "google");
        return user;
    }

    private void linkIdentity(AppUser user, GoogleIdentity google, String how) {
        Instant now = clock.instant();
        identityRepository.save(ExternalIdentity.builder()
                .user(user)
                .provider(IdentityProvider.GOOGLE)
                .subject(google.subject())
                .emailAtLink(google.email())
                .lastEmail(google.email())
                .linkedAt(now)
                .build());
        securityEvents.recordFor(user.getId(), SecurityEventType.GOOGLE_LINKED, SecurityOutcome.SUCCESS, how);
    }

    static String fullName(GoogleIdentity google, String email) {
        String name = google.name().isBlank() ? email.substring(0, Math.max(email.indexOf('@'), 1)) : google.name();
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).trim() : name;
    }

    private String newCode(OAuthHandoffKind kind, AppUser user, String subject, VerifiedGoogleSignIn verified) {
        String code = OAuthValues.random(OAuthValues.RANDOM_BYTES);
        Instant now = clock.instant();
        handoffRepository.save(OAuthHandoff.builder()
                .codeHash(OAuthValues.sha256Hex(code))
                .kind(kind)
                .user(user)
                .providerSubject(subject)
                .rememberMe(verified.rememberMe())
                .returnPath(verified.returnPath())
                .expiresAt(now.plus(kind == OAuthHandoffKind.SIGN_IN ? SIGN_IN_CODE_LIFETIME : LINK_CODE_LIFETIME))
                .createdAt(now)
                .build());
        return code;
    }

    private Optional<OAuthHandoff> findOpen(@Nullable String code, OAuthHandoffKind kind) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return handoffRepository.findByCodeHashAndKind(OAuthValues.sha256Hex(code.trim()), kind)
                .filter(handoff -> handoff.getUsedAt() == null && handoff.getExpiresAt().isAfter(now));
    }

    /**
     * GA-FR-19, GA-FR-21: result, code, the "remember me" choice (so the page
     * stores the session where password sign-in would) and the in-app page to
     * open afterwards.
     */
    private static String fragment(String result, String code, VerifiedGoogleSignIn verified) {
        return "result=" + result + "&code=" + code + "&remember=" + verified.rememberMe()
                + "&from=" + URLEncoder.encode(verified.returnPath(), StandardCharsets.UTF_8);
    }
}
