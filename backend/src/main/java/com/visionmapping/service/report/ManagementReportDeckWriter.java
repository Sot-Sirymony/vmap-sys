package com.visionmapping.service.report;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.GoalHealthRow;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.service.report.ReportText.Key;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TableCell.BorderEdge;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.TextShape.TextAutofit;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.lang.Nullable;

/**
 * V18 phase 3 (MR-FR-23 to MR-FR-31): the Management Report as a short,
 * editable PowerPoint deck for a management review. Built from the same model
 * as the Summary and the Excel report (MR-NFR-03), with shapes, text boxes and
 * a table only: no images or native chart objects (MR-FR-27). Every slide has
 * a real title, a slide number and speaker notes (MR-FR-29, MR-NFR-07).
 */
public final class ManagementReportDeckWriter {

    static final int MAX_GOALS = 8;
    static final int MAX_DUE_SOON = 6;
    static final int MAX_BLOCKERS = 6;
    static final int MAX_OVERDUE = 8;
    static final int MAX_NEXT = 8;
    static final int MAX_SUPPORT = 6;
    static final int MAX_CHANGES = 7;
    static final int MAX_COMMENTARY_LINES = 5;
    private static final int MAX_TITLE = 80;
    private static final int MAX_LINE = 110;
    private static final double GOAL_ROW = 50;

    private static final double WIDTH = 960;
    private static final double HEIGHT = 540;
    private static final double MARGIN = 48;
    private static final double BODY_TOP = 104;
    private static final double BODY_WIDTH = WIDTH - 2 * MARGIN;

    private static final Color PRIMARY = new Color(0x00, 0x78, 0xD4);
    private static final Color INK = new Color(0x32, 0x31, 0x30);
    private static final Color MUTED = new Color(0x60, 0x5E, 0x5C);
    private static final Color TRACK = new Color(0xED, 0xEB, 0xE9);
    private static final Map<Health, Color> HEALTH_COLOURS = Map.of(
            Health.RED, new Color(0xD1, 0x34, 0x38),
            Health.AMBER, new Color(0xCA, 0x50, 0x10),
            Health.GREEN, new Color(0x10, 0x7C, 0x10),
            Health.DONE_OPEN_WORK, new Color(0x8A, 0x88, 0x86),
            Health.DONE, new Color(0x8A, 0x88, 0x86));
    private static final Set<ObstacleType> SUPPORT_KINDS = EnumSet.of(ObstacleType.DECISION, ObstacleType.MONEY, ObstacleType.PARTNER);

    private final ManagementReport report;
    private final ManagementReportResponse summary;
    private final ReportText text;
    private final ReportExtras extras;
    private final String font;
    private final XMLSlideShow deck = new XMLSlideShow();

    private ManagementReportDeckWriter(ManagementReport report, ManagementReportResponse summary, ReportText text, ReportExtras extras) {
        this.report = report;
        this.summary = summary;
        this.text = text;
        this.extras = extras;
        this.font = text.isKhmer() ? ReportText.KHMER_FONT : "Calibri";
        deck.setPageSize(new Dimension((int) WIDTH, (int) HEIGHT));
    }

    /**
     * @param title the deck title, or null for "Progress report" (MR-FR-31)
     * @param presenter the participant's name for the title slide, or null to leave it out (Q-05)
     */
    public static byte[] write(ManagementReport report, ManagementReportResponse summary, ReportText text,
                               @Nullable String title, @Nullable String presenter) {
        return write(report, summary, text, title, presenter, ReportExtras.NONE);
    }

