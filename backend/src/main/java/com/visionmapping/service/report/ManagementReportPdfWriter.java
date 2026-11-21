package com.visionmapping.service.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.GoalHealthRow;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.service.report.ReportLists.Item;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.service.report.ReportText.Key;
import com.visionmapping.service.support.PdfFonts;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * V19 RM-FR-42: the Management Report as an A4 landscape PDF, from the same
 * model and Summary as the Excel report and the deck (RM-NFR-03), with
 * OpenPDF (RM-NFR-10). A cover, the participant's commentary, the headline
 * and period figures with their changes, the goal health table with reasons
 * and simple progress bars, What changed, blockers, overdue work, next
 * actions and the decisions and support needed; only the chosen sections
 * (RM-FR-39). Every page's footer has the status ("Final · frozen …",
 * "DRAFT · …") and its number. Khmer prints through {@link PdfFonts}.
 */
public final class ManagementReportPdfWriter {

    /** The cover's name and the status line in every footer. */
    public record Cover(String name, String status) {
    }

    static final int MAX_LIST = 25;
    private static final Color PRIMARY = new Color(0x00, 0x78, 0xD4);
    private static final Color TRACK = new Color(0xED, 0xEB, 0xE9);
    private static final Color MUTED = new Color(0x60, 0x5E, 0x5C);
    private static final Color HEADER = new Color(0xF3, 0xF2, 0xF1);
    private static final Color DRAFT = new Color(0xD1, 0x34, 0x38);

