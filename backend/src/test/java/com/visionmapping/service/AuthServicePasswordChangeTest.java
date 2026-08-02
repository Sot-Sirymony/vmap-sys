package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.ChangePasswordRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.JwtService;
import com.visionmapping.util.UserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password change. A real BCryptPasswordEncoder is used rather than a mock —
 * the whole feature is "does the supplied password match the stored hash", and a
 * stubbed encoder would assert only that the code calls the method it obviously
 * calls.
 */
@ExtendWith(MockitoExtension.class)
class AuthServicePasswordChangeTest {

    private static final String CURRENT_PASSWORD = "CurrentPass123";
    private static final String NEW_PASSWORD = "BrandNewPass456";

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserScope userScope;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(appUserRepository, passwordEncoder, authenticationManager, jwtService, userScope);
        user = AppUser.builder()
                .id(7L)
                .fullName("Pat Owner")
                .email("pat@example.com")
                .passwordHash(passwordEncoder.encode(CURRENT_PASSWORD))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("replaces the stored hash when the current password is right")
    void changesPassword() {
        when(userScope.currentUser()).thenReturn(user);
        String originalHash = user.getPasswordHash();

        authService.changePassword(new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD));

        assertThat(user.getPasswordHash()).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        // The old password must stop working, which is the entire point.
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, user.getPasswordHash())).isFalse();
        verify(appUserRepository).save(user);
    }

    /**
     * The core requirement: holding a valid token is not enough. Without this
     * check an unattended browser or a stolen token could lock the real owner
     * out of their own account.
     */
    @Test
    @DisplayName("refuses the change when the current password is wrong")
    void rejectsWrongCurrentPassword() {
        when(userScope.currentUser()).thenReturn(user);
        String originalHash = user.getPasswordHash();

        assertThatThrownBy(() -> authService.changePassword(
                new ChangePasswordRequest("NotMyPassword", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Current password is incorrect.");

        assertThat(user.getPasswordHash()).isEqualTo(originalHash);
        verify(appUserRepository, never()).save(any());
    }

    /**
     * Reported rather than silently accepted: a form that says "password
     * changed" having changed nothing reads as though the new password took
     * effect when the old one is still in force.
     */
    @Test
    @DisplayName("refuses a new password identical to the current one")
    void rejectsUnchangedPassword() {
        when(userScope.currentUser()).thenReturn(user);

        assertThatThrownBy(() -> authService.changePassword(
                new ChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("New password must be different from the current password.");

        verify(appUserRepository, never()).save(any());
    }

    /**
     * A wrong current password must not be reported as a failed sign-in. That
     * is why the check does not go through the AuthenticationManager: its
     * BadCredentialsException maps to 401, which the frontend treats as an
     * expired session and signs the user out for a simple typo.
     */
    @Test
    @DisplayName("does not authenticate through the sign-in path")
    void doesNotDelegateToAuthenticationManager() {
        when(userScope.currentUser()).thenReturn(user);

        authService.changePassword(new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("changes the password of the caller, not of whoever the request names")
    void operatesOnTheAuthenticatedUser() {
        when(userScope.currentUser()).thenReturn(user);

        authService.changePassword(new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD));

        // The request carries no user identifier at all; the account comes from
        // the security context, so one user cannot re-password another.
        verify(userScope).currentUser();
        verify(appUserRepository).save(user);
    }
}