    /**
     * V19 RM-FR-41: a Highlights slide after the title (the participant's
     * own words), a What changed slide after the executive summary when
     * compared, the participant's text first on the support slide, goal notes
     * in the progress slide's notes; only the chosen sections (RM-FR-39) and
     * "DRAFT" on a draft's title slide (RM-FR-43). Without commentary or a
     * comparison, the deck has the V18 shape (RMC-15).
     */
    public static byte[] write(ManagementReport report, ManagementReportResponse summary, ReportText text,
                               @Nullable String title, @Nullable String presenter, ReportExtras extras) {
        ManagementReportDeckWriter writer = new ManagementReportDeckWriter(report, summary, text, extras);
        try (XMLSlideShow deck = writer.deck; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.titleSlide(ReportRules.blank(title) ? text.get(Key.DECK_DEFAULT_TITLE) : title.strip(), presenter);
            if (extras.includes(ReportSection.COMMENTARY)) {
                writer.highlightsSlide();
            }
            writer.summarySlide();
            if (extras.includes(ReportSection.CHANGES) && summary.comparison() != null) {
                writer.changesSlide();
            }
            if (extras.includes(ReportSection.GOAL_HEALTH)) {
                writer.progressSlide();
            }
            if (extras.includes(ReportSection.SUMMARY)) {
                writer.prioritiesSlide();
            }
            if (extras.includes(ReportSection.BLOCKERS)) {
                writer.blockersSlide();
            }
            if (extras.includes(ReportSection.DETAILS)) {
                writer.overdueSlide();
            }
            if (extras.includes(ReportSection.NEXT_ACTIONS)) {
                writer.nextActionsSlide();
            }
            if (extras.includes(ReportSection.SUPPORT)) {
                writer.supportSlide();
            }
            writer.numberSlides();
            deck.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write the management report deck.", exception);
        }
    }

    // ---------- the slides (MR-FR-24) ----------

    private void titleSlide(String title, @Nullable String presenter) {
        XSLFSlide slide = deck.createSlide(layout(SlideLayout.TITLE));
        XSLFAutoShape accent = slide.createAutoShape();
        accent.setShapeType(ShapeType.RECT);
        accent.setAnchor(new Rectangle2D.Double(0, 0, 16, HEIGHT));
        accent.setFillColor(PRIMARY);
        accent.setLineColor(null);
        XSLFTextShape heading = slide.getPlaceholder(0);
        heading.setAnchor(new Rectangle2D.Double(MARGIN + 24, 150, BODY_WIDTH - 24, 110));
        write(heading, clip(title, MAX_TITLE), 40, true, INK);
        XSLFTextShape subtitle = slide.getPlaceholder(1);
        subtitle.setAnchor(new Rectangle2D.Double(MARGIN + 24, 270, BODY_WIDTH - 24, 120));
        subtitle.clearText();
        line(subtitle, asOfLine(), 20, false, MUTED);
        if (extras.draft()) {
            // RM-FR-43: a reviewer cannot mistake a draft for a final report.
            line(subtitle, text.get(Key.DRAFT_MARK), 24, true, HEALTH_COLOURS.get(Health.RED));
        }
        if (!ReportRules.blank(presenter)) {
            line(subtitle, text.format(Key.DECK_PREPARED_BY, presenter.strip()), 18, false, MUTED);
        }
        notes(slide, text.format(Key.NOTES_TITLE, scopeName(), summary.goals().size(), summary.headline().totalTasks()));
    }

    /** RM-FR-41: Highlights, Risks and Focus in the participant's words; left out when all three are empty. */
    private void highlightsSlide() {
        List<String[]> parts = new ArrayList<>();
        for (Object[] part : new Object[][] {{Key.HIGHLIGHTS, extras.highlights()}, {Key.RISKS, extras.risks()},
                {Key.NEXT_FOCUS, extras.nextFocus()}}) {
            String body = (String) part[1];
            if (!body.isEmpty()) {
                parts.add(new String[] {text.get((Key) part[0]), body});
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        XSLFSlide slide = contentSlide(text.get(Key.HIGHLIGHTS));
        double width = (BODY_WIDTH - 24.0 * (parts.size() - 1)) / parts.size();
        double x = MARGIN;
        for (String[] part : parts) {
            XSLFTextBox box = box(slide, x, BODY_TOP, width, 370);
            line(box, part[0], 18, true, PRIMARY);
            commentaryLines(box, part[1], parts.size() == 1 ? MAX_LINE : 60);
            x += width + 24;
        }
        notes(slide, text.get(Key.NOTES_HIGHLIGHTS));
    }

    /** Up to five bullet lines of the participant's text, clipped; "- " bullets become real bullets. */
    private void commentaryLines(XSLFTextShape box, String body, int maxChars) {
        List<String> lines = body.lines().map(String::strip).filter(line -> !line.isEmpty())
                .map(line -> line.startsWith("- ") ? line.substring(2) : line).toList();
        for (String value : lines.stream().limit(MAX_COMMENTARY_LINES).toList()) {
            bullet(line(box, clip(value, maxChars), 15, false, INK));
        }
        if (lines.size() > MAX_COMMENTARY_LINES) {
            line(box, text.format(Key.MORE_IN_EXCEL, lines.size() - MAX_COMMENTARY_LINES), 13, false, MUTED);
        }
    }

    /** RM-FR-35, RM-FR-41: the most important changes since the compared report. */
    private void changesSlide() {
        ManagementReportResponse.Comparison comparison = summary.comparison();
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_CHANGES));
        List<Item> items = new ArrayList<>();
        for (ManagementReportResponse.ChangeItem change : comparison.changes()) {
            String before = text.changeValue(change.kind(), change.before());
            String after = text.changeValue(change.kind(), change.after());
            String values = before.isEmpty() && after.isEmpty() ? "" : " · " + (before.isEmpty() ? after : before + " → " + after);
            items.add(new Item(change.title() + (change.renamed() ? " " + text.get(Key.RENAMED) : ""),
                    text.changeKind(change.kind()) + values + (ReportRules.blank(change.context()) ? "" : " · " + change.context())));
        }
        // MR-FR-25: the overflow line counts every change, not only those kept in the comparison.
        List<Item> shown = items.stream().limit(MAX_CHANGES).toList();
        listItems(slide, shown, MAX_CHANGES, 50, text.get(Key.NO_CHANGES));
        overflow(slide, BODY_TOP + 50.0 * shown.size(), comparison.totalChanges() - shown.size());
        notes(slide, text.format(Key.NOTES_CHANGES, comparison.name(), text.date(comparison.asOf()), comparison.totalChanges()));
    }

