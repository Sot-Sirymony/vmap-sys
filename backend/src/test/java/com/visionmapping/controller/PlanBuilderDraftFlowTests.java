package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.ai.AiDrafts;
import com.visionmapping.service.ai.ClaudeClient;
import com.visionmapping.service.ai.PlanBuilderDraftService;
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

/** V11 Phase 2 (PB-FR-30, PB-FR-31, PB-FR-33, PB-NFR-09): Build my plan drafts and adding them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanBuilderDraftFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlanBuilderDraftService draftService;

    @MockitoBean
    private ClaudeClient claudeClient;

    @BeforeEach
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        String admin = register(email("draft-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'draft-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void aDraftIsSavedResumedPrivateAndNeverPlanData() throws Exception {
        String user = consented(email("round"));
        long dreamId = dream(user);
        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());

        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("STEPS", List.of(newGoal("Save a deposit", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("STEPS"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.goals[0].title").value("Save a deposit"));

        // PB-FR-33: not plan data.
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();
        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + user)).andExpect(jsonPath("$.length()").value(0));

        // PB-NFR-09: another user can neither read, change nor discard it.
        String other = consented(email("round-other"));
        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        send(put("/api/ai/plan/drafts/" + dreamId), other, draft("GOALS", List.of())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
    }

    @Test
    void addSavesNewGoalsStepsAndTasksTogetherUnderNewAndExistingGoals() throws Exception {
        String user = consented(email("add"));
        long dreamId = dream(user);
        long existingGoal = goal(user, dreamId);
        step(user, existingGoal, "Already there");

        Map<String, Object> container = new HashMap<>(Map.of("key", "g0", "existingId", existingGoal,
                "steps", List.of(newStep("Visit banks", "Me", TODAY.plusWeeks(2), true,
                        List.of(newTask("Call bank A", "Me", TODAY.plusDays(5)))))));
        Map<String, Object> fresh = newGoal("Fix my credit", List.of(newStep("Get the report", "Me", TODAY.plusWeeks(1), false,
                List.of(newTask("Ask for the report", "Me", TODAY.plusDays(3))))));
        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("REVIEW", List.of(container, fresh))).andExpect(status().isOk());

        JsonNode added = read(mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isCreated()));
        assertThat(added.get("goalIds")).hasSize(1);
        assertThat(added.get("stepIds")).hasSize(2);
        assertThat(added.get("taskIds")).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ? and archived = false", Integer.class, dreamId))
                .isEqualTo(2);
        // A new step under an existing goal comes after its steps.
        assertThat(jdbcTemplate.queryForObject("select sequence_number from vision_steps where title = 'Visit banks' and goal_id = ?",
                Integer.class, existingGoal)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select is_complex from vision_steps where title = 'Visit banks' and goal_id = ?",
                Boolean.class, existingGoal)).isTrue();
        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());

        // Undo archives tasks too.
        List<Long> taskIds = List.of(added.get("taskIds").get(0).asLong(), added.get("taskIds").get(1).asLong());
        List<Long> stepIds = List.of(added.get("stepIds").get(0).asLong(), added.get("stepIds").get(1).asLong());
        send(post("/api/ai/plan/undo"), user, Map.of("goalIds", List.of(added.get("goalIds").get(0).asLong()),
                "stepIds", stepIds, "taskIds", taskIds)).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from task_items where archived = false and step_id in (?, ?)",
                Integer.class, stepIds.get(0), stepIds.get(1))).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from vision_steps where goal_id = ? and archived = false",
                Integer.class, existingGoal)).isEqualTo(1);
    }

    @Test
    void oneBadItemSavesNothingAndKeepsTheDraft() throws Exception {
        String user = consented(email("bad"));
        long dreamId = dream(user);
        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("REVIEW", List.of(newGoal("Good goal",
                List.of(newStep("No owner yet", " ", TODAY.plusWeeks(1), false, List.of())))))).andExpect(status().isOk());

        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Step \"No owner yet\" needs an owner."));
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();
        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user)).andExpect(status().isOk());

        // Nothing new at all.
        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("REVIEW", List.of())).andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDraftCannotPointAtAnotherUsersGoal() throws Exception {
        String user = consented(email("foreign"));
        long dreamId = dream(user);
        String other = consented(email("foreign-other"));
        long otherGoal = goal(other, dream(other));
        Map<String, Object> container = new HashMap<>(Map.of("key", "g0", "existingId", otherGoal,
                "steps", List.of(newStep("Sneaky", "Me", TODAY.plusWeeks(1), false, List.of()))));
        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("REVIEW", List.of(newGoal("Mine", List.of()), container)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from vision_steps where goal_id = ?", Integer.class, otherGoal)).isZero();
    }

    @Test
    void suggestionsAddedThroughADraftAreCounted() throws Exception {
        String user = consented(email("outcomes"));
        long dreamId = dream(user);
        AiDrafts.GoalDraft one = new AiDrafts.GoalDraft("Goal one", "d", "s", TODAY.plusMonths(2).toString(), "HIGH", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(one, one, one), ""), "claude-opus-5-5", 10, 10, false));
        long requestId = read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions")
                .header("Authorization", "Bearer " + user))).get("requestId").asLong();

        Map<String, Object> kept = newGoal("Goal one", List.of());
        kept.put("requestId", requestId);
        Map<String, Object> changed = newGoal("Goal one, changed", List.of());
        changed.put("requestId", requestId);
        changed.put("edited", true);
        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("REVIEW", List.of(kept, changed, newGoal("My own", List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("select added_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select edited_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(1);
    }

    @Test
    void expiredDraftsAreGoneAndCleanedUp() throws Exception {
        String user = consented(email("expired"));
        long dreamId = dream(user);
        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("GOALS", List.of(newGoal("Old", List.of())))).andExpect(status().isOk());
        jdbcTemplate.update("update plan_builder_drafts set expires_at = ? where dream_id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), dreamId);

        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        draftService.deleteExpired();
        assertThat(jdbcTemplate.queryForObject("select count(*) from plan_builder_drafts where dream_id = ?", Integer.class, dreamId))
                .isZero();
    }

    @Test
    void anArchivedDreamGetsNoNewDraft() throws Exception {
        String user = consented(email("archived"));
        long dreamId = dream(user);
        jdbcTemplate.update("update dreams set archived = true where id = ?", dreamId);
        send(put("/api/ai/plan/drafts/" + dreamId), user, draft("GOALS", List.of())).andExpect(status().isBadRequest());
    }

    @Test
    void stepsCanBeSuggestedForAGoalThatIsOnlyInTheDraft() throws Exception {
        String user = consented(email("draft-steps"));
        long dreamId = dream(user);
        AiDrafts.StepDraft soon = new AiDrafts.StepDraft("Soon", "d", "", TODAY.plusWeeks(2).toString(), "r", "p", "r");
        AiDrafts.StepDraft late = new AiDrafts.StepDraft("Too late", "d", "Me", TODAY.plusMonths(3).toString(), "r", "p", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(soon, late), ""), "claude-opus-5-5", 10, 10, false));

        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions"), user,
                Map.of("title", "Save a deposit", "targetDate", TODAY.plusMonths(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].owner").value("Me"))
                .andExpect(jsonPath("$.steps[0].targetDate").value(TODAY.plusWeeks(2).toString()))
                .andExpect(jsonPath("$.steps[1].targetDate").isEmpty())
                .andExpect(jsonPath("$.requestId").isNumber());

        String other = consented(email("draft-steps-other"));
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions"), other, Map.of("title", "x"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private static Map<String, Object> draft(String stage, List<Map<String, Object>> goals) {
        return Map.of("stage", stage, "content", Map.of("goals", goals));
    }

    private static Map<String, Object> newGoal(String title, List<Map<String, Object>> steps) {
        Map<String, Object> goal = new HashMap<>(Map.of("key", "k" + System.nanoTime(), "title", title, "priority", "HIGH",
                "targetDate", TODAY.plusMonths(4).toString(), "steps", steps));
        goal.put("edited", false);
        return goal;
    }

    private static Map<String, Object> newStep(String title, String owner, LocalDate date, boolean complex,
                                               List<Map<String, Object>> tasks) {
        return new HashMap<>(Map.of("key", "s" + System.nanoTime(), "title", title, "owner", owner,
                "targetDate", date.toString(), "priority", "MEDIUM", "complex", complex, "tasks", tasks));
    }

    private static Map<String, Object> newTask(String title, String owner, LocalDate date) {
        return Map.of("key", "t" + System.nanoTime(), "title", title, "owner", owner, "dueDate", date.toString(),
                "priority", "MEDIUM", "estimatedHours", 1.5);
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Home", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Own a home", "whyImportant", "Family",
                "successDefinition", "Keys in hand", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM",
                "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long goal(String token, long dreamId) throws Exception {
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Save a deposit", "description", "d",
                "successCriteria", "s", "priority", "HIGH", "targetDate", TODAY.plusMonths(4).toString(),
                "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP")).andExpect(status().isCreated())).get("id").asLong();
    }

    private void step(String token, long goalId, String title) throws Exception {
        send(post("/api/steps"), token, Map.of("owner", "Me", "goalId", goalId, "title", title, "sequenceNumber", 1,
                "complex", false, "priority", "HIGH", "targetDate", TODAY.plusWeeks(3).toString(), "status", "NOT_STARTED"))
                .andExpect(status().isCreated());
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Draft Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
