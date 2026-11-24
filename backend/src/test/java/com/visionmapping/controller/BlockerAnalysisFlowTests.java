package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.ai.AiDrafts;
import com.visionmapping.service.ai.ClaudeClient;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Vision Map plan assistant: the blocker analysis ranks what holds a dream back, with or without AI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockerAnalysisFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ClaudeClient claudeClient;

    @BeforeEach
    void aiOff() {
        reset(claudeClient);
        jdbcTemplate.update("update ai_settings set enabled = false");
    }

    @Test
    void theRulesRankBlockedWorkAndCountWhatItHoldsUpWithoutAi() throws Exception {
        String user = register(email("blockers-rules"));
        long dreamId = dream(user);
        long goalId = goal(user, dreamId);
        long first = step(user, goalId, "Get the data", 1);
        step(user, goalId, "Analyse the data", 2);
        step(user, goalId, "Write it up", 3);
        long blocked = task(user, first, Map.of("title", "Ask for the dataset", "status", "BLOCKED",
                "blockerReason", "No reply from the data office", "priority", "CRITICAL"));
        long waiting = task(user, first, Map.of("title", "Wait for ethics approval", "status", "WAITING", "priority", "LOW"));
        task(user, first, Map.of("title", "Done already", "status", "COMPLETED", "progressPercent", 100));

        JsonNode result = analyse(user, dreamId);

        assertThat(result.get("aiChecked").asBoolean()).isFalse();
        assertThat(result.get("blockedCount").asInt()).isEqualTo(1);
        assertThat(result.get("waitingCount").asInt()).isEqualTo(1);
        assertThat(result.get("heldUpSteps").asInt()).isEqualTo(2);
        JsonNode blockers = result.get("blockers");
        assertThat(blockers).hasSize(2);
        // The critical Blocked task comes before the low-priority Waiting one.
        JsonNode top = blockers.get(0);
        assertThat(top.get("itemId").asLong()).isEqualTo(blocked);
        assertThat(top.get("itemType").asText()).isEqualTo("TASK");
        assertThat(top.get("kind").asText()).isEqualTo("BLOCKED");
        assertThat(top.get("reason").asText()).isEqualTo("No reply from the data office");
        assertThat(top.get("heldUpSteps").asInt()).isEqualTo(2);
        assertThat(top.get("stepId").asLong()).isEqualTo(first);
        assertThat(top.get("hasHelper").asBoolean()).isFalse();
        assertThat(top.get("rootCause").isNull()).isTrue();
        assertThat(blockers.get(1).get("itemId").asLong()).isEqualTo(waiting);
    }

    @Test
    void aDreamWithNothingStuckSaysSo() throws Exception {
        String user = register(email("blockers-none"));
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Plan it", 1);
        task(user, stepId, Map.of("title", "Moving", "status", "IN_PROGRESS", "progressPercent", 20));

        JsonNode result = analyse(user, dreamId);
        assertThat(result.get("blockers")).isEmpty();
        assertThat(result.get("blockedCount").asInt()).isZero();
    }

    @Test
    void aiAddsARootCauseAndFirstMoveOnlyForBlockersInTheList() throws Exception {
        aiOnFor();
        String user = register(email("blockers-ai"));
        send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Get the data", 1);
        long blocked = task(user, stepId, Map.of("title", "Ask for the dataset", "status", "BLOCKED",
                "blockerReason", "No reply"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.BlockerReview.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.BlockerReview("Most blockers wait on other people.", List.of(
                        new AiDrafts.BlockerDraft("task-" + blocked, "PARTNER", "The request went to a general inbox.",
                                "Phone the data office and ask who owns the dataset.", "Data officer"),
                        new AiDrafts.BlockerDraft("TASK-999999", "MONEY", "Not in this plan", "x", "x")), List.of()),
                        "claude-opus-5-5", 10, 10, false));

        JsonNode result = analyse(user, dreamId);

        assertThat(result.get("aiChecked").asBoolean()).isTrue();
        assertThat(result.get("requestId").isNull()).isFalse();
        assertThat(result.get("pattern").asText()).isEqualTo("Most blockers wait on other people.");
        JsonNode top = result.get("blockers").get(0);
        assertThat(top.get("rootCause").asText()).isEqualTo("The request went to a general inbox.");
        assertThat(top.get("firstMove").asText()).startsWith("Phone the data office");
        assertThat(top.get("helper").asText()).isEqualTo("Data officer");
        assertThat(top.get("obstacleType").asText()).isEqualTo("PARTNER");
        assertThat(top.get("obstacleTypeGuessed").asBoolean()).isTrue();
        assertThat(result.toString()).doesNotContain("Not in this plan");
    }

    @Test
    void markingATaskBlockedSavesTheReasonAndOneObstacle() throws Exception {
        String user = register(email("mark-task"));
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Fund the study", 1);
        long taskId = task(user, stepId, Map.of("title", "Pay for the survey tool", "status", "IN_PROGRESS", "progressPercent", 10));

        JsonNode marked = read(send(post("/api/blocks"), user, Map.of("itemType", "TASK", "itemId", taskId,
                "obstacleType", "MONEY", "reason", "No budget approved yet")).andExpect(status().isOk()));
        assertThat(marked.get("obstacleCreated").asBoolean()).isTrue();
        long obstacleId = marked.get("obstacleId").asLong();

        JsonNode task = read(mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + user)));
        assertThat(task.get("status").asText()).isEqualTo("BLOCKED");
        assertThat(task.get("blockerReason").asText()).isEqualTo("No budget approved yet");
        assertThat(task.get("blockedCount").asInt()).isEqualTo(1);
        JsonNode obstacle = read(mockMvc.perform(get("/api/obstacles/" + obstacleId).header("Authorization", "Bearer " + user)));
        assertThat(obstacle.get("relatedTaskId").asLong()).isEqualTo(taskId);
        assertThat(obstacle.get("obstacleType").asText()).isEqualTo("MONEY");
        assertThat(obstacle.get("status").asText()).isEqualTo("OPEN");

        // Marked again: the open obstacle is reused, not duplicated (BR-28-9).
        JsonNode again = read(send(post("/api/blocks"), user, Map.of("itemType", "TASK", "itemId", taskId,
                "obstacleType", "MONEY", "reason", "Still no budget")).andExpect(status().isOk()));
        assertThat(again.get("obstacleCreated").asBoolean()).isFalse();
        assertThat(again.get("obstacleId").asLong()).isEqualTo(obstacleId);

        JsonNode top = analyse(user, dreamId).get("blockers").get(0);
        assertThat(top.get("itemId").asLong()).isEqualTo(taskId);
        assertThat(top.get("obstacleType").asText()).isEqualTo("MONEY");
        assertThat(top.get("obstacleId").asLong()).isEqualTo(obstacleId);
    }

    @Test
    void markingAStepBlockedSetsItsGapKindAndRecordsAnObstacle() throws Exception {
        String user = register(email("mark-step"));
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Choose the method", 1);
        task(user, stepId, Map.of("title", "Read about methods"));

        JsonNode marked = read(send(post("/api/blocks"), user, Map.of("itemType", "STEP", "itemId", stepId,
                "obstacleType", "KNOWLEDGE", "reason", "I do not know which statistical method fits", "severity", "HIGH"))
                .andExpect(status().isOk()));

        JsonNode step = read(mockMvc.perform(get("/api/steps/" + stepId).header("Authorization", "Bearer " + user)));
        assertThat(step.get("status").asText()).isEqualTo("BLOCKED");
        assertThat(step.get("gapKind").asText()).isEqualTo("KNOWLEDGE");
        JsonNode obstacle = read(mockMvc.perform(get("/api/obstacles/" + marked.get("obstacleId").asLong())
                .header("Authorization", "Bearer " + user)));
        assertThat(obstacle.get("relatedStepId").asLong()).isEqualTo(stepId);
        assertThat(obstacle.get("relatedTaskId").isNull()).isTrue();
        assertThat(obstacle.get("severity").asText()).isEqualTo("HIGH");
    }

    @Test
    void markingNeedsAReasonAndTheUsersOwnOpenItem() throws Exception {
        String user = register(email("mark-rules"));
        long stepId = step(user, goal(user, dream(user)), "Plan it", 1);
        long taskId = task(user, stepId, Map.of("title", "Draft it"));
        send(post("/api/blocks"), user, Map.of("itemType", "TASK", "itemId", taskId, "obstacleType", "TIME", "reason", " "))
                .andExpect(status().isBadRequest());
        long done = task(user, stepId, Map.of("title", "Finished", "status", "COMPLETED", "progressPercent", 100));
        send(post("/api/blocks"), user, Map.of("itemType", "TASK", "itemId", done, "obstacleType", "TIME", "reason", "Too busy"))
                .andExpect(status().isBadRequest());
        String other = register(email("mark-other"));
        send(post("/api/blocks"), other, Map.of("itemType", "TASK", "itemId", taskId, "obstacleType", "TIME", "reason", "Mine now"))
                .andExpect(status().isNotFound());
    }

    @Test
    void possiblyBlockedListsStuckWorkThatIsNotMarked() throws Exception {
        String user = register(email("possibly"));
        long dreamId = dream(user);
        long goalId = goal(user, dreamId);
        long first = step(user, goalId, "Collect the data", 1);
        long overdue = task(user, first, Map.of("title", "Send the survey"));
        long stalled = task(user, first, Map.of("title", "Clean the data", "status", "IN_PROGRESS", "progressPercent", 20));
        long fresh = task(user, first, Map.of("title", "Book a room"));
        long missing = step(user, goalId, "Run the workshop", 2);
        send(put("/api/steps/" + missing), user, stepBody(goalId, "Run the workshop", 2, "Need a sponsor for the venue"))
                .andExpect(status().isOk());
        java.sql.Timestamp tenDaysAgo = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(10)));
        java.sql.Timestamp monthAgo = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(30)));
        jdbcTemplate.update("update task_items set due_date = ?, updated_at = ? where id = ?",
                java.sql.Date.valueOf(TODAY.minusDays(9)), tenDaysAgo, overdue);
        jdbcTemplate.update("update task_items set started_at = ? where id = ?", monthAgo, stalled);

        JsonNode candidates = analyse(user, dreamId).get("candidates");
        Map<String, String> signals = new HashMap<>();
        candidates.forEach(candidate -> signals.put(candidate.get("itemType").asText() + "-" + candidate.get("itemId").asLong(),
                candidate.get("signal").asText()));
        assertThat(signals).containsEntry("TASK-" + overdue, "OVERDUE_NO_CHANGE").containsEntry("TASK-" + stalled, "STALLED")
                .containsEntry("STEP-" + missing, "SOMETHING_MISSING").doesNotContainKey("TASK-" + fresh);
        // Strongest signal first.
        assertThat(candidates.get(0).get("itemId").asLong()).isEqualTo(overdue);
        assertThat(candidates.get(0).get("daysOverdue").asInt()).isEqualTo(9);
        assertThat(candidates.get(0).get("fromAi").asBoolean()).isFalse();

        // Nothing was marked by the analysis (BR-28-10).
        JsonNode task = read(mockMvc.perform(get("/api/tasks/" + overdue).header("Authorization", "Bearer " + user)));
        assertThat(task.get("status").asText()).isEqualTo("NOT_STARTED");
    }

    @Test
    void aiAddsHiddenBlockersOnlyForOpenItemsInTheDream() throws Exception {
        aiOnFor();
        String user = register(email("hidden-ai"));
        send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Get approval", 1);
        long waitingOnBoss = task(user, stepId, Map.of("title", "Get the director to sign"));
        long done = task(user, stepId, Map.of("title", "Finished", "status", "COMPLETED", "progressPercent", 100));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.BlockerReview.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.BlockerReview("", List.of(), List.of(
                        new AiDrafts.HiddenBlockerDraft("TASK-" + waitingOnBoss, "DECISION", "Waiting on the director's decision."),
                        new AiDrafts.HiddenBlockerDraft("TASK-" + done, "TIME", "Completed work is not open"),
                        new AiDrafts.HiddenBlockerDraft("TASK-999999", "MONEY", "Not in this plan"))),
                        "claude-opus-5-5", 10, 10, false));

        JsonNode result = analyse(user, dreamId);
        assertThat(result.get("aiChecked").asBoolean()).isTrue();
        JsonNode candidates = result.get("candidates");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).get("itemId").asLong()).isEqualTo(waitingOnBoss);
        assertThat(candidates.get(0).get("signal").asText()).isEqualTo("AI");
        assertThat(candidates.get(0).get("suggestedType").asText()).isEqualTo("DECISION");
        assertThat(candidates.get(0).get("suggestedReason").asText()).isEqualTo("Waiting on the director's decision.");
        assertThat(result.toString()).doesNotContain("Not in this plan").doesNotContain("Completed work is not open");
    }

    @Test
    void aiReadsMarkedStepsAndRuleCandidatesTogether() throws Exception {
        aiOnFor();
        String user = register(email("ai-both"));
        send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
        long dreamId = dream(user);
        long goalId = goal(user, dreamId);
        long blockedStep = step(user, goalId, "Choose the method", 1);
        step(user, goalId, "Analyse the data", 2);
        send(post("/api/blocks"), user, Map.of("itemType", "STEP", "itemId", blockedStep, "obstacleType", "KNOWLEDGE",
                "reason", "I do not know which method fits")).andExpect(status().isOk());
        long dataStep = step(user, goalId, "Collect the data", 3);
        long overdue = task(user, dataStep, Map.of("title", "Send the survey"));
        java.sql.Timestamp tenDaysAgo = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(10)));
        jdbcTemplate.update("update task_items set due_date = ?, updated_at = ? where id = ?",
                java.sql.Date.valueOf(TODAY.minusDays(9)), tenDaysAgo, overdue);
        org.mockito.ArgumentCaptor<String> records = org.mockito.ArgumentCaptor.forClass(String.class);
        when(claudeClient.structured(anyString(), anyString(), records.capture(), anyString(), eq(AiDrafts.BlockerReview.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.BlockerReview("", List.of(
                        new AiDrafts.BlockerDraft("STEP-" + blockedStep, "KNOWLEDGE", "The method was never chosen.",
                                "Ask a statistician for 30 minutes.", "Statistician")), List.of(
                        new AiDrafts.HiddenBlockerDraft("TASK-" + overdue, "PARTNER", "No list of people to survey yet."))),
                        "claude-opus-5-5", 10, 10, false));

        JsonNode result = analyse(user, dreamId);

        JsonNode step = result.get("blockers").get(0);
        assertThat(step.get("itemType").asText()).isEqualTo("STEP");
        assertThat(step.get("kind").asText()).isEqualTo("BLOCKED");
        assertThat(step.get("reason").asText()).isEqualTo("I do not know which method fits");
        assertThat(step.get("obstacleType").asText()).isEqualTo("KNOWLEDGE");
        assertThat(step.get("obstacleTypeGuessed").asBoolean()).isFalse();
        assertThat(step.get("heldUpSteps").asInt()).isEqualTo(2);
        assertThat(step.get("firstMove").asText()).isEqualTo("Ask a statistician for 30 minutes.");
        JsonNode candidate = result.get("candidates").get(0);
        assertThat(candidate.get("itemId").asLong()).isEqualTo(overdue);
        assertThat(candidate.get("signal").asText()).isEqualTo("OVERDUE_NO_CHANGE");
        assertThat(candidate.get("fromAi").asBoolean()).isTrue();
        assertThat(candidate.get("suggestedType").asText()).isEqualTo("PARTNER");
        // Claude saw the marked step and the rule candidate, with their facts.
        assertThat(records.getValue()).contains("[STEP-" + blockedStep + "] BLOCKED step").contains("missing: KNOWLEDGE")
                .contains("holds up 2 later step(s)").contains("[TASK-" + overdue + "] OVERDUE_NO_CHANGE task")
                .contains("overdue 9 days").contains("no change for 10 days");
    }

    @Test
    void aStepThatKeepsSlippingIsPossiblyBlocked() throws Exception {
        String user = register(email("slipping"));
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Book the venue", 1);
        jdbcTemplate.update("update vision_steps set overdue_reschedules = 2, target_date = ? where id = ?",
                java.sql.Date.valueOf(TODAY.minusDays(4)), stepId);

        JsonNode candidate = analyse(user, dreamId).get("candidates").get(0);
        assertThat(candidate.get("itemType").asText()).isEqualTo("STEP");
        assertThat(candidate.get("signal").asText()).isEqualTo("KEEPS_SLIPPING");
        assertThat(candidate.get("reschedules").asInt()).isEqualTo(2);
        assertThat(candidate.get("daysOverdue").asInt()).isEqualTo(4);
    }

    @Test
    void whenTheAiCallFailsTheRuleHalfStillAnswers() throws Exception {
        aiOnFor();
        String user = register(email("ai-fails"));
        send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Get the data", 1);
        long blocked = task(user, stepId, Map.of("title", "Ask for the dataset", "status", "BLOCKED", "blockerReason", "No reply"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.BlockerReview.class)))
                .thenThrow(new com.visionmapping.exception.AiUnavailableException());

        JsonNode result = analyse(user, dreamId);
        assertThat(result.get("aiChecked").asBoolean()).isFalse();
        assertThat(result.get("blockers").get(0).get("itemId").asLong()).isEqualTo(blocked);
    }

    @Test
    void aCompletedStepCannotBeMarkedBlocked() throws Exception {
        String user = register(email("mark-done-step"));
        long stepId = step(user, goal(user, dream(user)), "Done already", 1);
        jdbcTemplate.update("update vision_steps set status = 'COMPLETED' where id = ?", stepId);
        send(post("/api/blocks"), user, Map.of("itemType", "STEP", "itemId", stepId, "obstacleType", "TIME", "reason", "Too busy"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherUsersDreamIsNotFound() throws Exception {
        String owner = register(email("blockers-owner"));
        long dreamId = dream(owner);
        String other = register(email("blockers-other"));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/blocker-analysis").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    private void aiOnFor() throws Exception {
        String admin = register(email("blockers-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'blockers-admin-%' and role = 'USER'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 20");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    private JsonNode analyse(String token, long dreamId) throws Exception {
        return read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/blocker-analysis")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Research", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Publish a paper", "whyImportant", "Career",
                "successDefinition", "Accepted paper", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "HIGH",
                "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long goal(String token, long dreamId) throws Exception {
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Finish the study", "description", "d",
                "successCriteria", "s", "priority", "HIGH", "targetDate", TODAY.plusMonths(4).toString(),
                "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP")).andExpect(status().isCreated())).get("id").asLong();
    }

    private long step(String token, long goalId, String title, int sequence) throws Exception {
        return read(send(post("/api/steps"), token, stepBody(goalId, title, sequence, "Laptop")).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private static Map<String, Object> stepBody(long goalId, String title, int sequence, String resources) {
        Map<String, Object> body = new HashMap<>(Map.of("owner", "Me", "goalId", goalId, "title", title, "description", "d",
                "sequenceNumber", sequence, "complex", false, "priority", "HIGH",
                "targetDate", TODAY.plusMonths(2).toString(), "status", "IN_PROGRESS"));
        body.put("resources", resources);
        return body;
    }

    private long task(String token, long stepId, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", stepId, "title", "Task", "owner", "Me", "priority", "HIGH",
                "dueDate", TODAY.plusMonths(1).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        return read(send(post("/api/tasks"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Blocker Tester", "email", email,
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