    private void summarySlide() {
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_SUMMARY));
        ManagementReportResponse.Headline headline = summary.headline();
        XSLFTextBox big = box(slide, MARGIN, BODY_TOP + 10, 380, 150);
        line(big, headline.overallProgress().stripTrailingZeros().toPlainString() + "%", 60, true, PRIMARY);
        line(big, text.get(Key.OVERALL_PROGRESS).replace(" (%)", ""), 16, false, MUTED);
        line(big, text.format(Key.TASKS_COMPLETED_OF, headline.completedTasks(), headline.totalTasks()), 16, false, MUTED);
        double y = BODY_TOP + 16;
        for (Health health : Health.values()) {
            healthLabel(slide, 500, y, text.health(health) + ": " + headline.goalsByHealth().getOrDefault(health.name(), 0L), health);
            y += 34;
        }
        XSLFTextBox sentences = box(slide, MARGIN, 300, BODY_WIDTH, 180);
        for (String sentence : headlineSentences()) {
            bullet(line(sentences, sentence, 20, false, INK));
        }
        notes(slide, text.get(Key.NOTES_SUMMARY));
    }

    /** MR-FR-24 (2): three sentences built by fixed rules. */
    List<String> headlineSentences() {
        Map<String, Long> health = summary.headline().goalsByHealth();
        long overdue = summary.headline().overdueTasks();
        int support = supportItems().size();
        return List.of(
                text.format(Key.SENTENCE_HEALTH, health.getOrDefault("GREEN", 0L), health.getOrDefault("AMBER", 0L),
                        health.getOrDefault("RED", 0L), health.getOrDefault("DONE", 0L) + health.getOrDefault("DONE_OPEN_WORK", 0L)),
                overdue == 0 ? text.get(Key.SENTENCE_NOTHING_OVERDUE) : text.format(Key.SENTENCE_OVERDUE, overdue),
                support == 0 ? text.get(Key.SENTENCE_NO_SUPPORT) : text.format(Key.SENTENCE_SUPPORT, support));
    }

    private void progressSlide() {
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_PROGRESS));
        List<GoalHealthRow> goals = summary.goals();
        if (goals.isEmpty()) {
            empty(slide, text.get(Key.NO_GOALS));
        }
        double y = BODY_TOP;
        // RM-FR-01: each goal gets a short "why" line, so the rows are taller;
        // with more goals than fit, the last row's place goes to the overflow line.
        int shown = goals.size() > MAX_GOALS ? MAX_GOALS - 1 : goals.size();
        for (GoalHealthRow goal : goals.stream().limit(shown).toList()) {
            XSLFTextBox label = box(slide, MARGIN, y, 360, 46);
            line(label, clip(goal.goalTitle(), 60), 16, true, INK);
            line(label, clip(text.reasons(goal.reasons(), "; "), 60), 12, false, MUTED);
            double progress = Math.max(0, Math.min(100, goal.progress().doubleValue()));
            rect(slide, 420, y + 8, 320, 16, TRACK);
            if (progress > 0) {
                rect(slide, 420, y + 8, 320 * progress / 100, 16, PRIMARY);
            }
            XSLFTextBox percent = box(slide, 748, y, 70, 40);
            line(percent, Math.round(progress) + "%", 16, true, INK);
            healthLabel(slide, 818, y - 2, text.health(Health.valueOf(goal.health())), Health.valueOf(goal.health()));
            y += GOAL_ROW;
        }
        overflow(slide, y, goals.size() - shown);
        notes(slide, text.get(Key.NOTES_PROGRESS) + goalNotes());
    }

    /** RM-FR-41: the participant's goal notes for the presenter, in the goal table's order; "" without any. */
    private String goalNotes() {
        if (!extras.includes(ReportSection.COMMENTARY) || extras.goalNotes().isEmpty()) {
            return "";
        }
        StringBuilder notes = new StringBuilder();
        for (GoalHealthRow goal : summary.goals()) {
            String note = extras.goalNotes().get(goal.goalId());
            if (!ReportRules.blank(note)) {
                notes.append("\n").append(goal.goalTitle()).append(": ").append(note.strip());
            }
        }
        return notes.isEmpty() ? "" : "\n\n" + text.get(Key.NOTES_GOAL_NOTES) + notes;
    }

    private void prioritiesSlide() {
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_PRIORITIES));
        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle2D.Double(MARGIN, BODY_TOP, 400, 200));
        tableRow(table, List.of(text.get(Key.PRIORITY), text.get(Key.ON_TRACK), text.get(Key.OVERDUE)), true);
        long open = 0;
        for (ManagementReportResponse.PriorityCount count : summary.openTasksByPriority()) {
            tableRow(table, List.of(text.priority(Priority.valueOf(count.priority())), String.valueOf(count.onTrack()),
                    String.valueOf(count.overdue())), false);
            open += count.onTrack() + count.overdue();
        }
        table.setColumnWidth(0, 200);
        table.setColumnWidth(1, 100);
        table.setColumnWidth(2, 100);

        LocalDate today = report.asOf();
        List<TaskInfo> dueSoon = report.tasks().map(TaskNode::task).filter(task -> ReportRules.isDueSoon(task, today))
                .sorted(Comparator.comparing(TaskInfo::dueDate)).toList();
        XSLFTextBox list = box(slide, 490, BODY_TOP, WIDTH - MARGIN - 490, 360);
        line(list, text.get(Key.DUE_SOON_ITEMS), 18, true, INK);
        if (dueSoon.isEmpty()) {
            line(list, text.get(Key.NONE_DUE_SOON), 16, false, MUTED);
        }
        for (TaskInfo task : dueSoon.stream().limit(MAX_DUE_SOON).toList()) {
            bullet(line(list, clip(task.title(), 50) + " · " + text.date(task.dueDate()), 16, false, INK));
        }
        if (dueSoon.size() > MAX_DUE_SOON) {
            line(list, text.format(Key.MORE_IN_EXCEL, dueSoon.size() - MAX_DUE_SOON), 14, false, MUTED);
        }
        notes(slide, text.format(Key.NOTES_PRIORITIES, open, summary.headline().overdueTasks(), summary.headline().dueSoonTasks()));
    }

    /** One item on a list slide: a bold first line and a muted second line. */
    private record Item(String heading, String detail) {
    }

    private void blockersSlide() {
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_BLOCKERS));
        List<Item> items = blockerItems();
        listItems(slide, items, MAX_BLOCKERS, 58, text.get(Key.NO_BLOCKERS));
        notes(slide, text.format(Key.NOTES_BLOCKERS, summary.headline().openBlockers(), summary.headline().blockedTasks(),
                summary.unplacedObstacles()));
    }

    /** MR-FR-24 (5): open blockers most severe first, then Blocked tasks; where, why and who could help. */
    List<Item> blockerItems() {
        List<Located> obstacles = new ArrayList<>();
        List<Item> blockedTasks = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().title() + " › " + step.step().title();
                step.blockers().forEach(obstacle -> obstacles.add(new Located(obstacle, where)));
                for (TaskNode task : step.tasks()) {
                    String taskWhere = where + " › " + task.task().title();
                    task.blockers().forEach(obstacle -> obstacles.add(new Located(obstacle, taskWhere)));
                    if (task.task().status() == WorkStatus.BLOCKED) {
                        String why = ReportRules.blank(task.task().blockerReason()) ? "" : task.task().blockerReason().strip();
                        blockedTasks.add(new Item(text.get(Key.BLOCKED_TASK) + ": " + task.task().title(),
                                where + (why.isEmpty() ? "" : " · " + text.get(Key.WHY) + ": " + why)));
                    }
                }
            }
        }
        obstacles.sort(Comparator.comparingInt((Located located) -> severityRank(located.obstacle().severity())));
        List<Item> items = new ArrayList<>();
        for (Located located : obstacles) {
            BlockerInfo obstacle = located.obstacle();
            String helper = text.helper(obstacle.type());
            items.add(new Item(obstacle.title(), located.where() + " · " + text.kind(obstacle.type()) + ", "
                    + text.severity(obstacle.severity()) + (helper.isEmpty() ? "" : " · " + text.get(Key.SUGGESTED_HELPER) + ": " + helper)));
        }
        items.addAll(blockedTasks);
        return items;
    }

    private record Located(BlockerInfo obstacle, String where) {
    }

    private void overdueSlide() {
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_OVERDUE));
        LocalDate today = report.asOf();
        record Late(TaskInfo task, Item item) {
        }
        List<Late> late = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                for (TaskNode task : step.tasks()) {
                    TaskInfo item = task.task();
                    if (ReportRules.isOverdue(item, today)) {
                        late.add(new Late(item, new Item(item.title(), goal.goal().title() + " › " + step.step().title() + " · "
                                + text.format(Key.DUE, text.date(item.dueDate())) + " ("
                                + text.format(Key.DAYS_OVERDUE, ChronoUnit.DAYS.between(item.dueDate(), today)) + ") · "
                                + text.get(Key.OWNER) + ": " + item.owner())));
                    }
                }
            }
        }
        // Most overdue first.
        late.sort(Comparator.comparing((Late entry) -> entry.task().dueDate()));
        List<Item> sorted = late.stream().map(Late::item).toList();
        List<Item> items = sorted;
        listItems(slide, sorted, MAX_OVERDUE, 50, text.get(Key.NO_OVERDUE));
        notes(slide, text.format(Key.NOTES_OVERDUE, items.size()));
    }

    private void nextActionsSlide() {
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_NEXT));
        List<Item> items = new ArrayList<>();
        for (GoalHealthRow row : summary.goals()) {
            if ("DONE".equals(row.health())) {
                continue;
            }
            report.goals().stream().filter(node -> node.goal().id().equals(row.goalId())).findFirst()
                    .flatMap(node -> node.tasks().map(TaskNode::task).filter(task -> task.status() != WorkStatus.COMPLETED).findFirst())
                    .ifPresent(task -> items.add(new Item(row.goalTitle(),
                            (ReportRules.blank(task.nextAction()) ? task.title() : task.nextAction().strip()) + " · "
                                    + text.get(Key.OWNER) + ": " + task.owner()
                                    + (task.dueDate() == null ? "" : " · " + text.format(Key.DUE, text.date(task.dueDate()))))));
        }
        listItems(slide, items, MAX_NEXT, 50, text.get(Key.NO_NEXT_ACTIONS));
        notes(slide, text.get(Key.NOTES_NEXT));
    }

    /**
     * MR-FR-24 (8), MR-FR-26: left out when nothing needs a decision or
     * support. RM-FR-41: the participant's own text comes first, then the
     * rule-based list below it.
     */
    private void supportSlide() {
        List<Item> items = supportItems();
        String own = !extras.includes(ReportSection.COMMENTARY) ? ""
                : extras.nothingNeeded() ? text.get(Key.NOTHING_NEEDED) : extras.decisionsNeeded();
        if (items.isEmpty() && own.isEmpty()) {
            return;
        }
        XSLFSlide slide = contentSlide(text.get(Key.SLIDE_SUPPORT));
        if (own.isEmpty()) {
            listItems(slide, items, MAX_SUPPORT, 58, "");
        } else {
            XSLFTextBox box = box(slide, MARGIN, BODY_TOP, BODY_WIDTH, 150);
            line(box, text.get(Key.YOUR_WORDS), 16, true, PRIMARY);
            commentaryLines(box, own, MAX_LINE);
            double y = BODY_TOP + 160;
            int room = items.isEmpty() ? 0 : Math.max(1, (int) ((HEIGHT - 80 - y) / 58));
            for (Item item : items.stream().limit(room).toList()) {
                XSLFTextBox row = box(slide, MARGIN, y, BODY_WIDTH, 58);
                line(row, clip(item.heading(), MAX_TITLE), 16, true, INK);
                line(row, clip(item.detail(), MAX_LINE), 14, false, MUTED);
                y += 58;
            }
            overflow(slide, y, items.size() - room);
        }
        notes(slide, text.get(Key.NOTES_SUPPORT));
    }

    /** Blockers about a decision, money or a partner, then steps whose gap needs help (V17 BL-FR-22). */
    List<Item> supportItems() {
        List<Item> items = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().title() + " › " + step.step().title();
                List<BlockerInfo> blockers = new ArrayList<>(step.blockers());
                step.tasks().forEach(task -> blockers.addAll(task.blockers()));
                for (BlockerInfo obstacle : blockers) {
                    if (SUPPORT_KINDS.contains(obstacle.type())) {
                        String helper = text.helper(obstacle.type());
                        items.add(new Item(obstacle.title(), where + " · " + text.kind(obstacle.type())
                                + (helper.isEmpty() ? "" : " · " + text.get(Key.SUGGESTED_HELPER) + ": " + helper)));
                    }
                }
            }
        }
        for (GoalNode goal : report.goals()) {
            goal.steps().stream().filter(StepNode::gapNeedsHelp).forEach(step -> items.add(new Item(step.step().title(),
                    goal.goal().title() + " · " + text.format(Key.GAP_NEEDS_HELP, text.kind(step.step().gapKind()).toLowerCase())
                            + " · " + text.get(Key.SUGGESTED_HELPER) + ": " + text.helper(step.step().gapKind()))));
        }
        return items;
    }

    // ---------- building blocks ----------

    private XSLFSlideLayout layout(SlideLayout type) {
        return deck.getSlideMasters().get(0).getLayout(type);
    }

    /** A slide with a real title placeholder (MR-NFR-07), styled the same on every slide (MR-FR-28). */
    private XSLFSlide contentSlide(String title) {
        XSLFSlide slide = deck.createSlide(layout(SlideLayout.TITLE_ONLY));
        XSLFTextShape heading = slide.getPlaceholder(0);
        heading.setAnchor(new Rectangle2D.Double(MARGIN, 28, BODY_WIDTH, 56));
        write(heading, title, 28, true, INK);
        heading.getTextParagraphs().forEach(paragraph -> paragraph.setTextAlign(TextAlign.LEFT));
        rect(slide, MARGIN, 86, 64, 4, PRIMARY);
        return slide;
    }

    private void listItems(XSLFSlide slide, List<Item> items, int max, double rowHeight, String emptyText) {
        if (items.isEmpty()) {
            empty(slide, emptyText);
            return;
        }
        double y = BODY_TOP;
        for (Item item : items.stream().limit(max).toList()) {
            XSLFTextBox box = box(slide, MARGIN, y, BODY_WIDTH, rowHeight);
            line(box, clip(item.heading(), MAX_TITLE), 16, true, INK);
            line(box, clip(item.detail(), MAX_LINE), 14, false, MUTED);
            y += rowHeight;
        }
        overflow(slide, y, items.size() - max);
    }

    /** MR-FR-25: "and N more (see the Excel report)" instead of text running off the slide. */
    private void overflow(XSLFSlide slide, double y, int more) {
        if (more > 0) {
            XSLFTextBox box = box(slide, MARGIN, Math.min(y, HEIGHT - 70), BODY_WIDTH, 28);
            line(box, text.format(Key.MORE_IN_EXCEL, more), 14, false, MUTED);
        }
    }

    /** MR-FR-26: a slide with nothing to show says so. */
    private void empty(XSLFSlide slide, String message) {
        XSLFTextBox box = box(slide, MARGIN, BODY_TOP + 40, BODY_WIDTH, 60);
        line(box, message, 22, false, MUTED);
    }

    /** MR-FR-27: health as a coloured dot and a text label, never colour alone. */
    private void healthLabel(XSLFSlide slide, double x, double y, String label, Health health) {
        XSLFAutoShape dot = slide.createAutoShape();
        dot.setShapeType(ShapeType.ELLIPSE);
        dot.setAnchor(new Rectangle2D.Double(x, y + 6, 14, 14));
        dot.setFillColor(HEALTH_COLOURS.get(health));
        dot.setLineColor(null);
        XSLFTextBox box = box(slide, x + 20, y, 300, 28);
        line(box, label, 16, false, INK);
    }

    /** A bullet with room between the dot and the text. */
    private static void bullet(XSLFTextParagraph paragraph) {
        paragraph.setBullet(true);
        paragraph.setLeftMargin(18.0);
        paragraph.setIndent(-18.0);
    }

    private void rect(XSLFSlide slide, double x, double y, double width, double height, Color colour) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(ShapeType.RECT);
        shape.setAnchor(new Rectangle2D.Double(x, y, width, height));
        shape.setFillColor(colour);
        shape.setLineColor(null);
    }

    private void tableRow(XSLFTable table, List<String> values, boolean header) {
        XSLFTableRow row = table.addRow();
        row.setHeight(30);
        for (int column = 0; column < values.size(); column++) {
            XSLFTableCell cell = row.addCell();
            cell.clearText();
            XSLFTextParagraph paragraph = cell.addNewTextParagraph();
            paragraph.setTextAlign(column == 0 ? TextAlign.LEFT : TextAlign.RIGHT);
            run(paragraph, values.get(column), 16, header, header ? Color.WHITE : INK);
            cell.setFillColor(header ? PRIMARY : null);
            cell.setBorderColor(BorderEdge.bottom, TRACK);
        }
    }

    private XSLFTextBox box(XSLFSlide slide, double x, double y, double width, double height) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x, y, width, height));
        box.setWordWrap(true);
        box.setTextAutofit(TextAutofit.NONE);
        box.clearText();
        return box;
    }

    private void write(XSLFTextShape shape, String value, double size, boolean bold, Color colour) {
        shape.clearText();
        line(shape, value, size, bold, colour);
    }

    private XSLFTextParagraph line(XSLFTextShape shape, String value, double size, boolean bold, Color colour) {
        XSLFTextParagraph paragraph = shape.addNewTextParagraph();
        run(paragraph, value, size, bold, colour);
        return paragraph;
    }

    private void run(XSLFTextParagraph paragraph, String value, double size, boolean bold, Color colour) {
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(value);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(colour);
        run.setFontFamily(font);
    }

    /** MR-FR-29: the figures behind each slide, for the presenter. */
    private void notes(XSLFSlide slide, String value) {
        XSLFNotes notes = deck.getNotesSlide(slide);
        for (XSLFTextShape shape : notes.getPlaceholders()) {
            if (shape.getTextType() == Placeholder.BODY) {
                shape.setText(value);
            }
        }
    }

    /** MR-FR-28: a slide number in the footer of every slide but the title. */
    private void numberSlides() {
        List<XSLFSlide> slides = deck.getSlides();
        for (int index = 1; index < slides.size(); index++) {
            XSLFTextBox box = box(slides.get(index), WIDTH - MARGIN - 80, HEIGHT - 36, 80, 24);
            XSLFTextParagraph paragraph = line(box, (index + 1) + " / " + slides.size(), 12, false, MUTED);
            paragraph.setTextAlign(TextAlign.RIGHT);
            XSLFTextBox scope = box(slides.get(index), MARGIN, HEIGHT - 36, 600, 24);
            line(scope, clip(asOfLine(), 90), 12, false, MUTED);
        }
    }

    private String scopeName() {
        return "ALL".equals(report.scopeKind()) || report.scopeLabel() == null ? text.get(Key.ALL_DREAMS) : report.scopeLabel();
    }

    private String asOfLine() {
        return text.asOfLine(report);
    }

    private static int severityRank(@Nullable Severity severity) {
        return severity == null ? Severity.values().length : Severity.values().length - 1 - severity.ordinal();
    }

    static String clip(String value, int max) {
        String clean = value == null ? "" : value.strip().replace('\n', ' ');
        return clean.length() <= max ? clean : clean.substring(0, max - 1).strip() + "…";
    }

}
