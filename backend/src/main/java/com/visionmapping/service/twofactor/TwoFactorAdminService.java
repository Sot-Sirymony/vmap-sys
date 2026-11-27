package com.visionmapping.service.twofactor;

import com.visionmapping.dto.response.TwoFactorReportRow;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.TwoFactorRecoveryCodeRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.AuthSessionService;
import com.visionmapping.service.notice.AccountNotice;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.stepup.StepUpAction;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.util.UserScope;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 (BRD 21.0.0, D-21-5, TF-FR-28 to TF-FR-30, TF-FR-36): the admin's view of
 * two-step sign-in, and resetting it for someone who lost their device and
 * their recovery codes. An admin may reset, never read or use, another person's
 * second factor; the person must still pass their first factor and set it up again.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TwoFactorAdminService {

    static final String SESSION_REASON = "TWO_FACTOR_RESET";

    private final UserScope userScope;
    private final AppUserRepository appUserRepository;
    private final UserTwoFactorRepository settingsRepository;
    private final TwoFactorRecoveryCodeRepository recoveryRepository;
    private final TrustedDeviceService trustedDevices;
    private final TwoFactorRequirementService requirement;
    private final AuthSessionService authSessionService;
    private final StepUpService stepUpService;
    private final AccountNotices accountNotices;
    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final PermissionService permissions;

    /** TF-FR-36: admins and coaches first, then anyone else with two-step sign-in on. */
    @Transactional(readOnly = true)
    public List<TwoFactorReportRow> report() {
        permissions.require(userScope.currentUser(), Permission.RESET_TWO_FACTOR, "Only admins can see two-step sign-in for other people.");
        Instant now = clock.instant();
        Map<Long, UserTwoFactor> settings = new LinkedHashMap<>();
        settingsRepository.findAll().stream().filter(UserTwoFactor::isEnabled).forEach(found -> settings.put(found.getUserId(), found));
        List<AppUser> people = new ArrayList<>(appUserRepository.findByRoleInOrderByRoleAscFullNameAsc(permissions.privilegedRoleKeys()));
        appUserRepository.findAllById(settings.keySet()).stream()
                .filter(user -> !permissions.isPrivileged(user))
                .sorted(Comparator.comparing(AppUser::getFullName))
                .forEach(people::add);
        // V24 (SA-FR-14): with two-step sign-in required for everyone, participants still setting it up are listed too.
        if (requirement.everyone()) {
            java.util.Set<Long> listed = new java.util.HashSet<>(people.stream().map(AppUser::getId).toList());
            appUserRepository.findByTwoFactorDueAtIsNotNullAndRoleNotInOrderByFullNameAsc(permissions.privilegedRoleKeys()).stream()
                    .filter(user -> listed.add(user.getId()))
                    .forEach(people::add);
        }
        return people.stream().map(user -> {
            Optional<UserTwoFactor> found = Optional.ofNullable(settings.get(user.getId()));
            Instant due = requirement.requiredFor(user) ? user.getTwoFactorDueAt() : null;
            return new TwoFactorReportRow(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                    found.isPresent(), found.map(value -> value.getMethod().name()).orElse(null), due,
                    found.isEmpty() && due != null && !due.isAfter(now));
        }).toList();
    }

    /**
     * TF-FR-28 to TF-FR-30: removes the person's secret, recovery codes and
     * trusted devices, ends their sessions, tells them, logs the reason and
     * raises an alert. Needs the admin's own fresh code; never for the admin's
     * own account.
     */
    public void reset(Long userId, @Nullable String reason) {
        AppUser admin = userScope.currentUser();
        permissions.require(admin, Permission.RESET_TWO_FACTOR, "Only admins can reset two-step sign-in.");
        if (admin.getId().equals(userId)) {
            throw new BusinessRuleException("You cannot reset your own two-step sign-in. Ask another admin.");
        }
        AppUser person = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found."));
        if (settingsRepository.findById(userId).isEmpty()) {
            throw new BusinessRuleException("This person does not use two-step sign-in.");
        }
        stepUpService.requireFromRequest(StepUpAction.ADMIN_TWO_FACTOR_RESET);
        settingsRepository.deleteById(userId);
        recoveryRepository.deleteByUserId(userId);
        trustedDevices.removeAll(userId, "reset by an admin");
        authSessionService.endAllSessions(userId, SESSION_REASON);
        // The queries above clear the persistence context, so the account is read again before it changes.
        person = appUserRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Person not found."));
        requirement.afterReset(person);
        String why = reason == null ? "" : reason.trim();
        securityEvents.record(SecurityEventType.TWO_FACTOR_RESET_BY_ADMIN, SecurityOutcome.SUCCESS, "user", userId,
                why.length() > 200 ? why.substring(0, 200) : why);
        accountNotices.changed(AccountNotice.Kind.TWO_FACTOR_CHANGED, person, null, "reset by an administrator");
    }
}
