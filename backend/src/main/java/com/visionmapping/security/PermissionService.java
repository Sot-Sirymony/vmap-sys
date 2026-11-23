package com.visionmapping.security;

import com.visionmapping.entity.AppUser;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * V23 (BRD 23.0.0, D-23-1): the one place that says what a person may do.
 * Replaces the role-name checks of V10 (SEC-01-03).
 *
 * <p>Two layers use it, as before: SecurityConfig lets only accounts with an
 * administrative permission reach /api/admin/** and only coaches reach
 * /api/coach/**; each service then asks for the exact permission its action
 * needs, so a service reused from another endpoint stays protected.
 *
 * <p>A role's permissions come from {@code role_permissions}, cached per role
 * and cleared by {@link #evict()} whenever a role changes (UM-FR-04). The Admin
 * role always has every permission, whatever the table says (D-23-2), so it can
 * never be locked out.
 */
@Slf4j
@Service
public class PermissionService {

    public static final String ADMIN_ROLE = "ADMIN";
    private static final Set<Permission> ALL = Collections.unmodifiableSet(EnumSet.allOf(Permission.class));

    private final Map<String, Set<Permission>> cache = new ConcurrentHashMap<>();

    /** The permissions migration V89 gives the built-in roles (UM-FR-03), for {@link #withBuiltInRoles()}. */
    static final Map<String, Set<Permission>> BUILT_IN = Map.of(
            "USER", EnumSet.of(Permission.USE_OWN_PLAN),
            "COACH", EnumSet.of(Permission.USE_OWN_PLAN, Permission.COACH_COHORTS));

    @Nullable
    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    public PermissionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private PermissionService() {
        this.jdbc = null;
    }

    /** For unit tests built without a database: the built-in roles only. */
    public static PermissionService withBuiltInRoles() {
        return new PermissionService();
    }

    /** The permissions of a role, by its key. Unknown roles have none. */
    public Set<Permission> permissionsOf(@Nullable String roleKey) {
        if (roleKey == null) {
            return Set.of();
        }
        if (ADMIN_ROLE.equals(roleKey)) {
            return ALL;
        }
        return cache.computeIfAbsent(roleKey, this::load);
    }

    public Set<Permission> permissionsOf(@Nullable AppUser user) {
        return user == null ? Set.of() : permissionsOf(user.getRole());
    }

    public boolean has(@Nullable AppUser user, Permission permission) {
        return permissionsOf(user).contains(permission);
    }

    public boolean hasAny(@Nullable AppUser user, Permission... permissions) {
        Set<Permission> held = permissionsOf(user);
        return Arrays.stream(permissions).anyMatch(held::contains);
    }

    /** @throws AccessDeniedException (403) unless the user's role has the permission */
    public AppUser require(@Nullable AppUser user, Permission permission, String message) {
        if (!has(user, permission)) {
            throw new AccessDeniedException(message);
        }
        return user;
    }

    /** @throws AccessDeniedException (403) unless the user's role has at least one of them */
    public AppUser requireAny(@Nullable AppUser user, String message, Permission... permissions) {
        if (!hasAny(user, permissions)) {
            throw new AccessDeniedException(message);
        }
        return user;
    }

    /** UM-FR-06: anything beyond using one's own plan. Such accounts must use two-step sign-in. */
    public boolean isPrivileged(@Nullable AppUser user) {
        return user != null && isPrivilegedRole(user.getRole());
    }

    public boolean isPrivilegedRole(@Nullable String roleKey) {
        return permissionsOf(roleKey).stream().anyMatch(permission -> permission != Permission.USE_OWN_PLAN);
    }

    /** UM-FR-06: the keys of every privileged role (for the two-step start-up job). */
    public Set<String> privilegedRoleKeys() {
        if (jdbc == null) {
            return Set.of(ADMIN_ROLE, "COACH");
        }
        java.util.Set<String> keys = new java.util.TreeSet<>();
        for (String key : jdbc.queryForList("select role_key from roles", String.class)) {
            if (isPrivilegedRole(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** UM-FR-04: call after any change to roles or their permissions. */
    public void evict() {
        cache.clear();
        // A request reading a role while the change is still being saved could cache the old
        // permissions; clearing again once the change is committed removes that.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            cache.clear();
                        }
                    });
        }
    }

    private Set<Permission> load(String roleKey) {
        if (jdbc == null) {
            return Collections.unmodifiableSet(BUILT_IN.getOrDefault(roleKey, EnumSet.noneOf(Permission.class)));
        }
        EnumSet<Permission> found = EnumSet.noneOf(Permission.class);
        for (String name : jdbc.queryForList("select permission from role_permissions where role_key = ?", String.class, roleKey)) {
            try {
                found.add(Permission.valueOf(name));
            } catch (IllegalArgumentException unknown) {
                log.warn("Ignoring unknown permission {} on role {}", name, roleKey);
            }
        }
        if (!ADMIN_ROLE.equals(roleKey)) {
            // UM-FR-01, D-23-9: managing roles is never given to another role, whatever the table says.
            found.remove(Permission.MANAGE_ROLES);
        }
        return Collections.unmodifiableSet(found);
    }
}
