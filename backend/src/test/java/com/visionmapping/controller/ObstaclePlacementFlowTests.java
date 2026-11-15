package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V17 phase 1 (BRD 17.0.0): blockers sit on a step or a task, their goal and
 * dream follow that anchor, and older dream-level obstacles are kept and
 * listed until the participant moves them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObstaclePlacementFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void blc01_aPlanObstacleOnADreamOnlyIsRefusedWithAClearMessage() throws Exception {
        String token = register("placement-refused");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);

        send(post("/api/obstacles"), token, obstacle(Map.of("relatedDreamId", plan.dreamId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Put this blocker on a step or a task."));
    }

    @Test
    void blc02_blc06_parentsFollowTheStepAndMoveWithIt() throws Exception {
        String token = register("placement-move");
        Branch first = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch second = PlanFixture.branch(mockMvc, objectMapper, token);

        // The request names the other dream; the step wins.
        JsonNode created = read(send(post("/api/obstacles"), token,
                obstacle(Map.of("relatedStepId", first.stepId(), "relatedDreamId", second.dreamId())))
                .andExpect(status().isCreated()));
        long id = created.get("id").asLong();
        assertThat(created.get("relatedGoalId").asLong()).isEqualTo(first.goalId());
        assertThat(created.get("relatedDreamId").asLong()).isEqualTo(first.dreamId());
        assertThat(created.get("placement").asText()).isEqualTo("STEP");

        // BL-FR-08: moving the step to a goal in another dream moves the obstacle's goal and dream too.
        send(put("/api/steps/" + first.stepId()), token, Map.of("owner", "Me", "goalId", second.goalId(),
                "title", "Fixture step", "sequenceNumber", 2, "complex", false, "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(4).toString(), "status", "NOT_STARTED"))
                .andExpect(status().isOk());

        JsonNode moved = read(mockMvc.perform(get("/api/obstacles/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(moved.get("relatedStepId").asLong()).isEqualTo(first.stepId());
        assertThat(moved.get("relatedGoalId").asLong()).isEqualTo(second.goalId());
        assertThat(moved.get("relatedDreamId").asLong()).isEqualTo(second.dreamId());
    }

    @Test
    void blc04_blc05_anOlderDreamLevelObstacleIsListedKeptAndCanBeMoved() throws Exception {
        String token = register("placement-legacy");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long id = legacyDreamOnlyObstacle(token, plan);

        JsonNode unplaced = read(mockMvc.perform(get("/api/obstacles?placement=UNPLACED")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
        assertThat(unplaced).hasSize(1);
        assertThat(unplaced.get(0).get("id").asLong()).isEqualTo(id);
        assertThat(unplaced.get(0).get("placement").asText()).isEqualTo("NONE");

        // BL-FR-06: still editable and resolvable where it is.
        send(put("/api/obstacles/" + id), token, obstacle(Map.of("relatedDreamId", plan.dreamId(), "status", "RESOLVED",
                "rootCause", "No budget line"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Move to a step: it leaves the list.
        send(put("/api/obstacles/" + id), token, obstacle(Map.of("relatedStepId", plan.stepId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedGoalId").value(plan.goalId()));
        JsonNode after = read(mockMvc.perform(get("/api/obstacles?placement=UNPLACED")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
        assertThat(after).isEmpty();
    }

    @Test
    void blc20_anotherUsersStepCannotBeUsed() throws Exception {
        String owner = register("placement-owner");
        long stepId = PlanFixture.step(mockMvc, objectMapper, owner);
        String stranger = register("placement-stranger");

        send(post("/api/obstacles"), stranger, obstacle(Map.of("relatedStepId", stepId)))
                .andExpect(status().isNotFound());
        assertThat(read(mockMvc.perform(get("/api/obstacles?placement=UNPLACED")
                .header("Authorization", "Bearer " + stranger)).andExpect(status().isOk()))).isEmpty();
    }

    @Test
    void blc16_blc17_exportMarksPlacementAndImportKeepsDreamOnlyRowsAsUnplaced() throws Exception {
        String author = register("placement-export");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, author);
        long taskId = PlanFixture.createdId(mockMvc, objectMapper, author, "/api/tasks", Map.of("stepId", plan.stepId(),
                "title", "Book the room", "owner", "Me", "priority", "HIGH",
                "dueDate", LocalDate.now().plusDays(7).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        send(post("/api/obstacles"), author, obstacle(Map.of("relatedTaskId", taskId))).andExpect(status().isCreated());
        legacyDreamOnlyObstacle(author, plan);

        byte[] workbook = mockMvc.perform(post("/api/excel/export").header("Authorization", "Bearer " + author))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Sheet sheet = book.getSheet("Obstacles");
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("Placed on");
            List<String> placements = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() > 0) {
                    placements.add(row.getCell(10).getStringCellValue());
                }
            }
            assertThat(placements).containsExactlyInAnyOrder("Task", "Not placed");
            assertThat(sheet.getSheetConditionalFormatting().getNumConditionalFormattings()).isEqualTo(1);
        }

        String importer = register("placement-import");
        MockMultipartFile upload = new MockMultipartFile("file", "vision-mapping.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);
        JsonNode summary = read(mockMvc.perform(multipart("/api/excel/import").file(upload)
                .header("Authorization", "Bearer " + importer)).andExpect(status().isOk()));
        assertThat(summary.get("rowsBySheet").get("Obstacles").asInt()).isEqualTo(2);
        assertThat(summary.get("warnings").get(0).asText()).startsWith("Obstacles: imported, needs a step (1)");

        JsonNode imported = read(mockMvc.perform(get("/api/obstacles").header("Authorization", "Bearer " + importer))
                .andExpect(status().isOk()));
        List<String> importedPlacements = new ArrayList<>();
        imported.forEach(row -> importedPlacements.add(row.get("placement").asText()));
        assertThat(importedPlacements).containsExactlyInAnyOrder("TASK", "NONE");
    }

    /** BLC-15: the V75 data fix corrects only derived ids, never the anchor, and records the old values. */
    @Test
    void blc15_theV75DataFixRepairsDerivedIdsAndKeepsAnAudit() throws Exception {
        String token = register("placement-v75");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch other = PlanFixture.branch(mockMvc, objectMapper, token);
        long id = read(send(post("/api/obstacles"), token, obstacle(Map.of("relatedStepId", plan.stepId())))
                .andExpect(status().isCreated())).get("id").asLong();
        // Before V17 the goal and dream were picked by hand and could disagree with the step.
        jdbcTemplate.update("update obstacles set related_goal_id = ?, related_dream_id = ? where id = ?",
                other.goalId(), other.dreamId(), id);

        for (String statement : migrationStatements()) {
            jdbcTemplate.execute(statement);
        }

        Map<String, Object> fixed = jdbcTemplate.queryForMap(
                "select related_step_id, related_goal_id, related_dream_id from obstacles where id = ?", id);
        assertThat(((Number) fixed.get("related_step_id")).longValue()).isEqualTo(plan.stepId());
        assertThat(((Number) fixed.get("related_goal_id")).longValue()).isEqualTo(plan.goalId());
        assertThat(((Number) fixed.get("related_dream_id")).longValue()).isEqualTo(plan.dreamId());
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "select old_related_goal_id, old_related_dream_id from obstacle_anchor_fix_v75 where obstacle_id = ?", id);
        assertThat(((Number) audit.get("old_related_goal_id")).longValue()).isEqualTo(other.goalId());
        assertThat(((Number) audit.get("old_related_dream_id")).longValue()).isEqualTo(other.dreamId());
    }

    // ---------- helpers ----------

    /** An obstacle saved before V17 with only a dream: created on a step, then reset in the database. */
    private long legacyDreamOnlyObstacle(String token, Branch plan) throws Exception {
        long id = read(send(post("/api/obstacles"), token, obstacle(Map.of("relatedStepId", plan.stepId(),
                "title", "Funding"))).andExpect(status().isCreated())).get("id").asLong();
        jdbcTemplate.update("update obstacles set related_step_id = null, related_goal_id = null, related_dream_id = ? where id = ?",
                plan.dreamId(), id);
        return id;
    }

    /** The V75 INSERT and UPDATE statements (the table and indexes already exist). */
    private static List<String> migrationStatements() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V75__obstacle_anchor_consistency.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        sql.lines().filter(line -> !line.trim().startsWith("--")).forEach(line -> code.append(line).append('\n'));
        List<String> statements = new ArrayList<>();
        for (String statement : code.toString().split(";")) {
            String trimmed = statement.trim();
            if (trimmed.startsWith("INSERT") || trimmed.startsWith("UPDATE")) {
                statements.add(trimmed);
            }
        }
        assertThat(statements).hasSize(4);
        return statements;
    }

    private static Map<String, Object> obstacle(Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("title", "No budget", "obstacleType", "MONEY",
                "severity", "HIGH", "status", "OPEN"));
        body.putAll(extra);
        return body;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        MvcResult done = result.andReturn();
        return objectMapper.readTree(done.getResponse().getContentAsString());
    }

    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V17 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
