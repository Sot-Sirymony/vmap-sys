package com.visionmapping.security;

/**
 * V33 (D-33-15): the permission authorities as compile-time constants, for
 * {@code @PreAuthorize} next to an endpoint, for example
 * {@code @PreAuthorize("hasAuthority('" + Authorities.MANAGE_USERS + "')")}.
 *
 * <p>Each constant equals {@link Permission#authority()} for the permission of
 * the same name; {@code AuthoritiesTest} keeps the two in step. Ownership is
 * never checked this way: user scoping stays in the services (CLAUDE.md rules
 * 13 and 14).
 */
public final class Authorities {

    static final String PREFIX = "PERM_";

    public static final String USE_OWN_PLAN = PREFIX + "USE_OWN_PLAN";
    public static final String COACH_COHORTS = PREFIX + "COACH_COHORTS";
    public static final String VIEW_USERS = PREFIX + "VIEW_USERS";
    public static final String MANAGE_USERS = PREFIX + "MANAGE_USERS";
    public static final String DELETE_USERS = PREFIX + "DELETE_USERS";
    public static final String RESET_TWO_FACTOR = PREFIX + "RESET_TWO_FACTOR";
    public static final String MANAGE_COHORTS = PREFIX + "MANAGE_COHORTS";
    public static final String MANAGE_MODULE_CONTENT = PREFIX + "MANAGE_MODULE_CONTENT";
    public static final String MANAGE_INTERFACE_TEXT = PREFIX + "MANAGE_INTERFACE_TEXT";
    public static final String HANDLE_SUPPORT = PREFIX + "HANDLE_SUPPORT";
    public static final String MANAGE_AI_SETTINGS = PREFIX + "MANAGE_AI_SETTINGS";
    public static final String VIEW_AI_REPORTS = PREFIX + "VIEW_AI_REPORTS";
    public static final String VIEW_SECURITY_LOG = PREFIX + "VIEW_SECURITY_LOG";
    public static final String MANAGE_ROLES = PREFIX + "MANAGE_ROLES";

    private Authorities() {
    }
}
