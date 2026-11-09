package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

/** V11 Phase 4 (PB-FR-12 to PB-FR-15): richer steps and the task builder. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanBuilderTaskFlowTests {

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
        String admin = register(email("task-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'task-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void stepsKeepEffortAndTheStepTheyStartAfter() throws Exception {
        String user = consented(email("order"));
        long goalId = goal(user, dream(user));
        long first = step(user, goalId, "Get payslips", null, null);
        JsonNode second = read(send(post("/api/steps"), user, stepBody(goalId, "Visit banks", 2.5, first)).andExpect(status().isCreated()));
        assertThat(second.get("estimatedHours").asDouble()).isEqualTo(2.5);
        assertThat(second.get("startsAfterStepId").asLong()).isEqualTo(first);
        long secondId = second.get("id").asLong();

        // Left out keeps what is saved; 0 clears the order.
        send(put("/api/steps/" + secondId), user, stepBody(goalId, "Visit banks", null, null))
                .andExpect(jsonPath("$.estimatedHours").value(2.5))
                .andExpect(jsonPath("$.startsAfterStepId").value(first));
        send(put("/api/steps/" + secondId), user, stepBody(goalId, "Visit banks", null, 0L))
                .andExpect(jsonPath("$.startsAfterStepId").isEmpty());
        send(put("/api/steps/" + secondId), user, stepBody(goalId, "Visit banks", null, secondId)).andExpect(status().isBadRequest());

        // Only a step of the same goal, and only the caller's own.
        long otherGoal = goal(user, dream(user));
        long elsewhere = step(user, otherGoal, "Elsewhere", null, null);
        send(post("/api/steps"), user, stepBody(goalId, "Wrong goal", null, elsewhere)).andExpect(status().isBadRequest());
        String other = consented(email("order-other"));
        long othersGoal = goal(other, dream(other));
        send(post("/api/steps"), other, stepBody(othersGoal, "Not yours", null, first)).andExpect(status().isNotFound());
        send(post("/api/steps"), user, stepBody(goalId, "Too long", 1000.0, null)).andExpect(status().isBadRequest());
    }

    @Test
    void stepSuggestionsCarryPriorityEffortComplexityAndOrder() throws Exception {
        String user = consented(email("step-fields"));
        long goalId = goal(user, dream(user));
        List<AiDrafts.StepDraft> drafts = List.of(
                new AiDrafts.StepDraft("Gather papers", "d", "Me", TODAY.plusWeeks(3).toString(), "r", "p", "r", "HIGH", false, 1.5, 0),
                new AiDrafts.StepDraft(" ", "d", "Me", TODAY.plusWeeks(1).toString(), "r", "p", "r", "LOW", false, 1.0, 0),
                new AiDrafts.StepDraft("Visit banks", "d", "Me", TODAY.plusWeeks(1).toString(), "r", "p", "r", "sometimes", true, -3.0, 1),
                new AiDrafts.StepDraft("Choose a loan", "d", "Me", TODAY.plusWeeks(6).toString(), "r", "p", "r", "MEDIUM", false, 2.0, 3),
                new AiDrafts.StepDraft("Waits for a later one", "d", "Me", TODAY.plusWeeks(8).toString(), "r", "p", "r", "MEDIUM", false, 1.0, 9));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(drafts, ""), "claude-opus-5-5", 10, 10, false));

        JsonNode steps = read(mockMvc.perform(post("/api/ai/plan/goals/" + goalId + "/step-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())).get("steps");
        assertThat(steps).hasSize(4);
        assertThat(steps.get(0).get("priority").asText()).isEqualTo("HIGH");
        assertThat(steps.get(0).get("estimatedHours").asDouble()).isEqualTo(1.5);
        assertThat(steps.get(0).get("startsAfter").isNull()).isTrue();
        // Claude's step 3 waits for its step 1; with the blank one dropped it is our step 2 after our step 1.
        assertThat(steps.get(1).get("complex").asBoolean()).isTrue();
        assertThat(steps.get(1).get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(steps.get(1).get("estimatedHours").isNull()).isTrue();
        assertThat(steps.get(1).get("startsAfter").asInt()).isEqualTo(1);
        // Due before the step it waits for: the date is cleared for the participant to pick.
        assertThat(steps.get(1).get("targetDate").isNull()).isTrue();
        assertThat(steps.get(2).get("startsAfter").asInt()).isEqualTo(2);
        assertThat(steps.get(3).get("startsAfter").isNull()).isTrue();
    }

    @Test
    void theTaskBuilderBreaksAComplexStepIntoTasksWithinItsDate() throws Exception {
        String user = consented(email("tasks"));
        long goalId = goal(user, dream(user));
        long stepId = step(user, goalId, "Visit banks", null, null); // due in 3 weeks
        send(post("/api/tasks"), user, Map.of("stepId", stepId, "title", "Book bank A", "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusDays(4).toString(), "status", "NOT_STARTED", "progressPercent", 0)).andExpect(status().isCreated());
        List<AiDrafts.TaskDraft> drafts = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            drafts.add(new AiDrafts.TaskDraft("Task " + index, index == 1 ? "" : "Me", TODAY.plusDays(index).toString(), "LOW", 0.5, "r"));
        }
        drafts.set(1, new AiDrafts.TaskDraft("After the step", "Me", TODAY.plusWeeks(5).toString(), "HIGH", 9999.0, "r"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.TaskDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.TaskDrafts(drafts, ""), "claude-opus-5-5", 10, 10, false));

        JsonNode answer = read(mockMvc.perform(post("/api/ai/plan/steps/" + stepId + "/task-suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        JsonNode tasks = answer.get("tasks");
        assertThat(tasks).hasSize(8);
        assertThat(tasks.get(0).get("owner").asText()).isEqualTo("Me");
        assertThat(tasks.get(0).get("estimatedHours").asDouble()).isEqualTo(0.5);
        assertThat(tasks.get(1).get("dueDate").isNull()).isTrue();
        assertThat(tasks.get(1).get("estimatedHours").isNull()).isTrue();
        long requestId = answer.get("requestId").asLong();
        assertThat(jdbcTemplate.queryForObject("select feature from ai_usage_logs where id = ?", String.class, requestId)).isEqualTo("TASK_BUILDER");
        assertThat(jdbcTemplate.queryForObject("select suggested_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(8);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.TaskDrafts.class));
        assertThat(system.getValue()).contains("Visit banks").contains("Task already there: Book bank A");

        String other = consented(email("tasks-other"));
        mockMvc.perform(post("/api/ai/plan/steps/" + stepId + "/task-suggestions").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void tasksCanBeSuggestedForADraftStepAndRefined() throws Exception {
        String user = consented(email("draft-tasks"));
        long dreamId = dream(user);
        AiDrafts.TaskDraft inside = new AiDrafts.TaskDraft("Call bank A", "Me", TODAY.plusDays(2).toString(), "HIGH", 0.5, "r");
        AiDrafts.TaskDraft late = new AiDrafts.TaskDraft("Late", "Me", TODAY.plusDays(20).toString(), "LOW", 1.0, "r");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.TaskDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.TaskDrafts(List.of(inside, late, inside), ""), "claude-opus-5-5", 10, 10, false));

        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-task-suggestions"), user, Map.of("goalTitle", "Save a deposit",
                "stepTitle", "Visit banks", "stepTargetDate", TODAY.plusDays(10).toString()))
                .andExpect(jsonPath("$.tasks.length()").value(3))
                .andExpect(jsonPath("$.tasks[0].dueDate").value(TODAY.plusDays(2).toString()))
                .andExpect(jsonPath("$.tasks[1].dueDate").isEmpty());
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-task-suggestions"), user, Map.of("stepTitle", "Visit banks",
                "ask", Map.of("refine", "ANOTHER", "replace", List.of("Call bank A"))))
                .andExpect(jsonPath("$.tasks.length()").value(1));

        String other = consented(email("draft-tasks-other"));
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-task-suggestions"), other, Map.of("stepTitle", "x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDraftAddsEffortOrderAndCountsSuggestedTasks() throws Exception {
        String user = consented(email("draft-order"));
        long dreamId = dream(user);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.TaskDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.TaskDrafts(List.of(
                        new AiDrafts.TaskDraft("Call bank A", "Me", TODAY.plusDays(2).toString(), "HIGH", 0.5, "r"),
                        new AiDrafts.TaskDraft("Call bank B", "Me", TODAY.plusDays(3).toString(), "HIGH", 0.5, "r")), ""),
                        "claude-opus-5-5", 10, 10, false));
        long requestId = read(send(post("/api/ai/plan/dreams/" + dreamId + "/draft-task-suggestions"), user,
                Map.of("stepTitle", "Visit banks"))).get("requestId").asLong();

        Map<String, Object> first = draftStep("s1", "Gather papers", null, List.of());
        Map<String, Object> second = draftStep("s2", "Visit banks", "s1", List.of(Map.of("key", "t1", "title", "Call bank A",
                "owner", "Me", "dueDate", TODAY.plusDays(2).toString(), "priority", "HIGH", "requestId", requestId, "edited", false)));
        second.put("estimatedHours", 3);
        second.put("complex", true);
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save a deposit", "priority", "HIGH",
                "steps", List.of(first, second)));
        send(put("/api/ai/plan/drafts/" + dreamId), user, Map.of("stage", "REVIEW", "content", Map.of("goals", List.of(goal))))
                .andExpect(status().isOk());
        JsonNode added = read(mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isCreated()));

        long firstId = added.get("stepIds").get(0).asLong();
        long secondId = added.get("stepIds").get(1).asLong();
        mockMvc.perform(get("/api/steps/" + secondId).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.startsAfterStepId").value(firstId))
                .andExpect(jsonPath("$.estimatedHours").value(3.0));
        assertThat(jdbcTemplate.queryForObject("select added_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(1);
    }

    // ---------- helpers ----------

    private static Map<String, Object> draftStep(String key, String title, String startsAfterKey, List<Map<String, Object>> tasks) {
        Map<String, Object> step = new HashMap<>(Map.of("key", key, "title", title, "owner", "Me",
                "targetDate", TODAY.plusWeeks(2).toString(), "priority", "MEDIUM", "complex", false, "tasks", tasks));
        step.put("startsAfterKey", startsAfterKey);
        return step;
    }

    private Map<String, Object> stepBody(long goalId, String title, Double hours, Long startsAfter) {
        Map<String, Object> body = new HashMap<>(Map.of("owner", "Me", "goalId", goalId, "title", title, "sequenceNumber", 1,
                "complex", false, "priority", "HIGH", "targetDate", TODAY.plusWeeks(3).toString(), "status", "NOT_STARTED"));
        body.put("estimatedHours", hours);
        body.put("startsAfterStepId", startsAfter);
        return body;
    }

    private long step(String token, long goalId, String title, Double hours, Long startsAfter) throws Exception {
        return read(send(post("/api/steps"), token, stepBody(goalId, title, hours, startsAfter)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Home", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
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

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Task Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
