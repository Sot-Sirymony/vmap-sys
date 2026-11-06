package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.ai.AiDrafts;
import com.visionmapping.service.ai.ClaudeClient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** V11 Phase 6 (PB-FR-16, PB-FR-28, PB-FR-29, PB-FR-39): draft check, fixes, capacity and the plan model. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanBuilderCheckFlowTests {

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

    private String admin;

    @BeforeEach
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        admin = register(email("check-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'check-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void theDraftCheckFindsRuleProblemsWithOneClickFixesEvenWithoutAi() throws Exception {
        String user = register(email("rules")); // no AI consent: rule checks only
        long dreamId = dream(user);
        LocalDate goalDate = TODAY.plusMonths(2);
        Map<String, Object> first = step("s1", "Gather papers", "", TODAY.plusWeeks(4), false, null, List.of());
        Map<String, Object> second = step("s2", "Visit banks", "Me", TODAY.plusWeeks(3), true, "s1", List.of());
        Map<String, Object> late = step("s3", "Sign", "Me", goalDate.plusWeeks(1), false, null, List.of(
                Map.of("key", "t1", "title", "Read contract", "owner", "Me", "dueDate", goalDate.plusWeeks(2).toString(), "priority", "LOW")));
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save a deposit", "priority", "HIGH",
                "targetDate", goalDate.toString(), "steps", List.of(first, second, late)));
        Map<String, Object> empty = new HashMap<>(Map.of("key", "g2", "title", "Fix credit", "priority", "HIGH", "steps", List.of()));
        saveDraft(user, dreamId, List.of(goal, empty));

        JsonNode check = read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/draft-check").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(check.get("aiChecked").asBoolean()).isFalse();
        assertThat(issue(check, "MISSING_OWNER", "s1").get("fixAction").asText()).isEqualTo("SET_OWNER_ME");
        JsonNode waits = issue(check, "BEFORE_WAITED_STEP", "s2");
        assertThat(waits.get("fixAction").asText()).isEqualTo("SET_DATE");
        assertThat(waits.get("fixValue").asText()).isEqualTo(TODAY.plusWeeks(4).toString());
        assertThat(issue(check, "COMPLEX_NO_TASKS", "s2").get("fixAction").asText()).isEqualTo("GO_TO_TASKS");
        assertThat(issue(check, "AFTER_GOAL_DATE", "s3").get("fixValue").asText()).isEqualTo(goalDate.toString());
        JsonNode taskLate = issue(check, "TASK_AFTER_STEP", "t1");
        assertThat(taskLate.get("parentKey").asText()).isEqualTo("s3");
        assertThat(taskLate.get("fixValue").asText()).isEqualTo(goalDate.plusWeeks(1).toString());
        assertThat(issue(check, "NO_STEPS", "g2").get("fixAction").asText()).isEqualTo("GO_TO_STEPS");
        verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DraftReview.class));
        // Checking saves nothing.
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();

        String other = consented(email("rules-other"));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/draft-check").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void claudeAddsOnlyRealIssuesAboutThisDraftAndProposesFixes() throws Exception {
        String user = consented(email("ai-check"));
        long dreamId = dream(user);
        Map<String, Object> vague = step("s1", "Do finance stuff", "Me", TODAY.plusWeeks(2), false, null, List.of());
        vague.put("resources", "Payslips");
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save a deposit", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(3).toString(), "steps", List.of(vague)));
        saveDraft(user, dreamId, List.of(goal));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DraftReview.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.DraftReview(List.of(
                        new AiDrafts.DraftIssueDraft("VAGUE_STEP", "s1", "Too vague to act on", "Name one action"),
                        new AiDrafts.DraftIssueDraft("GAP", "g1", "No step compares lenders", "Add a comparison step"),
                        new AiDrafts.DraftIssueDraft("GAP", "not-a-key", "x", "y"),
                        new AiDrafts.DraftIssueDraft("SPELLING", "s1", "x", "y"))), "claude-opus-5-5", 10, 10, false));

        JsonNode check = read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/draft-check").header("Authorization", "Bearer " + user)));
        assertThat(check.get("aiChecked").asBoolean()).isTrue();
        List<String> aiKinds = new ArrayList<>();
        check.get("issues").forEach(item -> {
            if (item.get("fromAi").asBoolean()) {
                aiKinds.add(item.get("kind").asText() + ":" + item.get("itemKey").asText() + ":" + item.get("fixAction").asText());
            }
        });
        assertThat(aiKinds).containsExactly("VAGUE_STEP:s1:FIX_WITH_AI", "GAP:g1:FIX_WITH_AI");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.DraftReview.class));
        assertThat(system.getValue()).contains("Step [s1] Do finance stuff");

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.FixDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.FixDraft("Compare three savings accounts",
                        TODAY.minusDays(1).toString(), "Compare lenders", "", TODAY.plusMonths(6).toString(), "Clearer"),
                        "claude-opus-5-5", 10, 10, false));
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-fix"), user, Map.of("itemKey", "s1", "kind", "VAGUE_STEP"))
                .andExpect(jsonPath("$.title").value("Compare three savings accounts"))
                .andExpect(jsonPath("$.targetDate").isEmpty())
                .andExpect(jsonPath("$.newStepTitle").isEmpty());
        // A gap fix proposes a new step under the goal, dated within the goal.
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-fix"), user, Map.of("itemKey", "s1", "kind", "GAP"))
                .andExpect(jsonPath("$.itemKey").value("g1"))
                .andExpect(jsonPath("$.newStepTitle").value("Compare lenders"))
                .andExpect(jsonPath("$.newStepOwner").value("Me"))
                .andExpect(jsonPath("$.newStepTargetDate").isEmpty());
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-fix"), user, Map.of("itemKey", "gone", "kind", "GAP"))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();
    }

    @Test
    void capacityShowsWhatTheSavedPlanAlreadyAsksOfEachWeek() throws Exception {
        String user = consented(email("capacity"));
        mockMvc.perform(get("/api/ai/plan/capacity").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.hoursPerWeek").isEmpty())
                .andExpect(jsonPath("$.planned.length()").value(0));

        long goalId = goal(user, dream(user));
        LocalDate due = TODAY.plusWeeks(2);
        long withTasks = savedStep(user, goalId, "With tasks", due, 10.0);
        savedStep(user, goalId, "Without tasks", due, 3.0);
        for (double hours : new double[] {1.5, 2.0}) {
            send(post("/api/tasks"), user, Map.of("stepId", withTasks, "title", "Task " + hours, "owner", "Me", "priority", "LOW",
                    "dueDate", due.toString(), "status", "NOT_STARTED", "progressPercent", 0, "estimatedHours", hours))
                    .andExpect(status().isCreated());
        }

        send(put("/api/ai/plan/capacity"), user, Map.of("hoursPerWeek", 5)).andExpect(status().isOk());
        JsonNode capacity = read(mockMvc.perform(get("/api/ai/plan/capacity").header("Authorization", "Bearer " + user)));
        assertThat(capacity.get("hoursPerWeek").asDouble()).isEqualTo(5.0);
        assertThat(capacity.get("planned")).hasSize(1);
        assertThat(capacity.get("planned").get(0).get("weekStart").asText()).isEqualTo(due.with(DayOfWeek.MONDAY).toString());
        // Tasks count; a step with tasks does not count again; a step without tasks does.
        assertThat(capacity.get("planned").get(0).get("hours").asDouble()).isEqualTo(6.5);
        send(put("/api/ai/plan/capacity"), user, Map.of("hoursPerWeek", 500)).andExpect(status().isBadRequest());

        String other = consented(email("capacity-other"));
        mockMvc.perform(get("/api/ai/plan/capacity").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.planned.length()").value(0));
    }

    @Test
    void planBuildingCanUseItsOwnModel() throws Exception {
        try {
            send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20,
                    "planModel", "claude-sonnet-5-5")).andExpect(jsonPath("$.planModel").value("claude-sonnet-5-5"));
            send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20,
                    "planModel", "gpt-9")).andExpect(status().isBadRequest());

            String user = consented(email("model"));
            long dreamId = dream(user);
            long goalId = goal(user, dreamId);
            when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                    .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(), "Why?"), "claude-sonnet-5-5", 1, 1, false));
            when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ResourceDrafts.class)))
                    .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ResourceDrafts(List.of()), "claude-opus-5-5", 1, 1, false));
            mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + user))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/ai/daily/goals/" + goalId + "/resource-ideas").header("Authorization", "Bearer " + user))
                    .andExpect(status().isOk());
            verify(claudeClient).structured(anyString(), eq("claude-sonnet-5-5"), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class));
            verify(claudeClient).structured(anyString(), eq("claude-opus-5-5"), anyString(), anyString(), eq(AiDrafts.ResourceDrafts.class));

            // Blank goes back to the main model for everything.
            send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20,
                    "planModel", "")).andExpect(jsonPath("$.planModel").isEmpty());
        } finally {
            jdbcTemplate.update("update ai_settings set plan_model = null");
        }
    }

    // ---------- helpers ----------

    private static JsonNode issue(JsonNode check, String kind, String key) {
        for (JsonNode item : check.get("issues")) {
            if (item.get("kind").asText().equals(kind) && item.get("itemKey").asText().equals(key)) {
                return item;
            }
        }
        throw new AssertionError("No " + kind + " issue for " + key + " in " + check);
    }

    private static Map<String, Object> step(String key, String title, String owner, LocalDate date, boolean complex, String after,
                                            List<Map<String, Object>> tasks) {
        Map<String, Object> step = new HashMap<>(Map.of("key", key, "title", title, "owner", owner, "targetDate", date.toString(),
                "priority", "MEDIUM", "complex", complex, "tasks", tasks));
        step.put("startsAfterKey", after);
        return step;
    }

    private void saveDraft(String token, long dreamId, List<Map<String, Object>> goals) throws Exception {
        send(put("/api/ai/plan/drafts/" + dreamId), token, Map.of("stage", "REVIEW", "content", Map.of("goals", goals)))
                .andExpect(status().isOk());
    }

    private long savedStep(String token, long goalId, String title, LocalDate date, double hours) throws Exception {
        return read(send(post("/api/steps"), token, Map.of("owner", "Me", "goalId", goalId, "title", title, "sequenceNumber", 1,
                "complex", false, "priority", "HIGH", "targetDate", date.toString(), "status", "NOT_STARTED", "estimatedHours", hours))
                .andExpect(status().isCreated())).get("id").asLong();
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
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Save a deposit", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(4).toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Check Tester", "email", email, "password", "Password123"))))
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
