package com.visionmapping.service;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.repository.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** FR-77: finds who has reminders on. Whether today's is due is ReminderService's call. */
@Slf4j
@Component
@RequiredArgsConstructor
class ReminderJob {

    private final AppUserRepository appUserRepository;
    private final ReminderService reminderService;
    private final Clock clock;

    @Scheduled(cron = "${app.reminders.cron:0 */15 * * * *}")
    public void run() {
        Instant now = Instant.now(clock);
        for (AppUser user : appUserRepository.findByRemindersEnabledTrueAndStatus(UserStatus.ACTIVE)) {
            try {
                reminderService.remindIfDue(user.getId(), now);
            } catch (RuntimeException exception) {
                // Broad on purpose: one user's failure must not stop the reminders
                // of everyone after them. The cause is logged with its stack trace.
                log.error("Could not process the reminder for user {}", user.getId(), exception);
            }
        }
    }
}
