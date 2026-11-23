package com.visionmapping.security.audit;

/** V10 (SEC-09-01): every kind of event the security log records. */
public enum SecurityEventType {
    // sign-in and sessions
    REGISTERED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGIN_LOCKED,
    LOGGED_OUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    REFRESH_TOKEN_REUSED,
    INVALID_TOKEN,
    // V14 Google sign-in (GA-FR-33): a reason code, never a token
    GOOGLE_SIGN_IN_SUCCEEDED,
    GOOGLE_SIGN_IN_FAILED,
    GOOGLE_LINKED,
    // V21 two-factor sign-in (TF-NFR-05): never a code, secret or token
    TWO_FACTOR_ENABLED,
    TWO_FACTOR_DISABLED,
    TWO_FACTOR_METHOD_CHANGED,
    TWO_FACTOR_CHALLENGE_STARTED,
    TWO_FACTOR_VERIFIED,
    TWO_FACTOR_FAILED,
    TWO_FACTOR_LOCKED,
    RECOVERY_CODE_USED,
    RECOVERY_CODES_REGENERATED,
    TRUSTED_DEVICE_ADDED,
    TRUSTED_DEVICE_REMOVED,
    TWO_FACTOR_RESET_BY_ADMIN,
    TWO_FACTOR_SETUP_SKIPPED,
    // V20 "Verify it's you" (SV-NFR-05): never the code itself
    STEP_UP_CODE_SENT,
    STEP_UP_VERIFIED,
    STEP_UP_FAILED,
    STEP_UP_LOCKED,
    STEP_UP_SKIPPED_NO_MAIL,
    // refused requests
    ACCESS_DENIED,
    NOT_OWNER,
    RATE_LIMITED,
    // people and data
    ROLE_CHANGED,
    COACH_VIEWED_PARTICIPANT,
    SHARE_LINK_CREATED,
    SHARE_LINK_OPENED,
    SHARE_LINK_REVOKED,
    EXCEL_IMPORTED,
    EXCEL_EXPORTED,
    ACCOUNT_EXPORTED,
    ACCOUNT_DELETED,
    // admin changes (SEC-08-06)
    AI_SETTINGS_CHANGED,
    AI_KEY_CHANGED,
    AI_KEY_REMOVED,
    // V10 SEC-09-04: AI spending passed 80% or 100% of the monthly budget, or jumped in one hour.
    AI_BUDGET_WARNING,
    // V23 UM-FR-41: the first admin made from ADMIN_BOOTSTRAP_EMAIL.
    ADMIN_BOOTSTRAPPED,
    // V23 UM-FR-42: an account blocked or unblocked by someone with "Manage users".
    USER_BLOCKED,
    USER_UNBLOCKED,
    // V23 UM-FR-42: roles created, changed (permissions added and removed) and deleted.
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    // V24 SA-FR-08: sign-in methods or the two-step scope changed by an Admin.
    SECURITY_ACCESS_CHANGED,
    // V23 phase 5 (UM-FR-42): accounts an admin creates, edits and deletes, and their invitations.
    USER_CREATED_BY_ADMIN,
    USER_UPDATED,
    USER_DELETED_BY_ADMIN,
    INVITATION_SENT,
    INVITATION_ACCEPTED,
    APPROVED_RESOURCE_CHANGED,
    MODULE_CONTENT_CHANGED,
    INTERFACE_TEXT_CHANGED,
    COHORT_CHANGED,
    COHORT_MEMBER_CHANGED,
    ISSUE_REPORT_TRIAGED
}
