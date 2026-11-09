package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

/** V11 Phase 7 (PB-FR-41 to PB-FR-50, PB-NFR-11 to PB-NFR-14): the plan assistant. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanAssistantFlowTests {

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
        String admin = register(email("assistant-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'assistant-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void theSectionCheckWorksWithoutAiAndKeepsToTheSection() throws Exception {
        String user = register(email("section-rules")); // no AI consent
        long dreamId = dream(user);
        long goalId = goal(user, dreamId, "Save a deposit", TODAY.plusMonths(3));
        goal(user, dreamId, "Fix my credit", TODAY.plusMonths(3));
        step(user, goalId, "Gather papers", TODAY.plusWeeks(2), "");

        JsonNode steps = check(user, dreamId, "PLAN", "STEPS");
        assertThat(kinds(steps)).containsExactly("NO_STEPS");
        assertThat(steps.get("canPropose").asBoolean()).isTrue();
        assertThat(kinds(check(user, dreamId, "PLAN", "RESOURCES"))).containsExactly("MISSING_RESOURCE");
        assertThat(check(user, dreamId, "PLAN", "RESOURCES").get("issues").get(0).get("itemRef").asText()).startsWith("step:");
        JsonNode people = check(user, dreamId, "PLAN", "PEOPLE");
        assertThat(people.get("issues")).isEmpty();
        assertThat(people.get("canPropose").asBoolean()).isFalse();

        Map<String, Object> complex = draftStep("s1", "Visit banks", "Me", TODAY.plusWeeks(2), true, List.of(
                Map.of("key", "t1", "title", "Call the bank", "owner", "", "dueDate", TODAY.plusWeeks(3).toString(), "priority", "LOW")));
        Map<String, Object> bare = draftStep("s2", "Compare lenders", "Me", TODAY.plusWeeks(4), true, List.of());
        bare.put("resources", "Comparison site");
        saveDraft(user, dreamId, List.of(draftGoal("g1", "Save more", TODAY.plusMonths(2), List.of(complex, bare))));
        assertThat(kinds(check(user, dreamId, "BUILDER", "TASKS")))
                .containsExactlyInAnyOrder("COMPLEX_NO_TASKS", "MISSING_OWNER", "TASK_AFTER_STEP");
        assertThat(kinds(check(user, dreamId, "BUILDER", "STEPS"))).doesNotContain("COMPLEX_NO_TASKS", "TASK_AFTER_STEP");

        // A section that is not on the page, and another user's dream.
        send(post(path(dreamId, "section-check")), user, Map.of("surface", "PLAN", "section", "TASKS")).andExpect(status().isBadRequest());
        String other = register(email("section-other"));
        send(post(path(dreamId, "section-check")), other, Map.of("surface", "PLAN", "section", "STEPS")).andExpect(status().isNotFound());
        verifyNoInteractions(claudeClient);
    }

    @Test
    void chatAnswersUseOnlyTheSectionAndAreKeptPerSection() throws Exception {
        String user = consented(email("section-chat"));
        long dreamId = dream(user);
        long goalId = goal(user, dreamId, "Save a deposit", TODAY.plusMonths(3));
        step(user, goalId, "Open a savings account", TODAY.plusWeeks(2), "Payslips");
        send(post("/api/partners"), user, Map.of("name", "Dr Secret Mentor", "email", "secret@example.com", "supportType", "MENTOR",
                "status", "TO_CONTACT", "relatedDreamId", dreamId)).andExpect(status().isCreated());
        when(claudeClient.send(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new ClaudeClient.Reply("Make each goal measurable.", "claude-opus-5-5", 10, 10, false, false));

        JsonNode turn = read(send(post("/api/ai/chat"), user, Map.of("message", "Is this goal specific enough?", "dreamId", dreamId,
                "surface", "PLAN", "section", "GOALS", "itemRef", "goal:" + goalId)).andExpect(status().isOk()));
        assertThat(turn.get("reply").get("contextSources").get(0).asText()).isEqualTo("section");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).send(anyString(), anyString(), system.capture(), anyList());
        assertThat(system.getValue()).contains("Goals section", "Save a deposit", "The participant is asking about [goal:" + goalId + "]")
                // Steps belong to another section; partner names and contacts never go to Claude.
                .doesNotContain("Open a savings account", "Dr Secret Mentor", "secret@example.com");
        assertThat(jdbcTemplate.queryForObject("select feature from ai_usage_logs where user_id = (select id from app_users where email like 'section-chat-%') order by id desc limit 1",
                String.class)).isEqualTo("PLAN_ASSISTANT_CHAT");

        long conversationId = turn.get("conversationId").asLong();
        mockMvc.perform(get("/api/ai/conversations?dreamId=" + dreamId + "&section=GOALS").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(conversationId))
                .andExpect(jsonPath("$[0].section").value("GOALS"));
        mockMvc.perform(get("/api/ai/conversations?dreamId=" + dreamId + "&section=STEPS").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.length()").value(0));

        // Another user's dream answers 404, even before AI is checked.
        String other = register(email("section-chat-other"));
        send(post("/api/ai/chat"), other, Map.of("message", "Hello", "dreamId", dreamId, "surface", "PLAN", "section", "GOALS"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ai/conversations?dreamId=" + dreamId + "&section=GOALS").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void proposalsKeepOnlyChangesThatFitTheSectionAndItsDates() throws Exception {
        String user = consented(email("section-propose"));
        long dreamId = dream(user);
        long savedGoal = goal(user, dreamId, "Save a deposit", TODAY.plusMonths(3));
        LocalDate goalDate = TODAY.plusMonths(2);
        Map<String, Object> existing = new HashMap<>(draftGoal("g0", "Save a deposit", null, List.of()));
        existing.put("existingId", savedGoal);
        Map<String, Object> fresh = draftGoal("g1", "Fix my credit", goalDate,
                List.of(draftStep("s1", "Do credit stuff", "Me", TODAY.plusWeeks(2), false, List.of())));
        saveDraft(user, dreamId, List.of(existing, fresh));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AssistantChangeDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.AssistantChangeDrafts(List.of(
                        change("EDIT", "s1", "TITLE", "Request a free credit report", null, null, null, null),
                        change("EDIT", "s1", "OWNER", "", null, null, null, null),
                        change("EDIT", "s1", "DATE", goalDate.plusDays(5).toString(), null, null, null, null),
                        change("EDIT", "g0", "TITLE", "Renamed saved goal", null, null, null, null),
                        change("EDIT", "nope", "TITLE", "Unknown", null, null, null, null),
                        change("ADD_STEP", null, null, null, "g1", "Pay off the card", "", goalDate.plusDays(3).toString()),
                        change("ADD_STEP", null, null, null, "g0", "Set up a transfer", "Me", TODAY.plusWeeks(1).toString()),
                        change("ADD_TASK", null, null, null, "s1", "Not in this section", "Me", null),
                        change("EDIT", "dream", "WHY", "Not in this section", null, null, null, null)),
                        "Two small fixes."), "claude-opus-5-5", 10, 10, false));

        JsonNode proposals = read(send(post(path(dreamId, "proposals")), user, Map.of("surface", "BUILDER", "section", "STEPS"))
                .andExpect(status().isOk()));
        List<String> kept = new ArrayList<>();
        proposals.get("changes").forEach(item -> kept.add(item.get("kind").asText() + ":"
                + (item.hasNonNull("itemRef") ? item.get("itemRef").asText() + ":" + item.get("field").asText() + "=" + item.get("value").asText()
                : item.get("parentRef").asText() + ":" + item.get("title").asText() + ":" + item.get("owner").asText() + ":" + item.get("date").asText())));
        // Saved items are never changed, unknown refs and other sections' kinds are dropped, a late date is dropped or cleared.
        assertThat(kept).containsExactly(
                "EDIT:s1:TITLE=Request a free credit report",
                "EDIT:s1:OWNER=Me",
                "ADD_STEP:g1:Pay off the card:Me:null",
                "ADD_STEP:g0:Set up a transfer:Me:" + TODAY.plusWeeks(1));
        assertThat(proposals.get("note").asText()).isEqualTo("Two small fixes.");
        long requestId = proposals.get("requestId").asLong();
        assertThat(jdbcTemplate.queryForObject("select suggested_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(4);
        // Outcomes are counted through the existing endpoint; nothing is saved by proposing.
        send(post("/api/ai/plan/requests/" + requestId + "/outcome"), user, Map.of("added", 1, "edited", 1)).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isEqualTo(1);

        // The prompt names the section and marks which items can change.
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.AssistantChangeDrafts.class));
        assertThat(system.getValue()).contains("Goal [g0] (already in the plan)", "Step [s1] (new, can change) Do credit stuff");

        send(post(path(dreamId, "proposals")), user, Map.of("surface", "PLAN", "section", "PEOPLE")).andExpect(status().isBadRequest());
        String other = register(email("section-propose-other"));
        send(post(path(dreamId, "proposals")), other, Map.of("surface", "BUILDER", "section", "STEPS")).andExpect(status().isNotFound());
    }

    @Test
    void aCardIsSavedOnThePlanPageOnlyThroughTheNormalChecks() throws Exception {
        String user = consented(email("section-apply"));
        long dreamId = dream(user);
        long goalId = goal(user, dreamId, "Save a deposit", TODAY.plusMonths(3));
        long stepId = step(user, goalId, "Do finance stuff", TODAY.plusWeeks(2), "Payslips");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AssistantChangeDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.AssistantChangeDrafts(List.of(
                        change("EDIT", "step:" + stepId, "TITLE", "Open a savings account", null, null, null, null),
                        change("ADD_STEP", null, null, null, "goal:" + goalId, "Set up a monthly transfer", "Me",
                                TODAY.plusWeeks(3).toString())), null), "claude-opus-5-5", 10, 10, false));
        long requestId = read(send(post(path(dreamId, "proposals")), user, Map.of("surface", "PLAN", "section", "STEPS")))
                .get("requestId").asLong();

        Map<String, Object> rename = new HashMap<>(Map.of("kind", "EDIT", "itemRef", "step:" + stepId, "field", "TITLE",
                "value", "Open a savings account", "requestId", requestId, "edited", false));
        send(post(path(dreamId, "apply")), user, rename).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(stepId));
        assertThat(jdbcTemplate.queryForObject("select title from vision_steps where id = ?", String.class, stepId)).isEqualTo("Open a savings account");
        // Everything else about the step stays as it was.
        assertThat(jdbcTemplate.queryForObject("select resources from vision_steps where id = ?", String.class, stepId)).isEqualTo("Payslips");

        Map<String, Object> add = new HashMap<>(Map.of("kind", "ADD_STEP", "parentRef", "goal:" + goalId, "title", "Set up a monthly transfer",
                "owner", "Me", "requestId", requestId, "edited", true));
        send(post(path(dreamId, "apply")), user, add).andExpect(status().isBadRequest()); // no date yet
        add.put("date", TODAY.plusWeeks(3).toString());
        send(post(path(dreamId, "apply")), user, add).andExpect(status().isCreated()).andExpect(jsonPath("$.itemType").value("STEP"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from vision_steps where goal_id = ?", Integer.class, goalId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select added_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select edited_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(1);

        send(post(path(dreamId, "apply")), user, Map.of("kind", "ADD_RISK", "parentRef", "step:" + stepId, "title", "Bank may refuse",
                "obstacleType", "MONEY", "severity", "HIGH", "solution", "Try a second bank")).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("select count(*) from obstacles where related_step_id = ?", Integer.class, stepId)).isEqualTo(1);
        send(post(path(dreamId, "apply")), user, Map.of("kind", "EDIT", "itemRef", "dream", "field", "WHY", "value", "So my family has a home"))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("select why_important from dreams where id = ?", String.class, dreamId))
                .isEqualTo("So my family has a home");
        send(post(path(dreamId, "apply")), user, Map.of("kind", "ADD_TASK", "parentRef", "step:" + stepId, "title", "x"))
                .andExpect(status().isBadRequest());

        // Another user's step, or a step of another dream, is not in this plan.
        String other = consented(email("section-apply-other"));
        long otherDream = dream(other);
        send(post(path(otherDream, "apply")), other, Map.of("kind", "EDIT", "itemRef", "step:" + stepId, "field", "TITLE", "value", "Mine"))
                .andExpect(status().isNotFound());
        long secondDream = dream(user);
        send(post(path(secondDream, "apply")), user, Map.of("kind", "EDIT", "itemRef", "step:" + stepId, "field", "TITLE", "value", "Moved"))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select title from vision_steps where id = ?", String.class, stepId)).isEqualTo("Open a savings account");
        verify(claudeClient, never()).send(anyString(), anyString(), anyString(), anyList());
    }

    // ---------- helpers ----------

    private static AiDrafts.AssistantChangeDraft change(String kind, String itemRef, String field, String value, String parentRef,
                                                         String title, String owner, String date) {
        return new AiDrafts.AssistantChangeDraft(kind, itemRef, field, value, parentRef, title, owner, date, null, null, null, "Clearer");
    }

    private JsonNode check(String token, long dreamId, String surface, String section) throws Exception {
        return read(send(post(path(dreamId, "section-check")), token, Map.of("surface", surface, "section", section))
                .andExpect(status().isOk()));
    }

    private static List<String> kinds(JsonNode check) {
        List<String> kinds = new ArrayList<>();
        check.get("issues").forEach(item -> kinds.add(item.get("kind").asText()));
        return kinds;
    }

    private static String path(long dreamId, String action) {
        return "/api/ai/plan/dreams/" + dreamId + "/assistant/" + action;
    }

    private static Map<String, Object> draftGoal(String key, String title, LocalDate date, List<Map<String, Object>> steps) {
        Map<String, Object> goal = new HashMap<>(Map.of("key", key, "title", title, "priority", "HIGH", "steps", steps));
        goal.put("targetDate", date == null ? null : date.toString());
        return goal;
    }

    private static Map<String, Object> draftStep(String key, String title, String owner, LocalDate date, boolean complex,
                                                 List<Map<String, Object>> tasks) {
        return new HashMap<>(Map.of("key", key, "title", title, "owner", owner, "targetDate", date.toString(),
                "priority", "MEDIUM", "complex", complex, "tasks", tasks));
    }

    private void saveDraft(String token, long dreamId, List<Map<String, Object>> goals) throws Exception {
        send(put("/api/ai/plan/drafts/" + dreamId), token, Map.of("stage", "STEPS", "content", Map.of("goals", goals)))
                .andExpect(status().isOk());
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Home", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Own a home", "whyImportant", "Family",
                "successDefinition", "Keys in hand", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM",
                "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        body.put("targetDate", TODAY.plusYears(1).toString());
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long goal(String token, long dreamId, String title, LocalDate date) throws Exception {
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", title, "priority", "HIGH",
                "targetDate", date.toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long step(String token, long goalId, String title, LocalDate date, String resources) throws Exception {
        return read(send(post("/api/steps"), token, Map.of("owner", "Me", "goalId", goalId, "title", title, "sequenceNumber", 1,
                "complex", false, "priority", "HIGH", "targetDate", date.toString(), "status", "NOT_STARTED", "resources", resources))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Assistant Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
