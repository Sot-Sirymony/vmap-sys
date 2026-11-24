package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Vision Map plan assistant: the blocker analysis ranks what holds a dream back, with or without AI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockerAnalysisFlowTests {

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
    void aiOff() {
        reset(claudeClient);
        jdbcTemplate.update("update ai_settings set enabled = false");
    }

    @Test
    void theRulesRankBlockedWorkAndCountWhatItHoldsUpWithoutAi() throws Exception {
        String user = register(email("blockers-rules"));
        long dreamId = dream(user);
        long goalId = goal(user, dreamId);
        long first = step(user, goalId, "Get the data", 1);
        step(user, goalId, "Analyse the data", 2);
        step(user, goalId, "Write it up", 3);
        long blocked = task(user, first, Map.of("title", "Ask for the dataset", "status", "BLOCKED",
                "blockerReason", "No reply from the data office", "priority", "CRITICAL"));
        long waiting = task(user, first, Map.of("title", "Wait for ethics approval", "status", "WAITING", "priority", "LOW"));
        task(user, first, Map.of("title", "Done already", "status", "COMPLETED", "progressPercent", 100));

        JsonNode result = analyse(user, dreamId);

        assertThat(result.get("aiChecked").asBoolean()).isFalse();
        assertThat(result.get("blockedCount").asInt()).isEqualTo(1);
        assertThat(result.get("waitingCount").asInt()).isEqualTo(1);
        assertThat(result.get("heldUpSteps").asInt()).isEqualTo(2);
        JsonNode blockers = result.get("blockers");
        assertThat(blockers).hasSize(2);
        // The critical Blocked task comes before the low-priority Waiting one.
        JsonNode top = blockers.get(0);
        assertThat(top.get("itemId").asLong()).isEqualTo(blocked);
        assertThat(top.get("itemType").asText()).isEqualTo("TASK");
        assertThat(top.get("kind").asText()).isEqualTo("BLOCKED");
        assertThat(top.get("reason").asText()).isEqualTo("No reply from the data office");
        assertThat(top.get("heldUpSteps").asInt()).isEqualTo(2);
        assertThat(top.get("stepId").asLong()).isEqualTo(first);
        assertThat(top.get("hasHelper").asBoolean()).isFalse();
        assertThat(top.get("rootCause").isNull()).isTrue();
        assertThat(blockers.get(1).get("itemId").asLong()).isEqualTo(waiting);
    }

    @Test
    void aDreamWithNothingStuckSaysSo() throws Exception {
        String user = register(email("blockers-none"));
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Plan it", 1);
        task(user, stepId, Map.of("title", "Moving", "status", "IN_PROGRESS", "progressPercent", 20));

        JsonNode result = analyse(user, dreamId);
        assertThat(result.get("blockers")).isEmpty();
        assertThat(result.get("blockedCount").asInt()).isZero();
    }

    @Test
    void aiAddsARootCauseAndFirstMoveOnlyForBlockersInTheList() throws Exception {
        String admin = register(email("blockers-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'blockers-admin-%' and role = 'USER'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 20");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
        String user = register(email("blockers-ai"));
        send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
        long dreamId = dream(user);
        long stepId = step(user, goal(user, dreamId), "Get the data", 1);
        long blocked = task(user, stepId, Map.of("title", "Ask for the dataset", "status", "BLOCKED",
                "blockerReason", "No reply"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.BlockerReview.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.BlockerReview("Most blockers wait on other people.", List.of(
                        new AiDrafts.BlockerDraft("task-" + blocked, "PARTNER", "The request went to a general inbox.",
                                "Phone the data office and ask who owns the dataset.", "Data officer"),
                        new AiDrafts.BlockerDraft("TASK-999999", "MONEY", "Not in this plan", "x", "x"))),
                        "claude-opus-5-5", 10, 10, false));

        JsonNode result = analyse(user, dreamId);

        assertThat(result.get("aiChecked").asBoolean()).isTrue();
        assertThat(result.get("requestId").isNull()).isFalse();
        assertThat(result.get("pattern").asText()).isEqualTo("Most blockers wait on other people.");
        JsonNode top = result.get("blockers").get(0);
        assertThat(top.get("rootCause").asText()).isEqualTo("The request went to a general inbox.");
        assertThat(top.get("firstMove").asText()).startsWith("Phone the data office");
        assertThat(top.get("helper").asText()).isEqualTo("Data officer");
        assertThat(top.get("obstacleType").asText()).isEqualTo("PARTNER");
        assertThat(top.get("obstacleTypeGuessed").asBoolean()).isTrue();
        assertThat(result.toString()).doesNotContain("Not in this plan");
    }

    @Test
    void anotherUsersDreamIsNotFound() throws Exception {
        String owner = register(email("blockers-owner"));
        long dreamId = dream(owner);
        String other = register(email("blockers-other"));
        mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/blocker-analysis").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    private JsonNode analyse(String token, long dreamId) throws Exception {
        return read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/blocker-analysis")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Research", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Publish a paper", "whyImportant", "Career",
                "successDefinition", "Accepted paper", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "HIGH",
                "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long goal(String token, long dreamId) throws Exception {
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Finish the study", "description", "d",
                "successCriteria", "s", "priority", "HIGH", "targetDate", TODAY.plusMonths(4).toString(),
                "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP")).andExpect(status().isCreated())).get("id").asLong();
    }

    private long step(String token, long goalId, String title, int sequence) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("owner", "Me", "goalId", goalId, "title", title, "description", "d",
                "sequenceNumber", sequence, "complex", true, "priority", "HIGH",
                "targetDate", TODAY.plusMonths(2).toString(), "status", "IN_PROGRESS"));
        body.put("resources", "Laptop");
        return read(send(post("/api/steps"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long task(String token, long stepId, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", stepId, "title", "Task", "owner", "Me", "priority", "HIGH",
                "dueDate", TODAY.plusMonths(1).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        return read(send(post("/api/tasks"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Blocker Tester", "email", email,
                                "password", "Steady-Harbor-Lamp-42"))))
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
