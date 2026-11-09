package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
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

/** V9 Phase 8 (AI-FR-18 to AI-FR-21): the coach co-pilot sees only what the coach may see, and only with consent. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiCoachFlowTests {

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

    private record Cohort(long id, String code, String coachToken) {
    }

    @BeforeEach
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        String adminEmail = email("coach-admin");
        admin = register(adminEmail, null);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 20");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void aWeeklySummaryNeedsConsentLeavesOutUnsharedJournalsAndIsKeptForTheWeek() throws Exception {
        Cohort cohort = cohortWithCoach();
        String withoutConsent = email("no-consent");
        register(withoutConsent, cohort.code());
        String participantEmail = email("summarised");
        String participant = register(participantEmail, cohort.code());
        consent(participant);
        send(post("/api/fear-entries"), participant, Map.of("fear", "Private fear text", "worstCase", "x", "likelihood", "LOW",
                "firstTest", "y")).andExpect(status().isCreated());
        long participantId = userId(participantEmail);
        String path = "/api/coach/participants/" + participantId + "/ai-summary";
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.CoachSummaryDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.CoachSummaryDraft("Steady progress.", "Mood looks low.",
                        "Nothing major.", "Ask about the bank visit."), "claude-opus-5-5", 10, 10, false));

        mockMvc.perform(get(path).header("Authorization", "Bearer " + cohort.coachToken())).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/coach/participants/" + userId(withoutConsent) + "/ai-summary")
                        .header("Authorization", "Bearer " + cohort.coachToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This participant has not turned on AI consent, so AI cannot read their plan."));

        mockMvc.perform(post(path).header("Authorization", "Bearer " + cohort.coachToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value("Steady progress."))
                .andExpect(jsonPath("$.mood").doesNotExist())
                .andExpect(jsonPath("$.suggestedAction").value("Ask about the bank visit."));
        mockMvc.perform(get(path).header("Authorization", "Bearer " + cohort.coachToken()))
                .andExpect(jsonPath("$.progress").value("Steady progress."));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, times(1)).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.CoachSummaryDraft.class));
        assertThat(system.getValue()).contains("Journals: not shared with the coach.").doesNotContain("Private fear text")
                .doesNotContain(participantEmail).contains("You help");

        // Another coach, or the participant, gets nothing.
        Cohort other = cohortWithCoach();
        mockMvc.perform(post(path).header("Authorization", "Bearer " + other.coachToken())).andExpect(status().isNotFound());
        mockMvc.perform(post(path).header("Authorization", "Bearer " + participant)).andExpect(status().isForbidden());
    }

    @Test
    void sharedJournalsAddTheMoodAndCoachRequestsDoNotUseTheCoachsDailyLimit() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("sharer");
        String participant = register(participantEmail, cohort.code());
        consent(participant);
        send(put("/api/cohort/sharing"), participant, Map.of("shareJournalsWithCoach", true)).andExpect(status().isOk());
        send(post("/api/fear-entries"), participant, Map.of("fear", "Shared fear text", "worstCase", "x", "likelihood", "LOW",
                "firstTest", "y")).andExpect(status().isCreated());
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.CoachSummaryDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.CoachSummaryDraft("p", "Mood is steady.", "r", "a"),
                        "claude-opus-5-5", 10, 10, false));
        jdbcTemplate.update("update ai_settings set daily_message_limit = 1");

        String path = "/api/coach/participants/" + userId(participantEmail) + "/ai-summary";
        mockMvc.perform(post(path).header("Authorization", "Bearer " + cohort.coachToken())).andExpect(jsonPath("$.mood").value("Mood is steady."));
        mockMvc.perform(post(path).header("Authorization", "Bearer " + cohort.coachToken())).andExpect(status().isOk());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, times(2)).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.CoachSummaryDraft.class));
        assertThat(system.getValue()).contains("Shared fear text");
    }

    @Test
    void aFeedbackDraftIsAboutAnItemInThatParticipantsPlanAndSendsNothing() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("feedback");
        String participant = register(participantEmail, cohort.code());
        consent(participant);
        long goalId = goal(participant, dream(participant));
        long stepId = read(send(post("/api/steps"), participant, Map.of("owner", "Me", "goalId", goalId, "title", "Visit three banks",
                "description", "d", "sequenceNumber", 1, "complex", false, "priority", "HIGH",
                "targetDate", TODAY.plusDays(5).toString(), "status", "NOT_STARTED"))).get("id").asLong();
        String stranger = register(email("stranger"), null);
        long strangerGoal = goal(stranger, dream(stranger));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.CoachFeedbackDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.CoachFeedbackDraft(" Great start on the banks. "),
                        "claude-opus-5-5", 10, 10, false));

        String path = "/api/coach/participants/" + userId(participantEmail) + "/feedback-draft";
        send(post(path), cohort.coachToken(), Map.of("targetType", "STEP", "targetId", stepId))
                .andExpect(jsonPath("$.message").value("Great start on the banks."));
        send(post(path), cohort.coachToken(), Map.of("targetType", "GOAL", "targetId", strangerGoal)).andExpect(status().isBadRequest());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.CoachFeedbackDraft.class));
        assertThat(system.getValue()).contains("The comment is about this step: Visit three banks");
        assertThat(jdbcTemplate.queryForObject("select count(*) from coach_comments where user_id = ?", Integer.class,
                userId(participantEmail))).isZero();
    }

    @Test
    void theCohortSummaryUsesOnlyConsentingParticipantsWithoutNamesAndCanBeRated() throws Exception {
        Cohort cohort = cohortWithCoach();
        String consentingEmail = email("cohort-yes");
        String consenting = register(consentingEmail, cohort.code());
        consent(consenting);
        jdbcTemplate.update("update app_users set full_name = 'Visible Name' where email = ?", consentingEmail);
        register(email("cohort-no"), cohort.code());
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.CohortSummaryDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.CohortSummaryDraft("A quiet week.", "Time", "Module 5",
                        "Run a planning session"), "claude-opus-5-5", 10, 10, false));

        String path = "/api/coach/cohorts/" + cohort.id() + "/ai-summary";
        mockMvc.perform(get(path).header("Authorization", "Bearer " + cohort.coachToken())).andExpect(status().isNoContent());
        JsonNode summary = read(mockMvc.perform(post(path).header("Authorization", "Bearer " + cohort.coachToken()))
                .andExpect(jsonPath("$.overview").value("A quiet week."))
                .andExpect(jsonPath("$.strugglingModules").value("Module 5")));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.CohortSummaryDraft.class));
        assertThat(system.getValue()).contains("1 of 2 participants gave AI consent").doesNotContain("Visible Name");

        long summaryId = summary.get("id").asLong();
        send(put("/api/coach/ai-summaries/" + summaryId + "/rating"), cohort.coachToken(), Map.of("accurate", true, "note", "Spot on"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + cohort.coachToken())).andExpect(jsonPath("$.accurate").value(true));
        Cohort other = cohortWithCoach();
        send(put("/api/coach/ai-summaries/" + summaryId + "/rating"), other.coachToken(), Map.of("accurate", false))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/coach/cohorts/" + cohort.id() + "/ai-summary").header("Authorization", "Bearer " + other.coachToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theParticipantListShowsRiskSignalsWithTheirReasonsAndAiConsent() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("risky");
        String participant = register(participantEmail, cohort.code());
        long goalId = goal(participant, dream(participant));
        for (int index = 1; index <= 3; index++) {
            send(post("/api/steps"), participant, Map.of("owner", "Me", "goalId", goalId, "title", "Late " + index, "description", "d",
                    "sequenceNumber", index, "complex", false, "priority", "HIGH", "targetDate", TODAY.minusDays(index).toString(),
                    "status", "NOT_STARTED")).andExpect(status().isCreated());
        }
        JsonNode list = read(mockMvc.perform(get("/api/coach/participants").header("Authorization", "Bearer " + cohort.coachToken())));
        JsonNode row = list.get(0);
        assertThat(row.get("riskReasons").toString()).contains("3 items overdue.");
        assertThat(row.get("aiConsent").asBoolean()).isFalse();
    }

    private Cohort cohortWithCoach() throws Exception {
        String coachEmail = email("coach");
        register(coachEmail, null);
        JsonNode created = read(send(post("/api/admin/cohorts"), admin, Map.of("name", "Cohort", "coachEmail", coachEmail)));
        return new Cohort(created.get("id").asLong(), created.get("joinCode").asText(), login(coachEmail));
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

    private void consent(String token) throws Exception {
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
    }

    private String register(String email, String joinCode) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "Coach Tester", "email", email, "password", "Steady-Harbor-Lamp-42"));
        if (joinCode != null) {
            body.put("joinCode", joinCode);
        }
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }

    private String login(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isOk())
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
