package com.visionmapping.service.twofactor;

import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.dto.response.TwoFactorChallengeResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.SignInChallenge;
import com.visionmapping.entity.TwoFactorRecoveryCode;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.entity.enums.TwoFactorMethod;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.exception.TwoFactorException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.SignInChallengeRepository;
import com.visionmapping.repository.TwoFactorRecoveryCodeRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.AuthService;
import com.visionmapping.service.AuthSessionService;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.notice.AccountNotice;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.stepup.StepUpCodes;
import com.visionmapping.service.support.LoginAttemptLimiter;
import com.visionmapping.service.support.RequestRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 (BRD 21.0.0, TF-FR-10 to TF-FR-20): the second step of signing in.
 *
 * <ul>
 *   <li>{@link #start} runs after a correct first factor. With 2FA on and an
 *       untrusted browser it makes a 5-minute challenge instead of a session.</li>
 *   <li>{@link #verify} checks an authenticator, email or recovery code; only a
 *       correct one opens the session. Five wrong codes end the challenge, and
 *       each counts as a failed sign-in for the email (TF-FR-16).</li>
 *   <li>{@link #resend} sends a new email code, at most once a minute.</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SignInChallengeService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration EMAIL_CODE_LIFE = Duration.ofMinutes(10);

    private final TwoFactorProperties properties;
    private final UserTwoFactorRepository settingsRepository;
    private final SignInChallengeRepository challengeRepository;
    private final TwoFactorRecoveryCodeRepository recoveryRepository;
    private final AppUserRepository appUserRepository;
    private final TotpSecretVault vault;
    private final TrustedDeviceService trustedDevices;
    private final AuthSessionService authSessionService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final RequestRateLimiter rateLimiter;
    private final StepUpMailer mailer;
    private final AccountNotices accountNotices;
    private final SecurityEventService securityEvents;
    private final Clock clock;

    /**
     * TF-FR-10, TF-FR-18, TF-FR-22: the challenge when a second step is needed;
     * empty when 2FA is off (for everyone or for this account) or the browser is
     * a trusted device of this account.
     */
    public Optional<TwoFactorChallengeResponse> start(AppUser user, boolean rememberMe, String returnPath,
                                                      SignInOrigin origin, @Nullable String deviceToken) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        Optional<UserTwoFactor> settings = settingsRepository.findById(user.getId()).filter(UserTwoFactor::isEnabled);
        if (settings.isEmpty() || trustedDevices.isTrusted(user, deviceToken)) {
            return Optional.empty();
        }
        TwoFactorMethod method = settings.get().getMethod();
        Instant now = clock.instant();
        String token = TwoFactorCodes.newToken();
        SignInChallenge challenge = challengeRepository.save(SignInChallenge.builder()
                .tokenHash(TwoFactorCodes.sha256(token))
                .user(user)
                .method(method)
                .rememberMe(rememberMe)
                .returnPath(returnPath)
                .origin(origin)
                .expiresAt(now.plus(properties.challengeLifetime()))
                .createdAt(now)
                .build());
        boolean emailUnavailable = method == TwoFactorMethod.EMAIL && !sendEmailCode(challenge, user);
        securityEvents.recordFor(user.getId(), SecurityEventType.TWO_FACTOR_CHALLENGE_STARTED, SecurityOutcome.SUCCESS,
                origin.name().toLowerCase() + ", " + method.name().toLowerCase());
        return Optional.of(response(token, challenge, user, emailUnavailable));
    }

    /**
     * TF-FR-13 to TF-FR-17, TF-FR-21: checks the second factor and, when it is
     * right, opens the session. Wrong tries are saved even though the answer is
     * an error.
     */
    @Transactional(noRollbackFor = {TwoFactorException.class, TooManyRequestsException.class})
    public AuthResponse verify(@Nullable String challengeToken, @Nullable String code, @Nullable String recoveryCode,
                               boolean rememberDevice, @Nullable String clientIp, @Nullable String userAgent) {
        rateLimiter.check(RequestRateLimiter.Action.TWO_FACTOR_VERIFY, clientIp);
        SignInChallenge challenge = findOpen(challengeToken).orElseThrow(TwoFactorException::ended);
        AppUser user = challenge.getUser();
        Long userId = user.getId();
        String email = user.getEmail();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw TwoFactorException.ended();
        }
        try {
            loginAttemptLimiter.checkAllowed(email, clientIp);
        } catch (TooManyRequestsException locked) {
            securityEvents.recordForEmail(email, null, SecurityEventType.LOGIN_LOCKED, SecurityOutcome.DENIED, "two-factor");
            throw locked;
        }
        boolean usingRecovery = recoveryCode != null && !recoveryCode.isBlank();
        boolean right = usingRecovery ? useRecoveryCode(userId, recoveryCode) : checkCode(challenge, code);
        if (!right) {
            throw wrongCode(challenge, email, clientIp);
        }
        boolean remember = challenge.isRememberMe();
        SignInOrigin origin = challenge.getOrigin();
        boolean admin = user.getRole() == UserRole.ADMIN;
        if (challengeRepository.complete(challenge.getId(), clock.instant()) != 1) {
            throw TwoFactorException.ended();
        }
        // complete() clears the persistence context, so the account is read again.
        AppUser account = appUserRepository.findById(userId).orElseThrow(TwoFactorException::ended);
        loginAttemptLimiter.recordSuccess(email);
        securityEvents.recordFor(userId, SecurityEventType.TWO_FACTOR_VERIFIED, SecurityOutcome.SUCCESS,
                usingRecovery ? "recovery code" : challenge.getMethod().name().toLowerCase());
        if (usingRecovery) {
            long left = recoveryRepository.countByUser_IdAndUsedAtIsNull(userId);
            // TF-NFR-06: "admin" leads the detail, so a recovery code used on an admin account raises an alert.
            securityEvents.recordFor(userId, SecurityEventType.RECOVERY_CODE_USED, SecurityOutcome.SUCCESS,
                    (admin ? "admin, " : "") + left + " left");
            accountNotices.changed(AccountNotice.Kind.RECOVERY_CODE_USED, account, null, String.valueOf(left));
        }
        securityEvents.recordFor(userId, origin == SignInOrigin.GOOGLE
                        ? SecurityEventType.GOOGLE_SIGN_IN_SUCCEEDED : SecurityEventType.LOGIN_SUCCEEDED,
                SecurityOutcome.SUCCESS, (remember ? "remember me" : "this tab only") + ", two-factor");
        String deviceToken = rememberDevice ? trustedDevices.remember(account, userAgent) : null;
        return AuthService.response(authSessionService.open(account, remember), deviceToken);
    }

    /** TF-FR-12: a new email code for an email-method challenge, at most once a minute. */
    public TwoFactorChallengeResponse resend(@Nullable String challengeToken) {
        SignInChallenge challenge = findOpen(challengeToken).orElseThrow(TwoFactorException::ended);
        if (challenge.getMethod() != TwoFactorMethod.EMAIL) {
            throw new TwoFactorException(HttpStatus.BAD_REQUEST, TwoFactorException.INVALID,
                    "This sign-in uses an authenticator app; there is no code to send.", null);
        }
        rateLimiter.check(RequestRateLimiter.Action.TWO_FACTOR_RESEND, challenge.getTokenHash());
        AppUser user = challenge.getUser();
        boolean emailUnavailable = !sendEmailCode(challenge, user);
        return response(challengeToken.trim(), challenge, user, emailUnavailable);
    }

    private boolean checkCode(SignInChallenge challenge, @Nullable String code) {
        if (challenge.getMethod() == TwoFactorMethod.EMAIL) {
            String typed = StepUpCodes.normalise(code);
            return typed != null && challenge.getCodeHash() != null
                    && properties.matches(challenge.getCodeHash(), typed);
        }
        Long userId = challenge.getUser().getId();
        Optional<UserTwoFactor> settings = settingsRepository.findById(userId).filter(UserTwoFactor::isEnabled);
        Optional<String> secret = settings.flatMap(found -> vault.open(found.getTotpSecret()));
        if (secret.isEmpty()) {
            log.error("Two-factor secret for user {} cannot be opened; was SETTINGS_ENCRYPTION_KEY changed?", userId);
            return false;
        }
        OptionalLong step = TotpCodes.matchingStep(secret.get(), code, TotpCodes.step(clock.instant()),
                settings.get().getLastTotpStep());
        return step.isPresent() && settingsRepository.acceptStep(userId, step.getAsLong()) == 1;
    }

    /** TF-FR-15: each recovery code works once; compared in constant time against every unused one. */
    private boolean useRecoveryCode(Long userId, String typed) {
        String clean = TwoFactorCodes.normaliseRecoveryCode(typed);
        if (clean == null) {
            return false;
        }
        Long match = null;
        for (TwoFactorRecoveryCode candidate : recoveryRepository.findByUser_IdAndUsedAtIsNull(userId)) {
            if (properties.matches(candidate.getCodeHash(), clean)) {
                match = candidate.getId();
            }
        }
        return match != null && recoveryRepository.markUsed(match, clock.instant()) == 1;
    }

    /** TF-FR-16: counts the try, and ends the challenge after the fifth. */
    private TwoFactorException wrongCode(SignInChallenge challenge, String email, @Nullable String clientIp) {
        challenge.setAttempts(challenge.getAttempts() + 1);
        int left = Math.max(0, MAX_ATTEMPTS - challenge.getAttempts());
        Long userId = challenge.getUser().getId();
        loginAttemptLimiter.recordFailure(email, clientIp);
        securityEvents.recordForEmail(email, userId, SecurityEventType.TWO_FACTOR_FAILED, SecurityOutcome.DENIED,
                left + " tries left");
        if (left == 0) {
            challenge.setCompletedAt(clock.instant());
            securityEvents.recordFor(userId, SecurityEventType.TWO_FACTOR_LOCKED, SecurityOutcome.DENIED, "five wrong codes");
            return new TwoFactorException(HttpStatus.BAD_REQUEST, TwoFactorException.WRONG_CODE,
                    "That code is not right. Please sign in again.", 0);
        }
        return new TwoFactorException(HttpStatus.BAD_REQUEST, TwoFactorException.WRONG_CODE,
                "That code is not right. " + left + (left == 1 ? " try left." : " tries left."), left);
    }

    /** TF-FR-14, TF-FR-38: a new email code for the challenge; false when it could not be sent. */
    private boolean sendEmailCode(SignInChallenge challenge, AppUser user) {
        if (mailer.delivery() == StepUpMailer.Delivery.NONE) {
            challenge.setCodeHash(null);
            return false;
        }
        try {
            // Threat model (sign-in, D): a known password must not flood the person's mailbox. Over the limit the
            // answer looks like a mail fault, so it says no more than a normal sign-in would.
            rateLimiter.check(RequestRateLimiter.Action.TWO_FACTOR_EMAIL, "user:" + user.getId());
        } catch (TooManyRequestsException tooMany) {
            log.warn("Sign-in code emails for user {} are over the hourly limit; none sent", user.getId());
            challenge.setCodeHash(null);
            return false;
        }
        String code = StepUpCodes.newCode();
        try {
            mailer.sendSignInCode(user.getEmail(), code, EMAIL_CODE_LIFE.toMinutes());
        } catch (MailException exception) {
            log.error("Could not send a sign-in code to user {}", user.getId(), exception);
            challenge.setCodeHash(null);
            return false;
        }
        challenge.setCodeHash(properties.hash(code));
        return true;
    }

    private Optional<SignInChallenge> findOpen(@Nullable String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return challengeRepository.findByTokenHash(TwoFactorCodes.sha256(token.trim()))
                .filter(challenge -> challenge.getCompletedAt() == null && challenge.getExpiresAt().isAfter(now));
    }

    private static TwoFactorChallengeResponse response(String token, SignInChallenge challenge, AppUser user,
                                                       boolean emailUnavailable) {
        boolean email = challenge.getMethod() == TwoFactorMethod.EMAIL;
        return new TwoFactorChallengeResponse(TwoFactorChallengeResponse.STATUS, token, challenge.getMethod().name(),
                email ? StepUpCodes.mask(user.getEmail()) : null, emailUnavailable, challenge.getExpiresAt());
    }
}
