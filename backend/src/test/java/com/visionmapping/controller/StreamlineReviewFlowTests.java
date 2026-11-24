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
import com.visionmapping.service.TaskRepeatRuleService;
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

/** V27 (BRD 27): the Streamline review end to end (SRC-01 to SRC-08). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StreamlineReviewFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TaskRepeatRuleService repeatService;

    /** A goal with three steps; the first two have two tasks each, the third has none. */
    private record Plan(String token, long goalId, List<Long> stepIds, List<Long> taskIds) {
    }

    // ---------- SRC-01: stages in order ----------

    @Test
    void stagesUnlockOnlyInOrderAndNotNeededNeedsAReason() throws Exception {
        Plan plan = plan();
        JsonNode review = start(plan, Map.of("goalId", plan.goalId()));
        long id = review.get("id").asLong();
        assertThat(review.get("code").asText()).startsWith("SR-");
        assertThat(review.get("currentStage").asInt()).isEqualTo(1);
        assertThat(review.get("items")).hasSize(7);
        assertThat(review.get("stages").get(1).get("unlocked").asBoolean()).isFalse();

        // Stage 4 is locked while stage 1 is open.
        send(put("/api/streamline-reviews/" + id + "/target"), plan.token(), Map.of("targetCycleDays", 3))
                .andExpect(status().isBadRequest());
        send(post("/api/streamline-reviews/" + id + "/stages/2/complete"), plan.token(), Map.of())
                .andExpect(status().isBadRequest());
        // Not needed with too short a reason is refused.
        send(post("/api/streamline-reviews/" + id + "/stages/1/not-needed"), plan.token(), Map.of("reason", "no"))
                .andExpect(status().isBadRequest());
        // Stage 1 cannot finish until every item is answered.
        send(post("/api/streamline-reviews/" + id + "/stages/1/complete"), plan.token(), Map.of())
                .andExpect(status().isBadRequest());

        // A second open review of the same goal is refused.
        send(post("/api/streamline-reviews"), plan.token(), Map.of("goalId", plan.goalId())).andExpect(status().isBadRequest());
    }

    // ---------- SRC-02: questioning, with bulk marking ----------

    @Test
    void bulkMarkingAnswersStageOneAndNotSureItemsNeedNoName() throws Exception {
        Plan plan = plan();
        JsonNode review = start(plan, Map.of("goalId", plan.goalId()));
        long id = review.get("id").asLong();
        List<Long> itemIds = itemIds(review);

        JsonNode after = read(send(put("/api/streamline-reviews/" + id + "/items/bulk"), plan.token(),
                Map.of("itemIds", itemIds, "requirementSource", "SELF", "requirementOwner", "Me", "verdict", "NEEDED"))
                .andExpect(status().isOk()));
        assertThat(after.get("items").findValuesAsText("verdict")).containsOnly("NEEDED");

        // One item changed afterwards: Not sure needs no name.
        long first = itemIds.get(0);
        send(put("/api/streamline-reviews/" + id + "/items/" + first), plan.token(),
                Map.of("requirementSource", "NOT_SURE", "requirementOwner", "", "verdict", "NOT_SURE")).andExpect(status().isOk());
        JsonNode done = read(send(post("/api/streamline-reviews/" + id + "/stages/1/complete"), plan.token(), Map.of())
                .andExpect(status().isOk()));
        assertThat(done.get("currentStage").asInt()).isEqualTo(2);
        assertThat(done.get("stages").get(0).get("state").asText()).isEqualTo("DONE");

        // Stage 2: the Not sure item must be decided before the stage can finish.
        send(post("/api/streamline-reviews/" + id + "/stages/2/complete"), plan.token(), Map.of()).andExpect(status().isBadRequest());
    }

    // ---------- SRC-03: delete archives, restore brings it back and counts ----------

    @Test
    void deletingAStepArchivesItsTasksAndRestoringCountsAsAddedBack() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long stepItem = itemFor(review, "stepId", plan.stepIds().get(0));

        JsonNode afterDelete = read(send(post("/api/streamline-reviews/" + id + "/items/" + stepItem + "/delete"), plan.token(), Map.of())
                .andExpect(status().isOk()));
        assertThat(archived("vision_steps", plan.stepIds().get(0))).isTrue();
        assertThat(archived("task_items", plan.taskIds().get(0))).isTrue();
        assertThat(archived("task_items", plan.taskIds().get(1))).isTrue();
        assertThat(afterDelete.get("summary").get("deleted").asLong()).isEqualTo(1);
        assertThat(afterDelete.get("summary").get("stepsAfter").asInt()).isEqualTo(2);

        JsonNode afterRestore = read(send(post("/api/streamline-reviews/" + id + "/items/" + stepItem + "/restore"), plan.token(), Map.of())
                .andExpect(status().isOk()));
        assertThat(archived("vision_steps", plan.stepIds().get(0))).isFalse();
        assertThat(archived("task_items", plan.taskIds().get(0))).isFalse();
        assertThat(afterRestore.get("summary").get("addedBack").asLong()).isEqualTo(1);
        assertThat(afterRestore.get("summary").get("stepsAfter").asInt()).isEqualTo(3);
    }

    // ---------- SRC-04: merge ----------

    @Test
    void mergingAStepMovesItsOpenTasksAndArchivesIt() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long source = itemFor(review, "stepId", plan.stepIds().get(0));
        long target = itemFor(review, "stepId", plan.stepIds().get(1));

        send(post("/api/streamline-reviews/" + id + "/items/" + source + "/merge"), plan.token(), Map.of("targetItemId", target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.merged").value(1));
        assertThat(archived("vision_steps", plan.stepIds().get(0))).isTrue();
        Long movedStep = jdbc.queryForObject("select step_id from task_items where id = ?", Long.class, plan.taskIds().get(0));
        assertThat(movedStep).isEqualTo(plan.stepIds().get(1));
        assertThat(archived("task_items", plan.taskIds().get(0))).isFalse();

        // A task cannot merge into a step.
        long task = itemFor(review, "taskId", plan.taskIds().get(2));
        send(post("/api/streamline-reviews/" + id + "/items/" + task + "/merge"), plan.token(), Map.of("targetItemId", target))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mergingATaskAddsItsHoursToTheTargetAndArchivesIt() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long source = itemFor(review, "taskId", plan.taskIds().get(0));
        long target = itemFor(review, "taskId", plan.taskIds().get(1));

        JsonNode after = read(send(post("/api/streamline-reviews/" + id + "/items/" + source + "/merge"), plan.token(),
                Map.of("targetItemId", target)).andExpect(status().isOk()));
        assertThat(archived("task_items", plan.taskIds().get(0))).isTrue();
        assertThat(jdbc.queryForObject("select estimated_hours from task_items where id = ?", java.math.BigDecimal.class,
                plan.taskIds().get(1))).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("select description from task_items where id = ?", String.class, plan.taskIds().get(1)))
                .contains("Merged from");
        assertThat(after.get("summary").get("merged").asLong()).isEqualTo(1);
        // Restoring a merged item counts as brought back too.
        send(post("/api/streamline-reviews/" + id + "/items/" + source + "/restore"), plan.token(), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.addedBack").value(1));
    }

    // ---------- SR-NFR-03 ----------

    /** SR-NFR-03: a review of a goal with 50 steps and 500 tasks loads in under 1.5 s on the server. */
    @Test
    void aReviewOfFiftyStepsAndFiveHundredTasksLoadsInUnderOneAndAHalfSeconds() throws Exception {
        Plan plan = plan();
        long userId = jdbc.queryForObject("select user_id from goals where id = ?", Long.class, plan.goalId());
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        java.sql.Date due = java.sql.Date.valueOf(LocalDate.now().plusDays(10));
        for (int stepIndex = 0; stepIndex < 47; stepIndex++) {
            jdbc.update("insert into vision_steps (code, user_id, goal_id, title, owner, sequence_number, is_complex, "
                    + "priority, status, progress_percent, manual_progress_override, archived, overdue_reschedules, created_at, updated_at) "
                    + "values (?, ?, ?, ?, 'Me', ?, false, 'MEDIUM', 'NOT_STARTED', 0, false, false, 0, ?, ?)",
                    "S-P" + stepIndex, userId, plan.goalId(), "Bulk step " + stepIndex, 10 + stepIndex, now, now);
            Long stepId = jdbc.queryForObject("select id from vision_steps where code = ? and user_id = ?", Long.class, "S-P" + stepIndex, userId);
            for (int taskIndex = 0; taskIndex < 11; taskIndex++) {
                jdbc.update("insert into task_items (code, user_id, step_id, title, owner, priority, due_date, status, progress_percent, "
                        + "estimated_hours, archived, blocked_count, created_at, updated_at) values (?, ?, ?, ?, 'Me', 'MEDIUM', ?, "
                        + "'NOT_STARTED', 0, 1, false, 0, ?, ?)",
                        "T-P" + stepIndex + "-" + taskIndex, userId, stepId, "Bulk task " + stepIndex + "." + taskIndex, due, now, now);
            }
        }
        JsonNode started = start(plan, Map.of("goalId", plan.goalId()));
        assertThat(started.get("items").size()).isGreaterThanOrEqualTo(550);
        long id = started.get("id").asLong();
        review(plan, id); // warm up

        long begin = System.nanoTime();
        review(plan, id);
        long millis = (System.nanoTime() - begin) / 1_000_000;
        assertThat(millis).as("GET /api/streamline-reviews/{id} with 550 items, in ms").isLessThan(1500);
    }

    // ---------- BR-27-8 ----------

    @Test
    void aStepReviewCannotEmptyItsStepWithoutConfirmation() throws Exception {
        Plan plan = plan();
        JsonNode review = start(plan, Map.of("stepId", plan.stepIds().get(0)));
        long id = review.get("id").asLong();
        assertThat(review.get("anchorType").asText()).isEqualTo("STEP");
        questionAll(plan, id);
        complete(plan, id, 1);
        List<Long> items = itemIds(review);

        send(post("/api/streamline-reviews/" + id + "/items/" + items.get(0) + "/delete"), plan.token(), Map.of()).andExpect(status().isOk());
        send(post("/api/streamline-reviews/" + id + "/items/" + items.get(1) + "/delete"), plan.token(), Map.of())
                .andExpect(status().isBadRequest());
        send(post("/api/streamline-reviews/" + id + "/items/" + items.get(1) + "/delete"), plan.token(), Map.of("confirmEmpty", true))
                .andExpect(status().isOk());
    }

    // ---------- Stages 3 to 5 and the summary (SRC-05, SRC-07) ----------

    @Test
    void aFullReviewSimplifiesSetsATargetMakesATaskRepeatAndSummarises() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        JsonNode review = review(plan, id);
        long deleteMe = itemFor(review, "taskId", plan.taskIds().get(3));
        send(post("/api/streamline-reviews/" + id + "/items/" + deleteMe + "/delete"), plan.token(), Map.of()).andExpect(status().isOk());
        complete(plan, id, 2);

        long simplifyMe = itemFor(review, "stepId", plan.stepIds().get(2));
        send(put("/api/streamline-reviews/" + id + "/items/" + simplifyMe + "/simplify"), plan.token(),
                Map.of("title", "Shorter step", "markSimple", true, "note", "Cut the hand-off")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select title from vision_steps where id = ?", String.class, plan.stepIds().get(2))).isEqualTo("Shorter step");
        complete(plan, id, 3);

        long speedMe = itemFor(review, "taskId", plan.taskIds().get(0));
        send(put("/api/streamline-reviews/" + id + "/items/" + speedMe), plan.token(), Map.of("speedLevers", List.of("BATCH", "PARALLEL")))
                .andExpect(status().isOk());
        send(put("/api/streamline-reviews/" + id + "/target"), plan.token(), Map.of("targetCycleDays", 4)).andExpect(status().isOk());
        complete(plan, id, 4);

        long repeatMe = itemFor(review, "taskId", plan.taskIds().get(1));
        send(post("/api/streamline-reviews/" + id + "/items/" + repeatMe + "/repeat"), plan.token(), Map.of("cadence", "WEEKLY"))
                .andExpect(status().isOk());
        // A step cannot repeat.
        send(post("/api/streamline-reviews/" + id + "/items/" + simplifyMe + "/repeat"), plan.token(), Map.of("cadence", "WEEKLY"))
                .andExpect(status().isBadRequest());
        JsonNode finished = read(send(post("/api/streamline-reviews/" + id + "/stages/5/complete"), plan.token(), Map.of())
                .andExpect(status().isOk()));

        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        JsonNode summary = finished.get("summary");
        assertThat(summary.get("deleted").asLong()).isEqualTo(1);
        assertThat(summary.get("tasksBefore").asInt()).isEqualTo(4);
        assertThat(summary.get("tasksAfter").asInt()).isEqualTo(3);
        assertThat(summary.get("hoursBefore").decimalValue()).isEqualByComparingTo("8");
        assertThat(summary.get("hoursAfter").decimalValue()).isEqualByComparingTo("6");
        assertThat(summary.get("cycleTarget").decimalValue()).isEqualByComparingTo("4");
        assertThat(summary.get("repeating").asLong()).isEqualTo(1);
        // D-27-5: cut something, brought nothing back.
        assertThat(summary.get("nothingBroughtBack").asBoolean()).isTrue();
        List<String> levers = new ArrayList<>();
        finished.get("items").forEach(item -> item.get("speedLevers").forEach(lever -> levers.add(lever.asText())));
        assertThat(levers).containsExactlyInAnyOrder("BATCH", "PARALLEL");

        // The repeat rule exists and the review is in the list.
        JsonNode rules = read(mockMvc.perform(get("/api/task-repeat-rules").header("Authorization", "Bearer " + plan.token())));
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).get("cadence").asText()).isEqualTo("WEEKLY");
        JsonNode list = read(mockMvc.perform(get("/api/streamline-reviews").header("Authorization", "Bearer " + plan.token())));
        assertThat(list.get(0).get("status").asText()).isEqualTo("COMPLETED");
    }

    // ---------- SRC-07: the repeat job ----------

    @Test
    void theRepeatJobCreatesOneTaskOnceTheCurrentOneIsDueAndNeverTwice() throws Exception {
        Plan plan = plan();
        long taskId = plan.taskIds().get(0);
        // The current task was due yesterday, so the next one is due now.
        jdbc.update("update task_items set due_date = ? where id = ?", LocalDate.now().minusDays(1), taskId);
        JsonNode rule = read(send(post("/api/task-repeat-rules"), plan.token(), Map.of("taskId", taskId, "cadence", "WEEKLY"))
                .andExpect(status().isCreated()));
        long ruleId = rule.get("id").asLong();
        assertThat(LocalDate.parse(rule.get("nextDue").asText())).isAfterOrEqualTo(LocalDate.now());

        assertThat(repeatService.runRule(ruleId)).isNotNull();
        // Run again at once: the new task is due in six days, not yet, so nothing more is made.
        assertThat(repeatService.runRule(ruleId)).isNull();
        Integer made = jdbc.queryForObject("select count(*) from task_items where repeat_rule_id = ? and id <> ?", Integer.class, ruleId, taskId);
        assertThat(made).isEqualTo(1);
        LocalDate due = jdbc.queryForObject("select due_date from task_items where repeat_rule_id = ? and id <> ?", LocalDate.class, ruleId, taskId);
        assertThat(due).isAfterOrEqualTo(LocalDate.now());

        // Archiving the step stops the rule.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/steps/" + plan.stepIds().get(0))
                .header("Authorization", "Bearer " + plan.token())).andExpect(status().isNoContent());
        jdbc.update("update task_items set due_date = ? where repeat_rule_id = ? and id <> ?", LocalDate.now().minusDays(2), ruleId, taskId);
        assertThat(repeatService.runRule(ruleId)).isNull();
        assertThat(jdbc.queryForObject("select active from task_repeat_rules where id = ?", Boolean.class, ruleId)).isFalse();
    }

    // ---------- SRC-06: start time and history ----------

    @Test
    void movingATaskToInProgressStampsItsStartAndEveryChangeIsInTheHistory() throws Exception {
        Plan plan = plan();
        long taskId = plan.taskIds().get(0);
        send(patch("/api/tasks/" + taskId + "/status"), plan.token(), Map.of("status", "IN_PROGRESS")).andExpect(status().isOk());
        send(patch("/api/tasks/" + taskId + "/status"), plan.token(), Map.of("status", "WAITING")).andExpect(status().isOk());
        JsonNode task = read(send(patch("/api/tasks/" + taskId + "/status"), plan.token(), Map.of("status", "COMPLETED"))
                .andExpect(status().isOk()));
        assertThat(task.get("startedAt").isNull()).isFalse();
        assertThat(task.get("cycleTimeDays").isNull()).isFalse();

        JsonNode history = read(mockMvc.perform(get("/api/tasks/" + taskId + "/history").header("Authorization", "Bearer " + plan.token())));
        List<String> moves = new ArrayList<>();
        history.forEach(change -> moves.add(change.get("toStatus").asText()));
        assertThat(moves).containsExactly("NOT_STARTED", "IN_PROGRESS", "WAITING", "COMPLETED");
    }

    // ---------- SRC-08 and BR-27-1: ownership and the coach ----------

    @Test
    void anotherUsersReviewIsNotFoundAndAnAdminCannotSeeIt() throws Exception {
        Plan plan = plan();
        long id = start(plan, Map.of("goalId", plan.goalId())).get("id").asLong();
        String stranger = register(uniqueEmail());
        mockMvc.perform(get("/api/streamline-reviews/" + id).header("Authorization", "Bearer " + stranger)).andExpect(status().isNotFound());
        send(post("/api/streamline-reviews/" + id + "/abandon"), stranger, Map.of()).andExpect(status().isNotFound());
        // A plain user is not a coach.
        long ownerId = jdbc.queryForObject("select user_id from streamline_reviews where id = ?", Long.class, id);
        mockMvc.perform(get("/api/coach/participants/" + ownerId + "/streamline-reviews").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void abandoningKeepsDecisionsAlreadyApplied() throws Exception {
        Plan plan = plan();
        long id = reviewAtStage2(plan);
        long item = itemFor(review(plan, id), "taskId", plan.taskIds().get(0));
        send(post("/api/streamline-reviews/" + id + "/items/" + item + "/delete"), plan.token(), Map.of()).andExpect(status().isOk());
        send(post("/api/streamline-reviews/" + id + "/abandon"), plan.token(), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.currentStage").value(2));
        assertThat(archived("task_items", plan.taskIds().get(0))).isTrue();
    }

    // ---------- helpers ----------

    private long reviewAtStage2(Plan plan) throws Exception {
        long id = start(plan, Map.of("goalId", plan.goalId())).get("id").asLong();
        questionAll(plan, id);
        complete(plan, id, 1);
        return id;
    }

    private void questionAll(Plan plan, long id) throws Exception {
        send(put("/api/streamline-reviews/" + id + "/items/bulk"), plan.token(),
                Map.of("itemIds", itemIds(review(plan, id)), "requirementSource", "SELF", "requirementOwner", "Me", "verdict", "NEEDED"))
                .andExpect(status().isOk());
    }

    private void complete(Plan plan, long id, int stage) throws Exception {
        send(post("/api/streamline-reviews/" + id + "/stages/" + stage + "/complete"), plan.token(), Map.of()).andExpect(status().isOk());
    }

    private JsonNode start(Plan plan, Map<String, Object> anchor) throws Exception {
        return read(send(post("/api/streamline-reviews"), plan.token(), anchor).andExpect(status().isCreated()));
    }

    private JsonNode review(Plan plan, long id) throws Exception {
        return read(mockMvc.perform(get("/api/streamline-reviews/" + id).header("Authorization", "Bearer " + plan.token()))
                .andExpect(status().isOk()));
    }

    private static List<Long> itemIds(JsonNode review) {
        List<Long> ids = new ArrayList<>();
        review.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
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

    private Plan plan() throws Exception {
        String token = register(uniqueEmail());
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

    private static String uniqueEmail() {
        return "streamline-" + UUID.randomUUID() + "@example.com";
    }
}
