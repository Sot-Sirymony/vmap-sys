package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.ChangeItem;
import com.visionmapping.dto.response.ManagementReportResponse.Comparison;
import com.visionmapping.dto.response.ManagementReportResponse.HeadlineChange;
import com.visionmapping.dto.response.ManagementReportResponse.RemovedGoal;
import com.visionmapping.dto.response.SavedReportResponse.Commentary;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport;
import com.visionmapping.service.report.ManagementReport.DreamInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.PeriodFigures;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReportDeckWriter;
import com.visionmapping.service.report.ManagementReportExcelWriter;
import com.visionmapping.service.report.ManagementReportPdfWriter;
import com.visionmapping.service.support.PdfFonts;
import com.lowagie.text.pdf.LayoutProcessor;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.visionmapping.service.report.ReportExtras;
import com.visionmapping.service.report.ReportFilters;
import com.visionmapping.service.report.ReportPeriod;
import com.visionmapping.service.report.ReportSection;
import com.visionmapping.service.report.ReportText;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-39 to RM-FR-43: the files' phase 3 additions, at their edges. */
class ReportExtrasWritersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);
    private static final DataFormatter FORMAT = new DataFormatter();
    private final ManagementReportService service = new ManagementReportService(null, null, null, null, null, null, null, null, null, null);

    @Test
    void anEmptyComparisonSaysNothingChangedAndListsGoalsThatLeft() throws IOException {
        ManagementReport report = report();
        ManagementReportResponse summary = service.toResponse(report).withComparison(comparison(List.of(),
                List.of(new RemovedGoal(9L, "Old goal", "COMPLETED_ARCHIVED"), new RemovedGoal(8L, "Gone", null))));
        ReportExtras nothingNeeded = new ReportExtras(new Commentary(null, null, "ignored", true, null, Map.of()), null, false);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                ManagementReportExcelWriter.write(report, summary, ReportText.of("en"), nothingNeeded)))) {
            assertThat(column(workbook.getSheet("Commentary"))).contains("Decisions and support needed", "Nothing needed this period.")
                    .doesNotContain("ignored", "Highlights");
            assertThat(column(workbook.getSheet("Changes"))).contains("Nothing changed since the compared report.", "Goal changes",
                    "Old goal", "Gone");
            Sheet changes = workbook.getSheet("Changes");
            assertThat(cellsOf(changes)).contains("No longer in this report · Completed and archived", "No longer in this report");
            // The goal table gains a Change column: "New" for a goal not in the compared report.
            assertThat(cellsOf(workbook.getSheet("Summary"))).contains("Change", "New");
        }
        try (XMLSlideShow deck = new XMLSlideShow(new ByteArrayInputStream(
                ManagementReportDeckWriter.write(report, summary, ReportText.of("en"), null, null, nothingNeeded)))) {
            List<String> titles = deck.getSlides().stream().map(slide -> slide.getPlaceholder(0).getText()).toList();
            // No Highlights, Risks or Focus text: no Highlights slide.
            assertThat(titles).doesNotContain("Highlights").contains("What changed", "Decisions and support needed");
            assertThat(text(deck.getSlides().get(titles.indexOf("What changed")))).contains("Nothing changed since the compared report.");
            assertThat(text(deck.getSlides().get(titles.indexOf("Decisions and support needed")))).contains("Nothing needed this period.");
        }
    }

    @Test
    void oneCommentarySectionFillsTheSlideAndManyChangesOverflow() throws IOException {
        List<ChangeItem> changes = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            changes.add(new ChangeItem("TASK_COMPLETED", "TASK", id, "Task " + id, "", null, null, id == 1));
        }
        ManagementReport report = report();
        ManagementReportResponse summary = service.toResponse(report).withComparison(comparison(changes, List.of()));
        String many = String.join("\n", List.of("- one", "- two", "three", "- four", "- five", "- six", ""));
        ReportExtras extras = new ReportExtras(new Commentary(many, " ", null, false, null, Map.of(1L, " ")),
                Set.of(ReportSection.COMMENTARY, ReportSection.CHANGES, ReportSection.SUPPORT), true);

        try (XMLSlideShow deck = new XMLSlideShow(new ByteArrayInputStream(
                ManagementReportDeckWriter.write(report, summary, ReportText.of("en"), "Board", "Dara", extras)))) {
            List<String> titles = deck.getSlides().stream().map(slide -> slide.getPlaceholder(0).getText()).toList();
            assertThat(titles).containsExactly("Board", "Highlights", "Executive summary", "What changed");
            assertThat(text(deck.getSlides().get(1))).contains("one", "three", "five", "and 1 more").doesNotContain("- one", "six");
            assertThat(text(deck.getSlides().get(3))).contains("Task 1 (renamed)", "and 5 more");
            assertThat(text(deck.getSlides().get(0))).contains("DRAFT", "Dara");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                ManagementReportExcelWriter.write(report, summary, ReportText.of("en"), extras)))) {
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Commentary");
            assertThat(column(workbook.getSheet("Commentary"))).doesNotContain("Goal notes", "Risks and issues");
            assertThat(column(workbook.getSheet("Changes"))).contains("Task completed");
        }
        assertThat(ReportExtras.NONE.hasCommentary()).isFalse();
        assertThat(new ReportExtras(new Commentary(null, null, null, false, null, null), Set.of(), false).goalNotes()).isEmpty();
    }

    @Test
    void thePdfCoversEverySectionAtItsEdges() throws IOException {
        List<ChangeItem> changes = new ArrayList<>();
        for (long id = 1; id <= 30; id++) {
            changes.add(new ChangeItem("TASK_COMPLETED", "TASK", id, "Task " + id, "Goal › Step", null, null, false));
        }
        ManagementReport report = report();
        ManagementReportResponse summary = service.toResponse(report).withComparison(comparison(changes,
                List.of(new RemovedGoal(9L, "Old goal", "MOVED"))));
        ReportExtras extras = new ReportExtras(new Commentary("- Booked the hall", "Venue may cancel", "ignored", true, "- Print",
                Map.of(1L, "Going well")), null, true);
        PdfFonts fonts = new PdfFonts();

        String text = pdfText(ManagementReportPdfWriter.write(report, summary, ReportText.of("en"), extras, fonts,
                new ManagementReportPdfWriter.Cover("Board pack", "DRAFT · 31 Dec 2026")));
        assertThat(text).contains("Board pack", "Management report", "Compared with November (R-001", "DRAFT · 31 Dec 2026",
                "Booked the hall", "Venue may cancel", "Nothing needed this period.", "Going well", "Change since R-", "−3",
                "Goal health", "Progress by goal", "New", "What changed", "Task 1", "and 5 more", "Old goal · Moved to another dream",
                "Overdue items", "Next actions", "Decisions and support needed", "In the participant's words", "Page 1")
                .doesNotContain("ignored");

        // No goals, no comparison, no commentary, few sections: the V18 figures and the chosen lists only.
        ManagementReport empty = new ManagementReport("ALL", null, null, null, TODAY, report.period(), List.of(), List.of(),
                PeriodFigures.NONE, ReportFilters.NONE, List.of());
        String plain = pdfText(ManagementReportPdfWriter.write(empty, service.toResponse(empty), ReportText.of("en"),
                new ReportExtras(null, Set.of(ReportSection.GOAL_HEALTH, ReportSection.SUPPORT, ReportSection.CHANGES), false), fonts,
                new ManagementReportPdfWriter.Cover("Management report", "Live report · 31 Dec 2026")));
        assertThat(plain).contains("No goals in this report yet", "Live report").doesNotContain("What changed", "Decisions and support needed",
                "Compared with");

        // Progress at both ends draws a one-part bar; Khmer prints.
        GoalNode done = GoalNode.assessed(new GoalInfo(2L, "G-2", "Pick a topic", WorkStatus.COMPLETED, Priority.LOW, new BigDecimal("100"),
                TODAY, null), new DreamInfo(1L, "Dream"), List.of(), TODAY);
        GoalNode none = GoalNode.assessed(new GoalInfo(3L, "G-3", "Write", WorkStatus.NOT_STARTED, Priority.LOW, BigDecimal.ZERO,
                null, null), new DreamInfo(1L, "Dream"), List.of(), TODAY);
        ManagementReport ends = new ManagementReport("DREAM", null, 1L, "Dream", TODAY, report.period(), List.of(done, none), List.of(),
                PeriodFigures.NONE, ReportFilters.NONE, List.of());
        byte[] khmer = ManagementReportPdfWriter.write(ends, service.toResponse(ends), ReportText.of("km"),
                new ReportExtras(null, Set.of(ReportSection.GOAL_HEALTH), false), fonts,
                new ManagementReportPdfWriter.Cover("Dream", "ចុងក្រោយ · បង្កកថ្ងៃ 2026-12-31"));
        assertThat(new String(khmer, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("NotoSansKhmer");
        assertThat(pdfText(khmer)).contains("100%", "0%");
    }

    private static String pdfText(byte[] pdf) throws IOException {
        LayoutProcessor.disable();
        try {
            PdfReader reader = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            LayoutProcessor.enable();
        }
    }

    // ---------- builders ----------

    private static Comparison comparison(List<ChangeItem> changes, List<RemovedGoal> removed) {
        return new Comparison(5L, "R-001", "November", TODAY.minusMonths(1),
                new HeadlineChange(new BigDecimal("-3.0"), 0, 0, 0, 0, 0, 0), List.of(new ManagementReportResponse.GoalChange(1L, true,
                null, null, null, null)), removed, changes, changes.size());
    }

    private static ManagementReport report() {
        GoalNode goal = GoalNode.assessed(new GoalInfo(1L, "G-1", "Write the paper", WorkStatus.IN_PROGRESS, Priority.HIGH,
                BigDecimal.TEN, TODAY.plusMonths(2), null), new DreamInfo(1L, "Dream"), List.of(new StepNode(new StepInfo(10L, "Draft",
                WorkStatus.IN_PROGRESS, Priority.HIGH, BigDecimal.TEN, TODAY.plusMonths(1), null), List.of(), List.of(), false)), TODAY);
        return new ManagementReport("DREAM", null, 1L, "Dream", TODAY, new ReportPeriod(ReportPeriod.Kind.THIS_MONTH,
                TODAY.withDayOfMonth(1), TODAY), List.of(goal), List.of(), PeriodFigures.NONE, ReportFilters.NONE, List.of());
    }

    private static List<String> column(Sheet sheet) {
        List<String> values = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getCell(0) != null) {
                values.add(FORMAT.formatCellValue(row.getCell(0)));
            }
        }
        return values;
    }

    private static List<String> cellsOf(Sheet sheet) {
        List<String> values = new ArrayList<>();
        sheet.forEach(row -> row.forEach(cell -> values.add(FORMAT.formatCellValue(cell))));
        return values;
    }

    private static String text(XSLFSlide slide) {
        StringBuilder text = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape) {
                text.append(textShape.getText()).append('\n');
            }
        }
        return text.toString();
    }
}
