package com.visionmapping.excel;

import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.util.Copies;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.springframework.lang.Nullable;

/**
 * V16 (BRD 16.0.0, TL-FR-49): the plan as a simple Gantt in the export. One
 * row per dream, goal, step and task, then one column per week; conditional
 * formatting fills the weeks each row covers and marks the current week, so
 * the bars move by themselves when someone edits a date in Excel.
 *
 * <p>Start dates follow the timeline's rules in short form: a task starts on
 * its start date (or its due date); a step, goal or dream on the earliest start
 * below it, and a dream on its commitment start when it has one.
 */
final class TimelineSheet {

    static final String NAME = "Timeline";
    static final List<String> HEADERS = List.of("Level", "Code", "Title", "Start", "End");
    /** Three years of weeks is enough for any plan this app holds, and keeps the sheet light. */
    static final int MAX_WEEKS = 156;
    private static final int FIRST_WEEK_COLUMN = HEADERS.size();

    private TimelineSheet() {
    }

    record Line(String level, String code, String title, @Nullable LocalDate start, @Nullable LocalDate end) {
    }

    /** The plan grouped by parent, each group in its own order. */
    private record Plan(Map<Long, List<GoalResponse>> goalsByDream, Map<Long, List<VisionStepResponse>> stepsByGoal,
                        Map<Long, List<TaskItemResponse>> tasksByStep) {
        private Plan {
            goalsByDream = Copies.map(goalsByDream);
            stepsByGoal = Copies.map(stepsByGoal);
            tasksByStep = Copies.map(tasksByStep);
        }
    }

    /** The rows in plan order: each dream, then its goals, steps and tasks. */
    static List<Line> lines(List<DreamResponse> dreams, List<GoalResponse> goals, List<VisionStepResponse> steps,
                            List<TaskItemResponse> tasks) {
        Plan plan = new Plan(
                goals.stream().sorted(Comparator.comparing((GoalResponse goal) -> order(goal.sortOrder())).thenComparing(GoalResponse::id))
                        .collect(Collectors.groupingBy(GoalResponse::dreamId)),
                steps.stream().sorted(Comparator.comparing((VisionStepResponse step) -> order(step.sequenceNumber())).thenComparing(VisionStepResponse::id))
                        .collect(Collectors.groupingBy(VisionStepResponse::goalId)),
                tasks.stream().sorted(Comparator.comparing((TaskItemResponse task) -> order(task.sortOrder())).thenComparing(TaskItemResponse::id))
                        .collect(Collectors.groupingBy(TaskItemResponse::stepId)));
        List<Line> lines = new ArrayList<>();
        for (DreamResponse dream : dreams) {
            lines.addAll(dreamLines(dream, plan));
        }
        return lines;
    }

    private static List<Line> dreamLines(DreamResponse dream, Plan plan) {
        List<Line> below = new ArrayList<>();
        for (GoalResponse goal : plan.goalsByDream().getOrDefault(dream.id(), List.of())) {
            below.addAll(goalLines(goal, plan));
        }
        List<Line> goals = below.stream().filter(line -> line.level().equals("Goal")).toList();
        LocalDate start = dream.commitmentStartDate() != null ? dream.commitmentStartDate() : earliest(goals, Line::start);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Dream", dream.code(), dream.title(), start, dream.targetDate() != null ? dream.targetDate() : latest(goals)));
        lines.addAll(below);
        return lines;
    }

