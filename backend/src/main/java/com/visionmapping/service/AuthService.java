package com.visionmapping.service;

import com.visionmapping.dto.request.ChangePasswordRequest;
import com.visionmapping.dto.request.LoginRequest;
import com.visionmapping.dto.request.RegisterRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.SessionEndedException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.service.support.LoginAttemptLimiter;
import com.visionmapping.util.UserScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
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
        return response(authSessionService.open(saved, request.remember()));
    }

    /**
     * V10 (SEC-07-01): the attempt limit is checked before the password, and
     * for any email typed, so a locked email answers the same way whether or not
     * it has an account. A disabled account fails here too (SEC-01-05), with the
     * same "Invalid email or password." answer as a wrong password.
     */
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase();
        loginAttemptLimiter.checkAllowed(email, clientIp);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException exception) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            throw exception;
        }
        loginAttemptLimiter.recordSuccess(email);
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password."));
        return response(authSessionService.open(user, request.remember()));
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
    public void changePassword(ChangePasswordRequest request) {
        AppUser user = userScope.currentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect.");
        }

        // Otherwise the form reports success having changed nothing, which reads
        // as though the new password took effect when it is the old one.
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);

        // V10 (SEC-07-03): every other session ends, so a device someone else
        // signed in on loses access. The session making the change stays open.
        authSessionService.endOtherSessions(user.getId(), currentSessionId());
    }

    private static Long currentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? principal.getSessionId()
                : null;
    }

    private AuthResponse response(AuthSessionService.Tokens tokens) {
        AppUser user = tokens.user();
        return new AuthResponse(
                tokens.accessToken(),
                "Bearer",
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                // FR-39.6: the theme travels with the session so the first paint
                // after signing in is already the user's own appearance.
                AppearancePreferenceService.toResponse(user),
                tokens.refreshToken(),
                tokens.expiresInSeconds()
        );
    }
}
