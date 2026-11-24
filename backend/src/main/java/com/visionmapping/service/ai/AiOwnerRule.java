package com.visionmapping.service.ai;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/**
 * V31 R-07 (DC-FR-61, D-31-6): an owner Claude gives must be "Me" or a role,
 * never a person's name. Blank stays "Me", as before (PB-FR-18). An owner with
 * a title such as "Dr." or with no role word is removed, so the participant
 * picks the owner. Prompts ask for the owner in English, so the role words are
 * English; "ខ្ញុំ" (me) is also accepted.
 */
final class AiOwnerRule {

    static final String ME = "Me";
    private static final int MAX = 160;

    private static final Pattern TITLE = Pattern.compile("(?i)(^|[^\\p{L}])(dr|mr|mrs|ms|miss|prof|sir|madam|lok|neak)\\.?($|[^\\p{L}])");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{L}]+");
    private static final String KHMER_ME = "ខ្ញុំ";

    /** Words that name a role, a kind of helper or a group, never one person. */
    static final Set<String> ROLE_WORDS = Set.of(
            "me", "myself", "self", "i",
            "mentor", "coach", "expert", "advisor", "adviser", "consultant", "specialist", "professional",
            "colleague", "coworker", "worker", "peer", "classmate", "friend", "family", "member", "spouse", "parent",
            "partner", "helper", "volunteer", "assistant", "trainer", "tutor", "teacher", "lecturer", "professor",
            "sponsor", "funder", "funding", "donor", "investor", "lender", "bank", "accountant", "bookkeeper", "lawyer",
            "solicitor", "notary", "doctor", "nurse", "therapist", "counsellor", "counselor", "statistician", "analyst",
            "researcher", "scientist", "engineer", "developer", "programmer", "designer", "writer", "editor",
            "translator", "interpreter", "reviewer", "supervisor", "manager", "director", "officer", "leader", "lead",
            "head", "staff", "team", "committee", "board", "agency", "organisation", "organization", "office",
            "department", "institute", "university", "school", "ministry", "contractor", "freelancer", "technician",
            "mechanic", "builder", "agent", "broker", "librarian", "admin", "administrator", "secretary", "support",
            "community", "group", "club", "network", "provider", "vendor", "supplier", "printer", "photographer",
            "planner", "realtor", "landlord", "employer", "client", "customer", "body", "authority", "council");

    private AiOwnerRule() {
    }

    /** The owner to keep: "Me" when blank, the trimmed role, or null when it is not "Me" or a role. */
    @Nullable
    static String check(@Nullable String owner) {
        if (owner == null || owner.isBlank()) {
            return ME;
        }
        String trimmed = owner.trim();
        if (trimmed.contains(KHMER_ME)) {
            return trimmed.length() <= MAX ? trimmed : ME;
        }
        if (TITLE.matcher(trimmed).find()) {
            return null;
        }
        for (String word : WORD_SPLIT.split(trimmed.toLowerCase(Locale.ROOT))) {
            if (ROLE_WORDS.contains(word)) {
                return trimmed.length() <= MAX ? trimmed : trimmed.substring(0, MAX);
            }
        }
        return null;
    }

    /** True when check would remove this owner. */
    static boolean stripped(@Nullable String owner) {
        return check(owner) == null;
    }
}
