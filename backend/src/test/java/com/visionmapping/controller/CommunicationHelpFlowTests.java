package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.time.ZoneId;
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

/** V15 Phase 3 (AH-FR-43 to AH-FR-50, AH-FR-52 to AH-FR-54, AH-FR-61 to AH-FR-67): the Communication helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunicationHelpFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.systemDefault());

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
        String admin = register(email("message-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'message-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void theFollowUpListAndTheDraftOrderNeedNoAi() throws Exception {
        String user = register(email("message-lists")); // no AI consent
        long partnerId = partner(user);
        long overdue = message(user, partnerId, "First ask", "SENT", TODAY.minusDays(4), null);
        long followedUp = message(user, partnerId, "Second nudge", "FOLLOWED_UP", TODAY.minusDays(1), null);
        message(user, partnerId, "Not yet due", "SENT", TODAY.plusDays(2), null);
        message(user, partnerId, "Already answered", "REPLIED", TODAY.minusDays(9), null);

        JsonNode due = read(mockMvc.perform(get("/api/communication-messages/follow-up-due").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(due).hasSize(2);
        assertThat(due.get(0).get("id").asLong()).isEqualTo(overdue);
        assertThat(due.get(0).get("daysOverdue").asLong()).isEqualTo(4);
        assertThat(due.get(0).get("partnerName").asText()).isEqualTo("Dr Secret Mentor");
        assertThat(due.get(1).get("id").asLong()).isEqualTo(followedUp);

        Plan plan = plan(user);
        long blockedTask = task(user, plan.stepId(), "Get the data", "BLOCKED", "Waiting for access", TODAY.plusDays(20));
        long soonTask = task(user, plan.stepId(), "Book the room", "IN_PROGRESS", null, TODAY.plusDays(3));
        long plain = message(user, partnerId, "No task", "DRAFT", null, null);
        long soon = message(user, partnerId, "Room booking", "DRAFT", null, soonTask);
        long blocked = message(user, partnerId, "Data access", "DRAFT", null, blockedTask);
        JsonNode order = read(mockMvc.perform(get("/api/communication-messages/draft-priority").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(order).hasSize(3);
        assertThat(order.get(0).get("id").asLong()).isEqualTo(blocked);
        assertThat(order.get(0).get("taskBlocked").asBoolean()).isTrue();
        assertThat(order.get(1).get("id").asLong()).isEqualTo(soon);
        assertThat(order.get(2).get("id").asLong()).isEqualTo(plain);
        verifyNoInteractions(claudeClient);
    }

    @Test
    void fillProposesOnlyEmptyFieldsFromOneSentence() throws Exception {
        String user = consented(email("message-fill"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(structured(new AiDrafts.PageAssistantDraft("", List.of(
                        new AiDrafts.PageChangeDraft("request", "Overwrite my request", "Already written."),
                        new AiDrafts.PageChangeDraft("hook", "Your talk on rural clinics stayed with me.", "Specific."),
                        new AiDrafts.PageChangeDraft("status", "SENT", "Never set by a helper."),
                        new AiDrafts.PageChangeDraft("messageBody", "A whole body", "Left to the participant.")))));

        JsonNode answer = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "FILL",
                "intent", "ask my mentor to review my concept note", "form", Map.of("request", "Twenty minutes of feedback")))
                .andExpect(status().isOk()));
        assertThat(answer.get("changes")).hasSize(1);
        assertThat(answer.get("changes").get(0).get("field").asText()).isEqualTo("hook");
        assertThat(lastFeature("message-fill")).isEqualTo("MESSAGE_FIELD_HELP");
        // A sentence is needed.
        send(post("/api/ai/communication/help"), user, Map.of("mode", "FILL", "form", Map.of("subject", "Hi")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkListsProblemsWithFixCardsForAllowedFieldsOnly() throws Exception {
        String user = consented(email("message-check"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.MessageCheckDraft.class)))
                .thenReturn(structured(new AiDrafts.MessageCheckDraft(new ArrayList<>(java.util.Arrays.asList(
                        new AiDrafts.MessageIssueDraft("The request does not say when.", "request", "Could you review it by Friday, for 20 minutes?"),
                        new AiDrafts.MessageIssueDraft("The tone sounds demanding.", null, null),
                        new AiDrafts.MessageIssueDraft("Mark it sent.", "status", "SENT"),
                        null,
                        new AiDrafts.MessageIssueDraft(" ", "hook", "Hello"))))));

        JsonNode answer = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "CHECK",
                "form", Map.of("request", "Review my note.", "messageBody", "You must review my note now."))).andExpect(status().isOk()));
        assertThat(answer.get("issues")).hasSize(3);
        assertThat(answer.get("issues").get(0).get("fix").get("field").asText()).isEqualTo("request");
        assertThat(answer.get("issues").get(1).get("fix").isNull()).isTrue();
        assertThat(answer.get("issues").get(2).get("fix").isNull()).isTrue();
        assertThat(lastFeature("message-check")).isEqualTo("MESSAGE_CHECK");
    }

    @Test
    void toneRebuildAndObjectionsEachChangeOnlyTheirOwnField() throws Exception {
        String user = consented(email("message-tone"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(structured(new AiDrafts.PageAssistantDraft("", List.of(
                        new AiDrafts.PageChangeDraft("messageBody", "សូមជួយពិនិត្យ", ""),
                        new AiDrafts.PageChangeDraft("objectionsAndAnswers", "Too busy – it takes 20 minutes.", ""),
                        new AiDrafts.PageChangeDraft("request", "Changed request", "")))));
        Map<String, String> form = Map.of("request", "Review my note", "messageBody", "Please review my note.");

        JsonNode tone = read(send(post("/api/ai/communication/help?language=en"), user, Map.of("mode", "TONE", "tone", "KHMER", "form", form))
                .andExpect(status().isOk()));
        assertThat(fields(tone.get("changes"))).containsExactly("messageBody");
        JsonNode body = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "BODY", "form", form)).andExpect(status().isOk()));
        assertThat(fields(body.get("changes"))).containsExactly("messageBody");
        JsonNode objections = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "OBJECTIONS", "form", form))
                .andExpect(status().isOk()));
        assertThat(fields(objections.get("changes"))).containsExactly("objectionsAndAnswers");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, org.mockito.Mockito.times(3))
                .structured(anyString(), anyString(), anyString(), prompt.capture(), eq(AiDrafts.PageAssistantDraft.class));
        // KHMER answers in Khmer even when the page is in English.
        assertThat(prompt.getAllValues().get(0)).contains("polite Khmer", "Answer in Khmer");
        assertThat(prompt.getAllValues().get(1)).contains("from the structured fields only");
        send(post("/api/ai/communication/help"), user, Map.of("mode", "TONE", "form", form)).andExpect(status().isBadRequest());
    }

    @Test
    void followUpAndThankYouOpenNewMessagesToTheSamePartnerAndChangeNothing() throws Exception {
        String user = consented(email("message-follow"));
        long partnerId = partner(user);
        Plan plan = plan(user);
        long taskId = task(user, plan.stepId(), "Finish the concept note", "IN_PROGRESS", null, TODAY.plusDays(10));
        message(user, partnerId, "Old ask about data", "REPLIED", null, null, "Old body that must stay private");
        long sent = message(user, partnerId, "Concept note review", "SENT", TODAY.minusDays(3), taskId);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.NewMessageDraft.class)))
                .thenReturn(structured(new AiDrafts.NewMessageDraft("", "Dear Dr, a quick follow-up...", "Would Tuesday or Thursday suit?")));

        JsonNode follow = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "FOLLOW_UP", "messageId", sent))
                .andExpect(status().isOk()));
        JsonNode draft = follow.get("message");
        assertThat(draft.get("partnerId").asLong()).isEqualTo(partnerId);
        assertThat(draft.get("subject").asText()).isEqualTo("Following up: Concept note review");
        assertThat(draft.get("request").asText()).isEqualTo("Would Tuesday or Thursday suit?");
        assertThat(draft.get("relatedTaskId").asLong()).isEqualTo(taskId);
        assertThat(lastFeature("message-follow")).isEqualTo("FOLLOW_UP_DRAFT");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.NewMessageDraft.class));
        assertThat(system.getValue()).contains("Dr Secret Mentor", "Old ask about data", "Linked task: Finish the concept note")
                .doesNotContain("secret@example.com", "+855 12 345", "Old body that must stay private");

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.NewMessageDraft.class)))
                .thenReturn(structured(new AiDrafts.NewMessageDraft("Thank you", "Thank you for your help...", "A new ask")));
        JsonNode thanks = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "THANK_YOU", "messageId", sent))
                .andExpect(status().isOk()));
        assertThat(thanks.get("message").get("subject").asText()).isEqualTo("Thank you");
        assertThat(thanks.get("message").get("request").asText()).isEmpty();

        // AH-FR-52: the sent message keeps its status and date, and no message is created.
        assertThat(jdbcTemplate.queryForMap("select status, follow_up_date from communication_messages where id = ?", sent))
                .containsEntry("status", "SENT");
        assertThat(jdbcTemplate.queryForObject("select count(*) from communication_messages where user_id = "
                + "(select id from app_users where email like 'message-follow-%')", Integer.class)).isEqualTo(2);
        // A follow-up needs a saved message.
        send(post("/api/ai/communication/help"), user, Map.of("mode", "FOLLOW_UP", "form", Map.of("subject", "x")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meetingPrepIsATaskTitledWithThePartnersRoleNotName() throws Exception {
        String user = consented(email("message-meeting"));
        long messageId = message(user, partner(user), "Concept note review", "REPLIED", null, null);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.MeetingPlanDraft.class)))
                .thenReturn(structured(new AiDrafts.MeetingPlanDraft("Agree the method", List.of("Q1?", "Q2?", "Q3?", "Q4?"),
                        "The draft note", "Could you look at the revised note next month?")));

        JsonNode answer = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "MEETING_PREP", "messageId", messageId))
                .andExpect(status().isOk()));
        JsonNode task = answer.get("task");
        assertThat(task.get("title").asText()).isEqualTo("Meet Research lead").doesNotContain("Secret");
        assertThat(task.get("owner").asText()).isEqualTo("Me");
        assertThat(task.get("description").asText()).contains("Outcome: Agree the method", "Question 3: Q3?", "Closing ask:")
                .doesNotContain("Q4?");
    }

    @Test
    void whichMessageFirstAddsReasonsAndHelpersCheckOwnershipFirst() throws Exception {
        String user = consented(email("message-first"));
        long partnerId = partner(user);
        long draft = message(user, partnerId, "Data access", "DRAFT", null, null);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PriorityReasons.class)))
                .thenReturn(structured(new AiDrafts.PriorityReasons(List.of(new AiDrafts.PriorityReason(1, "It has waited longest.")))));
        JsonNode answer = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "PRIORITISE")).andExpect(status().isOk()));
        assertThat(answer.get("ranking").get(0).get("id").asLong()).isEqualTo(draft);
        assertThat(answer.get("ranking").get(0).get("reason").asText()).isEqualTo("It has waited longest.");

        String other = register(email("message-first-other"));
        send(post("/api/ai/communication/help"), other, Map.of("mode", "CHECK", "messageId", draft)).andExpect(status().isNotFound());
        send(post("/api/ai/communication/help"), other, Map.of("mode", "CHECK", "form", Map.of("partnerId", String.valueOf(partnerId))))
                .andExpect(status().isNotFound());
        send(post("/api/ai/communication/help"), user, Map.of("mode", "CHECK")).andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private record Plan(long dreamId, long stepId) {
    }

    private Plan plan(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> dream = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Publish a paper", "whyImportant", "Growth",
                "successDefinition", "Accepted paper", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "HIGH", "status", "ACTIVE",
                "moonshot", false));
        dream.put("targetDate", TODAY.plusYears(1).toString());
        dream.put("scheduleMode", "BOTTOM_UP");
        long dreamId = read(send(post("/api/dreams"), token, dream).andExpect(status().isCreated())).get("id").asLong();
        long goalId = read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Write the concept note", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(3).toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"))
                .andExpect(status().isCreated())).get("id").asLong();
        long stepId = read(send(post("/api/steps"), token, Map.of("owner", "Me", "goalId", goalId, "title", "Search literature",
                "sequenceNumber", 1, "complex", true, "priority", "HIGH", "targetDate", TODAY.plusMonths(1).toString(),
                "status", "IN_PROGRESS")).andExpect(status().isCreated())).get("id").asLong();
        return new Plan(dreamId, stepId);
    }

    private long task(String token, long stepId, String title, String taskStatus, String blockerReason, LocalDate due) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", stepId, "title", title, "owner", "Me", "dueDate", due.toString(),
                "priority", "HIGH", "status", taskStatus, "progressPercent", 10));
        if (blockerReason != null) {
            body.put("blockerReason", blockerReason);
        }
        return read(send(post("/api/tasks"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long partner(String token) throws Exception {
        return read(send(post("/api/partners"), token, Map.of("name", "Dr Secret Mentor", "role", "Research lead",
                "email", "secret@example.com", "phone", "+855 12 345", "supportType", "MENTOR", "status", "ACTIVE"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long message(String token, long partnerId, String subject, String messageStatus, LocalDate followUp, Long taskId)
            throws Exception {
        return message(token, partnerId, subject, messageStatus, followUp, taskId, null);
    }

    private long message(String token, long partnerId, String subject, String messageStatus, LocalDate followUp, Long taskId,
                         String body) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("partnerId", partnerId, "subject", subject, "status", messageStatus,
                "request", "Twenty minutes of feedback"));
        if (followUp != null) {
            request.put("followUpDate", followUp.toString());
        }
        if (taskId != null) {
            request.put("relatedTaskId", taskId);
        }
        if (body != null) {
            request.put("messageBody", body);
        }
        return read(send(post("/api/communication-messages"), token, request).andExpect(status().isCreated())).get("id").asLong();
    }

    private static <T> ClaudeClient.Structured<T> structured(T value) {
        return new ClaudeClient.Structured<>(value, "claude-opus-5-5", 10, 10, false);
    }

    private static List<String> fields(JsonNode changes) {
        List<String> fields = new ArrayList<>();
        changes.forEach(change -> fields.add(change.get("field").asText()));
        return fields;
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
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Message Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
