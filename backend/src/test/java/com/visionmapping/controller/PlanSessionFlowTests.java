package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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

/** V11 Phase 8 (PB-NFR-03, PB-FR-36): the session's request limit, and requests and cost per finished plan. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanSessionFlowTests {

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
        admin = register(email("session-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'session-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 200");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20,
                "dailyMessageLimit", 200)).andExpect(status().isOk());
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.GoalDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.GoalDrafts(List.of(), "Why?"), "claude-opus-5-5", 100, 50, false));
    }

    @Test
    void aSessionStopsAskingClaudeAtItsLimitButTheGuideCarriesOn() throws Exception {
        String user = consented(email("session-limit"));
        long dreamId = dream(user);
        // Without a draft there is no session, so nothing is counted.
        goalSuggestions(user, dreamId).andExpect(status().isOk());

        saveDraft(user, dreamId).andExpect(jsonPath("$.aiRequestsUsed").value(0)).andExpect(jsonPath("$.aiRequestLimit").value(15));
        goalSuggestions(user, dreamId).andExpect(status().isOk());
        saveDraft(user, dreamId).andExpect(jsonPath("$.aiRequestsUsed").value(1));

        jdbcTemplate.update("update plan_builder_drafts set ai_requests = 14 where dream_id = ?", dreamId);
        goalSuggestions(user, dreamId).andExpect(status().isOk());
        goalSuggestions(user, dreamId).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15 AI requests")));
        // Saving the draft never writes an older count back.
        saveDraft(user, dreamId).andExpect(jsonPath("$.aiRequestsUsed").value(15));
        mockMvc.perform(get("/api/ai/plan/drafts/" + dreamId).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.aiRequestsUsed").value(15));

        // The draft check falls back to its rule checks; the plan assistant in the guide is refused.
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/draft-check").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiChecked").value(false));
        send(post("/api/ai/plan/dreams/" + dreamId + "/assistant/proposals"), user, Map.of("surface", "BUILDER", "section", "GOALS"))
                .andExpect(status().isTooManyRequests());
        send(post("/api/ai/chat"), user, Map.of("message", "Is this goal clear?", "dreamId", dreamId, "surface", "BUILDER", "section", "GOALS"))
                .andExpect(status().isTooManyRequests());
        // A crisis message is never held back by the limit.
        send(post("/api/ai/chat"), user, Map.of("message", "I want to die", "dreamId", dreamId, "surface", "BUILDER", "section", "GOALS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reply.safetyFlag").value("CRISIS"));
        verify(claudeClient, never()).send(anyString(), anyString(), anyString(), anyList());

        // Discarding the draft ends the session.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/ai/plan/drafts/" + dreamId)
                .header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        goalSuggestions(user, dreamId).andExpect(status().isOk());
        // Another dream's session is separate.
        long other = dream(user);
        saveDraft(user, other);
        goalSuggestions(user, other).andExpect(status().isOk());
    }

    @Test
    void addingAPlanRecordsItsRequestsAndCostForAdmins() throws Exception {
        JsonNode before = metrics();
        String user = consented(email("session-finished"));
        long dreamId = dream(user);
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save a deposit", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(3).toString(), "steps", List.of()));
        send(put("/api/ai/plan/drafts/" + dreamId), user, Map.of("stage", "REVIEW", "content", Map.of("goals", List.of(goal))))
                .andExpect(status().isOk());
        goalSuggestions(user, dreamId).andExpect(status().isOk());
        // Plan AI use on another dream, with no draft, is not part of this session.
        goalSuggestions(user, dream(user)).andExpect(status().isOk());
        goalSuggestions(user, dreamId).andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isCreated());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select ai_requests, ai_cost_usd, session_key from plan_builder_finished_plans where dream_id = ?", dreamId);
        assertThat(((Number) row.get("ai_requests")).intValue()).isEqualTo(2);
        String sessionKey = (String) row.get("session_key");
        // The cost is exactly what the session's own requests cost.
        java.math.BigDecimal sessionCost = jdbcTemplate.queryForObject(
                "select sum(cost_usd) from ai_usage_logs where plan_session_key = ?", java.math.BigDecimal.class, sessionKey);
        assertThat(((java.math.BigDecimal) row.get("ai_cost_usd")).compareTo(sessionCost)).isZero();
        assertThat(sessionCost.signum()).isPositive();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_usage_logs where plan_session_key = ?", Integer.class, sessionKey))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_usage_logs where user_id = (select id from app_users "
                + "where email like 'session-finished-%') and plan_session_key is null", Integer.class)).isEqualTo(1);

        JsonNode after = metrics();
        assertThat(after.get("finishedPlans").asLong()).isEqualTo(before.get("finishedPlans").asLong() + 1);
        assertThat(after.get("requestsPerFinishedPlan").isNumber()).isTrue();
        assertThat(after.get("costPerFinishedPlan").isNumber()).isTrue();
        JsonNode goals = null;
        for (JsonNode feature : after.get("features")) {
            if (feature.get("feature").asText().equals("GOAL_BUILDER")) {
                goals = feature;
            }
        }
        assertThat(goals).isNotNull();
        assertThat(goals.get("requestsPerFinishedPlan").asDouble()).isPositive();
        assertThat(goals.get("costPerFinishedPlan").asDouble()).isPositive();

        // A deleted account only loses its link to the row.
        send(post("/api/account/delete"), user,
                Map.of("password", "Password123")).andExpect(status().is2xxSuccessful());
        assertThat(jdbcTemplate.queryForObject("select count(*) from plan_builder_finished_plans where user_id is null and ai_requests = 2",
                Integer.class)).isPositive();
    }

    @Test
    void aStreamedPlanAssistantAnswerCarriesItsSessionKey() throws Exception {
        String user = consented(email("session-stream"));
        long dreamId = dream(user);
        saveDraft(user, dreamId);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> onText = invocation.getArgument(4);
            onText.accept("Make it measurable.");
            return new ClaudeClient.Reply("Make it measurable.", "claude-opus-5-5", 100, 50, false, false);
        }).when(claudeClient).stream(anyString(), anyString(), anyString(), anyList(), org.mockito.ArgumentMatchers.any());

        org.springframework.test.web.servlet.MvcResult started = send(post("/api/ai/chat/stream"), user,
                Map.of("message", "Is this goal clear?", "dreamId", dreamId, "surface", "BUILDER", "section", "GOALS")).andReturn();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(started)).andExpect(status().isOk());

        String key = jdbcTemplate.queryForObject("select session_key from plan_builder_drafts where dream_id = ?", String.class, dreamId);
        assertThat(key).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select plan_session_key from ai_usage_logs where feature = 'PLAN_ASSISTANT_CHAT' "
                + "and user_id = (select id from app_users where email like 'session-stream-%')", String.class)).isEqualTo(key);
        // The next request on the same thread carries no key once the request is over.
        goalSuggestions(user, dream(user)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select plan_session_key from ai_usage_logs where feature = 'GOAL_BUILDER' "
                + "and user_id = (select id from app_users where email like 'session-stream-%')", String.class)).isNull();
    }

    // ---------- helpers ----------

    private JsonNode metrics() throws Exception {
        return read(mockMvc.perform(get("/api/admin/ai/plan-metrics?days=30").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()));
    }

    private ResultActions goalSuggestions(String token, long dreamId) throws Exception {
        return mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions").header("Authorization", "Bearer " + token));
    }

    private ResultActions saveDraft(String token, long dreamId) throws Exception {
        return send(put("/api/ai/plan/drafts/" + dreamId), token, Map.of("stage", "GOALS", "content", Map.of("goals", List.of())))
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

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Session Tester", "email", email, "password", "Password123"))))
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
