package com.visionmapping.service.stepup;

/**
 * V20 (BRD 20.0.0, SV-FR-01 to SV-FR-09, D-20-3): the actions that need a
 * "Verify it's you" code, in one place. A WINDOW action is also covered by the
 * session's 10-minute verified window and opens it when verified; a FRESH
 * action always needs its own code, verified within {@code freshMinutes}, and
 * uses it once. Adding an action is one line here plus its test.
 */
public enum StepUpAction {

    CHANGE_PASSWORD(Rule.WINDOW, 10, false, "change your password", "ប្តូរពាក្យសម្ងាត់របស់អ្នក"),
    EXPORT_DATA(Rule.WINDOW, 10, false, "download all your data", "ទាញយកទិន្នន័យទាំងអស់របស់អ្នក"),
    /** SV-FR-04(a): the account check before the reminder email changes. */
    CHANGE_REMINDER_EMAIL(Rule.WINDOW, 10, false, "change your reminder email", "ប្តូរអ៊ីមែលរំលឹករបស់អ្នក"),
    /** SV-FR-04(b): sent to the new address, so it proves the person owns it. */
    CONFIRM_REMINDER_EMAIL(Rule.FRESH, 10, true, "use this address for your reminders", "ប្រើអាសយដ្ឋាននេះសម្រាប់ការរំលឹករបស់អ្នក"),
    DELETE_ACCOUNT(Rule.FRESH, 5, false, "delete your account", "លុបគណនីរបស់អ្នក"),
    /** V21 (TF-FR-06): turning two-factor on or off, changing the method, new recovery codes. */
    TWO_FACTOR_SETTINGS(Rule.FRESH, 10, false, "change your two-factor sign-in", "ប្តូរការចូលពីរជំហានរបស់អ្នក"),
    ADMIN_ROLE_CHANGE(Rule.FRESH, 10, false, "change a person's role", "ប្តូរតួនាទីរបស់មនុស្សម្នាក់"),
    ADMIN_AI_KEY(Rule.FRESH, 10, false, "change the AI key", "ប្តូរកូនសោ AI"),
    /** V21 (TF-FR-28): resetting another person's two-step sign-in. */
    ADMIN_TWO_FACTOR_RESET(Rule.FRESH, 10, false, "reset a person's two-step sign-in", "កំណត់ការចូលពីរជំហានរបស់មនុស្សម្នាក់ឡើងវិញ"),
    ADMIN_COHORT_COACH(Rule.FRESH, 10, false, "change a cohort's coach", "ប្តូរគ្រូបង្វឹករបស់ក្រុម"),
    /** V23 (UM-FR-31, UM-FR-33): blocking or unblocking another person's account. */
    ADMIN_USER_BLOCK(Rule.FRESH, 10, false, "block or unblock a person's account", "បិទ ឬបើកគណនីរបស់មនុស្សម្នាក់"),
    /** V23 (UM-FR-11, UM-FR-14): creating, changing or deleting a role. */
    ADMIN_ROLE_EDIT(Rule.FRESH, 10, false, "change a role and its permissions", "ប្តូរតួនាទី និងសិទ្ធិរបស់វា"),
    /** V23 (UM-FR-36): permanently deleting another person's account. */
    ADMIN_USER_DELETE(Rule.FRESH, 5, false, "permanently delete a person's account", "លុបគណនីរបស់មនុស្សម្នាក់ជាអចិន្ត្រៃយ៍");

    /** Whether the verified window covers the action. */
    public enum Rule { WINDOW, FRESH }

    private final Rule rule;
    private final int freshMinutes;
    private final boolean needsTarget;
    private final String english;
    private final String khmer;

    StepUpAction(Rule rule, int freshMinutes, boolean needsTarget, String english, String khmer) {
        this.rule = rule;
        this.freshMinutes = freshMinutes;
        this.needsTarget = needsTarget;
        this.english = english;
        this.khmer = khmer;
    }

    public Rule rule() {
        return rule;
    }

    /** How long after verifying the code the action may use it. */
    public int freshMinutes() {
        return freshMinutes;
    }

    /** Whether the code goes to, and is tied to, an address given with the request. */
    public boolean needsTarget() {
        return needsTarget;
    }

    /** "to {label}", for the email (SV-FR-17). */
    public String label(boolean khmerLanguage) {
        return khmerLanguage ? khmer : english;
    }
}
