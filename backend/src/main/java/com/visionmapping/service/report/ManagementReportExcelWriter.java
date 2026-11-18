package com.visionmapping.service.report;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportText.Key;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.lang.Nullable;

/**
 * V18 phase 2 (MR-FR-15 to MR-FR-22): the Management Report as an Excel
 * workbook. Three sheets: Summary, Report (one row per task with the eight
 * requested columns) and Blockers. It only formats the report model; it never
 * queries (MR-NFR-03), and it writes nothing to disk (MR-FR-15).
 */
public final class ManagementReportExcelWriter {

    /** MR-NFR-05: a bigger report asks the participant to narrow the filter. */
    public static final int MAX_ROWS = 10_000;
    public static final String TOO_MANY_ROWS = "This report has more than 10,000 rows. Choose one vision area or one dream.";

    private static final int HEADER_ROW = 3;
    private static final int TEXT_WIDTH = 32;
    private static final int WIDE_TEXT_WIDTH = 55;
    private static final String COMPLETED_FLAG = "COMPLETED";
    private static final String BLOCKED_FLAG = "BLOCKED";
    private static final String OVERDUE_FLAG = "Y";

    private final ManagementReport report;
    private final ManagementReportResponse summary;
    private final ReportText text;
    private final Workbook workbook = new XSSFWorkbook();
    private final Styles styles;

    private ManagementReportExcelWriter(ManagementReport report, ManagementReportResponse summary, ReportText text) {
        this.report = report;
        this.summary = summary;
        this.text = text;
        this.styles = new Styles(workbook, text.isKhmer() ? ReportText.KHMER_FONT : "Calibri");
    }

    public static byte[] write(ManagementReport report, ManagementReportResponse summary, ReportText text) {
        List<ReportRows.Row> rows = ReportRows.build(report, text);
        if (rows.size() > MAX_ROWS) {
            throw new BusinessRuleException(TOO_MANY_ROWS);
        }
        ManagementReportExcelWriter writer = new ManagementReportExcelWriter(report, summary, text);
        try (Workbook workbook = writer.workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.summarySheet();
            writer.reportSheet(rows);
            writer.blockersSheet();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write the management report.", exception);
        }
    }

    /** MR-FR-04, RM-FR-07: "All dreams · This month · as of 16 Nov 2026". */
    private String asOfLine() {
        return text.asOfLine(report);
    }

    private void titleRows(Sheet sheet) {
        cell(sheet.createRow(0), 0, text.get(Key.TITLE), styles.title);
        cell(sheet.createRow(1), 0, asOfLine(), styles.subtitle);
    }

    // ---------- Summary (MR-FR-20) ----------

