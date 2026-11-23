package com.visionmapping.service.admin;

import com.visionmapping.dto.response.AdminUserDetailResponse;
import com.visionmapping.dto.response.AdminUserPageResponse;
import com.visionmapping.dto.response.AdminUserSummaryResponse;
import com.visionmapping.dto.response.SecurityEventResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.SecurityEvent;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.IdentityProvider;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.ExternalIdentityRepository;
import com.visionmapping.repository.SecurityEventRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.service.support.EntityLookup;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V23 (BRD 23.0.0, phase 2): the Users list and detail panel for accounts with
 * the "View users" permission (UM-FR-17 to UM-FR-20).
 *
 * <p>D-23-3: answers hold account details and record <b>counts</b> only. No
 * dream, goal, task, journal, message or report text is ever read here.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    static final int DEFAULT_PAGE_SIZE = 25;
    private static final int RECENT_EVENTS = 20;
    private static final String NO_PERMISSION = "You do not have permission to see other people's accounts.";

    /** UM-FR-19: the records counted in the detail panel, label → table. Fixed names, never from a request. */
    private static final Map<String, String> COUNTED_TABLES = orderedMap(
            "visionAreas", "vision_areas",
            "dreams", "dreams",
            "goals", "goals",
            "steps", "vision_steps",
            "tasks", "task_items");

    private final AppUserRepository users;
    private final UserTwoFactorRepository twoFactors;
    private final ExternalIdentityRepository externalIdentities;
    private final SecurityEventRepository securityEvents;
    private final EntityLookup lookup;
    private final JdbcTemplate jdbc;
    private final com.visionmapping.repository.RoleRepository roles;
    private final PermissionService permissions;
    private final InvitationService invitations;
    private final com.visionmapping.repository.CohortRepository cohorts;

    /** What the list can be narrowed and sorted by (UM-FR-18). All optional. */
    public record Filter(@Nullable String q, @Nullable String role, @Nullable String status, @Nullable Long cohortId,
                         @Nullable String twoFactor, @Nullable String sort) {
    }

    public AdminUserPageResponse list(Filter filter, int page, int size) {
        requireViewer();
        if (filter.role() != null && !filter.role().isBlank() && !roles.existsById(filter.role().trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessRuleException("Unknown role: " + filter.role());
        }
        int pageSize = PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;
        Page<AppUser> found = users.findAll(specification(filter), PageRequest.of(Math.max(0, page), pageSize, sort(filter.sort())));
        Map<Long, UserTwoFactor> twoFactorById = twoFactorOf(found.getContent());
        Map<String, String> roleNames = roleNames();
        List<AdminUserSummaryResponse> items = found.getContent().stream()
                .map(user -> summary(user, twoFactorById.get(user.getId()), roleNames))
                .toList();

        long active = 0;
        long blocked = 0;
        Map<String, Long> byRole = new TreeMap<>();
        for (Object[] row : users.countByRoleAndStatus()) {
            long count = ((Number) row[2]).longValue();
            byRole.merge((String) row[0], count, Long::sum);
            if (row[1] == UserStatus.ACTIVE) {
                active += count;
            } else {
                blocked += count;
            }
        }
        return new AdminUserPageResponse(items, found.getNumber(), found.getSize(), found.getTotalElements(), found.hasNext(),
                active + blocked, active, blocked, byRole);
    }

    public AdminUserDetailResponse detail(Long id) {
        requireViewer();
        AppUser user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        UserTwoFactor twoFactor = twoFactors.findById(id).orElse(null);
        Map<String, Long> counts = new LinkedHashMap<>();
        COUNTED_TABLES.forEach((label, table) -> counts.put(label,
                jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, id)));
        List<SecurityEventResponse> events = securityEvents.findAboutUser(id, PageRequest.of(0, RECENT_EVENTS)).stream()
                .map(AdminUserService::event)
                .toList();
        // UM-FR-36: the delete dialog names the cohorts that would be left without a coach.
        List<AdminUserDetailResponse.CohortRef> coached = cohorts.findByCoach_IdAndArchivedFalseOrderByNameAsc(id).stream()
                .map(cohort -> new AdminUserDetailResponse.CohortRef(cohort.getId(), cohort.getName())).toList();
        boolean awaiting = invitations.awaitingAcceptance(user);
        return new AdminUserDetailResponse(summary(user, twoFactor, roleNames()), createdBy(user), user.getRoleChangedAt(),
                nameOf(user.getRoleChangedById()), user.getBlockedAt(), nameOf(user.getBlockedById()), counts, events,
                awaiting, awaiting ? invitations.openUntil(id).orElse(null) : null, coached);
    }

    private void requireViewer() {
        permissions.require(lookup.currentUser(), Permission.VIEW_USERS, NO_PERMISSION);
    }

    // ---------- the list query ----------

    private static Specification<AppUser> specification(Filter filter) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + escape(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')));
            }
            if (filter.role() != null && !filter.role().isBlank()) {
                where.add(cb.equal(root.get("role"), filter.role().trim().toUpperCase(Locale.ROOT)));
            }
            if (filter.status() != null && !filter.status().isBlank()) {
                where.add(cb.equal(root.get("status"), parse(UserStatus.class, filter.status(), "status")));
            }
            if (filter.cohortId() != null) {
                where.add(cb.equal(root.get("cohort").get("id"), filter.cohortId()));
            }
            if (filter.twoFactor() != null && !filter.twoFactor().isBlank()) {
                Subquery<Long> enabled = query.subquery(Long.class);
                var settings = enabled.from(UserTwoFactor.class);
                enabled.select(settings.get("userId"))
                        .where(cb.equal(settings.get("userId"), root.get("id")), cb.isNotNull(settings.get("enabledAt")));
                switch (filter.twoFactor().trim().toUpperCase(Locale.ROOT)) {
                    case "ON" -> where.add(cb.exists(enabled));
                    case "DUE" -> where.add(cb.and(cb.not(cb.exists(enabled)), cb.isNotNull(root.get("twoFactorDueAt"))));
                    case "OFF" -> where.add(cb.and(cb.not(cb.exists(enabled)), cb.isNull(root.get("twoFactorDueAt"))));
                    default -> throw new BusinessRuleException("Unknown two-step filter: use ON, DUE or OFF.");
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static Sort sort(@Nullable String sort) {
        String key = sort == null ? "joined" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "name" -> Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id"));
            case "lastsignin", "last-sign-in" -> Sort.by(Sort.Order.desc("lastSignInAt").nullsLast(), Sort.Order.desc("id"));
            case "joined" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            default -> throw new BusinessRuleException("Unknown sort: use name, joined or lastSignIn.");
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String what) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Unknown " + what + ": " + value);
        }
    }

    /** Typed text is matched literally: % and _ mean themselves. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---------- shaping the answer ----------

    private Map<Long, UserTwoFactor> twoFactorOf(List<AppUser> page) {
        List<Long> ids = page.stream().map(AppUser::getId).toList();
        return ids.isEmpty() ? Map.of()
                : twoFactors.findAllById(ids).stream().collect(Collectors.toMap(UserTwoFactor::getUserId, Function.identity()));
    }

    private static AdminUserSummaryResponse summary(AppUser user, @Nullable UserTwoFactor twoFactor, Map<String, String> roleNames) {
        boolean on = twoFactor != null && twoFactor.getEnabledAt() != null;
        String state = on ? "ON" : user.getTwoFactorDueAt() != null ? "DUE" : "OFF";
        Cohort cohort = user.getCohort();
        String role = user.getRole();
        return new AdminUserSummaryResponse(user.getId(), user.getFullName(), user.getEmail(), role,
                roleNames.getOrDefault(role, role), user.getStatus().name(),
                cohort == null ? null : cohort.getId(), cohort == null ? null : cohort.getName(),
                state, on ? null : user.getTwoFactorDueAt(), user.getCreatedAt(), user.getLastSignInAt());
    }

    private String createdBy(AppUser user) {
        if (user.getCreatedById() != null) {
            String admin = nameOf(user.getCreatedById());
            return admin == null ? "an admin" : admin;
        }
        boolean google = !user.isPasswordSet()
                && externalIdentities.findByUser_IdAndProvider(user.getId(), IdentityProvider.GOOGLE).isPresent();
        return google ? "Google" : "self sign-up";
    }

    @Nullable
    private String nameOf(@Nullable Long userId) {
        return userId == null ? null : users.findById(userId).map(AppUser::getFullName).orElse(null);
    }

    private Map<String, String> roleNames() {
        Map<String, String> names = new LinkedHashMap<>();
        jdbc.query("select role_key, name from roles", row -> {
            names.put(row.getString(1), row.getString(2));
        });
        return names;
    }

    private static SecurityEventResponse event(SecurityEvent event) {
        return new SecurityEventResponse(event.getId(), event.getOccurredAt(), event.getEventType().name(),
                event.getOutcome().name(), event.getActorId(), event.getIpAddress(), event.getTargetType(),
                event.getTargetId(), event.getDetail());
    }

    private static Map<String, String> orderedMap(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
