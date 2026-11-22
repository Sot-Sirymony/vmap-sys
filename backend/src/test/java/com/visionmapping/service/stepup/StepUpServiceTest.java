package com.visionmapping.service.stepup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AuthSession;
import com.visionmapping.entity.StepUpChallenge;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.StepUpException;
import com.visionmapping.repository.AuthSessionRepository;
import com.visionmapping.repository.StepUpChallengeRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.mail.StepUpMailer.Delivery;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.RequestRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** V20 SV-NFR-03, D-20-3, D-20-4: what a protected action needs before it may go on. */
class StepUpServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");
    private static final Long SESSION_ID = 7L;

    private final StepUpChallengeRepository repository = mock(StepUpChallengeRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final StepUpMailer mailer = mock(StepUpMailer.class);
    private final SecurityEventService events = mock(SecurityEventService.class);
    private final EntityLookup lookup = mock(EntityLookup.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AppUser user = AppUser.builder().id(1L).email("me@example.com").passwordHash("x").fullName("Me")
            .role(UserRole.USER).status(UserStatus.ACTIVE).build();
    private final AuthSession session = AuthSession.builder().id(SESSION_ID).user(user).expiresAt(NOW.plus(Duration.ofDays(1))).build();

    private StepUpService service(boolean enabled) {
        return new StepUpService(repository, sessions, mailer, events, mock(RequestRateLimiter.class), lookup, clock, enabled, "secret",
                mock(com.visionmapping.service.twofactor.AuthenticatorCodeCheck.class));
    }

    @BeforeEach
    void signedIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserPrincipal(user, SESSION_ID), null, java.util.List.of()));
        when(lookup.userId()).thenReturn(1L);
        when(lookup.currentUser()).thenReturn(user);
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(mailer.delivery()).thenReturn(Delivery.EMAIL);
    }

    @AfterEach
    void signedOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void switchedOffOrWithoutMailTheActionGoesOnAndTheSkipIsLogged() {
        service(false).require(StepUpAction.DELETE_ACCOUNT, null, null);
        assertThat(service(false).active()).isFalse();
        verify(events, never()).record(any(), any(), any(), any(), any());

        when(mailer.delivery()).thenReturn(Delivery.NONE);
        service(true).require(StepUpAction.DELETE_ACCOUNT, null, null);
        assertThat(service(true).active()).isFalse();
        verify(events).record(SecurityEventType.STEP_UP_SKIPPED_NO_MAIL, SecurityOutcome.SUCCESS, "step_up", null, "DELETE_ACCOUNT");
    }

    @Test
    void theVerifiedWindowCoversSettingsActionsButNeverDeleteOrAdminActions() {
        session.setVerifiedUntil(NOW.plusSeconds(60));
        service(true).require(StepUpAction.EXPORT_DATA, null, null);
        service(true).require(StepUpAction.CHANGE_PASSWORD, 99L, null);
        assertThatThrownBy(() -> service(true).require(StepUpAction.DELETE_ACCOUNT, null, null))
                .isInstanceOf(StepUpException.class).extracting("code").isEqualTo(StepUpException.REQUIRED);
        assertThatThrownBy(() -> service(true).require(StepUpAction.ADMIN_AI_KEY, null, null)).isInstanceOf(StepUpException.class);

        session.setVerifiedUntil(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service(true).require(StepUpAction.EXPORT_DATA, null, null)).isInstanceOf(StepUpException.class);
    }

    @Test
    void aVerifiedCodeForThisActionIsUsedOnceAndAnyOtherCodeIsRefused() {
        StepUpChallenge good = challenge(StepUpAction.DELETE_ACCOUNT, NOW.minusSeconds(60), null);
        when(repository.findByIdAndUser_IdAndSession_Id(5L, 1L, SESSION_ID)).thenReturn(Optional.of(good));
        service(true).require(StepUpAction.DELETE_ACCOUNT, 5L, null);
        assertThat(good.getUsedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service(true).require(StepUpAction.DELETE_ACCOUNT, 5L, null))
                .as("a code is used only once").isInstanceOf(StepUpException.class);

        StepUpChallenge otherAction = challenge(StepUpAction.EXPORT_DATA, NOW.minusSeconds(60), null);
        when(repository.findByIdAndUser_IdAndSession_Id(6L, 1L, SESSION_ID)).thenReturn(Optional.of(otherAction));
        assertThatThrownBy(() -> service(true).require(StepUpAction.DELETE_ACCOUNT, 6L, null)).isInstanceOf(StepUpException.class);

        StepUpChallenge stale = challenge(StepUpAction.DELETE_ACCOUNT, NOW.minus(Duration.ofMinutes(6)), null);
        when(repository.findByIdAndUser_IdAndSession_Id(8L, 1L, SESSION_ID)).thenReturn(Optional.of(stale));
        assertThatThrownBy(() -> service(true).require(StepUpAction.DELETE_ACCOUNT, 8L, null))
                .as("delete needs a code verified within 5 minutes").isInstanceOf(StepUpException.class);

        StepUpChallenge unverified = challenge(StepUpAction.DELETE_ACCOUNT, null, null);
        when(repository.findByIdAndUser_IdAndSession_Id(9L, 1L, SESSION_ID)).thenReturn(Optional.of(unverified));
        assertThatThrownBy(() -> service(true).require(StepUpAction.DELETE_ACCOUNT, 9L, null)).isInstanceOf(StepUpException.class);
        assertThatThrownBy(() -> service(true).require(StepUpAction.DELETE_ACCOUNT, 404L, null)).isInstanceOf(StepUpException.class);
    }

    @Test
    void aReminderAddressCodeWorksOnlyForTheAddressItWasSentTo() {
        StepUpChallenge sent = challenge(StepUpAction.CONFIRM_REMINDER_EMAIL, NOW.minusSeconds(30), StepUpCodes.targetHash("new@work.org"));
        when(repository.findByIdAndUser_IdAndSession_Id(5L, 1L, SESSION_ID)).thenReturn(Optional.of(sent));
        assertThatThrownBy(() -> service(true).require(StepUpAction.CONFIRM_REMINDER_EMAIL, 5L, "other@work.org"))
                .isInstanceOf(StepUpException.class);
        assertThatThrownBy(() -> service(true).require(StepUpAction.CONFIRM_REMINDER_EMAIL, 5L, null)).isInstanceOf(StepUpException.class);
        service(true).require(StepUpAction.CONFIRM_REMINDER_EMAIL, 5L, " New@Work.org ");
        assertThat(sent.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void anEndedSessionOrNoSessionCannotVerify() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserPrincipal(user), null, java.util.List.of()));
        assertThatThrownBy(() -> service(true).require(StepUpAction.EXPORT_DATA, null, null)).isInstanceOf(StepUpException.class);

        signedIn();
        session.setRevokedAt(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service(true).require(StepUpAction.EXPORT_DATA, null, null)).isInstanceOf(StepUpException.class);
    }

    @Test
    void oldCodesAreCleanedUpAfterAWeek() {
        when(repository.deleteCreatedBefore(any())).thenReturn(3, 0);
        service(true).deleteOldChallenges();
        service(true).deleteOldChallenges();
        verify(repository, org.mockito.Mockito.times(2)).deleteCreatedBefore(NOW.minus(Duration.ofDays(7)));
        verify(mailer, never()).sendCode(anyString(), anyString(), any(), eq(false), org.mockito.ArgumentMatchers.anyLong());
        verify(events, never()).record(any(), any(), any(), isNull(), any());
    }

    private StepUpChallenge challenge(StepUpAction action, Instant verifiedAt, String targetHash) {
        return StepUpChallenge.builder().id(1L).user(user).session(session).action(action).codeHash("h").targetHash(targetHash)
                .expiresAt(NOW.plus(Duration.ofMinutes(5))).createdAt(NOW.minus(Duration.ofMinutes(9))).verifiedAt(verifiedAt).build();
    }
}
