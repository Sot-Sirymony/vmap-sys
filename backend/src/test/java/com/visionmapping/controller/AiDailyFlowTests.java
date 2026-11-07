package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.service.ai.AiDrafts;
import com.visionmapping.service.ai.AiReminderService;
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

/** V9 Phase 6 (AI-FR-12 to AI-FR-17): daily-use suggestions, each checked and never saved by the AI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiDailyFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AiReminderService aiReminderService;

    @MockitoBean
    private ClaudeClient claudeClient;

    @BeforeEach
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        String adminEmail = email("daily-admin");
        String admin = register(adminEmail);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 20, ai_reminders = false");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void theNextStepComesOnlyFromTheParticipantsOwnOpenWork() throws Exception {
        String user = consented(email("next"));
        mockMvc.perform(post("/api/ai/daily/next-step?minutes=30").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.picks.length()").value(0))
                .andExpect(jsonPath("$.note").exists());
        verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.NextStepDraft.class));

        long goalId = goal(user, dream(user));
        long overdue = step(user, goalId, "Call the bank", TODAY.minusDays(3));
        long later = step(user, goalId, "Visit open homes", TODAY.plusDays(20));
        String other = consented(email("next-other"));
        long foreign = step(other, goal(other, dream(other)), "Someone else's step", TODAY);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.NextStepDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.NextStepDraft(List.of(
                        new AiDrafts.PickDraft("STEP", foreign, "Not theirs", 10),
                        new AiDrafts.PickDraft("STEP", overdue, "It is overdue and short", 20),
                        new AiDrafts.PickDraft("STEP", overdue, "Twice", 20),
                        new AiDrafts.PickDraft("STEP", later, "Next up", 9_999)), "You can do this."), "claude-opus-5-5", 10, 10, false));

        JsonNode answer = read(mockMvc.perform(post("/api/ai/daily/next-step?minutes=30").header("Authorization", "Bearer " + user)));
        assertThat(answer.get("picks")).hasSize(2);
        assertThat(answer.get("picks").get(0).get("itemId").asLong()).isEqualTo(overdue);
        assertThat(answer.get("picks").get(0).get("title").asText()).isEqualTo("Call the bank");
        assertThat(answer.get("picks").get(1).get("minutes").isNull()).isTrue();

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.NextStepDraft.class));
        assertThat(system.getValue()).contains("Call the bank").contains("(overdue)").doesNotContain("Someone else's step");
        mockMvc.perform(post("/api/ai/daily/next-step?minutes=1").header("Authorization", "Bearer " + user)).andExpect(status().isBadRequest());
    }

    @Test
    void theWeeklyReviewDraftUsesThisWeeksRecordsAndSavesNothing() throws Exception {
        String user = consented(email("review"));
        long stepId = step(user, goal(user, dream(user)), "Open savings account", TODAY.plusDays(2));
        long taskId = task(user, stepId, "Bring passport");
        send(patch("/api/tasks/" + taskId + "/status"), user, Map.of("status", "COMPLETED", "manualOverride", false)).andExpect(status().isOk());
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.WeeklyReviewDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.WeeklyReviewDraft("A steady week.", "Bring passport", "", "",
                        "Small steps work.", "1. Open the account"), "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(post("/api/ai/daily/weekly-review-draft").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.summary").value("A steady week."))
                .andExpect(jsonPath("$.delayedTasks").doesNotExist());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.WeeklyReviewDraft.class));
        assertThat(system.getValue()).contains("Tasks finished:").contains("Bring passport");
        assertThat(jdbcTemplate.queryForObject("select count(*) from reviews r join app_users u on u.id = r.user_id where u.email like 'review-%'",
                Integer.class)).isZero();
    }

    @Test
    void pitchScoresStayBetweenOneAndFiveAndTheAudienceMustBeKnown() throws Exception {
        String user = consented(email("pitch"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PitchFeedbackDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.PitchFeedbackDraft("I like the idea.", 0, 9, 3,
                        "Better pitch", "Name the ask"), "claude-opus-5-5", 10, 10, false));
        send(post("/api/ai/daily/pitch-feedback"), user, Map.of("audience", "SPONSOR", "pitch", "Fund my <bakery>"))
                .andExpect(jsonPath("$.clarity").value(1))
                .andExpect(jsonPath("$.benefit").value(5))
                .andExpect(jsonPath("$.ask").value(3))
                .andExpect(jsonPath("$.improvedPitch").value("Better pitch"));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.PitchFeedbackDraft.class));
        assertThat(system.getValue()).contains("Fund my ‹bakery›");
        send(post("/api/ai/daily/pitch-feedback"), user, Map.of("audience", "BOSS", "pitch", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void anOutreachDraftUsesThePartnersNameButNeverTheirContactDetails() throws Exception {
        String user = consented(email("outreach"));
        long partnerId = read(send(post("/api/partners"), user, Map.of("name", "Dara Mentor", "role", "Baker", "organization", "Golden Loaf",
                "supportType", "MENTOR", "status", "ACTIVE", "email", "dara@example.com", "phone", "012345678"))).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.OutreachDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.OutreachDraft("A question about ovens", "Dear Dara, ..."),
                        "claude-opus-5-5", 10, 10, false));
        send(post("/api/ai/daily/outreach-draft"), user, Map.of("partnerId", partnerId, "request", "Advice on choosing an oven"))
                .andExpect(jsonPath("$.subject").value("A question about ovens"));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.OutreachDraft.class));
        assertThat(system.getValue()).contains("Dara Mentor").contains("Golden Loaf")
                .doesNotContain("dara@example.com").doesNotContain("012345678");
        String other = consented(email("outreach-other"));
        send(post("/api/ai/daily/outreach-draft"), other, Map.of("partnerId", partnerId, "request", "x")).andExpect(status().isNotFound());
    }

    @Test
    void resourceIdeasLoseLinksAndAreAlwaysMarkedCheckBeforeUse() throws Exception {
        String user = consented(email("resources"));
        long goalId = goal(user, dream(user));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ResourceDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ResourceDrafts(List.of(
                        new AiDrafts.ResourceDraft(0, "COURSE", "Home buying basics https://example.com/course", "Explains loans"),
                        new AiDrafts.ResourceDraft(0, "WIZARD", "A loan officer", "Knows the rules"),
                        new AiDrafts.ResourceDraft(0, "BOOK", " ", "no name"))), "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/daily/goals/" + goalId + "/resource-ideas").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.resources.length()").value(2))
                .andExpect(jsonPath("$.resources[0].name").value("Home buying basics"))
                .andExpect(jsonPath("$.resources[0].checkBeforeUse").value(true))
                .andExpect(jsonPath("$.resources[1].kind").value("OTHER"));
    }

    @Test
    void stepResourceIdeasReadTheStepsDescriptionResourcesAndPeople() throws Exception {
        String user = consented(email("step-resources"));
        long goalId = goal(user, dream(user));
        long stepId = read(send(post("/api/steps"), user, Map.of("owner", "Me", "goalId", goalId, "title", "Visit three banks",
                "description", "Compare loan offers", "sequenceNumber", 1, "complex", false, "priority", "HIGH",
                "targetDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED", "resources", "Payslips\nPeople: A loan officer"))
                .andExpect(status().isCreated())).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ResourceDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ResourceDrafts(List.of(
                        new AiDrafts.ResourceDraft(0, "TOOL", "A loan comparison sheet", "Lines offers up side by side"))),
                        "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(post("/api/ai/daily/steps/" + stepId + "/resource-ideas").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources[0].name").value("A loan comparison sheet"))
                .andExpect(jsonPath("$.resources[0].checkBeforeUse").value(true));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> task = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), task.capture(), eq(AiDrafts.ResourceDrafts.class));
        assertThat(system.getValue()).contains("Step: Visit three banks").contains("Step description: Compare loan offers")
                .contains("Step resources and people: Payslips People: A loan officer").contains("Goal success criteria: s");
        assertThat(task.getValue()).contains("for this step").contains("do not repeat it");

        String other = consented(email("step-resources-other"));
        mockMvc.perform(post("/api/ai/daily/steps/" + stepId + "/resource-ideas").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void resourceIdeasReadTheGoalsOutcomeBeforeSuggesting() throws Exception {
        String user = consented(email("resources-grounded"));
        String criteria = "Ten thousand dollars saved. " + "x".repeat(600) + " Kept in a separate account.";
        long goalId = read(send(post("/api/goals"), user, Map.of("dreamId", dream(user), "title", "Save a deposit",
                "description", "Put money aside each month", "successCriteria", criteria, "priority", "HIGH",
                "targetDate", TODAY.plusMonths(4).toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"))
                .andExpect(status().isCreated())).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ResourceDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ResourceDrafts(List.of()), "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(post("/api/ai/daily/goals/" + goalId + "/resource-ideas").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> task = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), task.capture(), eq(AiDrafts.ResourceDrafts.class));
        assertThat(system.getValue()).contains("Dream success looks like: Keys in hand")
                .contains("Goal description: Put money aside each month")
                // The end of a long success criteria is not cut off.
                .contains("Kept in a separate account.");
        assertThat(task.getValue()).contains("success criteria");
    }

    @Test
    void aiReminderLinesQuoteThePassionStatementOnlyWhenSwitchedOnAndConsented() throws Exception {
        String email = email("remind");
        String user = consented(email);
        dream(user);
        AppUser appUser = appUserRepository.findByEmail(email).orElseThrow();
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("You said \"Family\". One small step today.", "claude-opus-5-5", 10, 10, false, false));

        assertThat(aiReminderService.encouragement(appUser)).isEmpty();
        verify(claudeClient, never()).send(anyString(), anyString(), anyString(), anyList());

        jdbcTemplate.update("update ai_settings set ai_reminders = true");
        assertThat(aiReminderService.encouragement(appUser)).contains("You said \"Family\". One small step today.");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getValue()).contains("why it matters: Family");

        send(put("/api/ai/consent"), user, Map.of("consent", false)).andExpect(status().isOk());
        assertThat(aiReminderService.encouragement(appUserRepository.findByEmail(email).orElseThrow())).isEmpty();
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

    private long step(String token, long goalId, String title, LocalDate date) throws Exception {
        return read(send(post("/api/steps"), token, Map.of("owner", "Me", "goalId", goalId, "title", title, "description", "d",
                "sequenceNumber", 1, "complex", false, "priority", "HIGH", "targetDate", date.toString(), "status", "NOT_STARTED"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long task(String token, long stepId, String title) throws Exception {
        return read(send(post("/api/tasks"), token, Map.of("stepId", stepId, "title", title, "description", "d", "owner", "Me",
                "priority", "MEDIUM", "dueDate", TODAY.plusDays(1).toString(), "status", "IN_PROGRESS", "progressPercent", 0))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Daily Tester", "email", email, "password", "Password123"))))
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
