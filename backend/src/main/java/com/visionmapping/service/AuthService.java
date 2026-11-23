package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.dto.request.ChangePasswordRequest;
import com.visionmapping.dto.request.LoginRequest;
import com.visionmapping.dto.request.RegisterRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.dto.response.SignInAnswer;
import com.visionmapping.dto.response.TwoFactorChallengeResponse;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.service.twofactor.SignInChallengeService;
import com.visionmapping.service.twofactor.TrustedDeviceService;
import java.util.Optional;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.SessionEndedException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.service.support.LoginAttemptLimiter;
import com.visionmapping.service.support.PasswordPolicy;
import com.visionmapping.util.UserScope;
import com.visionmapping.service.notice.AccountNotice;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.stepup.StepUpAction;
import org.springframework.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, sign-in, token refresh, sign-out and password change. Passwords
 * are stored only as hashes; sign-in attempts are rate-limited per email and per
 * network, and a changed password ends every other session.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserScope userScope;
    private final CohortService cohortService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final AuthSessionService authSessionService;
    private final SecurityEventService securityEvents;
    private final PasswordPolicy passwordPolicy;
    private final StepUpService stepUpService;
    private final AccountNotices accountNotices;
    private final SignInChallengeService signInChallenges;
    private final TrustedDeviceService trustedDevices;
    private final com.visionmapping.service.admin.SecurityAccessService securityAccess;
    private final com.visionmapping.service.twofactor.TwoFactorRequirementService twoFactorRequirement;

    public AuthResponse register(RegisterRequest request) {
        // V24 (SA-FR-11): with password sign-in off, new accounts come through Google only.
        securityAccess.requireSignUpAllowed(SignInOrigin.PASSWORD);
        String email = request.email().trim().toLowerCase();
        passwordPolicy.check(request.password(), email);
        if (appUserRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already registered.");
        }
        boolean hasCode = request.joinCode() != null && !request.joinCode().isBlank();
        Cohort cohort = hasCode ? cohortService.cohortByCode(request.joinCode()) : null;

        AppUser user = AppUser.builder()
                .fullName(request.fullName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .cohort(cohort)
                .build();
        AppUser saved = appUserRepository.save(user);
        // V24 (SA-FR-14): with two-step sign-in required for everyone, a new account gets its set-up date.
        twoFactorRequirement.roleGiven(saved);
        securityEvents.recordFor(saved.getId(), SecurityEventType.REGISTERED, SecurityOutcome.SUCCESS,
                cohort == null ? null : "joined a cohort");
        return response(authSessionService.open(saved, request.remember(), SignInOrigin.PASSWORD));
    }

    /**
     * V10 (SEC-07-01): the attempt limit is checked before the password, and
     * for any email typed, so a locked email answers the same way whether or not
     * it has an account. A disabled account fails here too (SEC-01-05), with the
     * same "Invalid email or password." answer as a wrong password.
     */
    public SignInAnswer login(LoginRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase();
        try {
            loginAttemptLimiter.checkAllowed(email, clientIp);
        } catch (TooManyRequestsException locked) {
            securityEvents.recordForEmail(email, null, SecurityEventType.LOGIN_LOCKED, SecurityOutcome.DENIED, null);
            throw locked;
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (org.springframework.security.authentication.DisabledException blocked) {
            // V23 (UM-FR-32): the password was right; not counted as a failed guess.
            securityEvents.recordForEmail(email, appUserRepository.findByEmail(email).map(AppUser::getId).orElse(null),
                    SecurityEventType.LOGIN_FAILED, SecurityOutcome.DENIED, "account blocked");
            throw new com.visionmapping.exception.AccountBlockedException();
        } catch (AuthenticationException exception) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            // V10 (SEC-09-01): the reason stays in the log, never in the answer.
            securityEvents.recordForEmail(email, appUserRepository.findByEmail(email).map(AppUser::getId).orElse(null),
                    SecurityEventType.LOGIN_FAILED, SecurityOutcome.DENIED, exception.getClass().getSimpleName());
            throw exception;
        }
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password."));
        // V24 (SA-FR-10): only after the right password, so a guesser learns nothing; not counted as a failed guess.
        if (!securityAccess.allows(user, SignInOrigin.PASSWORD)) {
            securityEvents.recordFor(user.getId(), SecurityEventType.LOGIN_FAILED, SecurityOutcome.DENIED, "password sign-in off");
            throw new com.visionmapping.exception.SignInMethodOffException(
                    com.visionmapping.exception.SignInMethodOffException.PASSWORD_OFF);
        }
        // V21 (TF-FR-10, TF-FR-16): with 2FA on, the password alone opens no session, and the failure count is
        // reset only after the second step, so a known password cannot be used to guess codes without limit.
        Optional<TwoFactorChallengeResponse> challenge = signInChallenges.start(user, request.remember(), "/",
                SignInOrigin.PASSWORD, request.deviceToken());
        if (challenge.isPresent()) {
            return challenge.get();
        }
        loginAttemptLimiter.recordSuccess(email);
        securityEvents.recordFor(user.getId(), SecurityEventType.LOGIN_SUCCEEDED, SecurityOutcome.SUCCESS,
                request.remember() ? "remember me" : "this tab only");
        return response(authSessionService.open(user, request.remember(), SignInOrigin.PASSWORD));
    }

    /**
     * V10 (SEC-07-04): a new access token for an open session. Public: it is
     * called when the access token has run out, so the refresh token is the
     * only credential.
     *
     * <p>Not rolled back on SessionEndedException: when a reused refresh token
     * ends the session, that revocation must be saved even though the call fails.
     */
    @Transactional(noRollbackFor = SessionEndedException.class)
    public AuthResponse refresh(String refreshToken) {
        return response(authSessionService.refresh(refreshToken));
    }

    /** V10 (SEC-07-03): ends the session the refresh token belongs to. */
    public void logout(String refreshToken) {
        authSessionService.logout(refreshToken);
    }

    /**
     * Sets a new password for the signed-in user, after proving they know the
     * current one.
     *
     * The old password is verified against the stored hash rather than through
     * the AuthenticationManager. Authenticating here would be the more familiar
     * shape, but a failure would surface as BadCredentialsException, which the
     * global handler answers with 401 and the message "Invalid email or
     * password." — the wording for a failed sign-in, and a status the frontend
     * treats as an expired session. On this endpoint that would sign the user out
     * mid-change for the ordinary mistake of mistyping their old password.
     */
    @SecurityAudit(value = SecurityEventType.PASSWORD_CHANGED, target = "user")
    public void changePassword(ChangePasswordRequest request, @Nullable String stepUp) {
        // V20 SV-FR-01: the email code first, so this is no password oracle for someone holding the session.
        stepUpService.require(StepUpAction.CHANGE_PASSWORD, stepUp);
        AppUser user = userScope.currentUser();

        // V14 (D-14-3): an account created with Google has no password anyone
        // knows; the email code above is its proof, and this sets its first one.
        if (user.isPasswordSet()) {
            String current = request.currentPassword();
            if (current == null || current.isBlank() || !passwordEncoder.matches(current, user.getPasswordHash())) {
                throw new BusinessRuleException("Current password is incorrect.");
            }
            // Otherwise the form reports success having changed nothing, which reads
            // as though the new password took effect when it is the old one.
            if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
                throw new BusinessRuleException("New password must be different from the current password.");
            }
        }
        passwordPolicy.check(request.newPassword(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordSet(true);
        appUserRepository.save(user);

        // V10 (SEC-07-03): every other session ends, so a device someone else
        // signed in on loses access. The session making the change stays open.
        authSessionService.endOtherSessions(user.getId(), currentSessionId());
        // V21 (TF-FR-24): remembered browsers must pass the second step again.
        trustedDevices.removeAll(user.getId(), "password changed");
        // V20 SV-FR-28: a notice to the account email once this is saved.
        accountNotices.changed(AccountNotice.Kind.PASSWORD_CHANGED, user);
    }

    private static Long currentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? principal.getSessionId()
                : null;
    }

    /** The sign-in answer for an opened session; also used by Google sign-in (V14 GA-FR-20). */
    public static AuthResponse response(AuthSessionService.Tokens tokens) {
        return response(tokens, null);
    }

    /** V21 (TF-FR-21): the same answer, with the device token after "Remember this device". */
    public static AuthResponse response(AuthSessionService.Tokens tokens, @Nullable String deviceToken) {
        AppUser user = tokens.user();
        return new AuthResponse(
                tokens.accessToken(),
                "Bearer",
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                tokens.permissions(),
                // FR-39.6: the theme travels with the session so the first paint
                // after signing in is already the user's own appearance.
                AppearancePreferenceService.toResponse(user),
                tokens.refreshToken(),
                tokens.expiresInSeconds(),
                deviceToken
        );
    }
}
