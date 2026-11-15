package com.visionmapping.service.report;

import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * V18 MR-NFR-06: the fixed words in the Management Report's files, in English
 * or Khmer. The participant's own text (titles, reasons, next actions) is
 * never translated. Khmer entries are first drafts for a Khmer speaker to
 * review, like the app's own (km-review-v18.csv).
 */
public final class ReportText {

    /** A Khmer-capable font named in the files (Q-04). */
    public static final String KHMER_FONT = "Noto Sans Khmer";

    public enum Key {
        TITLE, AS_OF, ALL_DREAMS,
        DREAM, GOALS, STEPS, TASKS, BLOCKERS_ISSUES, PROGRESS, PRIORITY, DUE_DATE, NEXT_ACTION,
        SUMMARY_SHEET, REPORT_SHEET, BLOCKERS_SHEET,
        OVERALL_PROGRESS, GOALS_IN_SCOPE, TOTAL_TASKS, COMPLETED_TASKS, OVERDUE_TASKS, BLOCKED_TASKS, OPEN_BLOCKERS, DUE_SOON,
        TASKS_BY_STATUS, STATUS, OPEN_BY_PRIORITY, ON_TRACK, OVERDUE, GOAL_HEALTH, GOAL, HEALTH, TARGET_DATE, BLOCKED_ITEMS,
        HEADLINE, VALUE,
        WHERE, KIND, SEVERITY, DAYS_OPEN, SUGGESTED_HELPER, SOLUTION_SO_FAR, NOT_ON_A_STEP, BLOCKED_TASK,
        ISSUE_BLOCKED, ISSUE_OVERDUE_BY, ISSUE_NO_OWNER, ISSUE_NO_DUE_DATE, NEXT_TASK_MARK, NO_BLOCKERS,
        HEALTH_RED, HEALTH_AMBER, HEALTH_GREEN, HEALTH_DONE, PAGE_OF,
        DECK_DEFAULT_TITLE, DECK_PREPARED_BY, SLIDE_SUMMARY, SLIDE_PROGRESS, SLIDE_PRIORITIES, SLIDE_BLOCKERS, SLIDE_OVERDUE,
        SLIDE_NEXT, SLIDE_SUPPORT, SENTENCE_HEALTH, SENTENCE_OVERDUE, SENTENCE_NOTHING_OVERDUE, SENTENCE_SUPPORT,
        SENTENCE_NO_SUPPORT, TASKS_COMPLETED_OF, MORE_IN_EXCEL, NO_GOALS, NO_OVERDUE, NO_NEXT_ACTIONS, DUE_SOON_ITEMS,
        NONE_DUE_SOON, OWNER, DAYS_OVERDUE, DUE, WHY, GAP_NEEDS_HELP, NOTES_SUMMARY, NOTES_PROGRESS, NOTES_PRIORITIES,
        NOTES_BLOCKERS, NOTES_OVERDUE, NOTES_NEXT, NOTES_SUPPORT, NOTES_TITLE
    }

    private static final Map<Key, String[]> TEXT = new EnumMap<>(Key.class);

