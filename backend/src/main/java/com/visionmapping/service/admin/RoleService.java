package com.visionmapping.service.admin;

import com.visionmapping.dto.request.AdminRoleChangeRequest;
import com.visionmapping.dto.request.RoleDeleteRequest;
import com.visionmapping.dto.request.RoleRequest;
import com.visionmapping.dto.response.PermissionResponse;
import com.visionmapping.dto.response.RoleResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.Role;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.CohortRepository;
import com.visionmapping.repository.RoleRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.notice.AccountNotice;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.stepup.StepUpAction;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.service.twofactor.TwoFactorRequirementService;
import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V23 (BRD 23.0.0, phase 4, UM-FR-08 to UM-FR-16): built-in and custom roles.
 *
 * <p>Only the Admin role can change roles (D-23-9): "Manage roles" exists on no
 * other role. Admin and Participant are locked (D-23-2, Q-09); Coach's
 * permissions can change but not its name; custom roles can change entirely.
 * Every change needs a reason and a fresh code, is logged with the permissions
 * added and removed, applies on the holders' next request (the permission cache
 * is cleared), and tells the holders by email.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    static final Set<String> LOCKED = Set.of(UserRole.ADMIN.name(), UserRole.USER.name());
    private static final String NOT_ALLOWED = "Only an admin can change roles.";

    /** UM-FR-07: ticking the first means ticking the second too. */
    private static final Map<Permission, Permission> NEEDS = Map.of(
            Permission.MANAGE_USERS, Permission.VIEW_USERS,
            Permission.DELETE_USERS, Permission.MANAGE_USERS,
            Permission.RESET_TWO_FACTOR, Permission.VIEW_USERS);

    private final RoleRepository roles;
    private final AppUserRepository users;
    private final CohortRepository cohorts;
    private final PermissionService permissions;
    private final EntityLookup lookup;
    private final StepUpService stepUpService;
    private final SecurityEventService securityEvents;
    private final AccountNotices notices;
    private final TwoFactorRequirementService twoFactorRequirement;
    private final RequestRateLimiter rateLimiter;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PermissionResponse> catalogue() {
        permissions.requireAny(lookup.currentUser(), NOT_ALLOWED, Permission.MANAGE_ROLES, Permission.VIEW_USERS);
        return Arrays.stream(Permission.values())
                .map(p -> new PermissionResponse(p.name(), p.group().name(), p.label(), p.description(),
                        p == Permission.MANAGE_ROLES, p == Permission.USE_OWN_PLAN))
                .toList();
    }

    /** UM-FR-08: readable with "Manage roles" or "View users". */
    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        permissions.requireAny(lookup.currentUser(), NOT_ALLOWED, Permission.MANAGE_ROLES, Permission.VIEW_USERS);
        return roles.findAllByOrderByBuiltInDescNameAsc().stream().map(this::toResponse).toList();
    }

    /** UM-FR-09. */
    public RoleResponse create(RoleRequest request, @Nullable String stepUp) {
        requireRoleManager();
        String name = request.name().trim();
        if (roles.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("A role named " + name + " already exists.");
        }
        Set<Permission> wanted = normalise(request.permissions());
        stepUpService.require(StepUpAction.ADMIN_ROLE_EDIT, stepUp);

        Role role = roles.saveAndFlush(new Role(newKey(name), name, blankToNull(request.description()), wanted));
        permissions.evict();
        securityEvents.record(SecurityEventType.ROLE_CREATED, SecurityOutcome.SUCCESS, "role", null,
                role.getKey() + " " + name + ": " + diff(EnumSet.noneOf(Permission.class), wanted) + "; reason: " + reason(request.reason()));
        return toResponse(role);
    }

    /** UM-FR-10 to UM-FR-13. */
    public RoleResponse update(String key, RoleRequest request, @Nullable String stepUp) {
        requireRoleManager();
        Role role = role(key);
        if (LOCKED.contains(role.getKey())) {
            throw new StateConflictException(role.getName() + " cannot be changed.");
        }
        String name = request.name().trim();
        String description = blankToNull(request.description());
        if (role.isBuiltIn() && (!name.equals(role.getName()) || !java.util.Objects.equals(description, role.getDescription()))) {
            throw new BusinessRuleException("A built-in role cannot be renamed; only its permissions can change.");
        }
        if (!role.isBuiltIn() && roles.existsByNameIgnoreCaseAndKeyNot(name, role.getKey())) {
            throw new BusinessRuleException("A role named " + name + " already exists.");
        }
        Set<Permission> before = EnumSet.copyOf(role.getPermissions().isEmpty() ? EnumSet.of(Permission.USE_OWN_PLAN) : role.getPermissions());
        Set<Permission> after = normalise(request.permissions());
        boolean losesCoaching = before.contains(Permission.COACH_COHORTS) && !after.contains(Permission.COACH_COHORTS);
        Map<Cohort, AppUser> handover = losesCoaching ? handover(role, request.cohortChanges()) : Map.of();
        stepUpService.require(StepUpAction.ADMIN_ROLE_EDIT, stepUp);

        boolean wasPrivileged = permissions.isPrivilegedRole(role.getKey());
        handover.forEach((cohort, coach) -> {
            cohort.setCoach(coach);
            securityEvents.record(SecurityEventType.COHORT_CHANGED, SecurityOutcome.SUCCESS, "cohort", cohort.getId(),
                    "coach " + (coach == null ? "removed" : "changed") + " (role " + role.getKey() + " can no longer coach)");
        });
        role.setName(name);
        role.setDescription(description);
        role.getPermissions().clear();
        role.getPermissions().addAll(after);
        roles.saveAndFlush(role);
        permissions.evict();

        boolean nowPrivileged = permissions.isPrivilegedRole(role.getKey());
        boolean permissionsChanged = !before.equals(after);
        for (AppUser holder : users.findByRole(role.getKey())) {
            if (nowPrivileged && !wasPrivileged) {
                twoFactorRequirement.roleGiven(holder);
            } else if (!nowPrivileged && wasPrivileged) {
                holder.setTwoFactorDueAt(null);
            }
            if (permissionsChanged) {
                notices.changed(AccountNotice.Kind.ROLE_UPDATED, holder, null, role.getName());
            }
        }
        securityEvents.record(SecurityEventType.ROLE_UPDATED, SecurityOutcome.SUCCESS, "role", null,
                role.getKey() + ": " + diff(before, after) + "; reason: " + reason(request.reason()));
        return toResponse(role);
    }

    /** UM-FR-14, UM-FR-15. */
    public void delete(String key, RoleDeleteRequest request, @Nullable String stepUp) {
        AppUser actor = requireRoleManager();
        Role role = role(key);
        if (role.isBuiltIn()) {
            throw new StateConflictException(role.getName() + " is built in and cannot be deleted.");
        }
        List<AppUser> holders = users.findByRole(role.getKey());
        Role target = null;
        if (!holders.isEmpty()) {
            if (request.moveTo() == null || request.moveTo().isBlank()) {
                throw new StateConflictException(holders.size() + " people have this role. Choose a role to move them to first.");
            }
            target = role(request.moveTo().trim().toUpperCase(Locale.ROOT));
            if (target.getKey().equals(role.getKey())) {
                throw new BusinessRuleException("Choose a different role to move them to.");
            }
            if (!permissions.permissionsOf(target.getKey()).contains(Permission.COACH_COHORTS)) {
                List<String> coached = holders.stream()
                        .flatMap(holder -> cohorts.findByCoach_IdAndArchivedFalseOrderByNameAsc(holder.getId()).stream())
                        .map(Cohort::getName).toList();
                if (!coached.isEmpty()) {
                    throw new StateConflictException("Some holders coach active cohorts (" + String.join(", ", coached)
                            + ") and " + target.getName() + " cannot coach. Give those cohorts another coach first.");
                }
            }
        }
        stepUpService.require(StepUpAction.ADMIN_ROLE_EDIT, stepUp);

        boolean privileged = target != null && permissions.isPrivilegedRole(target.getKey());
        for (AppUser holder : holders) {
            holder.setRole(target.getKey());
            holder.setRoleChangedAt(clock.instant());
            holder.setRoleChangedById(actor.getId());
            if (privileged) {
                twoFactorRequirement.roleGiven(holder);
            } else {
                holder.setTwoFactorDueAt(null);
            }
            users.save(holder);
            securityEvents.record(SecurityEventType.ROLE_CHANGED, SecurityOutcome.SUCCESS, "user", holder.getId(),
                    role.getKey() + " -> " + target.getKey() + "; reason: role " + role.getName() + " deleted");
            notices.changed(AccountNotice.Kind.ROLE_CHANGED, holder, null, target.getName());
        }
        users.flush();
        roles.delete(role);
        permissions.evict();
        securityEvents.record(SecurityEventType.ROLE_DELETED, SecurityOutcome.SUCCESS, "role", null,
                role.getKey() + " " + role.getName() + ": " + holders.size() + " moved"
                        + (target == null ? "" : " to " + target.getKey()) + "; reason: " + reason(request.reason()));
    }

    // ---------- helpers ----------

    private AppUser requireRoleManager() {
        AppUser actor = permissions.require(lookup.currentUser(), Permission.MANAGE_ROLES, NOT_ALLOWED);
        rateLimiter.check(RequestRateLimiter.Action.USER_ADMIN, String.valueOf(actor.getId()));
        return actor;
    }

    private Role role(String key) {
        return roles.findById(key.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + key));
    }

    /** UM-FR-01, UM-FR-07: known keys only; "Use own plan" always; needed permissions added; never "Manage roles". */
    static Set<Permission> normalise(List<String> keys) {
        EnumSet<Permission> set = EnumSet.of(Permission.USE_OWN_PLAN);
        for (String key : keys) {
            try {
                set.add(Permission.valueOf(key.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                throw new BusinessRuleException("Unknown permission: " + key);
            }
        }
        if (set.contains(Permission.MANAGE_ROLES)) {
            throw new BusinessRuleException("Manage roles belongs to the Admin role only.");
        }
        boolean added = true;
        while (added) {
            added = false;
            for (Map.Entry<Permission, Permission> need : NEEDS.entrySet()) {
                if (set.contains(need.getKey()) && set.add(need.getValue())) {
                    added = true;
                }
            }
        }
        return set;
    }

    /** "+A +B -C", or "no permission change". */
    static String diff(Set<Permission> before, Set<Permission> after) {
        List<String> parts = new ArrayList<>();
        after.stream().filter(p -> !before.contains(p)).forEach(p -> parts.add("+" + p.name()));
        before.stream().filter(p -> !after.contains(p)).forEach(p -> parts.add("-" + p.name()));
        return parts.isEmpty() ? "no permission change" : String.join(" ", parts);
    }

    /** A stable key from the name, for example "Program assistant" → "PROGRAM_ASSISTANT". */
    private String newKey(String name) {
        String base = trimUnderscores(Normalizer.normalize(name, Normalizer.Form.NFKD).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_"));
        // Cut to leave room for a "_2" suffix, then trim again so the cut never ends in "_".
        base = trimUnderscores(base.substring(0, Math.min(base.length(), 34)));
        if (base.isEmpty()) {
            base = "ROLE";
        }
        String key = base;
        for (int n = 2; roles.existsById(key) || UserRole.ADMIN.name().equals(key) || UserRole.USER.name().equals(key)
                || UserRole.COACH.name().equals(key); n++) {
            key = base + "_" + n;
        }
        return key;
    }

    /** Leading and trailing underscores removed, without a regex (no precedence or backtracking questions). */
    static String trimUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }

    private Map<Cohort, AppUser> handover(Role role,List<AdminRoleChangeRequest.CohortCoachChange> changes) {
        List<Cohort> coached = users.findByRole(role.getKey()).stream()
                .flatMap(holder -> cohorts.findByCoach_IdAndArchivedFalseOrderByNameAsc(holder.getId()).stream())
                .toList();
        if (coached.isEmpty()) {
            return Map.of();
        }
        Map<Long, AdminRoleChangeRequest.CohortCoachChange> byCohort = (changes == null ? List.<AdminRoleChangeRequest.CohortCoachChange>of() : changes)
                .stream().collect(Collectors.toMap(AdminRoleChangeRequest.CohortCoachChange::cohortId, Function.identity(), (a, b) -> b));
        List<String> missing = coached.stream().filter(c -> !byCohort.containsKey(c.getId())).map(Cohort::getName).toList();
        if (!missing.isEmpty()) {
            throw new StateConflictException("People with this role coach active cohorts: " + String.join(", ", missing)
                    + ". Choose a new coach, or none, for each.");
        }
        Map<Cohort, AppUser> handover = new LinkedHashMap<>();
        for (Cohort cohort : coached) {
            Long id = byCohort.get(cohort.getId()).newCoachId();
            if (id == null) {
                handover.put(cohort, null);
                continue;
            }
            AppUser coach = users.findById(id).orElseThrow(() -> new BusinessRuleException("The new coach was not found."));
            if (coach.getRole().equals(role.getKey()) || coach.getStatus() != UserStatus.ACTIVE
                    || !permissions.has(coach, Permission.COACH_COHORTS)) {
                throw new BusinessRuleException(coach.getFullName() + " cannot coach a cohort: they need an active account "
                        + "with another role that can coach.");
            }
            handover.put(cohort, coach);
        }
        return handover;
    }

    /** Built from the role itself (Admin: every permission), never from the cache, which may be mid-change. */
    private RoleResponse toResponse(Role role) {
        Set<Permission> held = UserRole.ADMIN.name().equals(role.getKey()) ? EnumSet.allOf(Permission.class)
                : role.getPermissions().isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(role.getPermissions());
        held.remove(UserRole.ADMIN.name().equals(role.getKey()) ? null : Permission.MANAGE_ROLES);
        boolean privileged = held.stream().anyMatch(permission -> permission != Permission.USE_OWN_PLAN);
        return new RoleResponse(role.getKey(), role.getName(), role.getDescription(), role.isBuiltIn(),
                !LOCKED.contains(role.getKey()), !role.isBuiltIn(), users.countByRole(role.getKey()), privileged,
                held.stream().sorted().map(Enum::name).toList());
    }

    private static String reason(String reason) {
        return reason.trim();
    }

    @Nullable
    private static String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
