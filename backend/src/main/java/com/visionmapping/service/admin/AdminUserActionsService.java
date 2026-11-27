package com.visionmapping.service.admin;

import com.visionmapping.dto.request.AdminReasonRequest;
import com.visionmapping.dto.request.AdminRoleChangeRequest;
import com.visionmapping.dto.request.AdminUserCreateRequest;
import com.visionmapping.dto.request.AdminUserDeleteRequest;
import com.visionmapping.dto.request.AdminUserUpdateRequest;
import com.visionmapping.service.AccountService;
import com.visionmapping.service.PasswordResetService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.service.twofactor.TwoFactorRequirementService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V23 (BRD 23.0.0, phase 3): changing a person's role, blocking and unblocking,
 * for accounts with the "Manage users" permission.
 *
 * <p>Every action passes the same checks, in this order, before anything changes:
 * <ol>
 *   <li>the actor has "Manage users" and is within the rate limit (UM-FR-44);</li>
 *   <li>the target exists (404) and is not the actor (UM-FR-39, 403);</li>
 *   <li><b>no escalation</b> (D-23-9): the actor holds every permission of the
 *       target's role, and of any role they give, so only an Admin can act on an
 *       Admin or give the Admin role (403);</li>
 *   <li>the last active Admin is never demoted or blocked (UM-FR-40, 409);</li>
 *   <li>a fresh "Verify it's you" code (D-23-4), checked last so a refused change
 *       does not use the code up.</li>
 * </ol>
 * Each change is logged with the reason and the person is told by email (D-23-8).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserActionsService {

    static final String SESSION_END_REASON = "BLOCKED";

    private final AppUserRepository users;
    private final CohortRepository cohorts;
    private final com.visionmapping.repository.RoleRepository roles;
    private final PermissionService permissions;
    private final EntityLookup lookup;
    private final StepUpService stepUpService;
    private final SecurityEventService securityEvents;
    private final AuthSessionService authSessionService;
    private final AccountNotices notices;
    private final TwoFactorRequirementService twoFactorRequirement;
    private final RequestRateLimiter rateLimiter;
    private final Clock clock;
    private final InvitationService invitations;
    private final PasswordResetService passwordResets;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    static final String NO_MAIL = "Invitations cannot be sent because email is not set up on this server. "
            + "No account was created.";
    static final String EMAIL_TAKEN = "This email already has an account.";

    /** UM-FR-29, UM-FR-30. */
    public void changeRole(Long id, AdminRoleChangeRequest request) {
        AppUser actor = requireManager();
        AppUser target = target(id, actor);
        Role newRole = roles.findById(request.role().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessRuleException("Unknown role: " + request.role()));
        if (newRole.getKey().equals(target.getRole())) {
            throw new BusinessRuleException("This person already has that role.");
        }
        requireCanGive(actor, newRole);
        if (target.hasRole(UserRole.ADMIN)) {
            requireAnotherActiveAdmin(target);
        }
        Map<Cohort, AppUser> handover = cohortHandover(target, newRole,
                request.cohortChanges() == null ? List.of() : request.cohortChanges());
        stepUpService.requireFromRequest(StepUpAction.ADMIN_ROLE_CHANGE);

        handover.forEach((cohort, coach) -> {
            cohort.setCoach(coach);
            securityEvents.record(SecurityEventType.COHORT_CHANGED, SecurityOutcome.SUCCESS, "cohort", cohort.getId(),
                    "coach " + (coach == null ? "removed" : "changed") + " (role change of user " + target.getId() + ")");
        });
        String oldRole = target.getRole();
        target.setRole(newRole.getKey());
        target.setRoleChangedAt(clock.instant());
        target.setRoleChangedById(actor.getId());
        if (!permissions.isPrivilegedRole(newRole.getKey())) {
            // A participant is not asked for two-step sign-in (V21 D-21-2), unless everyone is (V24 D-24-4).
            twoFactorRequirement.roleTaken(target);
        } else {
            // V21 TF-FR-32, UM-FR-06: a privileged role must set up two-step sign-in within the grace period.
            twoFactorRequirement.roleGiven(target);
        }
        users.save(target);
        securityEvents.record(SecurityEventType.ROLE_CHANGED, SecurityOutcome.SUCCESS, "user", target.getId(),
                oldRole + " -> " + newRole.getKey() + "; reason: " + request.reason().trim());
        notices.changed(AccountNotice.Kind.ROLE_CHANGED, target, null, newRole.getName());
    }

    /** UM-FR-31: no sign-in, and every open session ends at once. */
    public void block(Long id, AdminReasonRequest request) {
        AppUser actor = requireManager();
        AppUser target = target(id, actor);
        if (target.getStatus() == UserStatus.DISABLED) {
            throw new StateConflictException("This account is already blocked.");
        }
        if (target.hasRole(UserRole.ADMIN)) {
            requireAnotherActiveAdmin(target);
        }
        stepUpService.requireFromRequest(StepUpAction.ADMIN_USER_BLOCK);

        target.setStatus(UserStatus.DISABLED);
        target.setBlockedAt(clock.instant());
        target.setBlockedById(actor.getId());
        users.save(target);
        authSessionService.endAllSessions(target.getId(), SESSION_END_REASON);
        securityEvents.record(SecurityEventType.USER_BLOCKED, SecurityOutcome.SUCCESS, "user", target.getId(),
                target.getRole() + "; reason: " + request.reason().trim());
        notices.changed(AccountNotice.Kind.ACCOUNT_BLOCKED, target);
    }

    /** UM-FR-33: the person signs in again; old sessions are not restored. */
    public void unblock(Long id, AdminReasonRequest request) {
        AppUser actor = requireManager();
        AppUser target = target(id, actor);
        if (target.getStatus() == UserStatus.ACTIVE) {
            throw new StateConflictException("This account is not blocked.");
        }
        stepUpService.requireFromRequest(StepUpAction.ADMIN_USER_BLOCK);

        target.setStatus(UserStatus.ACTIVE);
        target.setBlockedAt(null);
        target.setBlockedById(null);
        users.save(target);
        securityEvents.record(SecurityEventType.USER_UNBLOCKED, SecurityOutcome.SUCCESS, "user", target.getId(),
                target.getRole() + "; reason: " + request.reason().trim());
        notices.changed(AccountNotice.Kind.ACCOUNT_UNBLOCKED, target);
    }

    /**
     * UM-FR-22 to UM-FR-25: a new account with no usable password, and an
     * invitation to choose one. Answers the new account's id.
     */
    public Long create(AdminUserCreateRequest request) {
        AppUser actor = requireManager();
        String roleKey = request.role() == null || request.role().isBlank()
                ? UserRole.USER.name() : request.role().trim().toUpperCase(Locale.ROOT);
        Role role = roles.findById(roleKey).orElseThrow(() -> new BusinessRuleException("Unknown role: " + request.role()));
        requireCanGive(actor, role);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new StateConflictException(EMAIL_TAKEN);
        }
        // Q-05: an invitation nobody receives would leave an account nobody can open.
        if (!invitations.canDeliver()) {
            throw new StateConflictException(NO_MAIL);
        }
        Cohort cohort = request.cohortId() == null ? null : openCohort(request.cohortId());
        boolean privileged = permissions.isPrivilegedRole(role.getKey());
        if (privileged) {
            stepUpService.requireFromRequest(StepUpAction.ADMIN_ROLE_CHANGE);
        }

        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        AppUser user = AppUser.builder()
                .fullName(request.fullName().trim())
                .email(email)
                // D-23-5: a password nobody knows until the person chooses theirs.
                .passwordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)))
                .passwordSet(false)
                .status(UserStatus.ACTIVE)
                .cohort(cohort)
                .createdById(actor.getId())
                .build();
        user.setRole(role.getKey());
        AppUser saved = users.save(user);
        if (privileged) {
            twoFactorRequirement.roleGiven(saved);
        }
        securityEvents.record(SecurityEventType.USER_CREATED_BY_ADMIN, SecurityOutcome.SUCCESS, "user", saved.getId(),
                "role " + role.getKey() + (cohort == null ? "" : "; cohort " + cohort.getId()));
        invitations.issue(saved, actor);
        return saved.getId();
    }

    /** UM-FR-27: name and cohort. The log names the fields that changed, not their values. */
    public void update(Long id, AdminUserUpdateRequest request) {
        AppUser actor = requireManager();
        AppUser target = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (!target.getId().equals(actor.getId()) && !holdsAll(actor, permissions.permissionsOf(target))) {
            throw new AccessDeniedException("You cannot change an account that has permissions you do not have.");
        }
        List<String> changed = new ArrayList<>();
        String name = request.fullName().trim();
        if (!name.equals(target.getFullName())) {
            target.setFullName(name);
            changed.add("fullName");
        }
        Long currentCohort = target.getCohort() == null ? null : target.getCohort().getId();
        if (!Objects.equals(currentCohort, request.cohortId())) {
            target.setCohort(request.cohortId() == null ? null : openCohort(request.cohortId()));
            changed.add("cohort");
        }
        if (changed.isEmpty()) {
            return;
        }
        users.save(target);
        securityEvents.record(SecurityEventType.USER_UPDATED, SecurityOutcome.SUCCESS, "user", target.getId(),
                String.join(", ", changed));
    }

    /** UM-FR-24: a new link in place of the old one, while the person has not accepted. */
    public void resendInvitation(Long id) {
        AppUser actor = requireManager();
        AppUser target = target(id, actor);
        if (!invitations.awaitingAcceptance(target)) {
            throw new StateConflictException("This person has already chosen a password. Send a password reset email instead.");
        }
        requireActive(target);
        if (!invitations.canDeliver()) {
            throw new StateConflictException("Invitations cannot be sent because email is not set up on this server.");
        }
        invitations.issue(target, actor);
    }

    /** UM-FR-28: the normal 30-minute reset link, to the account email; the viewer never sees it. */
    public void sendPasswordReset(Long id) {
        AppUser actor = requireManager();
        AppUser target = target(id, actor);
        requireActive(target);
        passwordResets.issueFor(target);
        securityEvents.record(SecurityEventType.PASSWORD_RESET_REQUESTED, SecurityOutcome.SUCCESS, "user", target.getId(),
                "sent by a program admin");
    }

    /**
     * UM-FR-35 to UM-FR-38: permanent delete of a blocked account, with the
     * self-deletion erase routine in this one transaction. The security log keeps
     * the account id, the viewer and the reason, never the name or email.
     */
    public void delete(Long id, AdminUserDeleteRequest request) {
        AppUser actor = permissions.require(lookup.currentUser(), Permission.DELETE_USERS,
                "You do not have permission to delete accounts.");
        rateLimiter.check(RequestRateLimiter.Action.USER_ADMIN, String.valueOf(actor.getId()));
        AppUser target = target(id, actor);
        if (target.getStatus() != UserStatus.DISABLED) {
            // Q-03: blocking first stops sign-in and shows the effect before anything is erased.
            throw new StateConflictException("Block this account before deleting it.");
        }
        if (!target.getEmail().equalsIgnoreCase(request.confirmEmail().trim())) {
            throw new BusinessRuleException("Type the person's email exactly to confirm.");
        }
        if (target.hasRole(UserRole.ADMIN)) {
            requireAnotherActiveAdmin(target);
        }
        stepUpService.requireFromRequest(StepUpAction.ADMIN_USER_DELETE);

        Long targetId = target.getId();
        String role = target.getRole();
        notices.changed(AccountNotice.Kind.ACCOUNT_DELETED_BY_ADMIN, target);
        accountService.erase(targetId);
        securityEvents.record(SecurityEventType.USER_DELETED_BY_ADMIN, SecurityOutcome.SUCCESS, "user", targetId,
                role + "; reason: " + request.reason().trim());
    }

    // ---------- the checks ----------

    private static void requireActive(AppUser target) {
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw new StateConflictException("This account is blocked. Unblock it first.");
        }
    }

    private Cohort openCohort(Long cohortId) {
        Cohort cohort = cohorts.findById(cohortId).orElseThrow(() -> new BusinessRuleException("Cohort not found."));
        if (cohort.isArchived()) {
            throw new BusinessRuleException("This cohort is archived. Choose an active cohort.");
        }
        return cohort;
    }

    private AppUser requireManager() {
        AppUser actor = permissions.require(lookup.currentUser(), Permission.MANAGE_USERS,
                "You do not have permission to change other people's accounts.");
        rateLimiter.check(RequestRateLimiter.Action.USER_ADMIN, String.valueOf(actor.getId()));
        return actor;
    }

    private AppUser target(Long id, AppUser actor) {
        AppUser target = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (target.getId().equals(actor.getId())) {
            throw new AccessDeniedException("You cannot change your own role or block your own account.");
        }
        // D-23-9: only someone who holds every permission the person has may act on them.
        if (!holdsAll(actor, permissions.permissionsOf(target))) {
            throw new AccessDeniedException("You cannot change an account that has permissions you do not have.");
        }
        return target;
    }

    private void requireCanGive(AppUser actor, Role role) {
        if (UserRole.ADMIN.name().equals(role.getKey()) && !actor.hasRole(UserRole.ADMIN)) {
            throw new AccessDeniedException("Only an admin can give the Admin role.");
        }
        if (!holdsAll(actor, permissions.permissionsOf(role.getKey()))) {
            throw new AccessDeniedException("You cannot give a role that has permissions you do not have.");
        }
    }

    private boolean holdsAll(AppUser actor, Set<Permission> needed) {
        return permissions.permissionsOf(actor).containsAll(needed);
    }

    private void requireAnotherActiveAdmin(AppUser target) {
        boolean targetCounts = target.getStatus() == UserStatus.ACTIVE;
        long active = users.countByRoleAndStatus(UserRole.ADMIN.name(), UserStatus.ACTIVE);
        if (active - (targetCounts ? 1 : 0) < 1) {
            throw new StateConflictException("There must be at least one active admin.");
        }
    }

    /**
     * UM-FR-30: someone who coaches active cohorts and moves to a role that cannot
     * coach needs a new coach, or none, named for every one of those cohorts.
     */
    private Map<Cohort, AppUser> cohortHandover(AppUser target, Role newRole,
                                                List<AdminRoleChangeRequest.CohortCoachChange> changes) {
        if (permissions.permissionsOf(newRole.getKey()).contains(Permission.COACH_COHORTS)) {
            return Map.of();
        }
        List<Cohort> coached = cohorts.findByCoach_IdAndArchivedFalseOrderByNameAsc(target.getId());
        if (coached.isEmpty()) {
            return Map.of();
        }
        Map<Long, AdminRoleChangeRequest.CohortCoachChange> byCohort = changes.stream()
                .collect(Collectors.toMap(AdminRoleChangeRequest.CohortCoachChange::cohortId, Function.identity(), (a, b) -> b));
        List<String> missing = coached.stream().filter(cohort -> !byCohort.containsKey(cohort.getId()))
                .map(Cohort::getName).toList();
        if (!missing.isEmpty()) {
            throw new StateConflictException("This person coaches active cohorts: " + String.join(", ", missing)
                    + ". Choose a new coach, or none, for each.");
        }
        Map<Cohort, AppUser> handover = new java.util.LinkedHashMap<>();
        for (Cohort cohort : coached) {
            Long newCoachId = byCohort.get(cohort.getId()).newCoachId();
            handover.put(cohort, newCoachId == null ? null : newCoach(newCoachId, target));
        }
        return handover;
    }

    private AppUser newCoach(Long id, AppUser leaving) {
        AppUser coach = users.findById(id).orElseThrow(() -> new BusinessRuleException("The new coach was not found."));
        if (coach.getId().equals(leaving.getId()) || coach.getStatus() != UserStatus.ACTIVE
                || !permissions.has(coach, Permission.COACH_COHORTS)) {
            throw new BusinessRuleException(coach.getFullName() + " cannot coach a cohort: they need an active account "
                    + "with a role that can coach.");
        }
        return coach;
    }


}
