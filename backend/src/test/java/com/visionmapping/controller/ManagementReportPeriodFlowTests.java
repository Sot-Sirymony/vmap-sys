package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V19 phase 1 (BRD 19.0.0): the report's period and its figures (RM-FR-06 to
 * RM-FR-09), health reasons (RM-FR-01) and "Done – open work" (RM-FR-02), in
 * the Summary, the Excel report and the deck.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementReportPeriodFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rmFr08_thePeriodCountsCompletedTasksProgressBlockersAndNewlyOverdueWork() throws Exception {
        String token = register("period-figures");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long moving = task(token, plan.stepId(), Map.of("title", "Draft the survey"));
        task(token, plan.stepId(), Map.of("title", "Late task", "dueDate", TODAY.minusDays(3).toString()));
        // Created last: a first task created Completed would complete the step and goal (rule 11).
        task(token, plan.stepId(), Map.of("title", "Done task", "status", "COMPLETED", "progressPercent", 100));
        send(put("/api/tasks/" + moving), token, Map.of("stepId", plan.stepId(), "title", "Draft the survey", "owner", "Me",
                "priority", "MEDIUM", "dueDate", TODAY.plusMonths(2).toString(), "status", "IN_PROGRESS", "progressPercent", 60))
                .andExpect(status().isOk());
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No budget",
                "obstacleType", "MONEY", "severity", "HIGH", "status", "OPEN", "relatedStepId", plan.stepId()));
        // Resolving needs a root cause (BR-25), written while it was still open.
        long closed = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No room",
                "obstacleType", "TIME", "severity", "LOW", "status", "OPEN", "relatedStepId", plan.stepId(),
                "rootCause", "Booked the hall"));
        send(patch("/api/obstacles/" + closed + "/status"), token, Map.of("status", "RESOLVED")).andExpect(status().isOk());

        String window = "?period=CUSTOM&from=" + TODAY.minusDays(10) + "&to=" + TODAY;
        JsonNode report = summary(token, window);

        JsonNode period = report.get("period");
        assertThat(period.get("kind").asText()).isEqualTo("CUSTOM");
        assertThat(period.get("from").asText()).isEqualTo(TODAY.minusDays(10).toString());
        assertThat(period.get("statusAsOfToday").asBoolean()).isFalse();
        JsonNode figures = report.get("periodFigures");
        assertThat(figures.get("tasksCompleted").asLong()).isEqualTo(1);
        // +60 on one task (creating a task writes no log), averaged over the three tasks.
        assertThat(figures.get("progressGained").decimalValue()).isEqualByComparingTo("20.0");
        assertThat(figures.get("blockersOpened").asLong()).isEqualTo(2);
        assertThat(figures.get("blockersClosed").asLong()).isEqualTo(1);
        assertThat(figures.get("tasksBecameOverdue").asLong()).isEqualTo(1);

        // Last month ended before today: status figures are today's, and nothing above happened in it.
        JsonNode lastMonth = summary(token, "?period=LAST_MONTH");
        assertThat(lastMonth.get("period").get("statusAsOfToday").asBoolean()).isTrue();
        assertThat(lastMonth.get("periodFigures").get("tasksCompleted").asLong()).isZero();
        assertThat(lastMonth.get("periodFigures").get("blockersClosed").asLong()).isZero();
        // The default is this month.
        assertThat(summary(token, "").get("period").get("kind").asText()).isEqualTo("THIS_MONTH");

        // The Excel summary sheet carries the same figures under the period's dates.
        try (XSSFWorkbook workbook = workbook(token, window)) {
            Sheet sheet = workbook.getSheet("Summary");
            assertThat(text(sheet, 1, 0)).contains("· " + format(TODAY.minusDays(10)) + " to " + format(TODAY) + " · as of");
            Row completed = rowStartingWith(sheet, "Tasks completed");
            assertThat(FORMAT.formatCellValue(completed.getCell(1))).isEqualTo("1");
            assertThat(FORMAT.formatCellValue(rowStartingWith(sheet, "Blockers closed").getCell(1))).isEqualTo("1");
        }
    }

    @Test
    void rmFr06_aBadPeriodIsRefusedWithItsReason() throws Exception {
        String token = register("period-bad");
        expectRefused(token, "?period=CUSTOM&from=" + TODAY + "&to=" + TODAY.minusDays(1), "The period must start on or before its end.");
        expectRefused(token, "?from=" + TODAY.minusDays(1) + "&to=" + TODAY.plusDays(1), "The period cannot end after today.");
        expectRefused(token, "?from=" + TODAY.minusDays(400) + "&to=" + TODAY, "A custom period can cover at most 366 days.");
        expectRefused(token, "?period=CUSTOM", "A custom period needs a from date and a to date.");
        expectRefused(token, "?period=FOREVER", "Unknown report period.");
    }

    @Test
    void rmFr01_rmFr02_healthComesWithItsReasonsEverywhereAndDoneGoalsWithOpenWorkNeedAttention() throws Exception {
        String token = register("period-health");
        Branch late = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch reopened = PlanFixture.branch(mockMvc, objectMapper, token);
        task(token, late.stepId(), Map.of("title", "Book the room"));
        task(token, reopened.stepId(), Map.of("title", "Added after completion"));
        jdbcTemplate.update("update goals set target_date = ? where id = ?", java.sql.Date.valueOf(TODAY.minusDays(3)), late.goalId());
        jdbcTemplate.update("update goals set status = 'COMPLETED' where id = ?", reopened.goalId());

        JsonNode report = summary(token, "");

        JsonNode red = goal(report, late.goalId());
        assertThat(red.get("health").asText()).isEqualTo("RED");
        assertThat(red.get("reasons").get(0).get("code").asText()).isEqualTo("GOAL_OVERDUE");
        assertThat(red.get("reasons").get(0).get("value").asLong()).isEqualTo(3);
        JsonNode open = goal(report, reopened.goalId());
        assertThat(open.get("health").asText()).isEqualTo("DONE_OPEN_WORK");
        assertThat(open.get("reasons").get(0).get("code").asText()).isEqualTo("COMPLETED_OPEN_WORK");
        assertThat(report.get("headline").get("goalsByHealth").get("DONE_OPEN_WORK").asLong()).isEqualTo(1);
        JsonNode attention = report.get("attention");
        JsonNode reopen = null;
        for (JsonNode item : attention) {
            if ("REOPEN_OR_FINISH".equals(item.get("kind").asText())) {
                reopen = item;
            }
        }
        assertThat(reopen).isNotNull();
        assertThat(reopen.get("itemType").asText()).isEqualTo("GOAL");
        assertThat(reopen.get("itemId").asLong()).isEqualTo(reopened.goalId());
        assertThat(reopen.get("stepId").isNull()).isTrue();

        try (XSSFWorkbook workbook = workbook(token, "")) {
            Sheet sheet = workbook.getSheet("Summary");
            Row header = rowStartingWith(sheet, "Dream");
            int why = -1;
            for (Cell cell : header) {
                if ("Why".equals(FORMAT.formatCellValue(cell))) {
                    why = cell.getColumnIndex();
                }
            }
            assertThat(why).isPositive();
            StringBuilder reasons = new StringBuilder();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                reasons.append(FORMAT.formatCellValue(sheet.getRow(index).getCell(why))).append('\n');
            }
            assertThat(reasons).contains("Target date passed 3 day(s) ago", "Completed, but 1 task(s) are still open");
        }

        MvcResult deck = mockMvc.perform(get("/api/reports/management.pptx").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        try (XMLSlideShow slides = new XMLSlideShow(new ByteArrayInputStream(deck.getResponse().getContentAsByteArray()))) {
            StringBuilder progress = new StringBuilder();
            for (XSLFShape shape : slides.getSlides().get(2).getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    progress.append(textShape.getText()).append('\n');
                }
            }
            assertThat(progress).contains("Target date passed 3 day(s) ago", "Done – open work");
        }
    }

    private void expectRefused(String token, String query, String message) throws Exception {
        mockMvc.perform(get("/api/reports/management" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
    }

    private static JsonNode goal(JsonNode report, long goalId) {
        for (JsonNode row : report.get("goals")) {
            if (row.get("goalId").asLong() == goalId) {
                return row;
            }
        }
        throw new AssertionError("no goal " + goalId);
    }

    private static Row rowStartingWith(Sheet sheet, String value) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && FORMAT.formatCellValue(row.getCell(0)).startsWith(value)) {
                return row;
            }
        }
        throw new AssertionError("no row starting with " + value);
    }

    private static String text(Sheet sheet, int row, int column) {
        return FORMAT.formatCellValue(sheet.getRow(row).getCell(column));
    }

    private static String format(LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH));
    }

    private XSSFWorkbook workbook(String token, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reports/management.xlsx" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    private JsonNode summary(String token, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reports/management" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long task(String token, long step, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task", "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        return PlanFixture.createdId(mockMvc, objectMapper, token, "/api/tasks", body);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V19 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