    private static List<Line> goalLines(GoalResponse goal, Plan plan) {
        List<Line> below = new ArrayList<>();
        for (VisionStepResponse step : plan.stepsByGoal().getOrDefault(goal.id(), List.of())) {
            below.addAll(stepLines(step, plan));
        }
        List<Line> steps = below.stream().filter(line -> line.level().equals("Step")).toList();
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Goal", goal.code(), goal.title(), earliest(steps, Line::start),
                goal.targetDate() != null ? goal.targetDate() : latest(steps)));
        lines.addAll(below);
        return lines;
    }

    private static List<Line> stepLines(VisionStepResponse step, Plan plan) {
        List<Line> tasks = plan.tasksByStep().getOrDefault(step.id(), List.of()).stream()
                .map(task -> new Line("Task", task.code(), task.title(), task.startDate() != null ? task.startDate() : task.dueDate(), task.dueDate()))
                .toList();
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Step", step.code(), step.title(), earliest(tasks, Line::start),
                step.targetDate() != null ? step.targetDate() : latest(tasks)));
        lines.addAll(tasks);
        return lines;
    }

    private static int order(@Nullable Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    static void write(Workbook workbook, CellStyle headerStyle, List<Line> lines) {
        Sheet sheet = workbook.createSheet(NAME);
        CreationHelper helper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(helper.createDataFormat().getFormat("d mmm yyyy"));
        CellStyle weekStyle = workbook.createCellStyle();
        weekStyle.cloneStyleFrom(headerStyle);
        weekStyle.setDataFormat(helper.createDataFormat().getFormat("d mmm"));
        weekStyle.setRotation((short) 90);
        CellStyle textOnly = workbook.createCellStyle();
        textOnly.setQuotePrefixed(true);

        LocalDate first = lines.stream().map(TimelineSheet::startOrEnd).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate last = lines.stream().map(Line::end).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        LocalDate firstWeek = first == null ? null : first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weeks = firstWeek == null || last == null ? 0
                : (int) Math.min(MAX_WEEKS, ChronoUnit.WEEKS.between(firstWeek, last) + 1);

        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(HEADERS.get(index));
            cell.setCellStyle(headerStyle);
        }
        for (int week = 0; week < weeks; week++) {
            Cell cell = header.createCell(FIRST_WEEK_COLUMN + week);
            cell.setCellValue(firstWeek.plusWeeks(week));
            cell.setCellStyle(weekStyle);
            sheet.setColumnWidth(FIRST_WEEK_COLUMN + week, 4 * 256);
        }

        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            Row row = sheet.createRow(index + 1);
            row.createCell(0).setCellValue(line.level());
            row.createCell(1).setCellValue(line.code());
            Cell title = row.createCell(2);
            title.setCellValue(indent(line.level()) + line.title());
            // SEC-05-04: a title that looks like a formula stays text.
            if (ExcelService.looksLikeFormula(line.title())) {
                title.setCellStyle(textOnly);
            }
            setDate(row.createCell(3), line.start(), dateStyle);
            setDate(row.createCell(4), line.end(), dateStyle);
        }

        sheet.createFreezePane(FIRST_WEEK_COLUMN, 1);
        for (int index = 0; index < HEADERS.size(); index++) {
            sheet.autoSizeColumn(index);
        }
        if (weeks > 0 && !lines.isEmpty()) {
            addBars(sheet, lines.size(), weeks);
        }
    }

    /**
     * A week cell is filled when the row's start (or its end, with no start)
     * is before the week ends and its end is on or after the week starts. The
     * current week's header is marked with TODAY(), so it stays right as days pass.
     */
    private static void addBars(Sheet sheet, int rowCount, int weeks) {
        SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        String week = CellReference.convertNumToColString(FIRST_WEEK_COLUMN);
        String lastWeek = CellReference.convertNumToColString(FIRST_WEEK_COLUMN + weeks - 1);

        ConditionalFormattingRule bar = formatting.createConditionalFormattingRule(
                "AND($E2<>\"\",IF($D2=\"\",$E2,$D2)<=" + week + "$1+6,$E2>=" + week + "$1)");
        fill(bar, IndexedColors.LIGHT_CORNFLOWER_BLUE);
        formatting.addConditionalFormatting(
                new CellRangeAddress[] {CellRangeAddress.valueOf(week + "2:" + lastWeek + (rowCount + 1))}, bar);

        ConditionalFormattingRule thisWeek = formatting.createConditionalFormattingRule(
                "AND(TODAY()>=" + week + "$1,TODAY()<" + week + "$1+7)");
        fill(thisWeek, IndexedColors.CORAL);
        formatting.addConditionalFormatting(
                new CellRangeAddress[] {CellRangeAddress.valueOf(week + "1:" + lastWeek + "1")}, thisWeek);
    }

    private static void fill(ConditionalFormattingRule rule, IndexedColors colour) {
        PatternFormatting pattern = rule.createPatternFormatting();
        pattern.setFillBackgroundColor(colour.getIndex());
        pattern.setFillPattern(FillPatternType.SOLID_FOREGROUND.getCode());
    }

    private static void setDate(Cell cell, @Nullable LocalDate date, CellStyle style) {
        if (date != null) {
            cell.setCellValue(date);
            cell.setCellStyle(style);
        }
    }

    private static String indent(String level) {
        return switch (level) {
            case "Goal" -> "  ";
            case "Step" -> "    ";
            case "Task" -> "      ";
            default -> "";
        };
    }

    @Nullable
    private static LocalDate startOrEnd(Line line) {
        return line.start() != null ? line.start() : line.end();
    }

    @Nullable
    private static LocalDate earliest(List<Line> lines, Function<Line, LocalDate> date) {
        return lines.stream().map(date).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    @Nullable
    private static LocalDate latest(List<Line> lines) {
        return lines.stream().map(Line::end).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }
}
