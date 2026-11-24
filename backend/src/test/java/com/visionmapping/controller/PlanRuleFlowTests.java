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
import java.time.LocalDate;
import java.util.ArrayList;
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

/** V31 MVP (BRD 31.0.0, section 5.2): the rules R-01 to R-08 on the draft check and on Save my plan. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanRuleFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate DREAM_DATE = TODAY.plusMonths(6);

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
        admin = register(email("rules-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'rules-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    /** AC-31-03, AC-31-06, AC-31-07, AC-31-22: MUST fails are listed with their rule, and the server refuses to save. */
    @Test
    void mustFailsAreListedByRuleAndBlockTheSave() throws Exception {
        String user = register(email("must"));
        long dreamId = dream(user);
        Map<String, Object> task = task("t1", "Call bank A", TODAY.plusDays(3));
        task.remove("priority");
        Map<String, Object> complex = step("s1", "Visit banks", TODAY.plusWeeks(2), List.of());
        complex.put("complex", true);
        Map<String, Object> withTask = step("s2", "Compare loans", TODAY.plusWeeks(3), List.of(task));
        Map<String, Object> goal = goal("g1", "Save a deposit", List.of(complex, withTask));
        goal.remove("successCriteria");
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of());

        // No AI consent: the check is the same rules, without AI (AC-31-22).
        JsonNode check = check(user, dreamId);
        assertThat(check.get("aiChecked").asBoolean()).isFalse();
        assertThat(issue(check, "GOAL_NO_MEASURE")).isNotNull();
        assertThat(issue(check, "GOAL_NO_MEASURE").get("rule").asText()).isEqualTo("R-02");
        assertThat(issue(check, "GOAL_NO_MEASURE").get("level").asText()).isEqualTo("MUST");
        assertThat(issue(check, "COMPLEX_NO_TASKS").get("rule").asText()).isEqualTo("R-03");
        JsonNode priority = issue(check, "TASK_NO_PRIORITY");
        assertThat(priority.get("rule").asText()).isEqualTo("R-03");
        assertThat(priority.get("fixAction").asText()).isEqualTo("SET_PRIORITY");
        assertThat(priority.get("fixValue").asText()).isEqualTo("MEDIUM");

        add(user, dreamId, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Fix this before saving: \"Save a deposit\": This goal does not say how you'll know it is done. (and 2 more)"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();
    }

    /** AC-31-08, AC-31-10, AC-31-11: date conflicts at every level are MUST fails, including dates typed by the participant. */
    @Test
    void dateConflictsAtEveryLevelBlockTheSave() throws Exception {
        String user = register(email("dates"));
        long dreamId = dream(user);
        Map<String, Object> late = task("t1", "Prepare reference table", TODAY.plusWeeks(3));
        Map<String, Object> step = step("s1", "Search literature", TODAY.plusWeeks(2), List.of(late));
        Map<String, Object> goal = goal("g1", "Prepare a concept note", List.of(step));
        goal.put("targetDate", DREAM_DATE.plusDays(10).toString());
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of());

        JsonNode check = check(user, dreamId);
        JsonNode taskConflict = issue(check, "TASK_AFTER_STEP");
        assertThat(taskConflict.get("rule").asText()).isEqualTo("R-04");
        assertThat(taskConflict.get("fixValue").asText()).isEqualTo(TODAY.plusWeeks(2).toString());
        JsonNode goalConflict = issue(check, "GOAL_AFTER_DREAM");
        assertThat(goalConflict.get("level").asText()).isEqualTo("MUST");
        assertThat(goalConflict.get("fixValue").asText()).isEqualTo(DREAM_DATE.toString());
        add(user, dreamId, null).andExpect(status().isBadRequest());

        // Fixed: saves.
        late.put("dueDate", TODAY.plusWeeks(2).toString());
        goal.put("targetDate", DREAM_DATE.toString());
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of());
        add(user, dreamId, null).andExpect(status().isCreated());
    }

    /** AC-31-13, AC-31-12: a Blocked task needs a reason; a risk must sit on a step. Both are saved as given once fixed. */
    @Test
    void blockersMustBeInTheRightPlace() throws Exception {
        String user = register(email("blockers"));
        long dreamId = dream(user);
        Map<String, Object> blocked = task("t1", "Search PubMed", TODAY.plusDays(5));
        blocked.put("status", "BLOCKED");
        Map<String, Object> goal = goal("g1", "Prepare a concept note", List.of(step("s1", "Search literature",
                TODAY.plusWeeks(2), List.of(blocked))));
        Map<String, Object> risk = Map.of("key", "r1", "title", "No library access", "obstacleType", "KNOWLEDGE",
                "severity", "HIGH", "linkKey", "g1");
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of(risk));

        JsonNode check = check(user, dreamId);
        assertThat(issue(check, "BLOCKED_NO_REASON").get("rule").asText()).isEqualTo("R-05");
        assertThat(issue(check, "RISK_NOT_ON_STEP").get("rule").asText()).isEqualTo("R-05");
        add(user, dreamId, null).andExpect(status().isBadRequest());

        blocked.put("blockerReason", "Waiting for a library card");
        Map<String, Object> placed = new HashMap<>(risk);
        placed.put("linkKey", "s1");
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of(placed));
        JsonNode added = read(add(user, dreamId, null).andExpect(status().isCreated()));
        long taskId = added.get("taskIds").get(0).asLong();
        Map<String, Object> saved = jdbcTemplate.queryForMap("select status, blocker_reason from task_items where id = ?", taskId);
        assertThat(saved.get("status")).isEqualTo("BLOCKED");
        assertThat(saved.get("blocker_reason")).isEqualTo("Waiting for a library card");
    }

    /** AC-31-05, AC-31-15, AC-31-16, AC-31-17: SHOULD fails warn but do not block; accepted ones are counted; the gap kind is saved. */
    @Test
    void shouldFailsWarnAndAreCountedWhenAccepted() throws Exception {
        String user = register(email("should"));
        long dreamId = dream(user);
        Map<String, Object> noKind = step("s1", "Draft methodology", TODAY.plusWeeks(2), List.of());
        noKind.put("resources", "I don't know how to write a methodology");
        Map<String, Object> knowledge = step("s2", "Run the analysis", TODAY.plusWeeks(3), List.of());
        knowledge.put("resources", "Statistics skills");
        knowledge.put("gapKind", "KNOWLEDGE");
        List<Map<String, Object>> goals = new ArrayList<>();
        goals.add(goal("g1", "Goal one", List.of(noKind, knowledge)));
        for (int index = 2; index <= 6; index++) {
            goals.add(goal("g" + index, "Goal " + index, List.of(step("s" + (index + 10), "Step " + index, TODAY.plusWeeks(2), List.of()))));
        }
        saveDraft(user, dreamId, goals, List.of(), List.of());

        JsonNode check = check(user, dreamId);
        assertThat(issue(check, "TOO_MANY_GOALS").get("level").asText()).isEqualTo("SHOULD");
        assertThat(issue(check, "GAP_NO_KIND").get("rule").asText()).isEqualTo("R-06");
        JsonNode helper = issue(check, "GAP_NO_HELPER");
        assertThat(helper.get("level").asText()).isEqualTo("SHOULD");
        assertThat(helper.get("itemKey").asText()).isEqualTo("s2");
        assertThat(issue(check, "MISSING_RESOURCE").get("level").asText()).isEqualTo("INFO");

        JsonNode added = read(add(user, dreamId, Map.of("acceptedShould", List.of("R-02", "R-06"))).andExpect(status().isCreated()));
        assertThat(added.get("goalIds")).hasSize(6);
        assertThat(jdbcTemplate.queryForObject("select gap_kind from vision_steps where title = 'Run the analysis' and user_id = "
                + "(select id from app_users where email = ?)", String.class, emailOf(user))).isEqualTo("KNOWLEDGE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from plan_check_events where dream_id = ? and at_save = true and accepted = true",
                Integer.class, dreamId)).isEqualTo(2);

        // Only R-01 to R-08 can be accepted.
        add(user, dream(user), Map.of("acceptedShould", List.of("R-99"))).andExpect(status().isBadRequest());
    }

    /** AC-31-19: an AI goal must be approved before saving; approving it clears the fail. */
    @Test
    void anAiGoalMustBeApproved() throws Exception {
        String user = consented(email("approve"));
        long dreamId = dream(user);
        AiDrafts.GoalDraft draft = new AiDrafts.GoalDraft("Use two AI research tools", "d", "One search with each tool",
                TODAY.plusMonths(2).toString(), "HIGH", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(draft), ""), "claude-opus-5-5", 10, 10, false));
        // The AI part of the check finds nothing; only the rules speak.
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DraftReview.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.DraftReview(List.of()), "claude-opus-5-5", 10, 10, false));
        long requestId = read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions")
                .header("Authorization", "Bearer " + user))).get("requestId").asLong();

        Map<String, Object> goal = goal("g1", "Use two AI research tools", List.of(step("s1", "Pick the tools", TODAY.plusWeeks(1), List.of())));
        goal.put("requestId", requestId);
        goal.put("approved", false);
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of());
        JsonNode check = check(user, dreamId);
        JsonNode approve = issue(check, "GOAL_NOT_APPROVED");
        assertThat(approve.get("rule").asText()).isEqualTo("R-07");
        assertThat(approve.get("fixAction").asText()).isEqualTo("APPROVE");
        add(user, dreamId, null).andExpect(status().isBadRequest());

        goal.put("approved", true);
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of());
        assertThat(issue(check(user, dreamId), "GOAL_NOT_APPROVED")).isNull();

        // DC-FR-04: the live list runs the rules alone and never asks Claude.
        reset(claudeClient);
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/draft-check?rulesOnly=true").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiChecked").value(false));
        verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DraftReview.class));
        add(user, dreamId, null).andExpect(status().isCreated());
    }

    /** AC-31-18: an AI owner that is not Me or a role is removed and counted; the step then needs an owner. */
    @Test
    void inventedOwnersAreRemovedAndCounted() throws Exception {
        String user = consented(email("owners"));
        long dreamId = dream(user);
        AiDrafts.StepDraft named = new AiDrafts.StepDraft("Ask for feedback", "d", "Dr. Sok", TODAY.plusWeeks(2).toString(), "r", "p", "r");
        AiDrafts.StepDraft role = new AiDrafts.StepDraft("Review the method", "d", "Statistics mentor", TODAY.plusWeeks(2).toString(),
                "r", "p", "r");
        AiDrafts.StepDraft blank = new AiDrafts.StepDraft("Write the draft", "d", "", TODAY.plusWeeks(2).toString(), "r", "p", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(named, role, blank), ""), "claude-opus-5-5",
                        10, 10, false));

        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions"), user,
                Map.of("title", "Prepare a concept note", "targetDate", TODAY.plusMonths(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].owner").isEmpty())
                .andExpect(jsonPath("$.steps[1].owner").value("Statistics mentor"))
                .andExpect(jsonPath("$.steps[2].owner").value("Me"));
        Map<String, Object> strip = jdbcTemplate.queryForMap(
                "select owners_seen, owners_stripped from ai_owner_strips where user_id = (select id from app_users where email = ?)",
                emailOf(user));
        assertThat(((Number) strip.get("owners_seen")).intValue()).isEqualTo(3);
        assertThat(((Number) strip.get("owners_stripped")).intValue()).isEqualTo(1);
    }

    /** AC-31-01, AC-31-02 (R-01): a step that is not under its goal is a MUST fail; another user's goal answers 404. */
    @Test
    void theHierarchyIsChecked() throws Exception {
        String user = register(email("tree"));
        long dreamId = dream(user);
        long goalA = savedGoal(user, dreamId, "Goal A");
        long goalB = savedGoal(user, dreamId, "Goal B");
        long stepOfB = savedStep(user, goalB);
        Map<String, Object> container = new HashMap<>(Map.of("key", "g1", "existingId", goalA, "title", "Goal A", "priority", "HIGH",
                "steps", List.of(Map.of("key", "s1", "existingId", stepOfB, "title", "Moved", "owner", "Me", "priority", "HIGH",
                        "complex", false, "tasks", List.of(task("t1", "New task", TODAY.plusDays(2)))))));
        container.put("edited", false);
        saveDraft(user, dreamId, List.of(container), List.of(), List.of());
        assertThat(issue(check(user, dreamId), "STEP_NOT_IN_GOAL").get("rule").asText()).isEqualTo("R-01");
        add(user, dreamId, null).andExpect(status().isBadRequest());
    }

    /** DC-FR-64, DC-FR-65: checks and saves are counted, and only admins can read the accuracy metrics. */
    @Test
    void checksAndSavesFeedTheAdminAccuracyMetrics() throws Exception {
        String user = register(email("metrics"));
        long dreamId = dream(user);
        Map<String, Object> goal = goal("g1", "Save a deposit", List.of(step("s1", "Open an account", TODAY.plusWeeks(1), List.of())));
        goal.remove("successCriteria");
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of());
        check(user, dreamId);
        goal.put("successCriteria", "Deposit saved");
        saveDraft(user, dreamId, List.of(goal), List.of(), List.of());
        check(user, dreamId);
        add(user, dreamId, null).andExpect(status().isCreated());

        String session = jdbcTemplate.queryForObject("select session_key from plan_builder_finished_plans where dream_id = ?",
                String.class, dreamId);
        assertThat(jdbcTemplate.queryForObject("select count(*) from plan_check_events where session_key = ? and first_run = true",
                Integer.class, session)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("select must_fails from plan_check_events where session_key = ? and first_run = true "
                + "and rule = 'R-02'", Integer.class, session)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from plan_check_events where session_key = ? and at_save = true",
                Integer.class, session)).isEqualTo(8);

        mockMvc.perform(get("/api/admin/ai/plan-accuracy?days=7").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.rules.length()").value(8))
                .andExpect(jsonPath("$.rules[1].rule").value("R-02"))
                .andExpect(jsonPath("$.checkedSessions").isNumber())
                .andExpect(jsonPath("$.savedPlans").isNumber());
        mockMvc.perform(get("/api/admin/ai/plan-accuracy").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private static JsonNode issue(JsonNode check, String kind) {
        for (JsonNode issue : check.get("issues")) {
            if (kind.equals(issue.get("kind").asText())) {
                return issue;
            }
        }
        return null;
    }

    private JsonNode check(String token, long dreamId) throws Exception {
        return read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/draft-check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private ResultActions add(String token, long dreamId, Object body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private void saveDraft(String token, long dreamId, List<Map<String, Object>> goals, List<Map<String, Object>> helpers,
                           List<Map<String, Object>> risks) throws Exception {
        send(put("/api/ai/plan/drafts/" + dreamId), token, Map.of("stage", "REVIEW",
                "content", Map.of("goals", goals, "helpers", helpers, "risks", risks))).andExpect(status().isOk());
    }

    private static Map<String, Object> goal(String key, String title, List<Map<String, Object>> steps) {
        Map<String, Object> goal = new HashMap<>(Map.of("key", key, "title", title, "priority", "HIGH",
                "targetDate", TODAY.plusMonths(4).toString(), "successCriteria", "Done when " + title.toLowerCase(), "steps", steps));
        goal.put("edited", false);
        return goal;
    }

    private static Map<String, Object> step(String key, String title, LocalDate date, List<Map<String, Object>> tasks) {
        return new HashMap<>(Map.of("key", key, "title", title, "owner", "Me", "targetDate", date.toString(), "priority", "MEDIUM",
                "complex", false, "tasks", tasks));
    }

    private static Map<String, Object> task(String key, String title, LocalDate date) {
        return new HashMap<>(Map.of("key", key, "title", title, "owner", "Me", "dueDate", date.toString(), "priority", "MEDIUM"));
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Become a researcher", "whyImportant", "Impact",
                "successDefinition", "Concept note accepted", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM",
                "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        body.put("targetDate", DREAM_DATE.toString());
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long savedGoal(String token, long dreamId, String title) throws Exception {
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", title, "description", "d",
                "successCriteria", "s", "priority", "HIGH", "targetDate", TODAY.plusMonths(4).toString(),
                "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP")).andExpect(status().isCreated())).get("id").asLong();
    }

    private long savedStep(String token, long goalId) throws Exception {
        return read(send(post("/api/steps"), token, Map.of("owner", "Me", "goalId", goalId, "title", "A step", "sequenceNumber", 1,
                "complex", false, "priority", "HIGH", "targetDate", TODAY.plusWeeks(3).toString(), "status", "NOT_STARTED"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private final Map<String, String> emails = new HashMap<>();

    private String emailOf(String token) {
        return emails.get(token);
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Rule Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(content).get("token").asText();
        emails.put(token, email);
        return token;
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
