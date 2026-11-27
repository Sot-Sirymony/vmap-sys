package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * V19 phase 2 (BRD 19.0.0): filters and the Details rows (RM-FR-10 to
 * RM-FR-15), saved reports from draft to final (RM-FR-16 to RM-FR-24) and
 * commentary (RM-FR-26 to RM-FR-30), through the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SavedReportFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rmc05_rmc06_filtersNarrowEverythingAndTheDetailsRowsMatchTheExcelReport() throws Exception {
        String token = register("saved-filters");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        task(token, plan.stepId(), Map.of("title", "Urgent call", "priority", "CRITICAL", "owner", "Dara"));
        task(token, plan.stepId(), Map.of("title", "Tidy notes", "priority", "LOW", "owner", "Sok"));

        JsonNode critical = summary(token, "?priority=CRITICAL");
        assertThat(critical.get("headline").get("totalTasks").asLong()).isEqualTo(1);
        assertThat(critical.get("filters").get("priorities").get(0).asText()).isEqualTo("CRITICAL");
        assertThat(critical.get("owners")).extracting(JsonNode::asText).containsExactly("Dara", "Sok");
        assertThat(summary(token, "?owner=sok").get("headline").get("totalTasks").asLong()).isEqualTo(1);
        // Health is judged on the whole goal, so it filters goals without changing.
        assertThat(summary(token, "?health=RED").get("goals")).isEmpty();
        assertThat(summary(token, "?health=GREEN").get("goals")).hasSize(1);

        JsonNode rows = read(mockMvc.perform(get("/api/reports/management/rows?priority=CRITICAL,LOW")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())).get("rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("taskId").isNumber()).isTrue();
        try (XSSFWorkbook workbook = workbook(token, "/api/reports/management.xlsx?priority=CRITICAL,LOW")) {
            Sheet report = workbook.getSheet("Report");
            assertThat(text(report, 1, 0)).endsWith("· Low, Critical");
            List<String> excelTasks = new ArrayList<>();
            for (int index = 4; index <= report.getLastRowNum(); index++) {
                // Dream, Goals, Steps, Tasks: the task is the fourth column.
                excelTasks.add(FORMAT.formatCellValue(report.getRow(index).getCell(3)));
            }
            List<String> detailTasks = new ArrayList<>();
            rows.forEach(row -> detailTasks.add(row.get("task").asText()));
            assertThat(detailTasks).isEqualTo(excelTasks);
        }
    }

    @Test
    void rmc07_rmc08_aDraftFollowsThePlanUntilItIsFinalisedThenItsFiguresAreFrozen() throws Exception {
        String token = register("saved-lifecycle");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long first = task(token, plan.stepId(), Map.of("title", "First"));
        long second = task(token, plan.stepId(), Map.of("title", "Second"));

        JsonNode draft = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId())));
        long id = draft.get("id").asLong();
        assertThat(draft.get("code").asText()).isEqualTo("R-001");
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("name").asText()).isEqualTo("Fixture dream – This month");
        assertThat(draft.get("verified").isNull()).isTrue();
        assertThat(list(token, "")).extracting(row -> row.get("id").asLong()).containsExactly(id);

        complete(token, plan.stepId(), first, "First");
        assertThat(saved(token, id).get("report").get("headline").get("completedTasks").asLong()).isEqualTo(1);

        JsonNode finalReport = read(send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk()));
        assertThat(finalReport.get("status").asText()).isEqualTo("FINAL");
        assertThat(finalReport.get("periodFrom").asText()).isEqualTo(TODAY.withDayOfMonth(1).toString());
        assertThat(finalReport.get("finalizedAt").isNull()).isFalse();

        complete(token, plan.stepId(), second, "Second");
        JsonNode frozen = saved(token, id);
        assertThat(frozen.get("verified").asBoolean()).isTrue();
        assertThat(frozen.get("report").get("headline").get("completedTasks").asLong()).isEqualTo(1);
        // The files and the rows come from the snapshot too.
        mockMvc.perform(get("/api/reports/saved/" + id + ".xlsx").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
                        "Fixture_dream_This_month_" + TODAY + ".xlsx")));
        JsonNode rows = read(mockMvc.perform(get("/api/reports/saved/" + id + "/rows").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).get("rows");
        assertThat(rows).filteredOn(row -> row.get("completed").asBoolean()).hasSize(1);
        mockMvc.perform(get("/api/reports/saved/" + id + ".pptx").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        // A tampered snapshot is reported, not shown as verified.
        jdbcTemplate.update("update management_reports set snapshot_checksum = ? where id = ?", "0".repeat(64), id);
        assertThat(saved(token, id).get("verified").asBoolean()).isFalse();
    }

    @Test
    void rmc09_aFinalReportCanOnlyBeRenamedOrArchivedAndADraftCanBeDeleted() throws Exception {
        String token = register("saved-readonly");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long id = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());

        send(put("/api/reports/saved/" + id + "/commentary"), token, Map.of("highlights", "Too late"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A final report cannot be changed. Make a new report from it instead."));
        send(put("/api/reports/saved/" + id), token, Map.of("name", "Moved", "dreamId", plan.dreamId() + 1000))
                .andExpect(status().isConflict());
        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/reports/saved/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A final report cannot be deleted. You can archive it."));
        JsonNode renamed = read(send(put("/api/reports/saved/" + id), token, Map.of("name", "November progress")).andExpect(status().isOk()));
        assertThat(renamed.get("name").asText()).isEqualTo("November progress");

        send(patch("/api/reports/saved/" + id + "/archive"), token, Map.of()).andExpect(status().isOk());
        assertThat(list(token, "")).isEmpty();
        assertThat(list(token, "?includeArchived=true")).hasSize(1);
        send(patch("/api/reports/saved/" + id + "/restore"), token, Map.of()).andExpect(status().isOk());
        assertThat(list(token, "?status=FINAL")).hasSize(1);
        assertThat(list(token, "?status=DRAFT")).isEmpty();
        assertThat(list(token, "?q=novem")).hasSize(1);

        long draft = created(send(post("/api/reports/saved"), token, Map.of("name", "Scratch"))).get("id").asLong();
        mockMvc.perform(delete("/api/reports/saved/" + draft).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reports/saved/" + draft).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test
    void rmc10_rmc11_newFromThisCarriesTheCommentaryAndTheDraftSuggestionFillsOnlyEmptySections() throws Exception {
        String token = register("saved-copy");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long done = task(token, plan.stepId(), Map.of("title", "Book the hall"));
        task(token, plan.stepId(), Map.of("title", "Send invitations", "nextAction", "Email the list"));
        complete(token, plan.stepId(), done, "Book the hall");

        long id = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId(), "period", "THIS_MONTH")))
                .get("id").asLong();
        send(put("/api/reports/saved/" + id + "/commentary"), token, Map.of("risks", "- Venue may cancel",
                "goalNotes", Map.of(String.valueOf(plan.goalId()), "On schedule"), "nothingNeeded", false))
                .andExpect(status().isOk());

        JsonNode suggestion = read(send(post("/api/reports/saved/" + id + "/commentary-draft"), token, Map.of()).andExpect(status().isOk()));
        assertThat(suggestion.get("highlights").asText()).contains("Fixture goal: completed 1 task(s) (Book the hall)");
        assertThat(suggestion.get("risks").isNull()).as("risks already has text").isTrue();
        assertThat(suggestion.get("nextFocus").asText()).contains("Fixture goal: Email the list");

        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());
        JsonNode copy = created(send(post("/api/reports/saved/" + id + "/copy"), token, Map.of()));
        assertThat(copy.get("status").asText()).isEqualTo("DRAFT");
        assertThat(copy.get("code").asText()).isEqualTo("R-002");
        assertThat(copy.get("compareToReportId").asLong()).isEqualTo(id);
        // Next month has not started, so the new draft covers this month.
        assertThat(copy.get("periodKind").asText()).isEqualTo("THIS_MONTH");
        assertThat(copy.get("commentary").get("risks").asText()).isEqualTo("- Venue may cancel");
        assertThat(copy.get("commentary").get("goalNotes").get(String.valueOf(plan.goalId())).asText()).isEqualTo("On schedule");
    }

    @Test
    void rmFr24_aParticipantKeepsAtMost200SavedReports() throws Exception {
        String token = register("saved-limit");
        long id = created(send(post("/api/reports/saved"), token, Map.of())).get("id").asLong();
        for (int index = 2; index <= 200; index++) {
            jdbcTemplate.update("""
                    insert into management_reports (user_id, code, name, scope_kind, period_kind, status, archived,
                      nothing_needed, language, created_at, updated_at)
                    select user_id, ?, name, scope_kind, period_kind, status, archived, nothing_needed, language, created_at, updated_at
                    from management_reports where id = ?""", "R-" + index, id);
        }
        send(post("/api/reports/saved"), token, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You have 200 saved reports. Delete drafts you no longer need first."));
    }

    @Test
    void anotherParticipantsReportIsNotFoundAndABadScopeIsRefused() throws Exception {
        String owner = register("saved-owner");
        String other = register("saved-other");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, owner);
        long id = created(send(post("/api/reports/saved"), owner, Map.of("dreamId", plan.dreamId()))).get("id").asLong();

        mockMvc.perform(get("/api/reports/saved/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        send(post("/api/reports/saved"), other, Map.of("dreamId", plan.dreamId())).andExpect(status().isNotFound());
        send(post("/api/reports/saved"), owner, Map.of("dreamId", plan.dreamId(), "areaId", plan.areaId()))
                .andExpect(status().isBadRequest());
        send(post("/api/reports/saved"), owner, Map.of("period", "CUSTOM", "from", TODAY.toString(), "to", TODAY.plusDays(2).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The period cannot end after today."));
        assertThat(list(other, "")).isEmpty();
    }

    // ---------- helpers ----------

    private void complete(String token, long step, long taskId, String title) throws Exception {
        send(put("/api/tasks/" + taskId), token, Map.of("stepId", step, "title", title, "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusMonths(2).toString(), "status", "COMPLETED", "progressPercent", 100))
                .andExpect(status().isOk());
    }

    private JsonNode saved(String token, long id) throws Exception {
        return read(mockMvc.perform(get("/api/reports/saved/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private List<JsonNode> list(String token, String query) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        read(mockMvc.perform(get("/api/reports/saved" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).get("content").forEach(rows::add);
        return rows;
    }

    private JsonNode summary(String token, String query) throws Exception {
        return read(mockMvc.perform(get("/api/reports/management" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private XSSFWorkbook workbook(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    private static String text(Sheet sheet, int row, int column) {
        Row line = sheet.getRow(row);
        return FORMAT.formatCellValue(line.getCell(column));
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
