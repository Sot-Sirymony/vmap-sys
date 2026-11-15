package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * V18 phase 2 (BRD 18.0.0, MR-FR-15 to MR-FR-22): the Management Report as an
 * Excel workbook, read back with Apache POI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementReportExcelFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mrc05To08_oneRowPerTaskWithTheRequestedColumnsBlockersAndNextActions() throws Exception {
        String token = register("excel-rows");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long empty = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/steps", Map.of("owner", "Me", "goalId", plan.goalId(),
                "title", "Write the rationale", "sequenceNumber", 2, "complex", false, "priority", "LOW",
                "targetDate", TODAY.plusMonths(3).toString(), "status", "NOT_STARTED"));
        task(token, plan.stepId(), Map.of("title", "Clean the data", "status", "BLOCKED", "blockerReason", "Waiting for data",
                "priority", "CRITICAL", "nextAction", "Email the data office"));
        task(token, plan.stepId(), Map.of("title", "Run the model", "dueDate", TODAY.minusDays(4).toString()));
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No budget", "obstacleType", "MONEY",
                "severity", "HIGH", "status", "OPEN", "relatedStepId", plan.stepId(), "conflictPrivateNote", "Private feelings"));
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/partners", Map.of("name", "Dr. Chan", "supportType", "MENTOR",
                "status", "TO_CONTACT", "email", "chan@example.com", "phone", "+855 12 345 678", "relatedStepId", plan.stepId()));

        MvcResult result = mockMvc.perform(get("/api/reports/management.xlsx?dreamId=" + plan.dreamId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Management_Report_Dream_" + plan.dreamId() + "_" + TODAY + ".xlsx\""))
                .andReturn();

        ManagementReportDeckFlowTests.saveSample(result, "sample-management-report.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(List.of(workbook.getSheetName(0), workbook.getSheetName(1), workbook.getSheetName(2)))
                    .containsExactly("Summary", "Report", "Blockers");
            XSSFSheet report = workbook.getSheet("Report");
            assertThat(text(report, 0, 0)).isEqualTo("Management report");
            assertThat(text(report, 1, 0)).isEqualTo("Fixture dream · as of " + TODAY.format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)));
            // MRC-05: one dream, so no Dream column; exactly the requested columns in order.
            assertThat(visibleHeaders(report)).containsExactly("Goals", "Steps", "Tasks", "Blockers / Management Issues",
                    "Progress (%)", "Priority", "Due Date", "Next Action");

            Row blocked = rowWithTask(report, "Clean the data");
            // MRC-06: the blocker reason and the open blocker on its step, one per line, and the row flagged orange.
            assertThat(text(blocked, 3)).isEqualTo("Blocked: Waiting for data\nNo budget (Money, High)");
            assertThat(text(blocked, 5)).isEqualTo("Critical");
            assertThat(text(blocked, 7)).isEqualTo("Email the data office");
            assertThat(text(blocked, 8)).isEqualTo("BLOCKED");
            assertThat(report.isColumnHidden(8)).isTrue();
            assertThat(report.isColumnHidden(9)).isTrue();

            Row late = rowWithTask(report, "Run the model");
            assertThat(text(late, 3)).contains("Overdue by 4 day(s)");
            assertThat(late.getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(late.getCell(6).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(TODAY.minusDays(4));
            assertThat(text(late, 9)).isEqualTo("Y");
            // MRC-08: no next action of its own, so the next open task in its step.
            assertThat(text(late, 7)).isEqualTo("Clean the data (next task)");

            // MRC-07: a step with no tasks still has its own row.
            Row stepOnly = rowWithStep(report, "Write the rationale");
            assertThat(text(stepOnly, 2)).isEmpty();
            assertThat(text(stepOnly, 5)).isEqualTo("Low");
            assertThat(stepOnly.getCell(6).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(TODAY.plusMonths(3));
            assertThat(empty).isPositive();

            // MR-FR-19, MR-FR-22: frozen header, filter, colours, print setup.
            assertThat(report.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 4);
            assertThat(report.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(report.getSheetConditionalFormatting().getNumConditionalFormattings()).isEqualTo(4);
            assertThat(report.getPrintSetup().getLandscape()).isTrue();
            assertThat(report.getRepeatingRows().formatAsString()).isEqualTo("4:4");

            XSSFSheet summary = workbook.getSheet("Summary");
            assertThat(text(summary, 3, 0)).isEqualTo("Headline");
            assertThat(cellsInColumn(summary, 0)).contains("Overdue tasks", "Goal health", "Tasks by status", "Open tasks by priority");

            XSSFSheet blockers = workbook.getSheet("Blockers");
            assertThat(cellsInColumn(blockers, 1)).contains("No budget", "Waiting for data");
            Row money = rowWhere(blockers, 1, "No budget");
            assertThat(text(money, 2)).isEqualTo("Money");
            assertThat(text(money, 6)).isEqualTo("Financial partner or sponsor");

            // MRC-12: no private conflict note, no partner contact details, anywhere.
            String everything = allText(workbook);
            assertThat(everything).doesNotContain("Private feelings", "chan@example.com", "+855 12 345 678");
        }
    }

    @Test
    void mrc05_aDreamColumnComesFirstWhenMoreThanOneDreamCanBeInScope() throws Exception {
        String token = register("excel-all");
        PlanFixture.branch(mockMvc, objectMapper, token);
        try (XSSFWorkbook workbook = download(token, "")) {
            XSSFSheet report = workbook.getSheet("Report");
            assertThat(visibleHeaders(report)).containsExactly("Dream", "Goals", "Steps", "Tasks", "Blockers / Management Issues",
                    "Progress (%)", "Priority", "Due Date", "Next Action");
            assertThat(text(workbook.getSheet("Blockers"), 4, 0)).isEqualTo("No blockers right now");
        }
    }

    @Test
    void mrc14_khmerHeadingsWithAKhmerFont() throws Exception {
        String token = register("excel-km");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        try (XSSFWorkbook workbook = download(token, "?language=km&dreamId=" + plan.dreamId())) {
            assertThat(workbook.getSheetName(1)).isEqualTo("របាយការណ៍");
            XSSFSheet report = workbook.getSheetAt(1);
            assertThat(text(report, 0, 0)).isEqualTo("របាយការណ៍គ្រប់គ្រង");
            assertThat(visibleHeaders(report).get(0)).isEqualTo("គោលដៅ");
            assertThat(workbook.getFontAt(report.getRow(3).getCell(0).getCellStyle().getFontIndex()).getFontName())
                    .isEqualTo("Noto Sans Khmer");
        }
        mockMvc.perform(get("/api/reports/management.xlsx?language=fr").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherUsersDreamAnswers404() throws Exception {
        String owner = register("excel-owner");
        String other = register("excel-other");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, owner);
        mockMvc.perform(get("/api/reports/management.xlsx?dreamId=" + plan.dreamId()).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/management.xlsx?areaId=" + plan.areaId()).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    private XSSFWorkbook download(String token, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reports/management.xlsx" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    private static List<String> visibleHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        for (Cell cell : sheet.getRow(3)) {
            headers.add(FORMAT.formatCellValue(cell));
        }
        return headers;
    }

    private static Row rowWithTask(Sheet sheet, String task) {
        return rowWhere(sheet, 2, task);
    }

    private static Row rowWithStep(Sheet sheet, String step) {
        return rowWhere(sheet, 1, step);
    }

    private static Row rowWhere(Sheet sheet, int column, String value) {
        for (Row row : sheet) {
            if (row.getRowNum() > 3 && value.equals(text(row, column))) {
                return row;
            }
        }
        throw new AssertionError("no row with " + value);
    }

    private static List<String> cellsInColumn(Sheet sheet, int column) {
        List<String> values = new ArrayList<>();
        sheet.forEach(row -> values.add(text(row, column)));
        return values;
    }

    private static String allText(XSSFWorkbook workbook) {
        StringBuilder out = new StringBuilder();
        workbook.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> out.append(FORMAT.formatCellValue(cell)).append('|'))));
        return out.toString();
    }

    private static String text(Sheet sheet, int row, int column) {
        return text(sheet.getRow(row), column);
    }

    private static String text(Row row, int column) {
        Cell cell = row == null ? null : row.getCell(column);
        return cell == null ? "" : FORMAT.formatCellValue(cell);
    }

    private void task(String token, long step, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task", "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/tasks", body);
    }


    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V18 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