    static {
        put(Key.TITLE, "Management report", "របាយការណ៍គ្រប់គ្រង");
        put(Key.AS_OF, "%s · as of %s", "%s · គិតត្រឹម %s");
        put(Key.ALL_DREAMS, "All dreams", "ក្តីស្រមៃទាំងអស់");
        put(Key.DREAM, "Dream", "ក្តីស្រមៃ");
        put(Key.GOALS, "Goals", "គោលដៅ");
        put(Key.STEPS, "Steps", "ជំហាន");
        put(Key.TASKS, "Tasks", "កិច្ចការ");
        put(Key.BLOCKERS_ISSUES, "Blockers / Management Issues", "ឧបសគ្គ / បញ្ហាគ្រប់គ្រង");
        put(Key.PROGRESS, "Progress (%)", "វឌ្ឍនភាព (%)");
        put(Key.PRIORITY, "Priority", "អាទិភាព");
        put(Key.DUE_DATE, "Due Date", "កាលបរិច្ឆេទកំណត់");
        put(Key.NEXT_ACTION, "Next Action", "សកម្មភាពបន្ទាប់");
        put(Key.SUMMARY_SHEET, "Summary", "សង្ខេប");
        put(Key.REPORT_SHEET, "Report", "របាយការណ៍");
        put(Key.BLOCKERS_SHEET, "Blockers", "ឧបសគ្គ");
        put(Key.OVERALL_PROGRESS, "Overall progress (%)", "វឌ្ឍនភាពសរុប (%)");
        put(Key.GOALS_IN_SCOPE, "Goals", "គោលដៅ");
        put(Key.TOTAL_TASKS, "Tasks", "កិច្ចការ");
        put(Key.COMPLETED_TASKS, "Completed tasks", "កិច្ចការដែលបានបញ្ចប់");
        put(Key.OVERDUE_TASKS, "Overdue tasks", "កិច្ចការហួសកំណត់");
        put(Key.BLOCKED_TASKS, "Blocked tasks", "កិច្ចការជាប់គាំង");
        put(Key.OPEN_BLOCKERS, "Open blockers", "ឧបសគ្គដែលនៅបើក");
        put(Key.DUE_SOON, "Due in 14 days", "ដល់កំណត់ក្នុងរយៈពេល ១៤ ថ្ងៃ");
        put(Key.TASKS_BY_STATUS, "Tasks by status", "កិច្ចការតាមស្ថានភាព");
        put(Key.STATUS, "Status", "ស្ថានភាព");
        put(Key.OPEN_BY_PRIORITY, "Open tasks by priority", "កិច្ចការដែលនៅបើកតាមអាទិភាព");
        put(Key.ON_TRACK, "On track", "តាមផែនការ");
        put(Key.OVERDUE, "Overdue", "ហួសកំណត់");
        put(Key.GOAL_HEALTH, "Goal health", "សុខភាពគោលដៅ");
        put(Key.GOAL, "Goal", "គោលដៅ");
        put(Key.HEALTH, "Health", "សុខភាព");
        put(Key.TARGET_DATE, "Target date", "កាលបរិច្ឆេទគោលដៅ");
        put(Key.BLOCKED_ITEMS, "Blocked items", "ធាតុដែលជាប់គាំង");
        put(Key.HEADLINE, "Headline", "ចំណុចសំខាន់");
        put(Key.VALUE, "Value", "តម្លៃ");
        put(Key.WHERE, "Where", "ទីតាំង");
        put(Key.KIND, "Kind", "ប្រភេទ");
        put(Key.SEVERITY, "Severity", "កម្រិតធ្ងន់ធ្ងរ");
        put(Key.DAYS_OPEN, "Days open", "ចំនួនថ្ងៃដែលនៅបើក");
        put(Key.SUGGESTED_HELPER, "Who could help", "អ្នកដែលអាចជួយ");
        put(Key.SOLUTION_SO_FAR, "Solution so far", "ដំណោះស្រាយរហូតមកដល់ពេលនេះ");
        put(Key.NOT_ON_A_STEP, "Not on a step yet", "មិនទាន់នៅលើជំហាន");
        put(Key.BLOCKED_TASK, "Blocked task", "កិច្ចការជាប់គាំង");
        put(Key.ISSUE_BLOCKED, "Blocked: %s", "ជាប់គាំង៖ %s");
        put(Key.ISSUE_OVERDUE_BY, "Overdue by %d day(s)", "ហួសកំណត់ %d ថ្ងៃ");
        put(Key.ISSUE_NO_OWNER, "No owner", "គ្មានអ្នកទទួលខុសត្រូវ");
        put(Key.ISSUE_NO_DUE_DATE, "No due date", "គ្មានកាលបរិច្ឆេទកំណត់");
        put(Key.NEXT_TASK_MARK, "%s (next task)", "%s (កិច្ចការបន្ទាប់)");
        put(Key.NO_BLOCKERS, "No blockers right now", "ឥឡូវនេះគ្មានឧបសគ្គទេ");
        put(Key.HEALTH_RED, "Off track", "ខុសផ្លូវ");
        put(Key.HEALTH_AMBER, "At risk", "មានហានិភ័យ");
        put(Key.HEALTH_GREEN, "On track", "តាមផែនការ");
        put(Key.HEALTH_DONE, "Done", "រួចរាល់");
        put(Key.PAGE_OF, "Page &P of &N", "ទំព័រ &P នៃ &N");
        // V18 phase 3: the deck (MR-FR-24). Sentences are built by fixed rules, never by AI (MR-NFR-10).
        put(Key.DECK_DEFAULT_TITLE, "Progress report", "របាយការណ៍វឌ្ឍនភាព");
        put(Key.DECK_PREPARED_BY, "Prepared by %s", "រៀបចំដោយ %s");
        put(Key.SLIDE_SUMMARY, "Executive summary", "សេចក្តីសង្ខេបសម្រាប់អ្នកគ្រប់គ្រង");
        put(Key.SLIDE_PROGRESS, "Progress by goal", "វឌ្ឍនភាពតាមគោលដៅ");
        put(Key.SLIDE_PRIORITIES, "Priorities and deadlines", "អាទិភាព និងកាលកំណត់");
        put(Key.SLIDE_BLOCKERS, "Blockers and management issues", "ឧបសគ្គ និងបញ្ហាគ្រប់គ្រង");
        put(Key.SLIDE_OVERDUE, "Overdue items", "ធាតុហួសកំណត់");
        put(Key.SLIDE_NEXT, "Next actions", "សកម្មភាពបន្ទាប់");
        put(Key.SLIDE_SUPPORT, "Decisions and support needed", "ការសម្រេចចិត្ត និងជំនួយដែលត្រូវការ");
        put(Key.SENTENCE_HEALTH, "%d goal(s) on track, %d at risk, %d off track, %d done.",
                "គោលដៅ %d តាមផែនការ %d មានហានិភ័យ %d ខុសផ្លូវ %d រួចរាល់។");
        put(Key.SENTENCE_OVERDUE, "%d task(s) overdue.", "កិច្ចការ %d ហួសកំណត់។");
        put(Key.SENTENCE_NOTHING_OVERDUE, "Nothing is overdue.", "គ្មានអ្វីហួសកំណត់ទេ។");
        put(Key.SENTENCE_SUPPORT, "%d item(s) need a decision or support.", "ធាតុ %d ត្រូវការការសម្រេចចិត្ត ឬជំនួយ។");
        put(Key.SENTENCE_NO_SUPPORT, "Nothing needs a decision right now.", "ឥឡូវនេះគ្មានអ្វីត្រូវការការសម្រេចចិត្តទេ។");
        put(Key.TASKS_COMPLETED_OF, "%d of %d tasks completed", "កិច្ចការ %d ក្នុងចំណោម %d បានបញ្ចប់");
        put(Key.MORE_IN_EXCEL, "and %d more (see the Excel report)", "និង %d ទៀត (មើលរបាយការណ៍ Excel)");
        put(Key.NO_GOALS, "No goals in this report yet", "មិនទាន់មានគោលដៅក្នុងរបាយការណ៍នេះទេ");
        put(Key.NO_OVERDUE, "Nothing is overdue", "គ្មានអ្វីហួសកំណត់ទេ");
        put(Key.NO_NEXT_ACTIONS, "Every goal is done", "គោលដៅទាំងអស់រួចរាល់");
        put(Key.DUE_SOON_ITEMS, "Due in the next 14 days", "ដល់កំណត់ក្នុងរយៈពេល ១៤ ថ្ងៃខាងមុខ");
        put(Key.NONE_DUE_SOON, "Nothing is due in the next 14 days", "គ្មានអ្វីដល់កំណត់ក្នុងរយៈពេល ១៤ ថ្ងៃខាងមុខទេ");
        put(Key.OWNER, "Owner", "អ្នកទទួលខុសត្រូវ");
        put(Key.DAYS_OVERDUE, "%d day(s) late", "យឺត %d ថ្ងៃ");
        put(Key.DUE, "Due %s", "កំណត់ %s");
        put(Key.WHY, "Why", "មូលហេតុ");
        put(Key.GAP_NEEDS_HELP, "Missing %s, no help planned yet", "ខ្វះ %s មិនទាន់មានផែនការជំនួយ");
        put(Key.NOTES_TITLE, "Scope: %s. Goals: %d. Tasks: %d.", "វិសាលភាព៖ %s។ គោលដៅ៖ %d។ កិច្ចការ៖ %d។");
        put(Key.NOTES_SUMMARY, "Overall progress is the average of the goals' progress. Off track: overdue, or a blocked task or critical blocker. At risk: overdue work, an open blocker, or more than 20 points behind schedule.",
                "វឌ្ឍនភាពសរុបគឺជាមធ្យមនៃវឌ្ឍនភាពគោលដៅ។ ខុសផ្លូវ៖ ហួសកំណត់ ឬមានកិច្ចការជាប់គាំង ឬឧបសគ្គធ្ងន់ធ្ងរ។ មានហានិភ័យ៖ ការងារហួសកំណត់ ឧបសគ្គនៅបើក ឬយឺតជាងកាលវិភាគលើសពី ២០ ពិន្ទុ។");
        put(Key.NOTES_PROGRESS, "Progress comes from completed tasks, rolled up through steps to goals.",
                "វឌ្ឍនភាពមកពីកិច្ចការដែលបានបញ្ចប់ ដោយបូកសរុបតាមជំហានទៅគោលដៅ។");
        put(Key.NOTES_PRIORITIES, "Open tasks: %d. Overdue: %d. Due in the next 14 days: %d.",
                "កិច្ចការនៅបើក៖ %d។ ហួសកំណត់៖ %d។ ដល់កំណត់ក្នុង ១៤ ថ្ងៃ៖ %d។");
        put(Key.NOTES_BLOCKERS, "Open blockers on steps or tasks: %d. Blocked tasks: %d. Not on a step yet: %d.",
                "ឧបសគ្គនៅបើកលើជំហាន ឬកិច្ចការ៖ %d។ កិច្ចការជាប់គាំង៖ %d។ មិនទាន់នៅលើជំហាន៖ %d។");
        put(Key.NOTES_OVERDUE, "Overdue tasks: %d, most overdue first.", "កិច្ចការហួសកំណត់៖ %d ដោយហួសកំណត់ច្រើនជាងគេមុន។");
        put(Key.NOTES_NEXT, "One next action per goal that is not done: its first open task.",
                "សកម្មភាពបន្ទាប់មួយក្នុងមួយគោលដៅដែលមិនទាន់រួចរាល់៖ កិច្ចការនៅបើកដំបូងរបស់វា។");
        put(Key.NOTES_SUPPORT, "Blockers about a decision, money or a partner, and steps missing knowledge, a skill or money with no help planned.",
                "ឧបសគ្គអំពីការសម្រេចចិត្ត ថវិកា ឬដៃគូ និងជំហានដែលខ្វះចំណេះដឹង ជំនាញ ឬថវិកា ដោយមិនទាន់មានផែនការជំនួយ។");
    }

