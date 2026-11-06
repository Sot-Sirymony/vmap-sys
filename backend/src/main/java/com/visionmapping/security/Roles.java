package com.visionmapping.security;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import org.springframework.security.access.AccessDeniedException;

/**
 * V10 (SEC-01-03): the one place that says who counts as an admin or a coach.
 *
 * Two layers use the same rule. SecurityConfig refuses /api/admin/** to anyone
 * but an ADMIN and /api/coach/** to anyone but a COACH or ADMIN, before a
 * controller runs. Services then call these methods again, so a service reused
 * from another endpoint stays protected. An admin may do everything a coach may.
 */
public final class Roles {

    private Roles() {
    }

    public static boolean isAdmin(AppUser user) {
        return user != null && user.getRole() == UserRole.ADMIN;
    }

    public static boolean canCoach(AppUser user) {
        return user != null && (user.getRole() == UserRole.COACH || user.getRole() == UserRole.ADMIN);
    }

    /** @throws AccessDeniedException (403) unless the user is an admin */
    public static AppUser requireAdmin(AppUser user, String message) {
        if (!isAdmin(user)) {
            throw new AccessDeniedException(message);
        }
        return user;
    }

    /** @throws AccessDeniedException (403) unless the user is a coach or an admin */
    public static AppUser requireCoach(AppUser user, String message) {
        if (!canCoach(user)) {
            throw new AccessDeniedException(message);
        }
        return user;
    }
}