    private final ManagementReport report;
    private final ManagementReportResponse summary;
    private final ReportText text;
    private final ReportExtras extras;
    private final PdfFonts fonts;
    private final Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);

    private ManagementReportPdfWriter(ManagementReport report, ManagementReportResponse summary, ReportText text, ReportExtras extras,
                                      PdfFonts fonts) {
        this.report = report;
        this.summary = summary;
        this.text = text;
        this.extras = extras;
        this.fonts = fonts;
    }

    public static byte[] write(ManagementReport report, ManagementReportResponse summary, ReportText text, ReportExtras extras,
                               PdfFonts fonts, Cover cover) {
        ManagementReportPdfWriter writer = new ManagementReportPdfWriter(report, summary, text, extras, fonts);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter pdf = PdfWriter.getInstance(writer.document, out);
            pdf.setPageEvent(writer.new Footer(cover.status()));
            writer.document.addTitle(cover.name());
            writer.document.addSubject(text.asOfLine(report));
            writer.document.open();
            writer.cover(cover);
            if (extras.includes(ReportSection.COMMENTARY) && extras.hasCommentary()) {
                writer.commentary();
            }
            writer.figures();
            if (extras.includes(ReportSection.SUMMARY)) {
                writer.breakdown();
            }
            if (extras.includes(ReportSection.GOAL_HEALTH)) {
                writer.goals();
            }
            if (extras.includes(ReportSection.CHANGES) && summary.comparison() != null) {
                writer.changes();
            }
            if (extras.includes(ReportSection.BLOCKERS)) {
                writer.list(Key.SLIDE_BLOCKERS, ReportLists.blockers(report, text), Key.NO_BLOCKERS);
            }
            if (extras.includes(ReportSection.DETAILS)) {
                writer.list(Key.SLIDE_OVERDUE, ReportLists.overdue(report, text), Key.NO_OVERDUE);
            }
            if (extras.includes(ReportSection.NEXT_ACTIONS)) {
                writer.list(Key.SLIDE_NEXT, ReportLists.nextActions(report, summary, text), Key.NO_NEXT_ACTIONS);
            }
            if (extras.includes(ReportSection.SUPPORT)) {
                writer.support();
            }
            writer.document.close();
            return out.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to write the management report PDF.", exception);
        }
    }

    // ---------- sections ----------

    private void cover(Cover cover) {
        add(paragraph(cover.name(), fonts.getTitle(), 0, 4));
        if (!cover.name().equals(text.get(Key.TITLE))) {
            add(paragraph(text.get(Key.TITLE), fonts.getBody(), 0, 2));
        }
        add(paragraph(text.asOfLine(report), fonts.getBody(), 0, 2));
        ManagementReportResponse.Comparison comparison = summary.comparison();
        if (comparison != null) {
            add(paragraph(text.format(Key.COMPARED_WITH, comparison.name(), comparison.code(), text.date(comparison.asOf())),
                    fonts.getBody(), 0, 2));
        }
        Font status = new Font(fonts.getHeading());
        if (extras.draft()) {
            // RM-FR-43: a reviewer cannot mistake a draft for a final report.
            status.setColor(DRAFT);
        }
        add(paragraph(cover.status(), status, 4, 8));
    }

    /** RM-FR-31: the four sections (empty ones left out) and the goal notes. */
    private void commentary() {
        heading(Key.COMMENTARY_SHEET);
        textBlock(Key.HIGHLIGHTS, extras.highlights());
        textBlock(Key.RISKS, extras.risks());
        textBlock(Key.DECISIONS_NEEDED, extras.nothingNeeded() ? text.get(Key.NOTHING_NEEDED) : extras.decisionsNeeded());
        textBlock(Key.NEXT_FOCUS, extras.nextFocus());
        List<List<String>> notes = new ArrayList<>();
        for (GoalHealthRow goal : summary.goals()) {
            String note = extras.goalNotes().get(goal.goalId());
            if (!ReportRules.blank(note)) {
                notes.add(List.of(goal.goalTitle(), note.strip()));
            }
        }
        if (!notes.isEmpty()) {
            add(paragraph(text.get(Key.GOAL_NOTES), fonts.getSmallBold(), 6, 2));
            add(table(List.of(text.get(Key.GOAL), text.get(Key.NOTE)), notes, new float[] {2, 5}));
        }
    }

    private void textBlock(Key heading, String body) {
        if (body.isEmpty()) {
            return;
        }
        add(paragraph(text.get(heading), fonts.getSmallBold(), 6, 2));
        for (String line : ReportLists.lines(body)) {
            add(paragraph("• " + line, fonts.getBody(), 0, 1));
        }
    }

    /** Always included: the headline figures (with their change when compared) and the period figures. */
    private void figures() {
        heading(Key.HEADLINE);
        ManagementReportResponse.Headline headline = summary.headline();
        ManagementReportResponse.HeadlineChange change = summary.comparison() == null ? null : summary.comparison().headline();
        List<String> headers = new ArrayList<>(List.of(text.get(Key.HEADLINE), text.get(Key.VALUE)));
        List<List<String>> rows = new ArrayList<>(List.of(
                new ArrayList<>(List.of(text.get(Key.OVERALL_PROGRESS), headline.overallProgress().stripTrailingZeros().toPlainString())),
                new ArrayList<>(List.of(text.get(Key.TOTAL_TASKS), String.valueOf(headline.totalTasks()))),
                new ArrayList<>(List.of(text.get(Key.COMPLETED_TASKS), String.valueOf(headline.completedTasks()))),
                new ArrayList<>(List.of(text.get(Key.OVERDUE_TASKS), String.valueOf(headline.overdueTasks()))),
                new ArrayList<>(List.of(text.get(Key.BLOCKED_TASKS), String.valueOf(headline.blockedTasks()))),
                new ArrayList<>(List.of(text.get(Key.OPEN_BLOCKERS), String.valueOf(headline.openBlockers()))),
                new ArrayList<>(List.of(text.get(Key.DUE_SOON), String.valueOf(headline.dueSoonTasks())))));
        if (change != null) {
            headers.add(text.format(Key.CHANGE_SINCE, summary.comparison().code()));
            List<String> changes = List.of(ReportText.signed(change.overallProgress()), ReportText.signed(change.totalTasks()),
                    ReportText.signed(change.completedTasks()), ReportText.signed(change.overdueTasks()),
                    ReportText.signed(change.blockedTasks()), ReportText.signed(change.openBlockers()),
                    ReportText.signed(change.dueSoonTasks()));
            for (int index = 0; index < rows.size(); index++) {
                rows.get(index).add(changes.get(index));
            }
        }
        PdfPTable figures = table(headers, rows, change == null ? new float[] {3, 1} : new float[] {3, 1, 1});
        figures.setWidthPercentage(60);
        figures.setHorizontalAlignment(Element.ALIGN_LEFT);
        add(figures);

        ManagementReport.PeriodFigures period = report.periodFigures();
        add(paragraph(text.format(Key.THIS_PERIOD, text.periodDates(report.period())), fonts.getSmallBold(), 8, 2));
        PdfPTable periodTable = table(List.of(text.get(Key.HEADLINE), text.get(Key.VALUE)), List.of(
                List.of(text.get(Key.PERIOD_TASKS_COMPLETED), String.valueOf(period.tasksCompleted())),
                List.of(text.get(Key.PERIOD_PROGRESS_GAINED), period.progressGained().toPlainString()),
                List.of(text.get(Key.PERIOD_BLOCKERS_OPENED), String.valueOf(period.blockersOpened())),
                List.of(text.get(Key.PERIOD_BLOCKERS_CLOSED), String.valueOf(period.blockersClosed())),
                List.of(text.get(Key.PERIOD_BECAME_OVERDUE), String.valueOf(period.tasksBecameOverdue()))), new float[] {3, 1});
        periodTable.setWidthPercentage(60);
        periodTable.setHorizontalAlignment(Element.ALIGN_LEFT);
        add(periodTable);
    }

    /** SUMMARY: goals by health and open tasks by priority. */
    private void breakdown() {
        heading(Key.GOAL_HEALTH);
        List<List<String>> health = new ArrayList<>();
        for (Health value : Health.values()) {
            health.add(List.of(text.health(value), String.valueOf(summary.headline().goalsByHealth().getOrDefault(value.name(), 0L))));
        }
        PdfPTable healthTable = table(List.of(text.get(Key.GOAL_HEALTH), text.get(Key.GOALS)), health, new float[] {3, 1});
        healthTable.setWidthPercentage(45);
        healthTable.setHorizontalAlignment(Element.ALIGN_LEFT);
        add(healthTable);
        List<List<String>> priorities = new ArrayList<>();
        summary.openTasksByPriority().forEach(count -> priorities.add(List.of(text.priority(Priority.valueOf(count.priority())),
                String.valueOf(count.onTrack()), String.valueOf(count.overdue()))));
        add(paragraph(text.get(Key.OPEN_BY_PRIORITY), fonts.getSmallBold(), 8, 2));
        PdfPTable priorityTable = table(List.of(text.get(Key.PRIORITY), text.get(Key.ON_TRACK), text.get(Key.OVERDUE)), priorities,
                new float[] {2, 1, 1});
        priorityTable.setWidthPercentage(45);
        priorityTable.setHorizontalAlignment(Element.ALIGN_LEFT);
        add(priorityTable);
    }

    /** GOAL_HEALTH: one row per goal, with its progress as a simple bar, its reasons and, when compared, its change. */
    private void goals() {
        heading(Key.SLIDE_PROGRESS);
        if (summary.goals().isEmpty()) {
            add(paragraph(text.get(Key.NO_GOALS), fonts.getBody(), 0, 4));
            return;
        }
        boolean compared = summary.comparison() != null;
        List<String> headers = new ArrayList<>(List.of(text.get(Key.GOAL), text.get(Key.HEALTH), text.get(Key.PROGRESS),
                text.get(Key.TARGET_DATE), text.get(Key.WHY)));
        if (compared) {
            headers.add(text.get(Key.CHANGE));
        }
        PdfPTable table = new PdfPTable(compared ? new float[] {3, 1.4f, 2, 1.3f, 3, 2.5f} : new float[] {3, 1.4f, 2, 1.3f, 3});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        headers.forEach(header -> table.addCell(headerCell(header)));
        for (GoalHealthRow goal : summary.goals()) {
            table.addCell(cell(goal.goalTitle()));
            table.addCell(cell(text.health(Health.valueOf(goal.health()))));
            table.addCell(progressCell(goal.progress().doubleValue()));
            table.addCell(cell(goal.targetDate() == null ? "—" : text.date(goal.targetDate())));
            table.addCell(cell(text.reasons(goal.reasons(), "; ")));
            if (compared) {
                table.addCell(cell(text.goalChange(summary.comparison().goal(goal.goalId()), goal)));
            }
        }
        add(table);
    }

    /** CHANGES: What changed, then the goals no longer in the report. */
    private void changes() {
        ManagementReportResponse.Comparison comparison = summary.comparison();
        List<Item> items = ReportLists.changes(comparison, text);
        list(Key.SLIDE_CHANGES, items, Key.NO_CHANGES, comparison.totalChanges());
        if (!comparison.removed().isEmpty()) {
            add(paragraph(text.get(Key.NO_LONGER_IN_REPORT), fonts.getSmallBold(), 6, 2));
            for (ManagementReportResponse.RemovedGoal goal : comparison.removed()) {
                String reason = text.removedReason(goal.reason());
                add(paragraph("• " + goal.title() + (reason.isEmpty() ? "" : " · " + reason), fonts.getBody(), 0, 1));
            }
        }
    }

    /** SUPPORT: the participant's own words first (when commentary is included), then the rule-based list. */
    private void support() {
        List<Item> items = ReportLists.support(report, text);
        String own = !extras.includes(ReportSection.COMMENTARY) ? ""
                : extras.nothingNeeded() ? text.get(Key.NOTHING_NEEDED) : extras.decisionsNeeded();
        if (items.isEmpty() && own.isEmpty()) {
            return;
        }
        heading(Key.SLIDE_SUPPORT);
        if (!own.isEmpty()) {
            add(paragraph(text.get(Key.YOUR_WORDS), fonts.getSmallBold(), 0, 2));
            ReportLists.lines(own).forEach(line -> add(paragraph("• " + line, fonts.getBody(), 0, 1)));
        }
        items(items, items.size());
    }

    private void list(Key heading, List<Item> items, Key empty) {
        list(heading, items, empty, items.size());
    }

    private void list(Key heading, List<Item> items, Key empty, int total) {
        heading(heading);
        if (items.isEmpty()) {
            add(paragraph(text.get(empty), fonts.getBody(), 0, 4));
            return;
        }
        items(items, total);
    }

    /** Up to 25 items, then "and N more (see the Excel report)" (MR-FR-25). */
    private void items(List<Item> items, int total) {
        List<Item> shown = items.stream().limit(MAX_LIST).toList();
        for (Item item : shown) {
            add(paragraph(item.heading(), fonts.getSmallBold(), 3, 0));
            Font detail = new Font(fonts.getSmall());
            detail.setColor(MUTED);
            add(paragraph(item.detail(), detail, 0, 1));
        }
        if (total > shown.size()) {
            add(paragraph(text.format(Key.MORE_IN_EXCEL, total - shown.size()), fonts.getSmall(), 3, 0));
        }
    }

    // ---------- building blocks ----------

    private void heading(Key key) {
        Paragraph heading = paragraph(text.get(key), fonts.getHeading(), 12, 4);
        heading.setKeepTogether(true);
        add(heading);
    }

    private Paragraph paragraph(String value, Font font, float before, float after) {
        Paragraph paragraph = new Paragraph(fonts.phrase(value, font));
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(after);
        return paragraph;
    }

    private PdfPTable table(List<String> headers, List<? extends List<String>> rows, float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingAfter(4);
        headers.forEach(header -> table.addCell(headerCell(header)));
        rows.forEach(row -> row.forEach(value -> table.addCell(cell(value))));
        return table;
    }

    private PdfPCell headerCell(String value) {
        PdfPCell cell = new PdfPCell(fonts.phrase(value, fonts.getSmallBold()));
        cell.setBackgroundColor(HEADER);
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell cell(String value) {
        PdfPCell cell = new PdfPCell(fonts.phrase(value == null ? "" : value, fonts.getSmall()));
        cell.setPadding(4);
        return cell;
    }

    /** RM-FR-42: a chart drawn as a simple bar, with the percentage in words beside it. */
    private PdfPCell progressCell(double value) {
        double progress = Math.max(0, Math.min(100, value));
        PdfPTable bar = new PdfPTable(progress <= 0 || progress >= 100 ? new float[] {1} : new float[] {(float) progress, (float) (100 - progress)});
        bar.setWidthPercentage(100);
        PdfPCell filled = new PdfPCell(fonts.phrase(" ", fonts.getSmall()));
        filled.setBackgroundColor(progress <= 0 ? TRACK : PRIMARY);
        filled.setBorder(Rectangle.NO_BORDER);
        filled.setFixedHeight(8);
        bar.addCell(filled);
        if (progress > 0 && progress < 100) {
            PdfPCell rest = new PdfPCell(fonts.phrase(" ", fonts.getSmall()));
            rest.setBackgroundColor(TRACK);
            rest.setBorder(Rectangle.NO_BORDER);
            rest.setFixedHeight(8);
            bar.addCell(rest);
        }
        PdfPCell cell = new PdfPCell();
        cell.setPadding(4);
        cell.addElement(bar);
        cell.addElement(new Paragraph(fonts.phrase(Math.round(progress) + "%", fonts.getSmall())));
        return cell;
    }

    private void add(Element element) {
        try {
            document.add(element);
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to write the management report PDF.", exception);
        }
    }

    /** RM-FR-42: the status on the left and the page number on the right of every page. */
    private final class Footer extends PdfPageEventHelper {

        private final String status;

        Footer(String status) {
            this.status = status;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document page) {
            Font font = new Font(fonts.getSmall());
            font.setColor(extras.draft() ? DRAFT : MUTED);
            float y = page.bottom() - 24;
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, fonts.phrase(status, font), page.left(), y, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    fonts.phrase(text.format(Key.PDF_PAGE, writer.getPageNumber()), fonts.getSmall()), page.right(), y, 0);
        }
    }
}
