package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V32 phase 2 (BRD 32 D-32-4 to D-32-6): Apply selected / Apply all on the server, and Undo for the whole batch. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StreamlineBatchFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /** A goal with three steps; the first two have two tasks each (2 hours each), the third has none. */
    private record Plan(String token, long goalId, List<Long> stepIds, List<Long> taskIds) {
    }

    @Test
    void sfc05_undoReversesADeleteAMergeAndAKeepWithoutCountingThemAsBroughtBack() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long emptyStep = itemFor(review, "stepId", plan.stepIds().get(2));
        long source = itemFor(review, "taskId", plan.taskIds().get(0));
        long target = itemFor(review, "taskId", plan.taskIds().get(1));
        long kept = itemFor(review, "stepId", plan.stepIds().get(1));

        JsonNode result = batch(plan, id, 2, List.of(
                Map.of("type", "DELETE", "itemId", emptyStep),
                Map.of("type", "MERGE", "itemId", source, "targetItemId", target),
                Map.of("type", "UPDATE", "itemId", kept, "update", Map.of("keep", true))));
        assertThat(result.get("applied").asInt()).isEqualTo(3);
        assertThat(result.get("failedAtPosition").isNull()).isTrue();
        assertThat(archived("vision_steps", plan.stepIds().get(2))).isTrue();
        assertThat(archived("task_items", plan.taskIds().get(0))).isTrue();
        assertThat(hours(plan.taskIds().get(1))).isEqualByComparingTo("4");

        JsonNode undo = undo(plan, id, result.get("batchId").asLong()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .transform(this::readText);
        assertThat(undo.get("undone").asInt()).isEqualTo(3);
        assertThat(undo.get("skipped")).isEmpty();
        assertThat(archived("vision_steps", plan.stepIds().get(2))).isFalse();
        assertThat(archived("task_items", plan.taskIds().get(0))).isFalse();
        assertThat(hours(plan.taskIds().get(1))).isEqualByComparingTo("2");
        // The merge note is gone: the description is back to what it was (empty).
        assertThat(jdbc.queryForObject("select description from task_items where id = ?", String.class, plan.taskIds().get(1))).isNull();
        JsonNode summary = undo.get("review").get("summary");
        assertThat(summary.get("deleted").asLong()).isZero();
        assertThat(summary.get("merged").asLong()).isZero();
        assertThat(summary.get("kept").asLong()).isZero();
        assertThat(summary.get("addedBack").asLong()).isZero();

        // Only once.
        undo(plan, id, result.get("batchId").asLong()).andExpect(status().isBadRequest());
    }

    @Test
    void undoingAStepMergeMovesItsTasksBack() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long source = itemFor(review, "stepId", plan.stepIds().get(0));
        long target = itemFor(review, "stepId", plan.stepIds().get(1));
        JsonNode result = batch(plan, id, 2, List.of(Map.of("type", "MERGE", "itemId", source, "targetItemId", target)));
        assertThat(stepOf(plan.taskIds().get(0))).isEqualTo(plan.stepIds().get(1));

        undo(plan, id, result.get("batchId").asLong()).andExpect(status().isOk());

        assertThat(archived("vision_steps", plan.stepIds().get(0))).isFalse();
        assertThat(stepOf(plan.taskIds().get(0))).isEqualTo(plan.stepIds().get(0));
        assertThat(stepOf(plan.taskIds().get(1))).isEqualTo(plan.stepIds().get(0));
        assertThat(jdbc.queryForObject("select description from vision_steps where id = ?", String.class, plan.stepIds().get(1)))
                .isNull();
    }

    @Test
    void aFailureStopsTheRunAndTheAppliedActionsStayAndShowInRecentChanges() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long kept = itemFor(review, "stepId", plan.stepIds().get(1));
        long emptyStep = itemFor(review, "stepId", plan.stepIds().get(2));

        JsonNode result = batch(plan, id, 2, List.of(
                Map.of("type", "UPDATE", "itemId", kept, "update", Map.of("keep", true)),
                Map.of("type", "MERGE", "itemId", kept, "targetItemId", kept),
                Map.of("type", "DELETE", "itemId", emptyStep)));

        assertThat(result.get("applied").asInt()).isEqualTo(1);
        assertThat(result.get("total").asInt()).isEqualTo(3);
        assertThat(result.get("failedAtPosition").asInt()).isEqualTo(2);
        assertThat(result.get("error").asText()).isEqualTo("Choose a different item to merge into.");
        assertThat(archived("vision_steps", plan.stepIds().get(2))).isFalse();

        JsonNode recent = read(mockMvc.perform(get("/api/streamline-reviews/" + id + "/batches?stage=2")
                .header("Authorization", "Bearer " + plan.token())).andExpect(status().isOk()));
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).get("applied").asInt()).isEqualTo(1);
        assertThat(recent.get(0).get("canUndo").asBoolean()).isTrue();
        assertThat(recent.get(0).get("changes").get(0).get("action").asText()).isEqualTo("UPDATE");

        // A batch where nothing applied leaves no entry.
        JsonNode none = batch(plan, id, 2, List.of(Map.of("type", "MERGE", "itemId", kept, "targetItemId", kept)));
        assertThat(none.get("applied").asInt()).isZero();
        assertThat(none.get("batchId").isNull()).isTrue();
    }

    @Test
    void sfc06_anItemChangedAfterTheBatchIsLeftAsItIs() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long kept = itemFor(review, "stepId", plan.stepIds().get(1));
        long emptyStep = itemFor(review, "stepId", plan.stepIds().get(2));
        JsonNode result = batch(plan, id, 2, List.of(
                Map.of("type", "UPDATE", "itemId", kept, "update", Map.of("keep", true)),
                Map.of("type", "DELETE", "itemId", emptyStep)));
        send(put("/api/streamline-reviews/" + id + "/items/" + kept), plan.token(), Map.of("keepReason", "The bank needs it"))
                .andExpect(status().isOk());

        JsonNode undo = readText(undo(plan, id, result.get("batchId").asLong()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(undo.get("undone").asInt()).isEqualTo(1);
        assertThat(undo.get("skipped")).hasSize(1);
        assertThat(undo.get("skipped").get(0).get("code").asText()).startsWith("S-");
        assertThat(archived("vision_steps", plan.stepIds().get(2))).isFalse();
        assertThat(jdbc.queryForObject("select decision from streamline_items where id = ?", String.class, kept)).isEqualTo("KEEP");
    }

    @Test
    void sfc07_undoEndsWithTheStageAndAfter24HoursAndStaysWithItsOwner() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        long kept = itemFor(review(plan, id), "stepId", plan.stepIds().get(1));
        long first = batch(plan, id, 2, List.of(Map.of("type", "UPDATE", "itemId", kept, "update", Map.of("keep", true))))
                .get("batchId").asLong();

        String other = register("streamline-batch-other-" + UUID.randomUUID() + "@example.com");
        undo(new Plan(other, 0, List.of(), List.of()), id, first).andExpect(status().isNotFound());
        send(post("/api/streamline-reviews/" + id + "/apply-batch"), other,
                Map.of("stage", 2, "actions", List.of(Map.of("type", "DELETE", "itemId", kept)))).andExpect(status().isNotFound());

        jdbc.update("update streamline_apply_batches set created_at = created_at - interval '25' hour where id = ?", first);
        undo(plan, id, first).andExpect(status().isBadRequest());

        long second = batch(plan, id, 2, List.of(Map.of("type", "UPDATE", "itemId", kept, "update", Map.of("keepReason", "Needed"))))
                .get("batchId").asLong();
        send(post("/api/streamline-reviews/" + id + "/stages/2/not-needed"), plan.token(), Map.of("reason", "Nothing else to cut here"))
                .andExpect(status().isOk());
        undo(plan, id, second).andExpect(status().isBadRequest());

        // Stage 2 is finished, so its suggestions can no longer be applied in a batch either.
        send(post("/api/streamline-reviews/" + id + "/apply-batch"), plan.token(),
                Map.of("stage", 2, "actions", List.of(Map.of("type", "DELETE", "itemId", kept)))).andExpect(status().isBadRequest());
    }

    @Test
    void undoRestoresASimplifiedTitleAndStopsARepeatRuleTheBatchCreated() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        long task = itemFor(review(plan, id), "taskId", plan.taskIds().get(0));
        notNeeded(plan, id, 2);
        JsonNode simplified = batch(plan, id, 3, List.of(Map.of("type", "SIMPLIFY", "itemId", task,
                "simplify", Map.of("title", "One short call", "note", "Smaller"))));
        assertThat(title(plan.taskIds().get(0))).isEqualTo("One short call");
        undo(plan, id, simplified.get("batchId").asLong()).andExpect(status().isOk());
        assertThat(title(plan.taskIds().get(0))).isEqualTo("Task 0.1");
        assertThat(jdbc.queryForObject("select simplified from streamline_items where id = ?", Boolean.class, task)).isFalse();

        notNeeded(plan, id, 3);
        notNeeded(plan, id, 4);
        JsonNode repeated = batch(plan, id, 5, List.of(Map.of("type", "REPEAT", "itemId", task, "cadence", "WEEKLY")));
        assertThat(jdbc.queryForObject("select count(*) from task_repeat_rules where source_task_id = ? and active = true", Integer.class,
                plan.taskIds().get(0))).isEqualTo(1);
        undo(plan, id, repeated.get("batchId").asLong()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from task_repeat_rules where source_task_id = ? and active = true", Integer.class,
                plan.taskIds().get(0))).isZero();
        assertThat(jdbc.queryForObject("select repeat_rule_id from task_items where id = ?", Long.class, plan.taskIds().get(0))).isNull();
        assertThat(jdbc.queryForObject("select automation from streamline_items where id = ?", String.class, task)).isNull();
    }

    @Test
    void aBatchMustBeForTheCurrentStageAndHoldAtMostFiftyActions() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        long kept = itemFor(review(plan, id), "stepId", plan.stepIds().get(1));
        send(post("/api/streamline-reviews/" + id + "/apply-batch"), plan.token(),
                Map.of("stage", 3, "actions", List.of(Map.of("type", "DELETE", "itemId", kept)))).andExpect(status().isBadRequest());
        List<Map<String, Object>> many = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            many.add(Map.of("type", "UPDATE", "itemId", kept, "update", Map.of("keep", true)));
        }
        send(post("/api/streamline-reviews/" + id + "/apply-batch"), plan.token(), Map.of("stage", 2, "actions", many))
                .andExpect(status().isBadRequest());
        send(post("/api/streamline-reviews/" + id + "/apply-batch"), plan.token(),
                Map.of("stage", 2, "actions", List.of(Map.of("type", "QUESTION", "itemId", kept)))).andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private JsonNode batch(Plan plan, long id, int stage, List<Map<String, Object>> actions) throws Exception {
        return read(send(post("/api/streamline-reviews/" + id + "/apply-batch"), plan.token(), Map.of("stage", stage, "actions", actions))
                .andExpect(status().isOk()));
    }

    private ResultActions undo(Plan plan, long id, long batchId) throws Exception {
        return send(post("/api/streamline-reviews/" + id + "/batches/" + batchId + "/undo"), plan.token(), Map.of());
    }

    private void notNeeded(Plan plan, long id, int stage) throws Exception {
        send(post("/api/streamline-reviews/" + id + "/stages/" + stage + "/not-needed"), plan.token(),
                Map.of("reason", "Nothing to change at this stage")).andExpect(status().isOk());
    }

    private long reviewAtStage2(Plan plan) throws Exception {
        long id = read(send(post("/api/streamline-reviews"), plan.token(), Map.of("goalId", plan.goalId())).andExpect(status().isCreated()))
                .get("id").asLong();
        List<Long> ids = new ArrayList<>();
        review(plan, id).get("items").forEach(item -> ids.add(item.get("id").asLong()));
        send(put("/api/streamline-reviews/" + id + "/items/bulk"), plan.token(),
                Map.of("itemIds", ids, "requirementSource", "SELF", "requirementOwner", "Me", "verdict", "NEEDED")).andExpect(status().isOk());
        send(post("/api/streamline-reviews/" + id + "/stages/1/complete"), plan.token(), Map.of()).andExpect(status().isOk());
        return id;
    }

    private JsonNode review(Plan plan, long id) throws Exception {
        return read(mockMvc.perform(get("/api/streamline-reviews/" + id).header("Authorization", "Bearer " + plan.token()))
                .andExpect(status().isOk()));
    }

    private static long itemFor(JsonNode review, String field, long id) {
        for (JsonNode item : review.get("items")) {
            if (item.hasNonNull(field) && item.get(field).asLong() == id) {
                return item.get("id").asLong();
            }
        }
        throw new AssertionError("No item for " + field + " " + id);
    }

    private boolean archived(String table, long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select archived from " + table + " where id = ?", Boolean.class, id));
    }

    private BigDecimal hours(long taskId) {
        return jdbc.queryForObject("select estimated_hours from task_items where id = ?", BigDecimal.class, taskId);
    }

    private Long stepOf(long taskId) {
        return jdbc.queryForObject("select step_id from task_items where id = ?", Long.class, taskId);
    }

    private String title(long taskId) {
        return jdbc.queryForObject("select title from task_items where id = ?", String.class, taskId);
    }

    private Plan plan() throws Exception {
        String token = register("streamline-batch-" + UUID.randomUUID() + "@example.com");
        long area = id(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        long dream = id(send(post("/api/dreams"), token, Map.of("visionAreaId", area, "title", "Own a home", "whyImportant", "It matters",
                "successDefinition", "Keys in hand", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP")));
        long goal = id(send(post("/api/goals"), token, Map.of("dreamId", dream, "title", "Save a deposit", "priority", "HIGH",
                "status", "IN_PROGRESS", "moonshot", false, "scheduleMode", "BOTTOM_UP")));
        List<Long> steps = new ArrayList<>();
        List<Long> tasks = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            steps.add(id(send(post("/api/steps"), token, Map.of("goalId", goal, "title", "Step " + index, "owner", "Me",
                    "sequenceNumber", index, "complex", false, "priority", "MEDIUM", "targetDate", LocalDate.now().plusDays(30).toString(),
                    "status", "NOT_STARTED"))));
        }
        for (int step = 0; step < 2; step++) {
            for (int index = 1; index <= 2; index++) {
                Map<String, Object> task = new HashMap<>(Map.of("stepId", steps.get(step), "title", "Task " + step + "." + index, "owner", "Me",
                        "priority", "MEDIUM", "dueDate", LocalDate.now().plusDays(10).toString(), "status", "NOT_STARTED",
                        "progressPercent", 0));
                task.put("estimatedHours", 2);
                tasks.add(id(send(post("/api/tasks"), token, task)));
            }
        }
        return new Plan(token, goal, steps, tasks);
    }

    private long id(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Streamline User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode readText(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
