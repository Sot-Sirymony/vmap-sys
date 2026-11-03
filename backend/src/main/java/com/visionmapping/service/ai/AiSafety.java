package com.visionmapping.service.ai;

import java.util.List;
import java.util.Locale;

/**
 * AI-NFR-09: the safety layer around each Dream Coach call. Crisis language is
 * caught before Claude is called, so the help-line answer appears every time
 * (AC-12), and Claude marks any crisis it notices itself with {@link #MARKER}.
 * This is a safety net, not a diagnosis: it errs towards showing help lines.
 */
public final class AiSafety {

    /** Claude starts a reply with this when the person seems to be at risk. */
    public static final String MARKER = "[CRISIS]";

    private static final List<String> CRISIS_PHRASES = List.of(
            "kill myself", "killing myself", "suicide", "suicidal", "end my life", "take my own life",
            "want to die", "wanna die", "better off dead", "no reason to live", "self-harm", "self harm",
            "hurt myself", "harm myself", "cut myself",
            // Khmer: kill oneself, want to die, harm oneself, end one's life.
            "សម្លាប់ខ្លួន", "ចង់ស្លាប់", "ធ្វើបាបខ្លួនឯង", "បញ្ចប់ជីវិត");

    private AiSafety() {
    }

    public static boolean looksLikeCrisis(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return CRISIS_PHRASES.stream().anyMatch(lower::contains);
    }

    public static boolean markedCrisis(String reply) {
        return reply.startsWith(MARKER);
    }

    public static String withoutMarker(String reply) {
        return reply.startsWith(MARKER) ? reply.substring(MARKER.length()).trim() : reply;
    }

    public static String crisisReply(String language, String helpLines, boolean hasCoach) {
        if ("km".equals(language)) {
            return "ខ្ញុំសោកស្តាយណាស់ដែលអ្នកកំពុងមានអារម្មណ៍បែបនេះ។ អ្នកមិនចាំបាច់ប្រឈមមុខនឹងរឿងនេះតែម្នាក់ឯងទេ។ "
                    + "សូមទាក់ទងអ្នកដែលអាចជួយបានឥឡូវនេះ៖\n\n" + helpLines + "\n\n"
                    + "ប្រសិនបើអ្នកកំពុងមានគ្រោះថ្នាក់ភ្លាមៗ សូមហៅលេខសង្គ្រោះបន្ទាន់ក្នុងតំបន់របស់អ្នក។ "
                    + (hasCoach ? "ខ្ញុំបានប្រាប់គ្រូណែនាំរបស់អ្នក ដើម្បីឱ្យគាត់សួរសុខទុក្ខអ្នក។"
                               : "ខ្ញុំបានប្រាប់អ្នកគ្រប់គ្រងកម្មវិធី ដើម្បីឱ្យមាននរណាម្នាក់សួរសុខទុក្ខអ្នក។");
        }
        return "I'm really sorry you're feeling this way. You don't have to face this alone. "
                + "Please reach out now to someone who can help:\n\n" + helpLines + "\n\n"
                + "If you are in immediate danger, call your local emergency number. "
                + (hasCoach ? "I've let your coach know so they can check in with you."
                           : "I've let the program team know so someone can check in with you.");
    }
}
