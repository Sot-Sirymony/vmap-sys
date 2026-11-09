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
import java.util.ArrayList;
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

/**
 * BRD 12 COV-FR-29 (PB-FR-46, PB-FR-47): the plan assistant's change cards.
 * What Claude proposes is checked before the participant sees it, and a card
 * is saved only through the normal services, for the participant's own plan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanAssistantChangesFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000abcd";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ClaudeClient claudeClient;

    private String user;
    private long dreamId;
    private long goalId;
    private long stepId;

    @BeforeEach
    void aiOnAndAPlan() throws Exception {
        reset(claudeClient);
        String admin = register("changes-admin");
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'changes-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());

        user = register("changes-user");
        send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
        long areaId = id(send(post("/api/vision-areas"), user, Map.of("name", "Home", "priority", "HIGH", "status", "ACTIVE")));
        dreamId = id(send(post("/api/dreams"), user, Map.of("visionAreaId", areaId, "title", "Own a home", "whyImportant", "Stability",
                "successDefinition", "Keys in hand", "dreamType", "LONG_TERM", "priority", "HIGH", "status", "ACTIVE",
                "targetDate", TODAY.plusYears(2).toString(), "scheduleMode", "BOTTOM_UP")));
        goalId = id(send(post("/api/goals"), user, Map.of("dreamId", dreamId, "title", "Save a deposit", "successCriteria", "Ten percent saved",
                "priority", "HIGH", "targetDate", TODAY.plusMonths(6).toString(), "status", "NOT_STARTED", "scheduleMode", "BOTTOM_UP")));
        stepId = id(send(post("/api/steps"), user, Map.of("goalId", goalId, "title", "Do finance stuff", "owner", "Me", "sequenceNumber", 1,
                "priority", "MEDIUM", "targetDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED")));
    }

    // ---------- proposals are checked before they are shown ----------

    @Test
    void keepsOnlyGoalEditsTheGoalsSectionAllowsWithDatesInsideTheDream() throws Exception {
        answer(new AiDrafts.AssistantChangeDrafts(List.of(
                change("EDIT", "goal:" + goalId, "TITLE", "Save 10% of the price", null, null, null, null),
                change("EDIT", "goal:" + goalId, "SUCCESS", "Deposit in a savings account", null, null, null, null),
                change("EDIT", "goal:" + goalId, "DATE", TODAY.plusMonths(5).toString(), null, null, null, null),
                change("EDIT", "goal:" + goalId, "DATE", TODAY.minusDays(3).toString(), null, null, null, null),
                change("EDIT", "goal:" + goalId, "DATE", TODAY.plusYears(5).toString(), null, null, null, null),
                change("EDIT", "goal:" + goalId, "OWNER", "Me", null, null, null, null),
                change("EDIT", "step:" + stepId, "TITLE", "Not this section", null, null, null, null),
                change("ADD_STEP", null, null, null, "goal:" + goalId, "Not allowed here", "Me", null),
                change("REWRITE", null, null, null, null, null, null, null)), "Three fixes."));

        JsonNode proposals = propose("GOALS");

        assertThat(summaries(proposals)).containsExactly(
                "EDIT goal:" + goalId + " TITLE=Save 10% of the price",
                "EDIT goal:" + goalId + " SUCCESS=Deposit in a savings account",
                "EDIT goal:" + goalId + " DATE=" + TODAY.plusMonths(5));
        assertThat(proposals.get("note").asText()).isEqualTo("Three fixes.");
    }

    @Test
    void proposesRisksTiedToAGoalOrStepWithSafeDefaults() throws Exception {
        answer(new AiDrafts.AssistantChangeDrafts(List.of(
                risk("goal:" + goalId, "Prices may rise", "MONEY", "HIGH"),
                risk("step:" + stepId, "Payslips may be late", "nonsense", null),
                risk("dream", "Tied to nothing it can name", null, "low"),
                risk(null, "  ", "TIME", "LOW"),
                change("EDIT", "dream", "WHY", "Not in this section", null, null, null, null)), null));

        JsonNode proposals = propose("RESOURCES");

        List<String> risks = new ArrayList<>();
        proposals.get("changes").forEach(item -> risks.add(item.get("title").asText() + " | " + item.path("parentRef").asText("none")
                + " | " + item.get("obstacleType").asText() + " | " + item.get("severity").asText()));
        assertThat(risks).containsExactly(
                "Prices may rise | goal:" + goalId + " | MONEY | HIGH",
                // An unknown type becomes OTHER and a missing severity MEDIUM.
                "Payslips may be late | step:" + stepId + " | OTHER | MEDIUM",
                "Tied to nothing it can name | none | OTHER | LOW");
    }

    @Test
    void refusesToProposeChangesForASectionThatHasNone() throws Exception {
        send(post(path("proposals")), user, Map.of("surface", "PLAN", "section", "MILESTONES")).andExpect(status().isBadRequest());
        send(post(path("proposals")), user, Map.of("surface", "ELSEWHERE", "section", "GOALS")).andExpect(status().isBadRequest());
    }

    @Test
    void keepsOnlyTheDreamFieldsTheStatementAllowsAndOnlyOwnersAndDatesOnTheDatesSection() throws Exception {
        answer(new AiDrafts.AssistantChangeDrafts(List.of(
                change("EDIT", "dream", "WHY", "So my children have a stable home", null, null, null, null),
                change("EDIT", "dream", "TITLE", "Not changeable here", null, null, null, null),
                change("EDIT", "goal:" + goalId, "TITLE", "Not this section", null, null, null, null)), null));
        assertThat(summaries(propose("STATEMENT"))).containsExactly("EDIT dream WHY=So my children have a stable home");

        answer(new AiDrafts.AssistantChangeDrafts(List.of(
                change("EDIT", "step:" + stepId, "OWNER", "Mortgage adviser", null, null, null, null),
                change("EDIT", "step:" + stepId, "TITLE", "Titles are not this section", null, null, null, null),
                change("EDIT", "step:" + stepId, "DATE", "not a date", null, null, null, null)), null));
        assertThat(summaries(propose("DATES"))).containsExactly("EDIT step:" + stepId + " OWNER=Mortgage adviser");
    }

    @Test
    void checksProposalsForEachSectionOfBuildMyPlan() throws Exception {
        Map<String, Object> task = Map.of("key", "t1", "title", "Call the bank", "owner", "Me",
                "dueDate", TODAY.plusWeeks(3).toString(), "priority", "LOW");
        Map<String, Object> draftStep = new HashMap<>(Map.of("key", "s1", "title", "Visit banks", "owner", "Me",
                "targetDate", TODAY.plusWeeks(4).toString(), "priority", "MEDIUM", "complex", true, "tasks", List.of(task)));
        Map<String, Object> draftGoal = new HashMap<>(Map.of("key", "g1", "title", "Get a mortgage offer", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(4).toString(), "steps", List.of(draftStep)));
        send(put("/api/ai/plan/drafts/" + dreamId), user, Map.of("stage", "STEPS", "content", Map.of("goals", List.of(draftGoal))))
                .andExpect(status().isOk());

        answer(new AiDrafts.AssistantChangeDrafts(List.of(
                change("EDIT", "t1", "TITLE", "Call two banks", null, null, null, null),
                change("EDIT", "t1", "DATE", TODAY.plusWeeks(10).toString(), null, null, null, null),
                change("ADD_TASK", null, null, null, "s1", "Ask about fees", "", TODAY.plusWeeks(2).toString()),
                change("ADD_TASK", null, null, null, "g1", "A task under a goal is not allowed", "Me", null),
                change("ADD_TASK", null, null, null, "s1", " ", "Me", null)), null));
        List<String> tasks = new ArrayList<>();
        proposeOn("BUILDER", "TASKS").get("changes").forEach(item -> tasks.add(item.get("kind").asText() + ":"
                + item.path("itemRef").asText(item.path("parentRef").asText()) + ":" + item.path("value").asText(item.path("title").asText())));
        assertThat(tasks).containsExactly("EDIT:t1:Call two banks", "ADD_TASK:s1:Ask about fees");

        answer(new AiDrafts.AssistantChangeDrafts(List.of(
                change("ADD_STEP", null, null, null, "g1", "Gather payslips", "Me", TODAY.plusWeeks(1).toString()),
                change("EDIT", "s1", "OWNER", "Mortgage adviser", null, null, null, null),
                change("ADD_RISK", null, null, null, null, "Not in review", null, null)), null));
        assertThat(proposeOn("BUILDER", "REVIEW").get("changes")).hasSize(2);

        answer(new AiDrafts.AssistantChangeDrafts(List.of(risk("g1", "Rates may rise", "MONEY", "HIGH")), null));
        assertThat(proposeOn("BUILDER", "HELPERS").get("changes").get(0).get("parentRef").asText()).isEqualTo("g1");

        answer(new AiDrafts.AssistantChangeDrafts(List.of(
                change("EDIT", "dream", "SUCCESS", "An accepted offer", null, null, null, null)), null));
        assertThat(proposeOn("BUILDER", "CLARIFY").get("changes")).hasSize(1);
    }

    // ---------- a card is saved through the normal checks ----------

    @Test
    void savesEachEditableFieldOfTheDreamAGoalAndAStep() throws Exception {
        apply(Map.of("kind", "EDIT", "itemRef", "dream", "field", "SUCCESS", "value", "Keys in hand by June")).andExpect(status().isCreated());
        apply(Map.of("kind", "EDIT", "itemRef", "dream", "field", "DATE", "date", TODAY.plusYears(3).toString())).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("select success_definition from dreams where id = ?", String.class, dreamId)).isEqualTo("Keys in hand by June");
        assertThat(jdbcTemplate.queryForObject("select target_date from dreams where id = ?", LocalDate.class, dreamId)).isEqualTo(TODAY.plusYears(3));

        apply(Map.of("kind", "EDIT", "itemRef", "goal:" + goalId, "field", "TITLE", "value", "Save 10% of the price")).andExpect(status().isCreated());
        apply(Map.of("kind", "EDIT", "itemRef", "goal:" + goalId, "field", "SUCCESS", "value", "Deposit saved")).andExpect(status().isCreated());
        apply(Map.of("kind", "EDIT", "itemRef", "goal:" + goalId, "field", "DATE", "date", TODAY.plusMonths(7).toString())).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForMap("select title, success_criteria, target_date from goals where id = ?", goalId))
                .containsEntry("title", "Save 10% of the price").containsEntry("success_criteria", "Deposit saved");

        apply(Map.of("kind", "EDIT", "itemRef", "step:" + stepId, "field", "OWNER", "value", " ")).andExpect(status().isCreated());
        apply(Map.of("kind", "EDIT", "itemRef", "step:" + stepId, "field", "DATE", "date", TODAY.plusMonths(3).toString())).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("select owner from vision_steps where id = ?", String.class, stepId)).isEqualTo("Me");
        assertThat(jdbcTemplate.queryForObject("select target_date from vision_steps where id = ?", LocalDate.class, stepId)).isEqualTo(TODAY.plusMonths(3));
    }

    @Test
    void refusesFieldsThatCannotBeChangedAndEmptyValues() throws Exception {
        apply(Map.of("kind", "EDIT", "itemRef", "dream", "field", "TITLE", "value", "x")).andExpect(status().isBadRequest());
        apply(Map.of("kind", "EDIT", "itemRef", "dream", "field", "WHY", "value", " ")).andExpect(status().isBadRequest());
        apply(Map.of("kind", "EDIT", "itemRef", "goal:" + goalId, "field", "OWNER", "value", "Me")).andExpect(status().isBadRequest());
        apply(Map.of("kind", "EDIT", "itemRef", "goal:" + goalId, "field", "TITLE", "value", "")).andExpect(status().isBadRequest());
        apply(Map.of("kind", "EDIT", "itemRef", "step:" + stepId, "field", "SUCCESS", "value", "x")).andExpect(status().isBadRequest());
        apply(Map.of("kind", "ADD_RISK", "title", " ")).andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("select title from goals where id = ?", String.class, goalId)).isEqualTo("Save a deposit");
    }

    /** BRD 13 EJ-FR-25: a made-up or mangled reference gets the same 404 as an unknown id, never a 500. */
    @Test
    void refusesAMangledReferenceWithNotFound() throws Exception {
        apply(Map.of("kind", "EDIT", "itemRef", "goal:abc", "field", "TITLE", "value", "New title")).andExpect(status().isNotFound());
        apply(Map.of("kind", "EDIT", "itemRef", "goal:", "field", "TITLE", "value", "New title")).andExpect(status().isNotFound());
        apply(Map.of("kind", "ADD_RISK", "parentRef", "step:9x", "title", "Prices may rise")).andExpect(status().isNotFound());
    }

    @Test
    void savesARiskTiedToAGoalOrToNothing() throws Exception {
        apply(Map.of("kind", "ADD_RISK", "parentRef", "goal:" + goalId, "title", "Prices may rise", "obstacleType", "MONEY", "severity", "HIGH"))
                .andExpect(status().isCreated());
        apply(Map.of("kind", "ADD_RISK", "title", "Motivation may dip")).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("select count(*) from obstacles where related_goal_id = ? and severity = 'HIGH'", Integer.class, goalId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("select obstacle_type, severity from obstacles where title = 'Motivation may dip' and related_dream_id = ?", dreamId))
                .containsEntry("obstacle_type", "OTHER").containsEntry("severity", "MEDIUM");
    }

    @Test
    void refusesItemsThatAreNotInThisPlan() throws Exception {
        apply(Map.of("kind", "EDIT", "itemRef", "task:1", "field", "TITLE", "value", "x")).andExpect(status().isNotFound());
        apply(Map.of("kind", "ADD_RISK", "parentRef", "partner:3", "title", "x")).andExpect(status().isNotFound());

        // A goal of the same person's other dream is not in this plan either.
        long areaId = id(send(post("/api/vision-areas"), user, Map.of("name", "Work", "priority", "LOW", "status", "ACTIVE")));
        long otherDream = id(send(post("/api/dreams"), user, Map.of("visionAreaId", areaId, "title", "Change jobs", "dreamType", "SHORT_TERM",
                "priority", "LOW", "status", "ACTIVE", "scheduleMode", "BOTTOM_UP")));
        long otherGoal = id(send(post("/api/goals"), user, Map.of("dreamId", otherDream, "title", "Update my CV", "priority", "LOW",
                "status", "NOT_STARTED", "scheduleMode", "BOTTOM_UP")));
        apply(Map.of("kind", "EDIT", "itemRef", "goal:" + otherGoal, "field", "TITLE", "value", "x")).andExpect(status().isNotFound());
        apply(Map.of("kind", "ADD_STEP", "parentRef", "goal:" + otherGoal, "title", "x", "date", TODAY.plusDays(5).toString()))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private void answer(AiDrafts.AssistantChangeDrafts drafts) {
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AssistantChangeDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(drafts, "claude-opus-5-5", 10, 10, false));
    }

    private JsonNode propose(String section) throws Exception {
        return proposeOn("PLAN", section);
    }

    private JsonNode proposeOn(String surface, String section) throws Exception {
        return read(send(post(path("proposals")), user, Map.of("surface", surface, "section", section)).andExpect(status().isOk()));
    }

    private static List<String> summaries(JsonNode proposals) {
        List<String> out = new ArrayList<>();
        proposals.get("changes").forEach(item -> out.add(item.get("kind").asText() + " " + item.get("itemRef").asText() + " "
                + item.get("field").asText() + "=" + item.get("value").asText()));
        return out;
    }

    private ResultActions apply(Map<String, Object> card) throws Exception {
        return send(post(path("apply")), user, new HashMap<>(card));
    }

    private static AiDrafts.AssistantChangeDraft change(String kind, String itemRef, String field, String value, String parentRef,
                                                        String title, String owner, String date) {
        return new AiDrafts.AssistantChangeDraft(kind, itemRef, field, value, parentRef, title, owner, date, null, null, null, "Helps.");
    }

    private static AiDrafts.AssistantChangeDraft risk(String parentRef, String title, String type, String severity) {
        return new AiDrafts.AssistantChangeDraft("ADD_RISK", null, null, null, parentRef, title, null, null, type, severity,
                "Plan for it", "Helps.");
    }

    private String path(String action) {
        return "/api/ai/plan/dreams/" + dreamId + "/assistant/" + action;
    }

    private String register(String prefix) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Changes Tester",
                                "email", prefix + "-" + System.nanoTime() + "@example.com", "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }

    private long id(ResultActions created) throws Exception {
        return read(created.andExpect(status().isCreated())).get("id").asLong();
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
