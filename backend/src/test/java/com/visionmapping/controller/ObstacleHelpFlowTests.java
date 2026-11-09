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

/** V15 Phase 2 (AH-FR-31 to AH-FR-39, AH-FR-41, AH-FR-42): the obstacle helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObstacleHelpFlowTests {

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
        String admin = register(email("obstacle-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'obstacle-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void partnerMatchingUsesTheParticipantsOwnPartnersWithoutAi() throws Exception {
        String user = register(email("obstacle-match")); // no AI consent
        partner(user, "Dr Lina", "Research lead", "MENTOR", "ACTIVE");
        partner(user, "Mr Dara", "Sponsor at the bank", "OTHER", "TO_CONTACT");
        partner(user, "Ms Gone", "Mentor", "MENTOR", "DECLINED");

        JsonNode knowledge = read(mockMvc.perform(get("/api/obstacles/partner-matches?obstacleType=KNOWLEDGE")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(knowledge.get("suggestedSupportType").asText()).isEqualTo("MENTOR");
        assertThat(names(knowledge.get("partners"))).containsExactly("Dr Lina");

        JsonNode money = read(mockMvc.perform(get("/api/obstacles/partner-matches?obstacleType=MONEY")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(money.get("suggestedSupportType").asText()).isEqualTo("FINANCIAL");
        assertThat(names(money.get("partners"))).containsExactly("Mr Dara");

        JsonNode system = read(mockMvc.perform(get("/api/obstacles/partner-matches?obstacleType=SYSTEM")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(system.get("suggestedSupportType").asText()).isEqualTo("OTHER");
        assertThat(system.get("partners")).isEmpty();
        verifyNoInteractions(claudeClient);
    }

    @Test
    void blockedTasksWithoutAnObstacleAndTheWhichFirstOrderNeedNoAi() throws Exception {
        String user = register(email("obstacle-blocked"));
        Plan plan = plan(user);
        long loose = task(user, plan.stepId(), "Get the survey data", "BLOCKED", "Waiting for the ministry", TODAY.plusDays(5));
        long named = task(user, plan.stepId(), "Book the room", "BLOCKED", "No budget", TODAY.plusDays(5));
        task(user, plan.stepId(), "Draft the outline", "IN_PROGRESS", null, TODAY.plusDays(5));
        obstacle(user, Map.of("title", "No budget for the room", "obstacleType", "MONEY", "severity", "HIGH", "status", "OPEN",
                "relatedTaskId", named));
        long critical = obstacle(user, Map.of("title", "Ethics approval", "obstacleType", "SYSTEM", "severity", "CRITICAL",
                "status", "IN_PROGRESS"));
        obstacle(user, Map.of("title", "Old worry", "obstacleType", "TIME", "severity", "CRITICAL", "status", "RESOLVED",
                "rootCause", "Overbooked"));
        long stepLevel = obstacle(user, Map.of("title", "Unclear method", "obstacleType", "KNOWLEDGE", "severity", "HIGH",
                "status", "OPEN", "relatedStepId", plan.stepId()));

        JsonNode blocked = read(mockMvc.perform(get("/api/tasks/blocked-without-obstacle").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).get("id").asLong()).isEqualTo(loose);
        assertThat(blocked.get(0).get("blockerReason").asText()).isEqualTo("Waiting for the ministry");
        assertThat(blocked.get(0).get("stepId").asLong()).isEqualTo(plan.stepId());

        // Severity first; then the step-level obstacle holds up both Blocked tasks under its step.
        JsonNode ranked = read(mockMvc.perform(get("/api/obstacles/priority").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).get("id").asLong()).isEqualTo(critical);
        assertThat(ranked.get(1).get("id").asLong()).isEqualTo(stepLevel);
        assertThat(ranked.get(1).get("blockedTasks").asInt()).isEqualTo(2);
        assertThat(ranked.get(2).get("blockedTasks").asInt()).isEqualTo(1);
        verifyNoInteractions(claudeClient);
    }

    @Test
    void classifyAndSolutionsReturnCheckedCardsAndNeverSendPartnerNames() throws Exception {
        String user = consented(email("obstacle-classify"));
        long partnerId = partner(user, "Dr Secret Mentor", "Research lead", "MENTOR", "ACTIVE");
        long obstacleId = obstacle(user, Map.of("title", "I cannot afford the survey", "obstacleType", "OTHER", "severity", "LOW",
                "status", "OPEN", "requiredPartnerId", partnerId));
        obstacle(user, Map.of("title", "Paid for printing", "obstacleType", "MONEY", "severity", "LOW", "status", "RESOLVED",
                "rootCause", "No budget line", "solution", "Asked the faculty fund"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ObstacleClassifyDraft.class)))
                .thenReturn(structured(new AiDrafts.ObstacleClassifyDraft("money", "It is about cost.", "SEVERE", "Not a value.")));

        JsonNode classify = read(send(post("/api/ai/obstacles/help"), user, Map.of("mode", "CLASSIFY", "obstacleId", obstacleId,
                "form", Map.of("obstacleType", "MONEY"))).andExpect(status().isOk()));
        assertThat(classify.get("changes")).hasSize(1);
        assertThat(classify.get("changes").get(0).get("field").asText()).isEqualTo("obstacleType");
        assertThat(classify.get("changes").get(0).get("value").asText()).isEqualTo("MONEY");
        assertThat(classify.get("requestId").asLong()).isPositive();
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.ObstacleClassifyDraft.class));
        // AH-FR-38: how a money obstacle was solved before; AH-FR-35: never the partner's name.
        assertThat(system.getValue()).contains("I cannot afford the survey", "Asked the faculty fund")
                .doesNotContain("Dr Secret Mentor", "secret@example.com");

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.SolutionDrafts.class)))
                .thenReturn(structured(new AiDrafts.SolutionDrafts(new ArrayList<>(java.util.Arrays.asList(
                        new AiDrafts.SolutionDraft("Ask the faculty fund again", "Email the fund officer today", "solution"),
                        null,
                        new AiDrafts.SolutionDraft("Use a free survey tool", "", "creativeAlternatives"),
                        new AiDrafts.SolutionDraft("Share costs with a partner study", "List two studies", "other"),
                        new AiDrafts.SolutionDraft("Fourth idea", "Too many", "solution"))))));
        JsonNode solutions = read(send(post("/api/ai/obstacles/help"), user, Map.of("mode", "SOLUTIONS", "obstacleId", obstacleId))
                .andExpect(status().isOk()));
        assertThat(solutions.get("changes")).hasSize(3);
        assertThat(solutions.get("changes").get(0).get("value").asText())
                .isEqualTo("Ask the faculty fund again First step: Email the fund officer today");
        assertThat(solutions.get("changes").get(1).get("field").asText()).isEqualTo("creativeAlternatives");
        assertThat(solutions.get("changes").get(2).get("field").asText()).isEqualTo("solution");
        // AH-FR-42: nothing is saved and the obstacle stays open.
        assertThat(jdbcTemplate.queryForMap("select solution, status from obstacles where id = ?", obstacleId))
                .containsEntry("solution", null).containsEntry("status", "OPEN");
    }

    @Test
    void rootCauseAsksUpToThreeQuestionsInPlaceThenProposesTheCause() throws Exception {
        String user = consented(email("obstacle-why"));
        long obstacleId = obstacle(user, Map.of("title", "I keep putting off the search", "obstacleType", "MOTIVATION",
                "severity", "HIGH", "status", "OPEN"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.RootCauseDraft.class)))
                .thenReturn(structured(new AiDrafts.RootCauseDraft("Why does starting feel hard?", "", "")));

        JsonNode first = read(send(post("/api/ai/obstacles/help"), user, Map.of("mode", "ROOT_CAUSE", "obstacleId", obstacleId))
                .andExpect(status().isOk()));
        assertThat(first.get("question").asText()).isEqualTo("Why does starting feel hard?");
        assertThat(first.get("changes")).isEmpty();

        // After three answers the cause is given, even if Claude tries to ask again.
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.RootCauseDraft.class)))
                .thenReturn(structured(new AiDrafts.RootCauseDraft("One more?", "The search has no clear stopping point.",
                        "All three answers mention not knowing when to stop.")));
        List<Map<String, String>> answers = List.of(
                Map.of("question", "Why does starting feel hard?", "answer", "There are thousands of papers"),
                Map.of("question", "Why does that matter?", "answer", "I do not know when I have enough"),
                Map.of("question", "Why is that unclear?", "answer", "Nobody told me how many to read"));
        JsonNode last = read(send(post("/api/ai/obstacles/help"), user, Map.of("mode", "ROOT_CAUSE", "obstacleId", obstacleId,
                "answers", answers)).andExpect(status().isOk()));
        assertThat(last.get("question").isNull()).isTrue();
        assertThat(last.get("changes").get(0).get("field").asText()).isEqualTo("rootCause");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(claudeClient, org.mockito.Mockito.times(2))
                .structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.RootCauseDraft.class));
        assertThat(system.getAllValues().get(1)).contains("Nobody told me how many to read");

        // A fourth answer is refused by validation.
        List<Map<String, String>> four = new ArrayList<>(answers);
        four.add(Map.of("question", "Why?", "answer", "Because"));
        send(post("/api/ai/obstacles/help"), user, Map.of("mode", "ROOT_CAUSE", "obstacleId", obstacleId, "answers", four))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nextTaskIsOwnedByMeAndDatedWithinTheLinkedStep() throws Exception {
        String user = consented(email("obstacle-task"));
        Plan plan = plan(user);
        long taskId = task(user, plan.stepId(), "Search PubMed", "BLOCKED", "Library access expired", TODAY.plusDays(5));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.NextTaskDraft.class)))
                .thenReturn(structured(new AiDrafts.NextTaskDraft("Ask the librarian to renew access", "Email today.",
                        TODAY.plusDays(3).toString(), "urgent", "Unblocks the search.")));

        // The unsaved form links the blocked task; the step comes from it.
        JsonNode answer = read(send(post("/api/ai/obstacles/help"), user, Map.of("mode", "NEXT_TASK",
                "form", Map.of("title", "No library access", "relatedTaskId", String.valueOf(taskId)))).andExpect(status().isOk()));
        JsonNode task = answer.get("task");
        assertThat(task.get("owner").asText()).isEqualTo("Me");
        assertThat(task.get("dueDate").asText()).isEqualTo(TODAY.plusDays(3).toString());
        assertThat(task.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(task.get("stepId").asLong()).isEqualTo(plan.stepId());
        assertThat(task.get("latestDate").asText()).isEqualTo(plan.stepDate().toString());

        // A date past the step's date is dropped for the participant to pick.
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.NextTaskDraft.class)))
                .thenReturn(structured(new AiDrafts.NextTaskDraft("Renew access", "", plan.stepDate().plusDays(10).toString(), "HIGH", "")));
        JsonNode late = read(send(post("/api/ai/obstacles/help"), user, Map.of("mode", "NEXT_TASK",
                "form", Map.of("title", "No library access", "relatedStepId", String.valueOf(plan.stepId())))).andExpect(status().isOk()));
        assertThat(late.get("task").get("dueDate").isNull()).isTrue();
        assertThat(late.get("task").get("priority").asText()).isEqualTo("HIGH");
        // Nothing is created.
        assertThat(jdbcTemplate.queryForObject("select count(*) from task_items where title = 'Renew access'", Integer.class)).isZero();
    }

    @Test
    void whichFirstAddsReasonsToTheTopThreeInTheRuleOrder() throws Exception {
        String user = consented(email("obstacle-first"));
        long critical = obstacle(user, Map.of("title", "Ethics approval", "obstacleType", "SYSTEM", "severity", "CRITICAL", "status", "OPEN"));
        long low = obstacle(user, Map.of("title", "Messy desk", "obstacleType", "OTHER", "severity", "LOW", "status", "OPEN"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PriorityReasons.class)))
                .thenReturn(structured(new AiDrafts.PriorityReasons(List.of(
                        new AiDrafts.PriorityReason(1, "Nothing else can start without it."),
                        new AiDrafts.PriorityReason(7, "Out of range.")))));

        JsonNode answer = read(send(post("/api/ai/obstacles/help"), user, Map.of("mode", "PRIORITISE")).andExpect(status().isOk()));
        assertThat(answer.get("ranking").get(0).get("id").asLong()).isEqualTo(critical);
        assertThat(answer.get("ranking").get(0).get("reason").asText()).isEqualTo("Nothing else can start without it.");
        assertThat(answer.get("ranking").get(1).get("id").asLong()).isEqualTo(low);
        assertThat(answer.get("ranking").get(1).get("reason").isNull()).isTrue();
    }

    @Test
    void helpersCheckOwnershipBeforeAiAndNeedAnOpenObstacle() throws Exception {
        String owner = consented(email("obstacle-owner"));
        long obstacleId = obstacle(owner, Map.of("title", "Mine", "obstacleType", "TIME", "severity", "LOW", "status", "OPEN"));
        String other = register(email("obstacle-other")); // no consent either
        send(post("/api/ai/obstacles/help"), other, Map.of("mode", "CLASSIFY", "obstacleId", obstacleId)).andExpect(status().isNotFound());
        send(post("/api/ai/obstacles/help"), owner, Map.of("mode", "CLASSIFY")).andExpect(status().isBadRequest());
        send(post("/api/ai/obstacles/help"), owner, Map.of("mode", "GUESS", "obstacleId", obstacleId)).andExpect(status().isBadRequest());
        // Without consent, the AI helpers are refused and Claude is never called.
        String noConsent = register(email("obstacle-noconsent"));
        long own = obstacle(noConsent, Map.of("title", "Busy", "obstacleType", "TIME", "severity", "LOW", "status", "OPEN"));
        int code = send(post("/api/ai/obstacles/help"), noConsent, Map.of("mode", "CLASSIFY", "obstacleId", own))
                .andReturn().getResponse().getStatus();
        assertThat(code).isGreaterThanOrEqualTo(400);
        verifyNoInteractions(claudeClient);
    }

    @Test
    void pageProposalsWorkForAnObstacleWithLinkedItems() throws Exception {
        // Regression: linked records load lazily, so the proposal view must be read in a transaction.
        String user = consented(email("obstacle-linked-propose"));
        Plan plan = plan(user);
        long obstacleId = obstacle(user, Map.of("title", "Unclear method", "obstacleType", "KNOWLEDGE", "severity", "HIGH",
                "status", "OPEN", "relatedStepId", plan.stepId(), "relatedDreamId", plan.dreamId()));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(structured(new AiDrafts.PageAssistantDraft("", List.of(
                        new AiDrafts.PageChangeDraft("rootCause", "No method course yet.", "")))));
        JsonNode answer = read(send(post("/api/ai/pages/assistant/proposals"), user, Map.of("page", "OBSTACLES", "area", "item",
                "itemId", obstacleId)).andExpect(status().isOk()));
        assertThat(answer.get("changes")).hasSize(1);
        assertThat(answer.get("sources").toString()).contains("linked");
    }

    // ---------- helpers ----------

    private record Plan(long dreamId, long goalId, long stepId, LocalDate stepDate) {
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
        LocalDate stepDate = TODAY.plusMonths(1);
        long stepId = read(send(post("/api/steps"), token, Map.of("owner", "Me", "goalId", goalId, "title", "Search literature",
                "sequenceNumber", 1, "complex", true, "priority", "HIGH", "targetDate", stepDate.toString(), "status", "IN_PROGRESS"))
                .andExpect(status().isCreated())).get("id").asLong();
        return new Plan(dreamId, goalId, stepId, stepDate);
    }

    private long task(String token, long stepId, String title, String status, String blockerReason, LocalDate due) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", stepId, "title", title, "owner", "Me", "dueDate", due.toString(),
                "priority", "HIGH", "status", status, "progressPercent", 10));
        if (blockerReason != null) {
            body.put("blockerReason", blockerReason);
        }
        return read(send(post("/api/tasks"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long partner(String token, String name, String role, String supportType, String partnerStatus) throws Exception {
        return read(send(post("/api/partners"), token, Map.of("name", name, "role", role, "email", "secret@example.com",
                "supportType", supportType, "status", partnerStatus)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long obstacle(String token, Map<String, Object> body) throws Exception {
        return read(send(post("/api/obstacles"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private static <T> ClaudeClient.Structured<T> structured(T value) {
        return new ClaudeClient.Structured<>(value, "claude-opus-5-5", 10, 10, false);
    }

    private static List<String> names(JsonNode partners) {
        List<String> names = new ArrayList<>();
        partners.forEach(partner -> names.add(partner.get("name").asText()));
        return names;
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Obstacle Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
