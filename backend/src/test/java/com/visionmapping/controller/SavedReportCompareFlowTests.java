package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V19 phase 3 (BRD 19.0.0): comparison and What changed (RM-FR-32 to
 * RM-FR-37), the Excel and deck additions (RM-FR-40, RM-FR-41), section
 * choice (RM-FR-39) and the draft mark (RM-FR-43), through the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SavedReportCompareFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rmc10_rmc12_theNextReportIsComparedWithTheLastFinalOneAndFreezesTheComparison() throws Exception {
        String token = register("compare-flow");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long first = task(token, plan.stepId(), Map.of("title", "First"));
        long second = task(token, plan.stepId(), Map.of("title", "Second"));
        long extraGoal = goal(token, plan.dreamId(), "Side goal");

        long november = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        // RM-FR-36: nothing to compare with yet.
        assertThat(saved(token, november).get("report").get("comparison").isNull()).isTrue();
        send(post("/api/reports/saved/" + november + "/finalize"), token, Map.of()).andExpect(status().isOk());

        complete(token, plan.stepId(), first, "First", TODAY.plusMonths(2));
        complete(token, plan.stepId(), second, "Second, renamed", TODAY.plusMonths(3), "NOT_STARTED");
        mockMvc.perform(delete("/api/goals/" + extraGoal).header("Authorization", "Bearer " + token)).andExpect(status().is2xxSuccessful());

        JsonNode december = created(send(post("/api/reports/saved/" + november + "/copy"), token, Map.of()));
        long decemberId = december.get("id").asLong();
        assertThat(december.get("compareToReportId").asLong()).isEqualTo(november);
        JsonNode comparison = saved(token, decemberId).get("report").get("comparison");
        assertThat(comparison.get("reportId").asLong()).isEqualTo(november);
        assertThat(comparison.get("code").asText()).isEqualTo("R-001");
        assertThat(comparison.get("headline").get("completedTasks").asLong()).isEqualTo(1);
        List<String> kinds = new ArrayList<>();
        comparison.get("changes").forEach(change -> kinds.add(change.get("kind").asText()));
        assertThat(kinds).containsExactly("DUE_LATER", "TASK_COMPLETED");
        JsonNode moved = comparison.get("changes").get(0);
        assertThat(moved.get("itemId").asLong()).isEqualTo(second);
        assertThat(moved.get("before").asText()).isEqualTo(TODAY.plusMonths(2).toString());
        assertThat(moved.get("after").asText()).isEqualTo(TODAY.plusMonths(3).toString());
        assertThat(moved.get("renamed").asBoolean()).isTrue();
        assertThat(comparison.get("removed").get(0).get("goalId").asLong()).isEqualTo(extraGoal);
        assertThat(comparison.get("removed").get(0).get("reason").asText()).isEqualTo("ARCHIVED");

        // RM-FR-19: the comparison is frozen with the figures.
        send(post("/api/reports/saved/" + decemberId + "/finalize"), token, Map.of()).andExpect(status().isOk());
        complete(token, plan.stepId(), second, "Second, renamed", TODAY.plusMonths(4));
        JsonNode frozen = saved(token, decemberId).get("report").get("comparison");
        assertThat(frozen.get("totalChanges").asInt()).isEqualTo(2);
        // A final report's comparison cannot be changed; its name can.
        send(put("/api/reports/saved/" + decemberId), token, Map.of("name", "December", "compareToReportId", decemberId))
                .andExpect(status().isConflict());
        send(put("/api/reports/saved/" + decemberId), token, Map.of("name", "December")).andExpect(status().isOk());
        // The automatic choice is the latest final report on the scope.
        long january = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        assertThat(saved(token, january).get("report").get("comparison").get("reportId").asLong()).isEqualTo(decemberId);
        JsonNode chosen = read(send(put("/api/reports/saved/" + january), token,
                Map.of("dreamId", plan.dreamId(), "compareToReportId", november)).andExpect(status().isOk()));
        assertThat(chosen.get("report").get("comparison").get("reportId").asLong()).isEqualTo(november);
    }

    @Test
    void rmc14_rmc15_rmc16_filesGainCommentaryAndChangesAndADraftIsMarked() throws Exception {
        String token = register("compare-files");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long first = task(token, plan.stepId(), Map.of("title", "First"));
        long november = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        send(post("/api/reports/saved/" + november + "/finalize"), token, Map.of()).andExpect(status().isOk());
        complete(token, plan.stepId(), first, "First", TODAY.plusMonths(2));
        long december = created(send(post("/api/reports/saved/" + november + "/copy"), token, Map.of())).get("id").asLong();
        send(put("/api/reports/saved/" + december + "/commentary"), token, Map.of("highlights", "- Finished the first task",
                "risks", "", "decisionsNeeded", "Need one hour with my mentor", "nothingNeeded", false, "nextFocus", "- Start the second",
                "goalNotes", Map.of(String.valueOf(plan.goalId()), "Going well"))).andExpect(status().isOk());

        try (XSSFWorkbook workbook = workbook(token, "/api/reports/saved/" + december + ".xlsx")) {
            assertThat(sheetNames(workbook)).containsExactly("Commentary", "Summary", "Report", "Blockers", "Changes");
            Sheet commentary = workbook.getSheet("Commentary");
            assertThat(text(commentary, 0, 0)).isEqualTo("Management report – DRAFT");
            assertThat(text(commentary, 2, 0)).startsWith("Compared with Fixture dream – This month (R-001");
            assertThat(column(commentary, 0)).contains("Highlights", "- Finished the first task", "Decisions and support needed",
                    "Need one hour with my mentor", "Focus for next period", "Goal notes", "Fixture goal").doesNotContain("Risks and issues");
            // MRC-05: the Report sheet's header is unchanged.
            assertThat(row(workbook.getSheet("Report"), 3)).containsExactly("Goals", "Steps", "Tasks", "Blockers / Management Issues", "Progress (%)",
                    "Priority", "Due Date", "Next Action");
            Sheet summary = workbook.getSheet("Summary");
            assertThat(row(summary, 3)).containsExactly("Headline", "Value", "Change since R-001");
            assertThat(text(summary, 7, 2)).isEqualTo("+1");
            assertThat(column(workbook.getSheet("Changes"), 0)).contains("Task completed");
        }
        try (XMLSlideShow deck = deck(token, "/api/reports/saved/" + december + ".pptx")) {
            List<String> titles = titles(deck);
            assertThat(titles.get(1)).isEqualTo("Highlights");
            assertThat(titles.get(2)).isEqualTo("Executive summary");
            assertThat(titles.get(3)).isEqualTo("What changed");
            assertThat(slideText(deck.getSlides().get(0))).contains("DRAFT");
            assertThat(slideText(deck.getSlides().get(1))).contains("Finished the first task", "Start the second");
            XSLFSlide support = deck.getSlides().get(titles.indexOf("Decisions and support needed"));
            assertThat(slideText(support)).contains("In the participant's words", "Need one hour with my mentor");
            XSLFSlide progress = deck.getSlides().get(titles.indexOf("Progress by goal"));
            StringBuilder notes = new StringBuilder();
            for (XSLFTextShape shape : deck.getNotesSlide(progress).getPlaceholders()) {
                notes.append(shape.getText()).append('\n');
            }
            assertThat(notes.toString()).contains("Goal notes:", "Fixture goal: Going well");
        }

        // RM-FR-39: the sections chosen are remembered for the next download, and a download may override them.
        JsonNode chosen = read(send(put("/api/reports/saved/" + december + "/sections"), token, Map.of("sections", List.of("DETAILS")))
                .andExpect(status().isOk()));
        assertThat(chosen.get("sections")).extracting(JsonNode::asText).containsExactly("DETAILS");
        try (XSSFWorkbook workbook = workbook(token, "/api/reports/saved/" + december + ".xlsx")) {
            assertThat(sheetNames(workbook)).containsExactly("Summary", "Report");
            // The executive summary stays; the breakdown and goal tables go.
            assertThat(column(workbook.getSheet("Summary"), 0)).anyMatch(value -> value.startsWith("This period"))
                    .doesNotContain("Tasks by status", "Dream");
        }
        try (XSSFWorkbook workbook = workbook(token, "/api/reports/saved/" + december + ".xlsx?sections=BLOCKERS,CHANGES")) {
            assertThat(sheetNames(workbook)).containsExactly("Summary", "Blockers", "Changes");
        }
        try (XMLSlideShow deck = deck(token, "/api/reports/saved/" + december + ".pptx")) {
            assertThat(titles(deck)).containsExactly("Progress report", "Executive summary", "Overdue items");
        }
        send(put("/api/reports/saved/" + december + "/sections"), token, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(8));

        // RMC-16: a final report's files carry no draft mark.
        send(post("/api/reports/saved/" + december + "/finalize"), token, Map.of()).andExpect(status().isOk());
        try (XSSFWorkbook workbook = workbook(token, "/api/reports/saved/" + december + ".xlsx?language=km")) {
            assertThat(text(workbook.getSheetAt(0), 0, 0)).isEqualTo("របាយការណ៍គ្រប់គ្រង");
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("មតិយោបល់");
        }
        try (XMLSlideShow deck = deck(token, "/api/reports/saved/" + december + ".pptx")) {
            assertThat(slideText(deck.getSlides().get(0))).doesNotContain("DRAFT");
        }
    }

    @Test
    void rmFr32_theLiveReportCanBeComparedWithAFinalReportOnTheSameScopeOnly() throws Exception {
        String token = register("compare-live");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long first = task(token, plan.stepId(), Map.of("title", "First"));
        long finalId = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        send(post("/api/reports/saved/" + finalId + "/finalize"), token, Map.of()).andExpect(status().isOk());
        long draftId = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        complete(token, plan.stepId(), first, "First", TODAY.plusMonths(2));

        JsonNode live = summary(token, "?dreamId=" + plan.dreamId() + "&compareTo=" + finalId);
        List<String> kinds = new ArrayList<>();
        live.get("comparison").get("changes").forEach(change -> kinds.add(change.get("kind").asText()));
        assertThat(kinds).contains("TASK_COMPLETED");
        assertThat(summary(token, "?dreamId=" + plan.dreamId()).get("comparison").isNull()).isTrue();
        String bad = "Choose a final report on the same scope to compare with.";
        mockMvc.perform(get("/api/reports/management?compareTo=" + finalId).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(bad));
        mockMvc.perform(get("/api/reports/management?dreamId=" + plan.dreamId() + "&compareTo=" + draftId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        send(put("/api/reports/saved/" + draftId), token, Map.of("dreamId", plan.dreamId(), "compareToReportId", draftId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(bad));
        send(post("/api/reports/saved"), token, Map.of("compareToReportId", finalId)).andExpect(status().isBadRequest());
        send(post("/api/reports/saved"), token, Map.of("compareToReportId", 999_999)).andExpect(status().isBadRequest());

        try (XSSFWorkbook workbook = workbook(token, "/api/reports/management.xlsx?dreamId=" + plan.dreamId() + "&compareTo=" + finalId)) {
            assertThat(sheetNames(workbook)).containsExactly("Summary", "Report", "Blockers", "Changes");
            assertThat(text(workbook.getSheetAt(0), 0, 0)).isEqualTo("Management report");
        }
        try (XSSFWorkbook workbook = workbook(token, "/api/reports/management.xlsx?dreamId=" + plan.dreamId() + "&sections=SUMMARY")) {
            assertThat(sheetNames(workbook)).containsExactly("Summary");
        }
        try (XMLSlideShow deck = deck(token, "/api/reports/management.pptx?dreamId=" + plan.dreamId() + "&compareTo=" + finalId)) {
            assertThat(titles(deck).get(2)).isEqualTo("What changed");
        }
        // Another user's final report answers 404, never its contents.
        String other = register("compare-other");
        mockMvc.perform(get("/api/reports/management?compareTo=" + finalId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private void complete(String token, long step, long taskId, String title, LocalDate due) throws Exception {
        complete(token, step, taskId, title, due, "COMPLETED");
    }

    private void complete(String token, long step, long taskId, String title, LocalDate due, String status) throws Exception {
        send(put("/api/tasks/" + taskId), token, Map.of("stepId", step, "title", title, "owner", "Me", "priority", "MEDIUM",
                "dueDate", due.toString(), "status", status, "progressPercent", "COMPLETED".equals(status) ? 100 : 0))
                .andExpect(status().isOk());
    }

    private long goal(String token, long dream, String title) throws Exception {
        return PlanFixture.createdId(mockMvc, objectMapper, token, "/api/goals", Map.of("dreamId", dream, "title", title,
                "description", "d", "successCriteria", "s", "priority", "LOW", "targetDate", TODAY.plusMonths(5).toString(),
                "status", "NOT_STARTED", "scheduleMode", "BOTTOM_UP"));
    }

    private JsonNode saved(String token, long id) throws Exception {
        return read(mockMvc.perform(get("/api/reports/saved/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private JsonNode summary(String token, String query) throws Exception {
        return read(mockMvc.perform(get("/api/reports/management" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private byte[] download(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
    }

    private XSSFWorkbook workbook(String token, String path) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(download(token, path)));
    }

    private XMLSlideShow deck(String token, String path) throws Exception {
        return new XMLSlideShow(new ByteArrayInputStream(download(token, path)));
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        List<String> names = new ArrayList<>();
        workbook.forEach(sheet -> names.add(sheet.getSheetName()));
        return names;
    }

    private static List<String> titles(XMLSlideShow deck) {
        return deck.getSlides().stream().map(slide -> slide.getPlaceholder(0).getText()).toList();
    }

    private static String slideText(XSLFSlide slide) {
        StringBuilder text = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape) {
                text.append(textShape.getText()).append('\n');
            }
        }
        return text.toString();
    }

    private static List<String> column(Sheet sheet, int column) {
        List<String> values = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getCell(column) != null) {
                values.add(FORMAT.formatCellValue(row.getCell(column)));
            }
        }
        return values;
    }

    private static List<String> row(Sheet sheet, int index) {
        List<String> values = new ArrayList<>();
        Row row = sheet.getRow(index);
        if (row != null) {
            row.forEach(cell -> values.add(FORMAT.formatCellValue(cell)));
        }
        return values;
    }

    private static String text(Sheet sheet, int row, int column) {
        return FORMAT.formatCellValue(sheet.getRow(row).getCell(column));
    }

    private JsonNode created(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated()));
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

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
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
