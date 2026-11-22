package com.visionmapping.service.twofactor;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.util.UserScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 (BRD 21.0.0, D-21-2, TF-FR-32 to TF-FR-37): two-step sign-in is required
 * for admins and coaches. Each gets a set-up-by date (the release, or the day
 * the role is given, plus the grace period). Before it they may skip; from it
 * they may only set it up (TF-FR-34, enforced by {@code TwoFactorSetupInterceptor}).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TwoFactorRequirementService {

    /** TF-FR-30: after an admin reset, an admin or coach has 3 days. */
    static final Duration AFTER_RESET = Duration.ofDays(3);

    private final TwoFactorProperties properties;
    private final AppUserRepository appUserRepository;
    private final UserTwoFactorRepository settingsRepository;
    private final UserScope userScope;
    private final SecurityEventService securityEvents;
    private final Clock clock;

    /** TF-FR-32, TF-FR-37: at start-up, dates for admins and coaches who have none; none for participants. */
    @EventListener(ApplicationReadyEvent.class)
    public void assignMissingDueDates() {
        if (!properties.enabled()) {
            return;
        }
        int assigned = appUserRepository.assignTwoFactorDueDates(clock.instant().plus(properties.gracePeriod()));
        appUserRepository.clearParticipantTwoFactorDueDates();
        if (assigned > 0) {
            log.info("Two-step sign-in: {} admin or coach accounts must set it up within {} days", assigned,
                    properties.gracePeriod().toDays());
        }
    }

    /** TF-FR-32, TF-FR-35: a new coach or admin gets the grace period from today, unless already dated. */
    public void roleGiven(AppUser user) {
        if (user.getRole() != UserRole.USER && user.getTwoFactorDueAt() == null && !hasTwoFactor(user.getId())) {
            user.setTwoFactorDueAt(clock.instant().plus(properties.gracePeriod()));
        }
    }

    /** TF-FR-30. */
    public void afterReset(AppUser user) {
        if (user.getRole() != UserRole.USER) {
            user.setTwoFactorDueAt(clock.instant().plus(AFTER_RESET));
        }
    }

    /** TF-FR-34: an admin or coach past their date without two-step sign-in. */
    @Transactional(readOnly = true)
    public boolean setupRequiredNow(AppUser user) {
        Instant due = user.getTwoFactorDueAt();
        return properties.enabled() && user.getRole() != UserRole.USER && due != null
                && !due.isAfter(clock.instant()) && !hasTwoFactor(user.getId());
    }

    /** TF-FR-33: "Skip for now", logged so the admin report can show it. */
    public void skip() {
        AppUser user = userScope.currentUser();
        Instant due = user.getTwoFactorDueAt();
        long daysLeft = due == null ? 0 : Math.max(0, Duration.between(clock.instant(), due).toDays());
        securityEvents.recordFor(user.getId(), SecurityEventType.TWO_FACTOR_SETUP_SKIPPED, SecurityOutcome.SUCCESS,
                daysLeft + " days left");
    }

    private boolean hasTwoFactor(Long userId) {
        return settingsRepository.findById(userId).filter(UserTwoFactor::isEnabled).isPresent();
    }
}