    private static final Map<WorkStatus, String[]> STATUS = new EnumMap<>(Map.of(
            WorkStatus.NOT_STARTED, new String[] {"Not Started", "មិនទាន់ចាប់ផ្តើម"},
            WorkStatus.IN_PROGRESS, new String[] {"In Progress", "កំពុងដំណើរការ"},
            WorkStatus.WAITING, new String[] {"Waiting", "កំពុងរង់ចាំ"},
            WorkStatus.BLOCKED, new String[] {"Blocked", "ជាប់គាំង"},
            WorkStatus.COMPLETED, new String[] {"Completed", "បានបញ្ចប់"},
            WorkStatus.PAUSED, new String[] {"Paused", "បានផ្អាក"}));

    private static final Map<Priority, String[]> PRIORITY = new EnumMap<>(Map.of(
            Priority.LOW, new String[] {"Low", "ទាប"},
            Priority.MEDIUM, new String[] {"Medium", "មធ្យម"},
            Priority.HIGH, new String[] {"High", "ខ្ពស់"},
            Priority.CRITICAL, new String[] {"Critical", "ធ្ងន់ធ្ងរ"}));

    private static final Map<Severity, String[]> SEVERITY = new EnumMap<>(Map.of(
            Severity.LOW, new String[] {"Low", "ទាប"},
            Severity.MEDIUM, new String[] {"Medium", "មធ្យម"},
            Severity.HIGH, new String[] {"High", "ខ្ពស់"},
            Severity.CRITICAL, new String[] {"Critical", "ធ្ងន់ធ្ងរ"}));

