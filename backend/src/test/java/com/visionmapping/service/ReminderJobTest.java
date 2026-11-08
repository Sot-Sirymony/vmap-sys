package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.repository.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BRD 13 EJ-FR-25: one user's failed reminder is logged and does not stop the others. */
@ExtendWith(MockitoExtension.class)
class ReminderJobTest {

    private static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");

    @Mock private AppUserRepository appUserRepository;
    @Mock private ReminderService reminderService;

    @Test
    void aFailingUserDoesNotStopTheNextOne() {
        ReminderJob job = new ReminderJob(appUserRepository, reminderService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(appUserRepository.findByRemindersEnabledTrueAndStatus(UserStatus.ACTIVE))
                .thenReturn(List.of(AppUser.builder().id(1L).build(), AppUser.builder().id(2L).build()));
        doThrow(new IllegalStateException("mail server down")).when(reminderService).remindIfDue(eq(1L), eq(NOW));

        assertThatCode(job::run).doesNotThrowAnyException();
        verify(reminderService).remindIfDue(2L, NOW);
    }
}
