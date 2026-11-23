package com.visionmapping.security;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.twofactor.TwoFactorRequirementService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * V23 (BRD 23.0.0, UM-FR-41, Q-07): on a new installation, makes the account
 * named by {@code ADMIN_BOOTSTRAP_EMAIL} the first admin, at start-up.
 *
 * <p>It acts only while <b>no</b> active admin exists, so once there is one the
 * setting does nothing (with a warning) and cannot be used later to add admins.
 * The account must already exist (the person signs up first). The promotion is
 * logged as {@code ADMIN_BOOTSTRAPPED} and raises an alert; the new admin has the
 * usual 14 days to set up two-step sign-in (V21).
 */
@Slf4j
@Component
public class AdminBootstrap {

    private final AppUserRepository users;
    private final SecurityEventService securityEvents;
    private final TwoFactorRequirementService twoFactorRequirement;
    private final PermissionService permissions;
    private final String email;

    public AdminBootstrap(AppUserRepository users, SecurityEventService securityEvents,
                          TwoFactorRequirementService twoFactorRequirement, PermissionService permissions,
                          @Value("${app.security.admin-bootstrap-email:}") String email) {
        this.users = users;
        this.securityEvents = securityEvents;
        this.twoFactorRequirement = twoFactorRequirement;
        this.permissions = permissions;
        this.email = email == null ? "" : email.trim().toLowerCase();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            run();
        } catch (RuntimeException exception) {
            log.error("Could not apply ADMIN_BOOTSTRAP_EMAIL", exception);
        }
    }

    /** @return the account made admin, if any */
    @Transactional
    public Optional<AppUser> run() {
        if (email.isEmpty()) {
            return Optional.empty();
        }
        if (users.existsByRoleAndStatus(UserRole.ADMIN.name(), UserStatus.ACTIVE)) {
            log.warn("ADMIN_BOOTSTRAP_EMAIL is set but an active admin already exists; it was ignored. Remove the setting.");
            return Optional.empty();
        }
        Optional<AppUser> found = users.findByEmail(email).filter(user -> user.getStatus() == UserStatus.ACTIVE);
        if (found.isEmpty()) {
            log.warn("ADMIN_BOOTSTRAP_EMAIL names no active account; sign up with that email first, then restart.");
            return Optional.empty();
        }
        AppUser user = found.get();
        String before = user.getRole();
        user.setRole(UserRole.ADMIN);
        twoFactorRequirement.roleGiven(user);
        users.save(user);
        permissions.evict();
        securityEvents.recordFor(null, SecurityEventType.ADMIN_BOOTSTRAPPED, SecurityOutcome.SUCCESS, "user", user.getId(),
                before + " -> ADMIN");
        log.warn("User {} was made the first admin from ADMIN_BOOTSTRAP_EMAIL. Remove the setting now.", user.getId());
        return Optional.of(user);
    }
}
