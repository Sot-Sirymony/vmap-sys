package com.visionmapping.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.SecurityAccessUpdateRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.SecuritySettings;
import com.visionmapping.entity.enums.IdentityProvider;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.entity.enums.TwoFactorScope;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.repository.AuthSessionRepository;
import com.visionmapping.repository.SecuritySettingsRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.oauth.GoogleOAuthSettings;
import com.visionmapping.service.stepup.StepUpAction;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.RequestRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** V24 (SA-FR-05 to SA-FR-08): a successful change, and refusals that never use the step-up code. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityAccessServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:00:00Z");

    @Mock private SecuritySettingsRepository repository;
    @Mock private PermissionService permissions;
    @Mock private EntityLookup lookup;
    @Mock private GoogleOAuthSettings googleSettings;
    @Mock private StepUpService stepUpService;
    @Mock private AuthSessionRepository sessions;
    @Mock private SecurityEventService securityEvents;
    @Mock private RequestRateLimiter rateLimiter;
    @Mock private com.visionmapping.service.twofactor.TwoFactorRequirementService twoFactorRequirement;

    private SecurityAccessService service;
    private SecuritySettings row;
    private final AppUser admin = AppUser.builder().id(7L).fullName("Admin A").role("ADMIN").build();

    @BeforeEach
    void setUp() {
        service = new SecurityAccessService(repository, permissions, lookup, googleSettings, stepUpService, sessions,
                securityEvents, rateLimiter, twoFactorRequirement, Clock.fixed(NOW, ZoneOffset.UTC));
        row = new SecuritySettings();
        row.setId(SecuritySettings.SINGLE_ROW_ID);
        row.setPasswordSignIn(true);
        row.setGoogleSignIn(true);
        row.setTwoFactorScope(TwoFactorScope.PRIVILEGED);
        when(repository.findById(SecuritySettings.SINGLE_ROW_ID)).thenReturn(Optional.of(row));
        when(lookup.currentUser()).thenReturn(admin);
        when(permissions.require(admin, Permission.MANAGE_ROLES, "Only an admin can see or change sign-in methods.")).thenReturn(admin);
        when(permissions.requireAny(eq(admin), anyString(), any(Permission[].class))).thenReturn(admin);
        when(permissions.has(admin, Permission.MANAGE_ROLES)).thenReturn(true);
        when(googleSettings.enabled()).thenReturn(true);
        when(stepUpService.active()).thenReturn(true);
    }

    @Test
    void turningPasswordOffSavesWhoDidItEndsPasswordSessionsAndLogsTheReason() {
        when(sessions.revokeBySignInMethod(SignInOrigin.PASSWORD, "ADMIN", SecurityAccessService.SESSION_ENDED_REASON, NOW))
                .thenReturn(3);

        var answer = service.update(new SecurityAccessUpdateRequest(false, true, null, "  Moving everyone to Google  "));

        verify(stepUpService).requireFromRequest(StepUpAction.ADMIN_SECURITY_ACCESS);
        verify(rateLimiter).check(RequestRateLimiter.Action.USER_ADMIN, "7");
        verify(sessions, never()).revokeBySignInMethod(eq(SignInOrigin.GOOGLE), anyString(), anyString(), any());
        verify(securityEvents).record(SecurityEventType.SECURITY_ACCESS_CHANGED, SecurityOutcome.SUCCESS, "security-settings", null,
                "password on, Google on, two-step PRIVILEGED -> password off, Google on, two-step PRIVILEGED; 3 sessions ended; "
                        + "reason: Moving everyone to Google");
        assertThat(row.isPasswordSignIn()).isFalse();
        assertThat(row.getUpdatedBy()).isSameAs(admin);
        assertThat(answer.passwordSignIn()).isFalse();
        assertThat(answer.canChange()).isTrue();
    }

    @Test
    void refusalsComeBeforeTheStepUpCode() {
        when(repository.countProviderOnly(UserStatus.ACTIVE, "ADMIN", IdentityProvider.GOOGLE)).thenReturn(1L);
        assertThatThrownBy(() -> service.update(new SecurityAccessUpdateRequest(true, false, null, "Google is retired")))
                .isInstanceOf(StateConflictException.class).hasMessageStartingWith("1 person can only sign in with Google");

        when(googleSettings.enabled()).thenReturn(false);
        assertThatThrownBy(() -> service.update(new SecurityAccessUpdateRequest(false, true, null, "Google only from now")))
                .isInstanceOf(StateConflictException.class).hasMessageContaining("not set up on this server");

        verify(stepUpService, never()).requireFromRequest(any());
        verify(sessions, never()).revokeBySignInMethod(any(), anyString(), anyString(), any());
        verify(securityEvents, never()).record(any(), any(), anyString(), isNull(), anyString());
        assertThat(row.isGoogleSignIn()).isTrue();
    }

    @Test
    void adminsAreNeverAffectedAndANewAccountFollowsTheSetting() {
        row.setGoogleSignIn(false);
        AppUser participant = AppUser.builder().id(8L).role("USER").build();
        assertThat(service.allows(admin, SignInOrigin.GOOGLE)).isTrue();
        assertThat(service.allows(participant, SignInOrigin.GOOGLE)).isFalse();
        assertThat(service.allows(participant, SignInOrigin.PASSWORD)).isTrue();
        assertThatThrownBy(() -> service.requireSignUpAllowed(SignInOrigin.GOOGLE))
                .hasMessage("Google sign-in is turned off. Sign in with your password.");
    }
}