    private void summarySheet() {
        Sheet sheet = workbook.createSheet(text.get(Key.SUMMARY_SHEET));
        titleRows(sheet);
        ManagementReportResponse.Headline headline = summary.headline();
        int row = HEADER_ROW;
        row = table(sheet, row, List.of(text.get(Key.HEADLINE), text.get(Key.VALUE)), List.of(
                List.of(text.get(Key.OVERALL_PROGRESS), headline.overallProgress()),
                List.of(text.get(Key.GOALS_IN_SCOPE), (long) summary.goals().size()),
                List.of(text.get(Key.TOTAL_TASKS), headline.totalTasks()),
                List.of(text.get(Key.COMPLETED_TASKS), headline.completedTasks()),
                List.of(text.get(Key.OVERDUE_TASKS), headline.overdueTasks()),
                List.of(text.get(Key.BLOCKED_TASKS), headline.blockedTasks()),
                List.of(text.get(Key.OPEN_BLOCKERS), headline.openBlockers()),
                List.of(text.get(Key.DUE_SOON), headline.dueSoonTasks())));
        // RM-FR-08: what happened in the period, under its dates.
        ManagementReport.PeriodFigures period = report.periodFigures();
        row = table(sheet, row + 1, List.of(text.format(Key.THIS_PERIOD, text.periodDates(report.period())), text.get(Key.VALUE)), List.of(
                List.of(text.get(Key.PERIOD_TASKS_COMPLETED), period.tasksCompleted()),
                List.of(text.get(Key.PERIOD_PROGRESS_GAINED), period.progressGained()),
                List.of(text.get(Key.PERIOD_BLOCKERS_OPENED), period.blockersOpened()),
                List.of(text.get(Key.PERIOD_BLOCKERS_CLOSED), period.blockersClosed()),
                List.of(text.get(Key.PERIOD_BECAME_OVERDUE), period.tasksBecameOverdue())));
        List<List<Object>> health = new ArrayList<>();
        for (ReportRules.Health value : ReportRules.Health.values()) {
            health.add(List.of(text.health(value), headline.goalsByHealth().getOrDefault(value.name(), 0L)));
        }
        row = table(sheet, row + 1, List.of(text.get(Key.GOAL_HEALTH), text.get(Key.GOALS)), health);
        List<List<Object>> statuses = new ArrayList<>();
        summary.tasksByStatus().forEach((status, count) -> statuses.add(List.of(text.status(WorkStatus.valueOf(status)), count)));
        row = table(sheet, row + 1, List.of(text.get(Key.TASKS_BY_STATUS), text.get(Key.TASKS)), statuses);
        List<List<Object>> priorities = new ArrayList<>();
        summary.openTasksByPriority().forEach(count -> priorities.add(List.of(text.priority(Priority.valueOf(count.priority())),
                count.onTrack(), count.overdue())));
        row = table(sheet, row + 1, List.of(text.get(Key.OPEN_BY_PRIORITY), text.get(Key.ON_TRACK), text.get(Key.OVERDUE)), priorities);
        List<List<Object>> goals = new ArrayList<>();
        for (ManagementReportResponse.GoalHealthRow goal : summary.goals()) {
            goals.add(List.of(goal.dreamTitle(), goal.goalTitle(), text.health(ReportRules.Health.valueOf(goal.health())), goal.progress(),
                    goal.targetDate() == null ? "" : goal.targetDate(), goal.overdueTasks(), goal.blockedItems(),
                    goal.nextAction() == null ? "" : goal.nextAction(), text.reasons(goal.reasons(), "\n")));
        }
        // RM-FR-01: a Why column with the rules that set each goal's health.
        table(sheet, row + 1, List.of(text.get(Key.DREAM), text.get(Key.GOAL), text.get(Key.HEALTH), text.get(Key.PROGRESS),
                text.get(Key.TARGET_DATE), text.get(Key.OVERDUE_TASKS), text.get(Key.BLOCKED_ITEMS), text.get(Key.NEXT_ACTION),
                text.get(Key.WHY)), goals);
        for (int column = 0; column < 9; column++) {
            sheet.autoSizeColumn(column);
            capWidth(sheet, column, WIDE_TEXT_WIDTH);
        }
        printSetup(sheet, -1);
    }

    /** A header row and its rows from {@code start}; returns the next free row. */
    private int table(Sheet sheet, int start, List<String> headers, List<List<Object>> rows) {
        Row header = sheet.createRow(start);
        for (int column = 0; column < headers.size(); column++) {
            cell(header, column, headers.get(column), styles.header);
        }
        int index = start + 1;
        for (List<Object> values : rows) {
            Row row = sheet.createRow(index++);
            for (int column = 0; column < values.size(); column++) {
                value(row, column, values.get(column));
            }
        }
        return index;
    }

    // ---------- Report (MR-FR-16 to MR-FR-19, MR-FR-22) ----------

