package com.visionmapping.service.report;

import com.visionmapping.dto.response.ManagementReportResponse.HealthReason;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
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
        NOTES_BLOCKERS, NOTES_OVERDUE, NOTES_NEXT, NOTES_SUPPORT, NOTES_TITLE,
        // V19 phase 1 (BRD 19.0.0)
        HEALTH_DONE_OPEN_WORK, AS_OF_PERIOD, PERIOD_THIS_WEEK, PERIOD_LAST_WEEK, PERIOD_THIS_MONTH, PERIOD_LAST_MONTH,
        PERIOD_THIS_QUARTER, PERIOD_LAST_QUARTER, PERIOD_RANGE, THIS_PERIOD, PERIOD_TASKS_COMPLETED, PERIOD_PROGRESS_GAINED,
        PERIOD_BLOCKERS_OPENED, PERIOD_BLOCKERS_CLOSED, PERIOD_BECAME_OVERDUE,
        REASON_COMPLETED, REASON_COMPLETED_OPEN_WORK, REASON_GOAL_OVERDUE, REASON_BLOCKED_TASKS, REASON_CRITICAL_BLOCKER,
        REASON_CRITICAL_BLOCKERS, REASON_OVERDUE_WORK, REASON_OPEN_BLOCKERS, REASON_BEHIND_SCHEDULE, REASON_ON_TRACK,
        // V19 phase 2
        FILTER_OWNER, FILTER_NO_COMPLETED,
        DRAFT_COMPLETED_TASKS, DRAFT_GOAL_DONE, DRAFT_RISK_GOAL, DRAFT_RISK_BLOCKER, DRAFT_SUPPORT_BLOCKER, DRAFT_SUPPORT_GAP,
        DRAFT_FOCUS,
        // V19 phase 3
        DRAFT_MARK, DRAFT_GOAL_ON_TRACK, COMMENTARY_SHEET, CHANGES_SHEET, HIGHLIGHTS, RISKS, DECISIONS_NEEDED, NEXT_FOCUS,
        NOTHING_NEEDED, GOAL_NOTES, NOTE, SLIDE_CHANGES, COMPARED_WITH, CHANGE, CHANGE_SINCE, BEFORE, NOW, ITEM, NEW_GOAL,
        RENAMED, NO_LONGER_IN_REPORT, REMOVED_COMPLETED_ARCHIVED, REMOVED_ARCHIVED, REMOVED_MOVED, TARGET_MOVED, NO_CHANGES,
        CHANGE_GOAL_WORSE, CHANGE_DUE_LATER, CHANGE_NEW_BLOCKER, CHANGE_BLOCKER_CLOSED, CHANGE_GOAL_BETTER, CHANGE_TASK_COMPLETED,
        GOAL_CHANGES, WHAT_CHANGED, NOTES_HIGHLIGHTS, NOTES_CHANGES, NOTES_GOAL_NOTES, YOUR_WORDS, POINTS,
        // V19 phase 4
        PDF_STATUS_FINAL, PDF_STATUS_DRAFT, PDF_STATUS_LIVE, PDF_PAGE, PDF_NO_LONGER_SHARED
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
        // V19 phase 1: the period (RM-FR-06 to RM-FR-08), Done – open work (RM-FR-02) and health reasons (RM-FR-01).
        put(Key.HEALTH_DONE_OPEN_WORK, "Done – open work", "រួចរាល់ – នៅមានការងារ");
        put(Key.AS_OF_PERIOD, "%s · %s · as of %s", "%s · %s · គិតត្រឹម %s");
        put(Key.PERIOD_THIS_WEEK, "This week", "សប្តាហ៍នេះ");
        put(Key.PERIOD_LAST_WEEK, "Last week", "សប្តាហ៍មុន");
        put(Key.PERIOD_THIS_MONTH, "This month", "ខែនេះ");
        put(Key.PERIOD_LAST_MONTH, "Last month", "ខែមុន");
        put(Key.PERIOD_THIS_QUARTER, "This quarter", "ត្រីមាសនេះ");
        put(Key.PERIOD_LAST_QUARTER, "Last quarter", "ត្រីមាសមុន");
        put(Key.PERIOD_RANGE, "%s to %s", "%s ដល់ %s");
        put(Key.THIS_PERIOD, "This period (%s)", "រយៈពេលនេះ (%s)");
        put(Key.PERIOD_TASKS_COMPLETED, "Tasks completed", "កិច្ចការដែលបានបញ្ចប់");
        put(Key.PERIOD_PROGRESS_GAINED, "Progress gained (points)", "វឌ្ឍនភាពកើនឡើង (ពិន្ទុ)");
        put(Key.PERIOD_BLOCKERS_OPENED, "Blockers opened", "ឧបសគ្គថ្មី");
        put(Key.PERIOD_BLOCKERS_CLOSED, "Blockers closed", "ឧបសគ្គដែលបានបិទ");
        put(Key.PERIOD_BECAME_OVERDUE, "Tasks that became overdue", "កិច្ចការដែលបានក្លាយជាហួសកំណត់");
        put(Key.REASON_COMPLETED, "Completed", "បានបញ្ចប់");
        put(Key.REASON_COMPLETED_OPEN_WORK, "Completed, but %d task(s) are still open", "បានបញ្ចប់ ប៉ុន្តែកិច្ចការ %d នៅបើក");
        put(Key.REASON_GOAL_OVERDUE, "Target date passed %d day(s) ago", "កាលបរិច្ឆេទគោលដៅបានកន្លងផុត %d ថ្ងៃ");
        put(Key.REASON_BLOCKED_TASKS, "%d blocked task(s)", "កិច្ចការជាប់គាំង %d");
        put(Key.REASON_CRITICAL_BLOCKER, "Critical blocker: %s", "ឧបសគ្គធ្ងន់ធ្ងរ៖ %s");
        put(Key.REASON_CRITICAL_BLOCKERS, "Critical blocker: %s and %d more", "ឧបសគ្គធ្ងន់ធ្ងរ៖ %s និង %d ទៀត");
        put(Key.REASON_OVERDUE_WORK, "%d overdue task(s) or step(s)", "កិច្ចការ ឬជំហានហួសកំណត់ %d");
        put(Key.REASON_OPEN_BLOCKERS, "%d open blocker(s)", "ឧបសគ្គនៅបើក %d");
        put(Key.REASON_BEHIND_SCHEDULE, "Progress %d%% with %d%% of the time gone", "វឌ្ឍនភាព %d%% ខណៈពេលវេលាកន្លងផុត %d%%");
        put(Key.REASON_ON_TRACK, "No overdue work or open blockers", "គ្មានការងារហួសកំណត់ ឬឧបសគ្គនៅបើក");
        // V19 phase 2: filters in the as-of line (RM-FR-10) and the rule-based commentary draft (RM-FR-28).
        put(Key.FILTER_OWNER, "Owner: %s", "អ្នកទទួលខុសត្រូវ៖ %s");
        put(Key.FILTER_NO_COMPLETED, "Without completed goals", "មិនរាប់បញ្ចូលគោលដៅដែលបានបញ្ចប់");
        put(Key.DRAFT_COMPLETED_TASKS, "- %s: completed %d task(s) (%s)", "- %s៖ បានបញ្ចប់កិច្ចការ %d (%s)");
        put(Key.DRAFT_GOAL_DONE, "- %s is done", "- %s រួចរាល់");
        put(Key.DRAFT_RISK_GOAL, "- %s is off track: %s", "- %s ខុសផ្លូវ៖ %s");
        put(Key.DRAFT_RISK_BLOCKER, "- %s (%s, %s) on %s", "- %s (%s, %s) លើ %s");
        put(Key.DRAFT_SUPPORT_BLOCKER, "- %s (%s) on %s", "- %s (%s) លើ %s");
        put(Key.DRAFT_SUPPORT_GAP, "- %s: missing %s, no help planned yet", "- %s៖ ខ្វះ %s មិនទាន់មានផែនការជំនួយ");
        put(Key.DRAFT_FOCUS, "- %s: %s", "- %s៖ %s");
        // V19 phase 3
        put(Key.DRAFT_MARK, "DRAFT", "សេចក្តីព្រាង");
        put(Key.DRAFT_GOAL_ON_TRACK, "- %s moved to On track", "- %s បានមកតាមផែនការវិញ");
        put(Key.COMMENTARY_SHEET, "Commentary", "មតិយោបល់");
        put(Key.CHANGES_SHEET, "Changes", "ការផ្លាស់ប្តូរ");
        put(Key.HIGHLIGHTS, "Highlights", "ចំណុចលេចធ្លោ");
        put(Key.RISKS, "Risks and issues", "ហានិភ័យ និងបញ្ហា");
        put(Key.DECISIONS_NEEDED, "Decisions and support needed", "ការសម្រេចចិត្ត និងការគាំទ្រដែលត្រូវការ");
        put(Key.NEXT_FOCUS, "Focus for next period", "ការផ្តោតសម្រាប់រយៈពេលបន្ទាប់");
        put(Key.NOTHING_NEEDED, "Nothing needed this period.", "មិនត្រូវការអ្វីទេក្នុងរយៈពេលនេះ។");
        put(Key.GOAL_NOTES, "Goal notes", "កំណត់ចំណាំគោលដៅ");
        put(Key.NOTE, "Note", "កំណត់ចំណាំ");
        put(Key.SLIDE_CHANGES, "What changed", "អ្វីដែលបានផ្លាស់ប្តូរ");
        put(Key.COMPARED_WITH, "Compared with %s (%s, frozen %s)", "ប្រៀបធៀបជាមួយ %s (%s, បង្កកថ្ងៃ %s)");
        put(Key.CHANGE, "Change", "ការផ្លាស់ប្តូរ");
        put(Key.CHANGE_SINCE, "Change since %s", "ការផ្លាស់ប្តូរតាំងពី %s");
        put(Key.BEFORE, "Before", "មុន");
        put(Key.NOW, "Now", "ឥឡូវ");
        put(Key.ITEM, "Item", "ធាតុ");
        put(Key.NEW_GOAL, "New", "ថ្មី");
        put(Key.RENAMED, "(renamed)", "(បានប្តូរឈ្មោះ)");
        put(Key.NO_LONGER_IN_REPORT, "No longer in this report", "លែងមាននៅក្នុងរបាយការណ៍នេះ");
        put(Key.REMOVED_COMPLETED_ARCHIVED, "Completed and archived", "បានបញ្ចប់ និងដាក់ក្នុងបណ្ណសារ");
        put(Key.REMOVED_ARCHIVED, "Archived", "បានដាក់ក្នុងបណ្ណសារ");
        put(Key.REMOVED_MOVED, "Moved to another dream", "បានផ្លាស់ទៅក្តីស្រមៃផ្សេង");
        put(Key.TARGET_MOVED, "Target %s → %s", "កាលកំណត់ %s → %s");
        put(Key.NO_CHANGES, "Nothing changed since the compared report.", "គ្មានអ្វីផ្លាស់ប្តូរតាំងពីរបាយការណ៍ដែលបានប្រៀបធៀបទេ។");
        put(Key.CHANGE_GOAL_WORSE, "Health got worse", "សុខភាពធ្លាក់ចុះ");
        put(Key.CHANGE_DUE_LATER, "Date moved later", "កាលបរិច្ឆេទត្រូវបានពន្យារ");
        put(Key.CHANGE_NEW_BLOCKER, "New blocker", "ឧបសគ្គថ្មី");
        put(Key.CHANGE_BLOCKER_CLOSED, "Blocker no longer open", "ឧបសគ្គលែងបើកទៀតហើយ");
        put(Key.CHANGE_GOAL_BETTER, "Health got better", "សុខភាពប្រសើរឡើង");
        put(Key.CHANGE_TASK_COMPLETED, "Task completed", "កិច្ចការបានបញ្ចប់");
        put(Key.GOAL_CHANGES, "Goal changes", "ការផ្លាស់ប្តូរគោលដៅ");
        put(Key.WHAT_CHANGED, "What changed", "អ្វីដែលបានផ្លាស់ប្តូរ");
        put(Key.NOTES_HIGHLIGHTS, "The participant's own words for this period.", "ពាក្យផ្ទាល់ខ្លួនរបស់អ្នកចូលរួមសម្រាប់រយៈពេលនេះ។");
        put(Key.NOTES_CHANGES, "Changes since %s (frozen %s): %d in all.", "ការផ្លាស់ប្តូរតាំងពី %s (បង្កកថ្ងៃ %s)៖ សរុប %d។");
        put(Key.NOTES_GOAL_NOTES, "Goal notes:", "កំណត់ចំណាំគោលដៅ៖");
        put(Key.POINTS, "%s points", "%s ពិន្ទុ");
        // V19 phase 4
        put(Key.PDF_STATUS_FINAL, "Final · frozen %s", "ចុងក្រោយ · បង្កកថ្ងៃ %s");
        put(Key.PDF_STATUS_DRAFT, "DRAFT · %s", "សេចក្តីព្រាង · %s");
        put(Key.PDF_STATUS_LIVE, "Live report · %s", "របាយការណ៍បច្ចុប្បន្ន · %s");
        put(Key.PDF_PAGE, "Page %d", "ទំព័រ %d");
        put(Key.PDF_NO_LONGER_SHARED, "This report is no longer shared.", "របាយការណ៍នេះលែងបានចែករំលែកទៀតហើយ។");
        put(Key.YOUR_WORDS, "In the participant's words", "តាមពាក្យរបស់អ្នកចូលរួម");
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
            case DONE_OPEN_WORK -> Key.HEALTH_DONE_OPEN_WORK;
            case DONE -> Key.HEALTH_DONE;
        });
    }

    /** RM-FR-06: "This month", or "1 Nov 2026 to 17 Nov 2026" for a custom period. */
    public String periodLabel(ReportPeriod period) {
        return switch (period.kind()) {
            case THIS_WEEK -> get(Key.PERIOD_THIS_WEEK);
            case LAST_WEEK -> get(Key.PERIOD_LAST_WEEK);
            case THIS_MONTH -> get(Key.PERIOD_THIS_MONTH);
            case LAST_MONTH -> get(Key.PERIOD_LAST_MONTH);
            case THIS_QUARTER -> get(Key.PERIOD_THIS_QUARTER);
            case LAST_QUARTER -> get(Key.PERIOD_LAST_QUARTER);
            case CUSTOM -> periodDates(period);
        };
    }

    /** "1 Nov 2026 to 17 Nov 2026". */
    public String periodDates(ReportPeriod period) {
        return format(Key.PERIOD_RANGE, date(period.from()), date(period.to()));
    }

    /**
     * MR-FR-04, RM-FR-07, RM-FR-10: "All dreams · This month · as of 17 Nov
     * 2026", followed by any filters: " · High, Critical · Owner: Dara".
     */
    public String asOfLine(ManagementReport report) {
        String scope = "ALL".equals(report.scopeKind()) || report.scopeLabel() == null ? get(Key.ALL_DREAMS) : report.scopeLabel();
        String line = format(Key.AS_OF_PERIOD, scope, periodLabel(report.period()), date(report.asOf()));
        String filters = filters(report.filters());
        return filters.isEmpty() ? line : line + " · " + filters;
    }

    /** "High, Critical · Owner: Dara · Off track · Without completed goals", or empty without filters. */
    public String filters(ReportFilters filters) {
        List<String> parts = new java.util.ArrayList<>();
        if (!filters.priorities().isEmpty()) {
            parts.add(String.join(", ", filters.priorities().stream().sorted().map(this::priority).toList()));
        }
        if (filters.owner() != null) {
            parts.add(format(Key.FILTER_OWNER, filters.owner()));
        }
        if (!filters.healths().isEmpty()) {
            parts.add(String.join(", ", filters.healths().stream().sorted().map(this::health).toList()));
        }
        if (!filters.includeCompleted()) {
            parts.add(get(Key.FILTER_NO_COMPLETED));
        }
        return String.join(" · ", parts);
    }

    /** RM-FR-01: one health reason in words. An unknown code is shown as it is rather than dropped. */
    public String reason(HealthReason reason) {
        long value = reason.value() == null ? 0 : reason.value();
        return switch (reason.code()) {
            case "COMPLETED" -> get(Key.REASON_COMPLETED);
            case "COMPLETED_OPEN_WORK" -> format(Key.REASON_COMPLETED_OPEN_WORK, value);
            case "GOAL_OVERDUE" -> format(Key.REASON_GOAL_OVERDUE, value);
            case "BLOCKED_TASKS" -> format(Key.REASON_BLOCKED_TASKS, value);
            case "CRITICAL_BLOCKER" -> value > 1 ? format(Key.REASON_CRITICAL_BLOCKERS, reason.title(), value - 1)
                    : format(Key.REASON_CRITICAL_BLOCKER, reason.title());
            case "OVERDUE_WORK" -> format(Key.REASON_OVERDUE_WORK, value);
            case "OPEN_BLOCKERS" -> format(Key.REASON_OPEN_BLOCKERS, value);
            case "BEHIND_SCHEDULE" -> format(Key.REASON_BEHIND_SCHEDULE, value, reason.elapsed() == null ? 0 : reason.elapsed());
            case "ON_TRACK" -> get(Key.REASON_ON_TRACK);
            default -> reason.code();
        };
    }

    public String reasons(List<HealthReason> reasons, String separator) {
        return String.join(separator, reasons.stream().map(this::reason).toList());
    }

    /** RM-FR-35: the kind of one What changed line. */
    public String changeKind(String kind) {
        return switch (kind) {
            case "GOAL_WORSE" -> get(Key.CHANGE_GOAL_WORSE);
            case "DUE_LATER" -> get(Key.CHANGE_DUE_LATER);
            case "NEW_BLOCKER" -> get(Key.CHANGE_NEW_BLOCKER);
            case "BLOCKER_CLOSED" -> get(Key.CHANGE_BLOCKER_CLOSED);
            case "GOAL_BETTER" -> get(Key.CHANGE_GOAL_BETTER);
            case "TASK_COMPLETED" -> get(Key.CHANGE_TASK_COMPLETED);
            default -> kind;
        };
    }

    /**
     * RM-FR-34: one goal's change in words: "At risk → On track · +12 points ·
     * Target 10 Jan 2027 → 24 Jan 2027 · (renamed)", "New", or "" when not
     * compared or nothing changed.
     */
    public String goalChange(@Nullable com.visionmapping.dto.response.ManagementReportResponse.GoalChange change,
                             com.visionmapping.dto.response.ManagementReportResponse.GoalHealthRow row) {
        if (change == null) {
            return "";
        }
        if (change.isNew()) {
            return get(Key.NEW_GOAL);
        }
        List<String> parts = new java.util.ArrayList<>();
        if (change.healthBefore() != null && !change.healthBefore().equals(row.health())) {
            parts.add(health(ReportRules.Health.valueOf(change.healthBefore())) + " → " + health(ReportRules.Health.valueOf(row.health())));
        }
        if (change.progressChange() != null && change.progressChange().signum() != 0) {
            parts.add(format(Key.POINTS, signed(change.progressChange())));
        }
        if (change.targetBefore() != null) {
            parts.add(format(Key.TARGET_MOVED, date(change.targetBefore()), row.targetDate() == null ? "—" : date(row.targetDate())));
        }
        if (change.previousTitle() != null) {
            parts.add(get(Key.RENAMED));
        }
        return String.join(" · ", parts);
    }

    /** RM-FR-34: why a goal left the report, or "" when not known. */
    public String removedReason(@Nullable String reason) {
        if (reason == null) {
            return "";
        }
        return switch (reason) {
            case "COMPLETED_ARCHIVED" -> get(Key.REMOVED_COMPLETED_ARCHIVED);
            case "ARCHIVED" -> get(Key.REMOVED_ARCHIVED);
            case "MOVED" -> get(Key.REMOVED_MOVED);
            default -> "";
        };
    }

    /**
     * RM-FR-35: a change's before and after in words: a health name, a date,
     * a severity, or "" when the kind has none.
     */
    public String changeValue(String kind, @Nullable String value) {
        if (value == null) {
            return "";
        }
        return switch (kind) {
            case "GOAL_WORSE", "GOAL_BETTER" -> health(ReportRules.Health.valueOf(value));
            case "DUE_LATER" -> date(LocalDate.parse(value));
            case "NEW_BLOCKER" -> severity(Severity.valueOf(value));
            default -> value;
        };
    }

    /** RM-FR-33: "+12", "−2" or "0", with a true minus sign; one decimal place for progress points. */
    public static String signed(java.math.BigDecimal value) {
        java.math.BigDecimal scaled = value.setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        String plain = scaled.abs().toPlainString();
        return scaled.signum() > 0 ? "+" + plain : scaled.signum() < 0 ? "−" + plain : "0";
    }

    public static String signed(long value) {
        return value > 0 ? "+" + value : value < 0 ? "−" + Math.abs(value) : "0";
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