    private static final Map<ObstacleStatus, String[]> OBSTACLE_STATUS = new EnumMap<>(Map.of(
            ObstacleStatus.OPEN, new String[] {"Open", "បើក"},
            ObstacleStatus.IN_PROGRESS, new String[] {"In Progress", "កំពុងដំណើរការ"},
            ObstacleStatus.RESOLVED, new String[] {"Resolved", "បានដោះស្រាយ"},
            ObstacleStatus.ACCEPTED, new String[] {"Accepted", "បានទទួលយក"}));

    private static final Map<ObstacleType, String[]> KIND = new EnumMap<>(ObstacleType.class);

    /** The helper type that could clear each kind of blocker (CLAUDE.md, Partner Management page). */
    private static final Map<ObstacleType, String[]> HELPER = new EnumMap<>(ObstacleType.class);

    static {
        KIND.put(ObstacleType.KNOWLEDGE, new String[] {"Knowledge", "ចំណេះដឹង"});
        KIND.put(ObstacleType.SKILL, new String[] {"Skill", "ជំនាញ"});
        KIND.put(ObstacleType.TIME, new String[] {"Time", "ពេលវេលា"});
        KIND.put(ObstacleType.MONEY, new String[] {"Money", "ថវិកា"});
        KIND.put(ObstacleType.MOTIVATION, new String[] {"Motivation", "ការលើកទឹកចិត្ត"});
        KIND.put(ObstacleType.PARTNER, new String[] {"Partner", "ដៃគូ"});
        KIND.put(ObstacleType.SYSTEM, new String[] {"System", "ប្រព័ន្ធ"});
        KIND.put(ObstacleType.DECISION, new String[] {"Decision", "ការសម្រេចចិត្ត"});
        KIND.put(ObstacleType.OTHER, new String[] {"Other", "ផ្សេងៗ"});
        HELPER.put(ObstacleType.KNOWLEDGE, new String[] {"Mentor or expert", "អ្នកណែនាំ ឬអ្នកជំនាញ"});
        HELPER.put(ObstacleType.SKILL, new String[] {"Technical expert or trainer", "អ្នកជំនាញបច្ចេកទេស ឬគ្រូបង្វឹក"});
        HELPER.put(ObstacleType.TIME, new String[] {"Delegate or assistant", "អ្នកតំណាង ឬជំនួយការ"});
        HELPER.put(ObstacleType.MONEY, new String[] {"Financial partner or sponsor", "ដៃគូហិរញ្ញវត្ថុ ឬអ្នកឧបត្ថម្ភ"});
        HELPER.put(ObstacleType.MOTIVATION, new String[] {"Accountability partner", "ដៃគូទទួលខុសត្រូវ"});
        HELPER.put(ObstacleType.DECISION, new String[] {"Advisor", "អ្នកប្រឹក្សា"});
    }

