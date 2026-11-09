package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

/** V15 Phase 1 (AH-FR-01 to AH-FR-12, AH-FR-55, AH-FR-56, AH-NFR-03 to AH-NFR-07): the page assistant. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageAssistantFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final java.time.LocalDate TODAY = java.time.LocalDate.now(java.time.ZoneId.systemDefault());

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
        admin = register(email("page-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'page-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void theRuleChecksWorkWithoutAi() throws Exception {
        String user = register(email("page-rules")); // no AI consent
        long obstacleId = obstacle(user, partner(user), "I keep putting off the literature search");

        JsonNode obstacleCheck = read(send(post("/api/ai/pages/assistant/check"), user,
                Map.of("page", "OBSTACLES", "area", "item", "itemId", obstacleId)).andExpect(status().isOk()));
        assertThat(obstacleCheck.get("canPropose").asBoolean()).isTrue();
        assertThat(fields(obstacleCheck)).contains("rootCause", "solution");

        // AH-FR-46: the message rules, read from the unsaved form.
        String longBody = "word ".repeat(260);
        JsonNode messageCheck = read(send(post("/api/ai/pages/assistant/check"), user, Map.of("page", "COMMUNICATION",
                "area", "message", "form", Map.of("subject", "Concept note", "messageBody", longBody))).andExpect(status().isOk()));
        assertThat(fields(messageCheck)).containsExactly("request", "benefitToPartner", "callToAction", "messageBody");

        // A typed request clears that rule.
        JsonNode filled = read(send(post("/api/ai/pages/assistant/check"), user, Map.of("page", "COMMUNICATION", "area", "message",
                "form", Map.of("request", "Review my concept note for 20 minutes", "benefitToPartner", "First look at the data",
                        "callToAction", "Could we meet Tuesday?"))).andExpect(status().isOk()));
        assertThat(fields(filled)).isEmpty();

        // Self-assessments never get change cards.
        JsonNode assessment = read(send(post("/api/ai/pages/assistant/check"), user,
                Map.of("page", "ASSESSMENT", "area", "statement:fearScore")).andExpect(status().isOk()));
        assertThat(assessment.get("canPropose").asBoolean()).isFalse();
        JsonNode workStyle = read(send(post("/api/ai/pages/assistant/check"), user,
                Map.of("page", "WORK_STYLE", "area", "question:3")).andExpect(status().isOk()));
        assertThat(workStyle.get("canPropose").asBoolean()).isFalse();
        verifyNoInteractions(claudeClient);
    }

    @Test
    void obstacleChatSeesTheUnsavedFormButNeverPartnerDetailsOrThePrivateNote() throws Exception {
        String user = consented(email("page-obstacle-chat"));
        long partnerId = partner(user);
        long obstacleId = obstacle(user, partnerId, "I keep putting off the literature search");
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("Ask why it feels heavy.", "claude-opus-5-5", 10, 10, false, false));

        JsonNode turn = read(send(post("/api/ai/chat"), user, Map.of("message", "Help me understand this obstacle",
                "assistantPage", "OBSTACLES", "area", "item", "itemId", obstacleId,
                "form", Map.of("rootCause", "Unsaved idea: the search feels endless", "conflictPrivateNote", "Typed secret reaction",
                        "requiredPartnerId", String.valueOf(partnerId))))
                .andExpect(status().isOk()));
        assertThat(sources(turn)).contains("page", "obstacle");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getValue()).contains("I keep putting off the literature search", "Unsaved idea: the search feels endless",
                        "A required partner is set (name not shared)", "Never mark an obstacle resolved")
                .doesNotContain("Dr Secret Mentor", "secret@example.com", "+855 12 345", "Saved private reaction", "Typed secret reaction");
        assertThat(lastFeature("page-obstacle-chat")).isEqualTo("PAGE_ASSISTANT_CHAT");

        // AH-FR-11: kept as a Dream Coach conversation tagged with the page and item.
        long conversationId = turn.get("conversationId").asLong();
        mockMvc.perform(get("/api/ai/conversations?section=PAGE:OBSTACLES:" + obstacleId).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(conversationId));
        mockMvc.perform(get("/api/ai/conversations?section=PAGE:OBSTACLES").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.length()").value(0));

        // AH-NFR-07: another user's obstacle answers 404 before AI is checked, even without consent.
        String other = register(email("page-obstacle-other"));
        send(post("/api/ai/chat"), other, Map.of("message", "Hello", "assistantPage", "OBSTACLES", "area", "item", "itemId", obstacleId))
                .andExpect(status().isNotFound());
    }

    @Test
    void messageChatSendsOnlyThePartnersNameAndRoleAndEarlierSubjects() throws Exception {
        String user = consented(email("page-message-chat"));
        long partnerId = partner(user);
        send(post("/api/communication-messages"), user, Map.of("partnerId", partnerId, "subject", "First ask about data",
                "messageBody", "Old body that must stay private", "status", "SENT")).andExpect(status().isCreated());
        long messageId = read(send(post("/api/communication-messages"), user, Map.of("partnerId", partnerId,
                "subject", "Concept note review", "status", "DRAFT")).andExpect(status().isCreated())).get("id").asLong();
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("Make the ask smaller.", "claude-opus-5-5", 10, 10, false, false));

        send(post("/api/ai/chat"), user, Map.of("message", "Check my message", "assistantPage", "COMMUNICATION", "area", "message",
                "itemId", messageId, "form", Map.of("request", "Twenty minutes of feedback"))).andExpect(status().isOk());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getValue()).contains("Dr Secret Mentor", "Research lead", "Concept note review", "Twenty minutes of feedback",
                        "First ask about data", "Never sound demanding")
                .doesNotContain("secret@example.com", "+855 12 345", "Private partner notes", "Old body that must stay private");
    }

    @Test
    void selfAssessmentChatsCarryTheirGuards() throws Exception {
        String user = consented(email("page-guards"));
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("The score is your own judgement.", "claude-opus-5-5", 10, 10, false, false));

        send(post("/api/ai/chat"), user, Map.of("message", "What should I score?", "assistantPage", "ASSESSMENT",
                "area", "statement:fearScore")).andExpect(status().isOk());
        send(post("/api/ai/chat"), user, Map.of("message", "Which should I pick?", "assistantPage", "WORK_STYLE",
                "area", "question:3")).andExpect(status().isOk());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, org.mockito.Mockito.times(2)).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getAllValues().get(0)).contains("never suggest, change or comment on what score");
        assertThat(system.getAllValues().get(1)).contains("never choose an answer", "never call one style better");
    }

    @Test
    void proposalsKeepOnlyAllowedFieldsAndValuesAndCanBeRatedAndCounted() throws Exception {
        String user = consented(email("page-propose"));
        long obstacleId = obstacle(user, partner(user), "No time for the literature search");
        List<AiDrafts.PageChangeDraft> drafts = new ArrayList<>(List.of(
                new AiDrafts.PageChangeDraft("obstacleType", "time", "It is about hours in the week."),
                new AiDrafts.PageChangeDraft("severity", "HUGE", "Not a real value."),
                new AiDrafts.PageChangeDraft("status", "RESOLVED", "The assistant may never resolve an obstacle."),
                new AiDrafts.PageChangeDraft("requiredPartnerId", "999", "Not a field cards may change."),
                new AiDrafts.PageChangeDraft("rootCause", "  ", "Empty."),
                new AiDrafts.PageChangeDraft("rootCause", "The search has no clear stopping point.", "Names the real cause.")));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.PageAssistantDraft("Two ideas.", drafts), "claude-opus-5-5", 10, 10, false));

        JsonNode answer = read(send(post("/api/ai/pages/assistant/proposals"), user,
                Map.of("page", "OBSTACLES", "area", "item", "itemId", obstacleId)).andExpect(status().isOk()));
        assertThat(answer.get("changes")).hasSize(2);
        assertThat(answer.get("changes").get(0).get("field").asText()).isEqualTo("obstacleType");
        assertThat(answer.get("changes").get(0).get("value").asText()).isEqualTo("TIME");
        assertThat(answer.get("changes").get(1).get("field").asText()).isEqualTo("rootCause");
        // A proposal saves nothing.
        assertThat(jdbcTemplate.queryForObject("select root_cause from obstacles where id = ?", String.class, obstacleId)).isNull();

        long requestId = answer.get("requestId").asLong();
        send(post("/api/ai/pages/assistant/requests/" + requestId + "/outcome"), user, Map.of("added", 1, "edited", 0))
                .andExpect(status().isNoContent());
        send(put("/api/ai/plan/requests/" + requestId + "/rating"), user, Map.of("rating", 1)).andExpect(status().is2xxSuccessful());
        assertThat(jdbcTemplate.queryForMap("select feature, suggested_items, added_items, rating from ai_usage_logs where id = ?", requestId))
                .containsEntry("feature", "PAGE_ASSISTANT").containsEntry("suggested_items", 2).containsEntry("added_items", 1)
                .containsEntry("rating", 1);

        // AH-FR-43: help with one message field changes only that field.
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.PageAssistantDraft("", List.of(
                        new AiDrafts.PageChangeDraft("hook", "Your talk on rural clinics stayed with me.", "Specific to them."),
                        new AiDrafts.PageChangeDraft("request", "Please review it.", "Another field."))), "claude-opus-5-5", 10, 10, false));
        JsonNode field = read(send(post("/api/ai/pages/assistant/proposals"), user, Map.of("page", "COMMUNICATION", "area", "field:hook",
                "form", Map.of("subject", "Concept note"))).andExpect(status().isOk()));
        assertThat(field.get("changes")).hasSize(1);
        assertThat(field.get("changes").get(0).get("field").asText()).isEqualTo("hook");
    }

    @Test
    void adminsSeeV15MetricsAndBaselines() throws Exception {
        String user = register(email("page-metrics-user"));
        obstacle(user, partner(user), "No budget for the survey");

        JsonNode metrics = read(mockMvc.perform(get("/api/ai/pages/metrics?days=30").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()));
        assertThat(metrics.get("days").asInt()).isEqualTo(30);
        assertThat(metrics.get("baselines").get("openObstacles").asLong()).isPositive();
        mockMvc.perform(get("/api/ai/pages/metrics").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
    }

    @Test
    void moduleAndModulesPagesReadOnlyTheirOwnDataAndModuleCardsKeepToTheExerciseFields() throws Exception {
        String user = consented(email("page-modules"));
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("Start with module 1.", "claude-opus-5-5", 10, 10, false, false));

        send(post("/api/ai/chat"), user, Map.of("message", "Which module should I do next?", "assistantPage", "MODULES", "area", "list"))
                .andExpect(status().isOk());
        send(put("/api/chain-assessments"), user, Map.of("takenOn", TODAY.toString(), "settlingScore", 3,
                "fearScore", 2, "criticismScore", 3, "visionScore", 4, "knowledgeScore", 3, "resourcesScore", 2)).andExpect(status().isOk());
        send(post("/api/ai/chat"), user, Map.of("message", "Explain my result", "assistantPage", "ASSESSMENT", "area", "result"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, org.mockito.Mockito.times(2)).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getAllValues().get(0)).contains("The 15 modules", "No assessment taken yet", "Plan counts: 0 dreams");
        assertThat(system.getAllValues().get(1)).contains("Assessment takes (every take)", "fear 2", "resources 2");

        // Module 1 has a form: empty fields are listed, and cards may only fill its own fields. Module 4 comes first.
        completeModules("page-modules", 4);
        JsonNode check = read(send(post("/api/ai/pages/assistant/check"), user, Map.of("page", "MODULE", "area", "exercise", "itemId", 1,
                "form", Map.of("row0.belief", "I am too old to learn this", "row0.replacement", ""))).andExpect(status().isOk()));
        assertThat(check.get("canPropose").asBoolean()).isTrue();
        assertThat(fields(check)).containsExactly("row0.replacement");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.PageAssistantDraft("", List.of(
                        new AiDrafts.PageChangeDraft("row0.replacement", "I can learn one tool at a time.", "Your own words."),
                        new AiDrafts.PageChangeDraft("row5.replacement", "Too far down.", "Not a row on screen."),
                        new AiDrafts.PageChangeDraft("row0.secret", "Not a field.", "Unknown."))), "claude-opus-5-5", 10, 10, false));
        JsonNode answer = read(send(post("/api/ai/pages/assistant/proposals"), user, Map.of("page", "MODULE", "area", "exercise",
                "itemId", 1, "form", Map.of("row0.belief", "I am too old to learn this"))).andExpect(status().isOk()));
        assertThat(answer.get("changes")).hasSize(1);
        assertThat(answer.get("changes").get(0).get("field").asText()).isEqualTo("row0.replacement");

        // Module 4 is done on another page: help, but no cards and no AI request for them.
        JsonNode linked = read(send(post("/api/ai/pages/assistant/check"), user, Map.of("page", "MODULE", "area", "exercise", "itemId", 4))
                .andExpect(status().isOk()));
        assertThat(linked.get("canPropose").asBoolean()).isFalse();
        JsonNode none = read(send(post("/api/ai/pages/assistant/proposals"), user, Map.of("page", "MODULE", "area", "exercise", "itemId", 4))
                .andExpect(status().isOk()));
        assertThat(none.get("changes")).isEmpty();
        assertThat(none.get("requestId").isNull()).isTrue();
        // A module page needs its module, and an unknown module answers 404.
        send(post("/api/ai/pages/assistant/check"), user, Map.of("page", "MODULE", "area", "exercise")).andExpect(status().isBadRequest());
        send(post("/api/ai/pages/assistant/check"), user, Map.of("page", "MODULE", "area", "exercise", "itemId", 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void workStyleChatSendsTheResultAndPartnerCountsButNoNames() throws Exception {
        String user = consented(email("page-work-style"));
        send(put("/api/work-style-profile"), user, Map.of("paceFastScore", 6, "paceDeliberateScore", 2, "focusTaskScore", 5,
                "focusPeopleScore", 3)).andExpect(status().isOk());
        long partnerId = partner(user);
        Map<String, Object> styled = new HashMap<>(Map.of("name", "Dr Secret Mentor", "role", "Research lead", "supportType", "MENTOR",
                "status", "ACTIVE", "workStyleType", "PLANNER"));
        send(put("/api/partners/" + partnerId), user, styled).andExpect(status().isOk());
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("Pace is a clear lean.", "claude-opus-5-5", 10, 10, false, false));

        JsonNode turn = read(send(post("/api/ai/chat"), user, Map.of("message", "What does my result mean?", "assistantPage", "WORK_STYLE",
                "area", "result")).andExpect(status().isOk()));
        assertThat(sources(turn)).contains("workStyle");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getValue()).contains("dominant DRIVER", "fast 6, deliberate 2", "PLANNER=1", "MENTOR=1")
                .doesNotContain("Dr Secret Mentor", "secret@example.com");
    }

    @Test
    void linkedItemsAreReadAsTheParticipantsOwnAndAnotherUsersLinkAnswers404() throws Exception {
        String user = consented(email("page-linked"));
        long areaId = read(send(post("/api/vision-areas"), user, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> dream = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Publish a paper", "whyImportant", "Growth",
                "successDefinition", "Accepted paper", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "HIGH", "status", "ACTIVE",
                "moonshot", false));
        dream.put("targetDate", TODAY.plusYears(1).toString());
        dream.put("scheduleMode", "BOTTOM_UP");
        long dreamId = read(send(post("/api/dreams"), user, dream).andExpect(status().isCreated())).get("id").asLong();
        long goalId = read(send(post("/api/goals"), user, Map.of("dreamId", dreamId, "title", "Write the concept note", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(3).toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"))
                .andExpect(status().isCreated())).get("id").asLong();
        long stepId = read(send(post("/api/steps"), user, Map.of("owner", "Me", "goalId", goalId, "title", "Search literature",
                "sequenceNumber", 1, "complex", true, "priority", "HIGH", "targetDate", TODAY.plusMonths(1).toString(),
                "status", "IN_PROGRESS")).andExpect(status().isCreated())).get("id").asLong();
        long taskId = read(send(post("/api/tasks"), user, Map.of("stepId", stepId, "title", "Search PubMed", "owner", "Me",
                "dueDate", TODAY.plusWeeks(1).toString(), "priority", "HIGH", "status", "IN_PROGRESS",
                "progressPercent", 40)).andExpect(status().isCreated())).get("id").asLong();
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("Try a narrower search.", "claude-opus-5-5", 10, 10, false, false));

        send(post("/api/ai/chat"), user, Map.of("message", "What could I try?", "assistantPage", "OBSTACLES", "area", "form",
                "form", Map.of("title", "Too many papers", "relatedDreamId", String.valueOf(dreamId), "relatedGoalId", String.valueOf(goalId),
                        "relatedStepId", String.valueOf(stepId), "relatedTaskId", String.valueOf(taskId)))).andExpect(status().isOk());
        send(post("/api/ai/chat"), user, Map.of("message", "Check my message", "assistantPage", "COMMUNICATION", "area", "message",
                "form", Map.of("subject", "Search help", "relatedGoalId", String.valueOf(goalId), "relatedTaskId", String.valueOf(taskId))))
                .andExpect(status().isOk());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, org.mockito.Mockito.times(2)).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getAllValues().get(0)).contains("Linked dream: Publish a paper", "Linked goal: Write the concept note",
                "Linked step: Search literature", "Linked task: Search PubMed", "progress 40%");
        assertThat(system.getAllValues().get(1)).contains("Linked goal: Write the concept note", "Linked task: Search PubMed");

        // Another user's dream or partner in the unsaved form answers 404.
        String other = consented(email("page-linked-other"));
        send(post("/api/ai/pages/assistant/check"), other, Map.of("page", "OBSTACLES", "area", "form",
                "form", Map.of("title", "Mine", "relatedDreamId", String.valueOf(dreamId)))).andExpect(status().isNotFound());
        send(post("/api/ai/pages/assistant/check"), other, Map.of("page", "COMMUNICATION", "area", "message",
                "form", Map.of("partnerId", String.valueOf(partner(user))))).andExpect(status().isNotFound());
    }

    @Test
    void metricsCountPageAnswersAndModuleBaselines() throws Exception {
        String user = consented(email("page-metrics-flow"));
        completeModules("page-metrics-flow", 4, 1);
        send(post("/api/communication-messages"), user, Map.of("partnerId", partner(user), "subject", "Late reply", "status", "SENT",
                "followUpDate", TODAY.minusDays(3).toString())).andExpect(status().isCreated());
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.PageAssistantDraft("", List.of(
                        new AiDrafts.PageChangeDraft("request", "Twenty minutes on Tuesday?", "Specific."))), "claude-opus-5-5", 10, 10, false));
        long requestId = read(send(post("/api/ai/pages/assistant/proposals"), user, Map.of("page", "COMMUNICATION", "area", "message",
                "form", Map.of("subject", "Help"))).andExpect(status().isOk())).get("requestId").asLong();
        send(post("/api/ai/pages/assistant/requests/" + requestId + "/outcome"), user, Map.of("added", 0, "edited", 1))
                .andExpect(status().isNoContent());

        JsonNode metrics = read(mockMvc.perform(get("/api/ai/pages/metrics?days=7").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()));
        JsonNode row = null;
        for (JsonNode feature : metrics.get("features")) {
            if ("PAGE_ASSISTANT".equals(feature.get("feature").asText())) {
                row = feature;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("editedItems").asLong()).isPositive();
        assertThat(metrics.get("baselines").get("moduleCompletions").asLong()).isPositive();
        assertThat(metrics.get("baselines").get("followUpsOverdue").asLong()).isPositive();
    }

    // ---------- helpers ----------

    private long partner(String token) throws Exception {
        return read(send(post("/api/partners"), token, Map.of("name", "Dr Secret Mentor", "role", "Research lead",
                "email", "secret@example.com", "phone", "+855 12 345", "notes", "Private partner notes", "supportType", "MENTOR",
                "status", "TO_CONTACT")).andExpect(status().isCreated())).get("id").asLong();
    }

    private long obstacle(String token, long partnerId, String title) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "obstacleType", "MOTIVATION", "severity", "HIGH",
                "status", "OPEN", "requiredPartnerId", partnerId, "conflictPrivateNote", "Saved private reaction"));
        return read(send(post("/api/obstacles"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Marks modules done for the user whose email starts with prefix, a few minutes apart, as if saved in order. */
    private void completeModules(String prefix, int... numbers) {
        for (int index = 0; index < numbers.length; index++) {
            jdbcTemplate.update("insert into module_completions (user_id, module_number, completed_at, created_at, updated_at) "
                    + "select id, ?, dateadd('MINUTE', -?, current_timestamp), current_timestamp, current_timestamp from app_users where email like ?",
                    numbers[index], (numbers.length - index) * 5, prefix + "-%");
        }
    }

    private static List<String> fields(JsonNode check) {
        List<String> fields = new ArrayList<>();
        check.get("issues").forEach(issue -> fields.add(issue.get("field").asText()));
        return fields;
    }

    private static List<String> sources(JsonNode turn) {
        List<String> sources = new ArrayList<>();
        turn.get("reply").get("contextSources").forEach(source -> sources.add(source.asText()));
        return sources;
    }

    private String lastFeature(String prefix) {
        return jdbcTemplate.queryForObject("select feature from ai_usage_logs where user_id = (select id from app_users where email like '"
                + prefix + "-%') order by id desc limit 1", String.class);
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Page Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
