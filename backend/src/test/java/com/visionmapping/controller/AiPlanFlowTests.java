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

    private String adminToken;

    @BeforeEach
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        String admin = register(email("plan-admin"));
        adminToken = admin;
        jdbcTemplate.update("update app_users set role = 'ADMIN' where full_name = 'Plan Tester' and role = 'USER' and email like 'plan-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 20");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void visionAreaSuggestionsKnowExistingAreasAndNothingIsSaved() throws Exception {
        String user = consented(email("areas"));
        dream(user);
        List<AiDrafts.AreaDraft> drafts = new java.util.ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            drafts.add(new AiDrafts.AreaDraft("Area " + index, "d", "v", "HIGH", "r"));
        }
        drafts.set(1, new AiDrafts.AreaDraft(" ", "d", "v", "LOW", "r"));
        drafts.set(2, new AiDrafts.AreaDraft("Health", "d", "v", "SOMETIMES", "r"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AreaDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.AreaDrafts(drafts), "claude-opus-5-5", 10, 10, false));

        JsonNode answer = read(mockMvc.perform(post("/api/ai/plan/vision-area-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));

        assertThat(answer.get("areas")).hasSize(5);
        assertThat(answer.get("areas").get(1).get("name").asText()).isEqualTo("Health");
        assertThat(answer.get("areas").get(1).get("priority").asText()).isEqualTo("MEDIUM");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.AreaDrafts.class));
        assertThat(system.getValue()).contains("Home").contains("Own a home");
        assertThat(jdbcTemplate.queryForObject("select count(*) from vision_areas where name = 'Area 1'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select feature from ai_usage_logs order by id desc limit 1", String.class))
                .isEqualTo("AREA_BUILDER");
    }

    @Test
    void dreamSuggestionsAreCheckedAndAStretchDreamIsOneMoonshot() throws Exception {
        String user = consented(email("dreams"));
        long areaId = read(send(post("/api/vision-areas"), user, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        AiDrafts.DreamDraft good = new AiDrafts.DreamDraft("Lead a research team", "d", "w", "s", "LONG_TERM", "HIGH",
                TODAY.plusYears(2).toString(), "r");
        AiDrafts.DreamDraft odd = new AiDrafts.DreamDraft("Write a book", "d", "w", "s", "SOMEDAY", "HIGH",
                TODAY.minusDays(3).toString(), "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DreamDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.DreamDrafts(List.of(good, odd), ""), "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(post("/api/ai/plan/vision-areas/" + areaId + "/dream-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.dreams.length()").value(2))
                .andExpect(jsonPath("$.dreams[0].moonshot").value(false))
                .andExpect(jsonPath("$.dreams[1].dreamType").value("LONG_TERM"))
                .andExpect(jsonPath("$.dreams[1].targetDate").isEmpty())
                .andExpect(jsonPath("$.clarifyingQuestion").isEmpty());
        mockMvc.perform(post("/api/ai/plan/vision-areas/" + areaId + "/dream-suggestions?stretch=true").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.dreams.length()").value(1))
                .andExpect(jsonPath("$.dreams[0].moonshot").value(true));
        assertThat(jdbcTemplate.queryForObject("select count(*) from dreams where vision_area_id = ?", Integer.class, areaId)).isZero();

        String other = consented(email("dreams-other"));
        mockMvc.perform(post("/api/ai/plan/vision-areas/" + areaId + "/dream-suggestions").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
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

        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions?language=fr").header("Authorization", "Bearer " + user))
                .andExpect(status().isBadRequest());

        String other = consented(email("goals-other"));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void goalSuggestionsReadTheDreamsAreaWhyAndFullSuccessDefinition() throws Exception {
        String user = consented(email("grounded"));
        long areaId = read(send(post("/api/vision-areas"), user, Map.of("name", "Career Growth", "description", "Work I am proud of",
                "priority", "HIGH", "status", "ACTIVE"))).get("id").asLong();
        String success = "Publish two papers. " + "x".repeat(600) + " Lead one funded study by the end.";
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Become a public health researcher",
                "description", "Use AI tools in research", "whyImportant", "Help my community make better health choices",
                "successDefinition", success, "letterRank", "A", "dreamType", "LONG_TERM", "priority", "HIGH",
                "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        long dreamId = read(send(post("/api/dreams"), user, body).andExpect(status().isCreated())).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(), "Which field?"), "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> task = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), task.capture(), eq(AiDrafts.GoalDrafts.class));
        assertThat(system.getValue()).contains("Vision area: Career Growth").contains("Area description: Work I am proud of")
                .contains("Description: Use AI tools in research")
                .contains("Why it matters: Help my community make better health choices")
                // The end of a long success definition is no longer cut off at 300 characters.
                .contains("Lead one funded study by the end.");
        assertThat(task.getValue()).contains("success definition").contains("why it matters");
    }

    @Test
    void stepSuggestionsReadTheGoalsSuccessCriteria() throws Exception {
        String user = consented(email("grounded-steps"));
        long goalId = read(send(post("/api/goals"), user, Map.of("dreamId", dream(user), "title", "Save a deposit",
                "description", "Put money aside each month", "successCriteria", "Ten thousand dollars saved by June",
                "priority", "HIGH", "targetDate", TODAY.plusMonths(4).toString(), "status", "IN_PROGRESS",
                "scheduleMode", "BOTTOM_UP")).andExpect(status().isCreated())).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(), "How much?"), "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(post("/api/ai/plan/goals/" + goalId + "/step-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> task = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), task.capture(), eq(AiDrafts.StepDrafts.class));
        assertThat(system.getValue()).contains("Goal description: Put money aside each month")
                .contains("Goal success criteria: Ten thousand dollars saved by June");
        assertThat(task.getValue()).contains("success criteria");
    }

    @Test
    void taskSuggestionsReadTheStepsDescriptionResourcesAndPeopleAndCanBeAdded() throws Exception {
        String user = consented(email("grounded-tasks"));
        long dreamId = dream(user);
        long goalId = goal(user, dreamId);
        long stepId = step(user, goalId, "Visit three banks", TODAY.plusMonths(2), "Payslips\nPeople: A loan officer");
        AiDrafts.TaskDraft draft = new AiDrafts.TaskDraft("Book a loan officer meeting", "Me", TODAY.plusWeeks(1).toString(),
                "HIGH", 1.0, "Gets a loan answer");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.TaskDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.TaskDrafts(List.of(draft), ""), "claude-opus-5-5", 10, 10, false));

        JsonNode answer = read(mockMvc.perform(post("/api/ai/plan/steps/" + stepId + "/task-suggestions")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> task = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), task.capture(), eq(AiDrafts.TaskDrafts.class));
        assertThat(system.getValue()).contains("Step description: d").contains("Step owner: Me")
                .contains("Step resources and people: Payslips People: A loan officer")
                .contains("Goal success criteria: s");
        assertThat(task.getValue()).contains("resources and people").contains("success criteria");

        long requestId = answer.get("requestId").asLong();
        Map<String, Object> taskBody = Map.of("stepId", stepId, "title", "Book a loan officer meeting", "owner", "Me",
                "priority", "HIGH", "dueDate", TODAY.plusWeeks(1).toString(), "status", "NOT_STARTED", "progressPercent", 0);
        JsonNode added = read(send(post("/api/ai/plan/add"), user, Map.of("requestId", requestId,
                "tasks", List.of(Map.of("task", taskBody, "edited", false)))).andExpect(status().isCreated()));
        assertThat(added.get("taskIds")).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from task_items where step_id = ? and archived = false",
                Integer.class, stepId)).isEqualTo(1);
        assertThat(usageCounts(requestId)).containsExactly(1, 1, 0);

        // Tasks cannot be said to come from a goal answer, and another user's step answers 404.
        long goalAnswer = goalSuggestions(user, dreamId, 1);
        send(post("/api/ai/plan/add"), user, Map.of("requestId", goalAnswer, "tasks", List.of(Map.of("task", taskBody, "edited", false))))
                .andExpect(status().isBadRequest());
        String other = consented(email("grounded-tasks-other"));
        send(post("/api/ai/plan/add"), other, Map.of("tasks", List.of(Map.of("task", taskBody, "edited", false))))
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

    // ---------- V11 Phase 1 ----------

    @Test
    void suggestedDatesAfterTheParentsDateAreCleared() throws Exception {
        String user = consented(email("bounds"));
        long dreamId = dream(user);
        jdbcTemplate.update("update dreams set target_date = ? where id = ?", TODAY.plusMonths(6), dreamId);
        AiDrafts.GoalDraft inside = new AiDrafts.GoalDraft("Inside", "d", "s", TODAY.plusMonths(3).toString(), "HIGH", "r");
        AiDrafts.GoalDraft after = new AiDrafts.GoalDraft("After the dream", "d", "s", TODAY.plusYears(1).toString(), "HIGH", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(inside, after), ""), "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.goals[0].targetDate").value(TODAY.plusMonths(3).toString()))
                .andExpect(jsonPath("$.goals[1].targetDate").isEmpty())
                .andExpect(jsonPath("$.requestId").isNumber());

        long goalId = goal(user, dreamId); // target in 4 months
        AiDrafts.StepDraft soon = new AiDrafts.StepDraft("Soon", "d", "Me", TODAY.plusWeeks(2).toString(), "r", "p", "r");
        AiDrafts.StepDraft late = new AiDrafts.StepDraft("After the goal", "d", "Me", TODAY.plusMonths(5).toString(), "r", "p", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(soon, late), ""), "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/plan/goals/" + goalId + "/step-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.steps[0].targetDate").value(TODAY.plusWeeks(2).toString()))
                .andExpect(jsonPath("$.steps[1].targetDate").isEmpty());
    }

    @Test
    void addSavesEverythingOrNothingCountsOutcomesAndCanBeUndone() throws Exception {
        String user = consented(email("add"));
        long dreamId = dream(user);
        long requestId = goalSuggestions(user, dreamId, 3);
        assertThat(usageCounts(requestId)).containsExactly(3, 0, 0);

        // One bad item (another user's dream) and nothing is saved.
        String other = consented(email("add-other"));
        long otherDream = dream(other);
        send(post("/api/ai/plan/add"), user, Map.of("requestId", requestId, "goals", List.of(
                goalItem(dreamId, "Kept", false), goalItem(otherDream, "Not mine", false))))
                .andExpect(status().isNotFound());
        assertThat(goalsUnder(dreamId)).isZero();
        assertThat(usageCounts(requestId)).containsExactly(3, 0, 0);

        // A blank title is refused by the same rules as the normal create.
        send(post("/api/ai/plan/add"), user, Map.of("goals", List.of(goalItem(dreamId, " ", false))))
                .andExpect(status().isBadRequest());

        JsonNode added = read(send(post("/api/ai/plan/add"), user, Map.of("requestId", requestId, "goals", List.of(
                goalItem(dreamId, "As suggested", false), goalItem(dreamId, "Edited", true))))
                .andExpect(status().isCreated()));
        assertThat(added.get("goalIds")).hasSize(2);
        assertThat(goalsUnder(dreamId)).isEqualTo(2);
        assertThat(usageCounts(requestId)).containsExactly(3, 1, 1);

        // Steps cannot be reported against goal suggestions.
        long goalId = added.get("goalIds").get(0).asLong();
        send(post("/api/ai/plan/add"), user, Map.of("requestId", requestId,
                "steps", List.of(Map.of("step", stepBody(goalId, "Step", TODAY.plusWeeks(1), "r"), "edited", false))))
                .andExpect(status().isBadRequest());

        // Another user cannot undo it; the owner can.
        send(post("/api/ai/plan/undo"), other, Map.of("goalIds", List.of(goalId))).andExpect(status().isNotFound());
        List<Long> goalIds = List.of(added.get("goalIds").get(0).asLong(), added.get("goalIds").get(1).asLong());
        send(post("/api/ai/plan/undo"), user, Map.of("requestId", requestId, "goalIds", goalIds)).andExpect(status().isNoContent());
        assertThat(goalsUnder(dreamId)).isZero();
        assertThat(usageCounts(requestId)).containsExactly(3, 0, 0);
    }

    @Test
    void stepsAreAddedTogetherWithTheirOutcome() throws Exception {
        String user = consented(email("add-steps"));
        long goalId = goal(user, dream(user));
        AiDrafts.StepDraft draft = new AiDrafts.StepDraft("Visit banks", "d", "Me", TODAY.plusWeeks(2).toString(), "r", "p", "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(draft, draft), ""), "claude-opus-5-5", 10, 10, false));
        long requestId = read(mockMvc.perform(post("/api/ai/plan/goals/" + goalId + "/step-suggestions")
                .header("Authorization", "Bearer " + user))).get("requestId").asLong();

        JsonNode added = read(send(post("/api/ai/plan/add"), user, Map.of("requestId", requestId, "steps", List.of(
                Map.of("step", stepBody(goalId, "Visit banks", TODAY.plusWeeks(2), "r"), "edited", false))))
                .andExpect(status().isCreated()));
        assertThat(added.get("stepIds")).hasSize(1);
        assertThat(usageCounts(requestId)).containsExactly(2, 1, 0);
    }

    @Test
    void planAnswersCanBeRatedOnlyByTheirOwner() throws Exception {
        String user = consented(email("rate"));
        long requestId = goalSuggestions(user, dream(user), 1);
        send(put("/api/ai/plan/requests/" + requestId + "/rating"), user, Map.of("rating", 1, "comment", " Useful "))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select rating from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select rating_comment from ai_usage_logs where id = ?", String.class, requestId))
                .isEqualTo("Useful");

        String other = consented(email("rate-other"));
        send(put("/api/ai/plan/requests/" + requestId + "/rating"), other, Map.of("rating", -1)).andExpect(status().isNotFound());
        send(post("/api/ai/plan/requests/" + requestId + "/outcome"), other, Map.of("added", 1, "edited", 0))
                .andExpect(status().isNotFound());

        // A usage row of another feature is not a plan answer.
        jdbcTemplate.update("insert into ai_usage_logs (user_id, feature, model, input_tokens, output_tokens, "
                + "cost_usd, outcome, created_at, updated_at) select user_id, 'CHAT', model, 0, 0, 0, 'OK', now(), now() "
                + "from ai_usage_logs where id = ?", requestId);
        Long chatRow = jdbcTemplate.queryForObject("select max(id) from ai_usage_logs where feature = 'CHAT'", Long.class);
        send(put("/api/ai/plan/requests/" + chatRow + "/rating"), user, Map.of("rating", 1)).andExpect(status().isNotFound());

        // Outcomes never pass what was suggested.
        send(post("/api/ai/plan/requests/" + requestId + "/outcome"), user, Map.of("added", 5, "edited", 0))
                .andExpect(status().isNoContent());
        assertThat(usageCounts(requestId)).containsExactly(1, 1, 0);
    }

    @Test
    void adminsSeePlanMetricsAndOthersDoNot() throws Exception {
        String user = consented(email("metrics"));
        long dreamId = dream(user);
        long requestId = goalSuggestions(user, dreamId, 4);
        send(post("/api/ai/plan/add"), user, Map.of("requestId", requestId, "goals", List.of(
                goalItem(dreamId, "One", false), goalItem(dreamId, "Two", true)))).andExpect(status().isCreated());
        send(put("/api/ai/plan/requests/" + requestId + "/rating"), user, Map.of("rating", 1)).andExpect(status().isNoContent());

        JsonNode metrics = read(mockMvc.perform(get("/api/admin/ai/plan-metrics?days=7").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()));
        assertThat(metrics.get("days").asInt()).isEqualTo(7);
        JsonNode goals = null;
        for (JsonNode feature : metrics.get("features")) {
            if (feature.get("feature").asText().equals("GOAL_BUILDER")) {
                goals = feature;
            }
        }
        assertThat(goals).isNotNull();
        assertThat(goals.get("suggestedItems").asLong()).isGreaterThanOrEqualTo(4);
        assertThat(goals.get("addedItems").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(goals.get("editedItems").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(goals.get("acceptanceRate").isNumber()).isTrue();

        mockMvc.perform(get("/api/admin/ai/plan-metrics").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
    }

    private long goalSuggestions(String token, long dreamId, int count) throws Exception {
        List<AiDrafts.GoalDraft> drafts = new java.util.ArrayList<>();
        for (int index = 1; index <= count; index++) {
            drafts.add(new AiDrafts.GoalDraft("Goal " + index, "d", "s", TODAY.plusMonths(index).toString(), "HIGH", "r"));
        }
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(drafts, ""), "claude-opus-5-5", 10, 10, false));
        return read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).get("requestId").asLong();
    }

    private static Map<String, Object> goalItem(long dreamId, String title, boolean edited) {
        return Map.of("goal", Map.of("dreamId", dreamId, "title", title, "priority", "HIGH", "status", "NOT_STARTED",
                "scheduleMode", "BOTTOM_UP", "moonshot", false), "edited", edited);
    }

    private int goalsUnder(long dreamId) {
        return jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ? and archived = false", Integer.class, dreamId);
    }

    /** suggested, added, edited. */
    private List<Integer> usageCounts(long requestId) {
        return jdbcTemplate.queryForObject("select suggested_items, added_items, edited_items from ai_usage_logs where id = ?",
                (row, index) -> List.of(row.getInt(1), row.getInt(2), row.getInt(3)), requestId);
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
