package com.visionmapping.service.twofactor;

import com.visionmapping.repository.SignInChallengeRepository;
import com.visionmapping.repository.TrustedDeviceRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21: the daily clean-up. Unfinished setups go after 15 minutes (TF-FR-03);
 * challenges a day after they end; trusted devices a week after they expire or
 * are removed, so the Security page can still show a just-removed device.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwoFactorCleanupJob {

    static final Duration UNFINISHED_SETUP = Duration.ofMinutes(15);
    static final Duration CHALLENGE_KEPT = Duration.ofDays(1);
    static final Duration DEVICE_KEPT = Duration.ofDays(7);

    private final UserTwoFactorRepository settingsRepository;
    private final SignInChallengeRepository challengeRepository;
    private final TrustedDeviceRepository deviceRepository;
    private final Clock clock;

    @Scheduled(cron = "${app.two-factor.cleanup-cron:0 55 3 * * *}")
    @Transactional
    public void deleteOld() {
        Instant now = clock.instant();
        int setups = settingsRepository.deleteUnfinishedBefore(now.minus(UNFINISHED_SETUP))
                + settingsRepository.clearPendingBefore(now.minus(UNFINISHED_SETUP));
        int challenges = challengeRepository.deleteExpiredBefore(now.minus(CHALLENGE_KEPT));
        int devices = deviceRepository.deleteEndedBefore(now.minus(DEVICE_KEPT));
        if (setups + challenges + devices > 0) {
            log.info("Deleted {} unfinished two-factor setups, {} old sign-in challenges and {} old trusted devices",
                    setups, challenges, devices);
        }
    }
}
