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

/** V9 Phase 5 (AI-FR-07 to AI-FR-11): suggestions only, checked before they are shown, never saved by the AI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiPlanFlowTests {

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
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        String admin = register(email("plan-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where full_name = 'Plan Tester' and role = 'USER' and email like 'plan-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 20");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void goalSuggestionsAreCheckedAndNothingIsSaved() throws Exception {
        String user = consented(email("goals"));
        long dreamId = dream(user);
        List<AiDrafts.GoalDraft> drafts = new java.util.ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            drafts.add(new AiDrafts.GoalDraft("Goal " + index, "d", "s", TODAY.plusMonths(index).toString(), "HIGH", "r"));
        }
        drafts.set(1, new AiDrafts.GoalDraft("Past date", "d", "s", TODAY.minusDays(1).toString(), "SOMETIMES", "r"));
        drafts.set(2, new AiDrafts.GoalDraft(" ", "d", "s", "not a date", "LOW", "r"));
        when(claudeClient.structured(eq(KEY), eq("claude-opus-5-5"), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(drafts, ""), "claude-opus-5-5", 1000, 500, false));

        JsonNode answer = read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions?language=km")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));

        assertThat(answer.get("goals")).hasSize(5);
        assertThat(answer.get("goals").get(1).get("title").asText()).isEqualTo("Past date");
        assertThat(answer.get("goals").get(1).get("targetDate").isNull()).isTrue();
        assertThat(answer.get("goals").get(1).get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(answer.get("clarifyingQuestion").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select feature from ai_usage_logs order by id desc limit 1", String.class))
                .isEqualTo("GOAL_BUILDER");

        String other = consented(email("goals-other"));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void aVagueDreamGetsOneQuestionAndAStretchGoalIsOneMoonshot() throws Exception {
        String user = consented(email("vague"));
        long dreamId = dream(user);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(), "What kind of home, and where?"),
                        "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.goals.length()").value(0))
                .andExpect(jsonPath("$.clarifyingQuestion").value("What kind of home, and where?"));

        AiDrafts.GoalDraft bold = new AiDrafts.GoalDraft("Own two homes", "d", "s", TODAY.plusYears(3).toString(), "HIGH", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(bold, bold), ""), "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions?stretch=true").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.goals.length()").value(1))
                .andExpect(jsonPath("$.goals[0].moonshot").value(true));
    }

    @Test
    void stepSuggestionsKnowTheGoalButNeverPartnerContacts() throws Exception {
        String user = consented(email("steps"));
        long goalId = goal(user, dream(user));
        send(post("/api/partners"), user, Map.of("name", "Hidden Mentor", "role", "Mentor", "supportType", "MENTOR",
                "status", "ACTIVE", "email", "hidden@example.com")).andExpect(status().isCreated());
        AiDrafts.StepDraft draft = new AiDrafts.StepDraft("Visit three banks", "Compare loans", "", TODAY.plusWeeks(2).toString(),
                "Payslips", "A loan officer", "Know the budget");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(draft), ""), "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(post("/api/ai/plan/goals/" + goalId + "/step-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.steps[0].owner").value("Me"))
                .andExpect(jsonPath("$.steps[0].resources").value("Payslips"))
                .andExpect(jsonPath("$.steps[0].people").value("A loan officer"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.StepDrafts.class));
        assertThat(system.getValue()).contains("Save a deposit").contains("<participant_data>")
                .doesNotContain("Hidden Mentor").doesNotContain("hidden@example.com");
    }

    @Test
    void thePlanCheckAlwaysFlagsMissingResourcesAndKeepsOnlyRealAiIssues() throws Exception {
        String user = consented(email("check"));
        long dreamId = dream(user);
        long goalId = goal(user, dreamId);
        long stepId = step(user, goalId, "Talk to someone", TODAY.plusMonths(5), null);
        step(user, goalId, "Open savings account", TODAY.plusWeeks(1), "Passport and $50");
        goal(user, dreamId);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PlanReview.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.PlanReview(List.of(
                        new AiDrafts.IssueDraft("VAGUE_STEP", "STEP", stepId, "Who, and about what?", "Name the person and the question."),
                        new AiDrafts.IssueDraft("GAP", "GOAL", 999_999, "Not in this plan", "x"),
                        new AiDrafts.IssueDraft("MISSING_OWNER", "STEP", stepId, "Rule kinds come from the rules", "x"))),
                        "claude-opus-5-5", 10, 10, false));

        JsonNode check = read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/check").header("Authorization", "Bearer " + user)));
        assertThat(check.get("aiChecked").asBoolean()).isTrue();
        String issues = check.get("issues").toString();
        assertThat(issues).contains("MISSING_RESOURCE").contains("AFTER_GOAL_DATE").contains("NO_STEPS").contains("VAGUE_STEP")
                .doesNotContain("999999").doesNotContain("Rule kinds come from the rules");
        long missingResource = 0;
        for (JsonNode issue : check.get("issues")) {
            if (issue.get("kind").asText().equals("MISSING_RESOURCE")) {
                missingResource++;
                assertThat(issue.get("itemId").asLong()).isEqualTo(stepId);
            }
        }
        assertThat(missingResource).isEqualTo(1);

        // AI off: the rule-based half still answers (AI-NFR-02).
        jdbcTemplate.update("update ai_settings set enabled = false");
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/check").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiChecked").value(false))
                .andExpect(jsonPath("$.issues[?(@.kind == 'MISSING_RESOURCE')]").exists());
    }

    @Test
    void aStepMovedWhileOverdueAndOverdueAgainIsSlippingAndGetsHelp() throws Exception {
        String user = consented(email("slip"));
        long goalId = goal(user, dream(user));
        long stepId = step(user, goalId, "Call the bank", TODAY.minusDays(10), null);
        mockMvc.perform(get("/api/ai/plan/slipping-steps").header("Authorization", "Bearer " + user)).andExpect(jsonPath("$.length()").value(0));

        // Moved while overdue, to a date that has also passed.
        send(put("/api/steps/" + stepId), user, stepBody(goalId, "Call the bank", TODAY.minusDays(2), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueReschedules").value(1));
        mockMvc.perform(get("/api/ai/plan/slipping-steps").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$[0].stepId").value(stepId))
                .andExpect(jsonPath("$[0].overdueReschedules").value(1));

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.RescueDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.RescueDraft("Find the bank's number", "yesterday",
                        "A friend who banks there", "Phone calls feel heavy"), "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/plan/steps/" + stepId + "/rescue").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.smallerStep").value("Find the bank's number"))
                .andExpect(jsonPath("$.newDate").doesNotExist())
                .andExpect(jsonPath("$.helper").value("A friend who banks there"));
    }

    @Test
    void stepResourcesAreSavedKeptAndClearedOnPurpose() throws Exception {
        String user = consented(email("resources"));
        long goalId = goal(user, dream(user));
        long stepId = step(user, goalId, "Buy an oven", TODAY.plusWeeks(3), "$800 and a van");
        Map<String, Object> withoutResources = stepBody(goalId, "Buy an oven", TODAY.plusWeeks(3), null);
        withoutResources.remove("resources");
        send(put("/api/steps/" + stepId), user, withoutResources).andExpect(jsonPath("$.resources").value("$800 and a van"));
        send(put("/api/steps/" + stepId), user, stepBody(goalId, "Buy an oven", TODAY.plusWeeks(3), " "))
                .andExpect(jsonPath("$.resources").doesNotExist());
    }

    @Test
    void builderRequestsCountTowardTheDailyLimitAndNeedConsent() throws Exception {
        String noConsent = register(email("builder-no-consent"));
        long dreamId = dream(noConsent);
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + noConsent))
                .andExpect(status().isBadRequest());
        verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class));

        String user = consented(email("builder-limit"));
        long ownDream = dream(user);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(), "Which city?"), "claude-opus-5-5", 10, 10, false));
        jdbcTemplate.update("update ai_settings set daily_message_limit = 1");
        mockMvc.perform(post("/api/ai/plan/dreams/" + ownDream + "/goal-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/plan/dreams/" + ownDream + "/goal-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isTooManyRequests());
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Home", "priority", "HIGH", "status", "ACTIVE"))).get("id").asLong();
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

    private long step(String token, long goalId, String title, LocalDate date, String resources) throws Exception {
        return read(send(post("/api/steps"), token, stepBody(goalId, title, date, resources)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private static Map<String, Object> stepBody(long goalId, String title, LocalDate date, String resources) {
        Map<String, Object> body = new HashMap<>(Map.of("owner", "Me", "goalId", goalId, "title", title, "description", "d",
                "sequenceNumber", 1, "complex", false, "priority", "HIGH", "targetDate", date.toString(), "status", "NOT_STARTED"));
        body.put("resources", resources);
        return body;
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Plan Tester", "email", email, "password", "Password123"))))
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
