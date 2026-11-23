package com.visionmapping.security;

/**
 * V23 (BRD 23.0.0, UM-FR-01): everything a role can be allowed to do. The list
 * is fixed in code; admins choose which roles have which (phase 4). Every
 * protected action names the permission it needs (D-23-1).
 *
 * <p>Two permissions are special: {@link #USE_OWN_PLAN} belongs to every role,
 * and {@link #MANAGE_ROLES} belongs only to the Admin role (D-23-9).
 */
public enum Permission {

    USE_OWN_PLAN(Group.OWN_PLAN, "Use own plan", "Work on their own vision map."),
    COACH_COHORTS(Group.COACHING, "Coach cohorts",
            "Be a cohort's coach and read, never change, its participants (shared journals only)."),
    VIEW_USERS(Group.PEOPLE, "View users", "See the list of accounts and their details (never plan data)."),
    MANAGE_USERS(Group.PEOPLE, "Manage users", "Invite, edit, block and unblock accounts, and change their role."),
    DELETE_USERS(Group.PEOPLE, "Delete users", "Permanently delete blocked accounts."),
    RESET_TWO_FACTOR(Group.PEOPLE, "Reset two-step sign-in", "Reset another person's two-step sign-in."),
    MANAGE_COHORTS(Group.PROGRAM, "Manage cohorts", "Create and change cohorts, their coaches and join codes."),
    MANAGE_MODULE_CONTENT(Group.PROGRAM, "Manage module content", "Edit the program's learning modules."),
    MANAGE_INTERFACE_TEXT(Group.PROGRAM, "Manage interface text", "Edit the app's interface text and translations."),
    HANDLE_SUPPORT(Group.PROGRAM, "Handle support", "Triage everyone's issue reports and read the failed request log."),
    MANAGE_AI_SETTINGS(Group.AI, "Manage AI settings", "Change the Claude key, budget, model and AI switches."),
    VIEW_AI_REPORTS(Group.AI, "View AI reports", "See AI usage and quality reports."),
    VIEW_SECURITY_LOG(Group.SECURITY, "View security log", "Read the security log."),
    MANAGE_ROLES(Group.ROLES, "Manage roles", "Create and change roles. Admin role only.");

    /** How the Roles page groups the permissions. */
    public enum Group { OWN_PLAN, COACHING, PEOPLE, PROGRAM, AI, SECURITY, ROLES }

    private final Group group;
    private final String label;
    private final String description;

    Permission(Group group, String label, String description) {
        this.group = group;
        this.label = label;
        this.description = description;
    }

    public Group group() {
        return group;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** The authority Spring Security sees for this permission. */
    public String authority() {
        return "PERM_" + name();
    }

    /**
     * UM-FR-06: anything beyond using one's own plan makes a role privileged.
     * "Administrative" excludes coaching too: it is what opens /api/admin/**.
     */
    public boolean administrative() {
        return this != USE_OWN_PLAN && this != COACH_COHORTS;
    }
}