    private final boolean khmer;

    private ReportText(boolean khmer) {
        this.khmer = khmer;
    }

    /** English unless the language is "km". */
    public static ReportText of(@Nullable String language) {
        return new ReportText("km".equalsIgnoreCase(language));
    }

    public boolean isKhmer() {
        return khmer;
    }

    public String get(Key key) {
        return pick(TEXT.get(key));
    }

    public String format(Key key, Object... args) {
        return String.format(Locale.ROOT, get(key), args);
    }

    public String status(WorkStatus status) {
        return pick(STATUS.get(status));
    }

    public String priority(@Nullable Priority priority) {
        return priority == null ? "" : pick(PRIORITY.get(priority));
    }

    public String severity(@Nullable Severity severity) {
        return severity == null ? "" : pick(SEVERITY.get(severity));
    }

    public String obstacleStatus(ObstacleStatus status) {
        return pick(OBSTACLE_STATUS.get(status));
    }

    public String kind(@Nullable ObstacleType type) {
        return type == null ? "" : pick(KIND.get(type));
    }

    /** The suggested helper for a kind of blocker, or empty when there is no clear match. */
    public String helper(@Nullable ObstacleType type) {
        String[] pair = type == null ? null : HELPER.get(type);
        return pair == null ? "" : pick(pair);
    }

    public String health(ReportRules.Health health) {
        return get(switch (health) {
            case RED -> Key.HEALTH_RED;
            case AMBER -> Key.HEALTH_AMBER;
            case GREEN -> Key.HEALTH_GREEN;
            case DONE -> Key.HEALTH_DONE;
        });
    }

    /** "16 Nov 2026" in English; ISO dates in Khmer, which reads them the same way. */
    public String date(LocalDate date) {
        return khmer ? date.toString() : date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
    }

    private String pick(String[] pair) {
        return khmer ? pair[1] : pair[0];
    }

    private static void put(Key key, String english, String khmer) {
        TEXT.put(key, new String[] {english, khmer});
    }
}