    private void reportSheet(List<ReportRows.Row> rows) {
        Sheet sheet = workbook.createSheet(text.get(Key.REPORT_SHEET));
        titleRows(sheet);
        // Q-01: a Dream column first only when more than one dream can be in scope.
        boolean dreamColumn = !"DREAM".equals(report.scopeKind());
        List<String> headers = new ArrayList<>();
        if (dreamColumn) {
            headers.add(text.get(Key.DREAM));
        }
        headers.addAll(List.of(text.get(Key.GOALS), text.get(Key.STEPS), text.get(Key.TASKS), text.get(Key.BLOCKERS_ISSUES),
                text.get(Key.PROGRESS), text.get(Key.PRIORITY), text.get(Key.DUE_DATE), text.get(Key.NEXT_ACTION)));
        int offset = dreamColumn ? 1 : 0;
        int issuesColumn = offset + 3;
        int priorityColumn = offset + 5;
        int dueColumn = offset + 6;
        int nextColumn = offset + 7;
        int flagColumn = offset + 8;
        int overdueColumn = offset + 9;

        Row header = sheet.createRow(HEADER_ROW);
        for (int column = 0; column < headers.size(); column++) {
            cell(header, column, headers.get(column), styles.header);
        }
        int index = HEADER_ROW + 1;
        for (ReportRows.Row data : rows) {
            Row row = sheet.createRow(index++);
            if (dreamColumn) {
                cell(row, 0, data.dream(), styles.text);
            }
            cell(row, offset, data.goal(), data.firstOfGoal() ? styles.boldText : styles.text);
            cell(row, offset + 1, data.step(), styles.text);
            cell(row, offset + 2, data.task(), styles.text);
            cell(row, issuesColumn, String.join("\n", data.issues()), styles.wrap);
            Cell progress = row.createCell(offset + 4);
            progress.setCellValue(data.progress().doubleValue());
            progress.setCellStyle(styles.percent);
            cell(row, priorityColumn, text.priority(data.priority()), styles.text);
            Cell due = row.createCell(dueColumn);
            if (data.due() != null) {
                due.setCellValue(data.due());
            }
            due.setCellStyle(styles.date);
            cell(row, nextColumn, data.nextAction(), styles.wrap);
            // Hidden helper columns for the conditional formatting below.
            cell(row, flagColumn, data.completed() ? COMPLETED_FLAG : data.blocked() ? BLOCKED_FLAG : "", styles.text);
            cell(row, overdueColumn, data.overdue() ? OVERDUE_FLAG : "", styles.text);
        }
        int last = Math.max(index - 1, HEADER_ROW + 1);
        sheet.createFreezePane(0, HEADER_ROW + 1);
        sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, last, 0, headers.size() - 1));
        for (int column = 0; column < headers.size(); column++) {
            sheet.autoSizeColumn(column);
            capWidth(sheet, column, column == issuesColumn ? WIDE_TEXT_WIDTH : TEXT_WIDTH);
        }
        sheet.setColumnWidth(issuesColumn, WIDE_TEXT_WIDTH * 256);
        sheet.setColumnWidth(nextColumn, Math.max(sheet.getColumnWidth(nextColumn), TEXT_WIDTH * 256));
        sheet.setColumnHidden(flagColumn, true);
        sheet.setColumnHidden(overdueColumn, true);
        rowColours(sheet, headers.size(), last, flagColumn, overdueColumn, dueColumn, priorityColumn);
        printSetup(sheet, HEADER_ROW);
    }

    /**
     * MR-FR-19 (the CLAUDE.md Excel rules): Completed rows green, rows with a
     * Blocked task or an open blocker orange, overdue due dates red, High and
     * Critical priorities highlighted.
     */
    private void rowColours(Sheet sheet, int width, int last, int flagColumn, int overdueColumn, int dueColumn, int priorityColumn) {
        SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        int first = HEADER_ROW + 2; // 1-based number of the first data row
        String flag = "$" + CellReference.convertNumToColString(flagColumn) + first;
        String overdue = "$" + CellReference.convertNumToColString(overdueColumn) + first;
        String priority = "$" + CellReference.convertNumToColString(priorityColumn) + first;
        CellRangeAddress[] wholeRows = {new CellRangeAddress(HEADER_ROW + 1, last, 0, width - 1)};
        fill(formatting, wholeRows, flag + "=\"" + COMPLETED_FLAG + "\"", IndexedColors.LIGHT_GREEN);
        fill(formatting, wholeRows, flag + "=\"" + BLOCKED_FLAG + "\"", IndexedColors.LIGHT_ORANGE);
        ConditionalFormattingRule late = formatting.createConditionalFormattingRule(overdue + "=\"" + OVERDUE_FLAG + "\"");
        late.createFontFormatting().setFontColorIndex(IndexedColors.DARK_RED.getIndex());
        late.createPatternFormatting().setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        formatting.addConditionalFormatting(new CellRangeAddress[] {new CellRangeAddress(HEADER_ROW + 1, last, dueColumn, dueColumn)}, late);
        ConditionalFormattingRule urgent = formatting.createConditionalFormattingRule("OR(" + priority + "=\""
                + text.priority(Priority.HIGH) + "\"," + priority + "=\"" + text.priority(Priority.CRITICAL) + "\")");
        urgent.createFontFormatting().setFontStyle(false, true);
        urgent.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        formatting.addConditionalFormatting(new CellRangeAddress[] {new CellRangeAddress(HEADER_ROW + 1, last, priorityColumn, priorityColumn)}, urgent);
    }

    private static void fill(SheetConditionalFormatting formatting, CellRangeAddress[] range, String formula, IndexedColors colour) {
        ConditionalFormattingRule rule = formatting.createConditionalFormattingRule(formula);
        PatternFormatting pattern = rule.createPatternFormatting();
        pattern.setFillBackgroundColor(colour.getIndex());
        pattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        formatting.addConditionalFormatting(range, rule);
    }

    // ---------- Blockers (MR-FR-21) ----------

    private record BlockerRow(int rank, long daysOpen, List<Object> values) {

        BlockerRow {
            values = com.visionmapping.util.Copies.list(values);
        }
    }

    private void blockersSheet() {
        Sheet sheet = workbook.createSheet(text.get(Key.BLOCKERS_SHEET));
        titleRows(sheet);
        LocalDate today = report.asOf();
        List<BlockerRow> placed = new ArrayList<>();
        List<BlockerRow> blockedTasks = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().title() + " › " + step.step().title();
                step.blockers().forEach(obstacle -> placed.add(obstacleRow(obstacle, where, today)));
                for (TaskNode task : step.tasks()) {
                    String taskWhere = where + " › " + task.task().title();
                    task.blockers().forEach(obstacle -> placed.add(obstacleRow(obstacle, taskWhere, today)));
                    if (task.task().status() == WorkStatus.BLOCKED) {
                        blockedTasks.add(blockedTaskRow(task.task(), taskWhere, today));
                    }
                }
            }
        }
        placed.sort(Comparator.comparingInt(BlockerRow::rank).thenComparing(Comparator.comparingLong(BlockerRow::daysOpen).reversed()));
        List<List<Object>> rows = new ArrayList<>();
        placed.forEach(row -> rows.add(row.values()));
        blockedTasks.forEach(row -> rows.add(row.values()));
        for (BlockerInfo obstacle : report.unplaced()) {
            String context = obstacle.context();
            String where = context.isEmpty() ? text.get(Key.NOT_ON_A_STEP) : context + " · " + text.get(Key.NOT_ON_A_STEP);
            rows.add(obstacleRow(obstacle, where, today).values());
        }
        List<String> headers = List.of(text.get(Key.WHERE), text.get(Key.BLOCKERS_ISSUES), text.get(Key.KIND), text.get(Key.SEVERITY),
                text.get(Key.STATUS), text.get(Key.DAYS_OPEN), text.get(Key.SUGGESTED_HELPER), text.get(Key.SOLUTION_SO_FAR));
        if (rows.isEmpty()) {
            Row header = sheet.createRow(HEADER_ROW);
            for (int column = 0; column < headers.size(); column++) {
                cell(header, column, headers.get(column), styles.header);
            }
            cell(sheet.createRow(HEADER_ROW + 1), 0, text.get(Key.NO_BLOCKERS), styles.text);
        } else {
            table(sheet, HEADER_ROW, headers, rows);
        }
        sheet.createFreezePane(0, HEADER_ROW + 1);
        for (int column = 0; column < headers.size(); column++) {
            sheet.autoSizeColumn(column);
            capWidth(sheet, column, column == 0 || column == 7 ? WIDE_TEXT_WIDTH : TEXT_WIDTH);
        }
        printSetup(sheet, HEADER_ROW);
    }

    /** Title, kind, severity, status, days open, helper and solution only (MR-NFR-02). */
    private BlockerRow obstacleRow(BlockerInfo obstacle, String where, LocalDate today) {
        long days = obstacle.createdOn() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(obstacle.createdOn(), today));
        Severity severity = obstacle.severity();
        int rank = severity == null ? Severity.values().length : Severity.values().length - 1 - severity.ordinal();
        return new BlockerRow(rank, days, List.of(where, obstacle.title(), text.kind(obstacle.type()), text.severity(severity),
                text.obstacleStatus(obstacle.status()), days, text.helper(obstacle.type()),
                obstacle.solution() == null ? "" : obstacle.solution()));
    }

    private BlockerRow blockedTaskRow(TaskInfo task, String where, LocalDate today) {
        long days = task.lastUpdatedOn() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(task.lastUpdatedOn(), today));
        return new BlockerRow(0, days, List.of(where, task.blockerReason() == null ? "" : task.blockerReason(),
                text.get(Key.BLOCKED_TASK), "", text.status(WorkStatus.BLOCKED), days, "", ""));
    }

    // ---------- shared ----------

    /** MR-FR-22: landscape, one page wide, header rows on every page, "Page N of M". */
    private void printSetup(Sheet sheet, int headerRow) {
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        if (headerRow >= 0) {
            sheet.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, -1, -1));
        }
        sheet.getFooter().setCenter(text.get(Key.PAGE_OF));
    }

    private static void capWidth(Sheet sheet, int column, int maxChars) {
        if (sheet.getColumnWidth(column) > maxChars * 256) {
            sheet.setColumnWidth(column, maxChars * 256);
        }
    }

    private void value(Row row, int column, @Nullable Object value) {
        Cell cell = row.createCell(column);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(value instanceof BigDecimal ? styles.decimal : styles.number);
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.date);
        } else {
            cell.setCellValue(value == null ? "" : value.toString());
            cell.setCellStyle(styles.wrap);
        }
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** The workbook's few styles, all in one font so Khmer text displays (Q-04). */
    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle header;
        final CellStyle text;
        final CellStyle boldText;
        final CellStyle wrap;
        final CellStyle percent;
        final CellStyle decimal;
        final CellStyle number;
        final CellStyle date;

        Styles(Workbook workbook, String fontName) {
            Font plain = font(workbook, fontName, false, (short) 11);
            Font bold = font(workbook, fontName, true, (short) 11);
            title = style(workbook, font(workbook, fontName, true, (short) 16));
            Font grey = font(workbook, fontName, false, (short) 11);
            grey.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitle = style(workbook, grey);
            header = style(workbook, bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);
            text = style(workbook, plain);
            boldText = style(workbook, bold);
            wrap = style(workbook, plain);
            wrap.setWrapText(true);
            percent = style(workbook, plain);
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.0\"%\""));
            decimal = style(workbook, plain);
            decimal.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
            number = style(workbook, plain);
            number.setDataFormat(workbook.createDataFormat().getFormat("0"));
            date = style(workbook, plain);
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        }

        private static Font font(Workbook workbook, String name, boolean bold, short points) {
            Font font = workbook.createFont();
            font.setFontName(name);
            font.setBold(bold);
            font.setFontHeightInPoints(points);
            return font;
        }

        private static CellStyle style(Workbook workbook, Font font) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            return style;
        }
    }
}
