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

/** V11 Phase 5 (PB-FR-23 to PB-FR-26, PB-FR-38, PB-FR-40): helpers, risks and resources. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanBuilderHelpersFlowTests {

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
        String admin = register(email("helpers-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'helpers-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void helpersAreMatchedToOwnPartnersWithoutSendingThemToClaude() throws Exception {
        String user = consented(email("match"));
        long dreamId = dream(user);
        long adviser = partner(user, "Sokha Chan", "Mortgage adviser", "FINANCIAL", "ACTIVE");
        partner(user, "Dara Lim", "Loan officer", "FINANCIAL", "DECLINED");
        partner(user, "Vanna Ouk", "Yoga teacher", "EMOTIONAL", "ACTIVE");
        mockAnswer(new AiDrafts.HelpersRisksDraft(
                List.of(new AiDrafts.HelperDraft("Independent mortgage adviser", "financial", "g1", "Compare loans"),
                        new AiDrafts.HelperDraft("Lawyer", "SOMETHING", "nope", "Check the contract")),
                List.of(new AiDrafts.RiskDraft("Deposit takes longer", "d", "MONEY", "high", "Save monthly", "s1", true),
                        new AiDrafts.RiskDraft("Unsure which area", "d", "decision", "nonsense", "Visit two areas", "", false)),
                List.of(new AiDrafts.ResourceDraft(0, "BOOK", "A known book https://example.com/x", "Basics"))));

        JsonNode answer = read(send(post("/api/ai/plan/dreams/" + dreamId + "/helpers-and-risks"), user, Map.of("items", List.of(
                Map.of("key", "g1", "kind", "GOAL", "title", "Save a deposit"),
                Map.of("key", "s1", "kind", "STEP", "title", "Open a savings account", "goalKey", "g1")))).andExpect(status().isOk()));

        JsonNode helper = answer.get("helpers").get(0);
        assertThat(helper.get("supportType").asText()).isEqualTo("FINANCIAL");
        assertThat(helper.get("forKey").asText()).isEqualTo("g1");
        assertThat(helper.get("matches")).hasSize(1);
        assertThat(helper.get("matches").get(0).get("partnerId").asLong()).isEqualTo(adviser);
        assertThat(answer.get("helpers").get(1).get("supportType").asText()).isEqualTo("OTHER");
        assertThat(answer.get("helpers").get(1).get("forKey").isNull()).isTrue();

        JsonNode money = answer.get("risks").get(0);
        assertThat(money.get("severity").asText()).isEqualTo("HIGH");
        assertThat(money.get("helperType").asText()).isEqualTo("FINANCIAL");
        assertThat(money.get("helperLabel").asText()).isEqualTo("Financial partner or sponsor");
        assertThat(money.get("forKey").asText()).isEqualTo("s1");
        assertThat(money.get("fromFears").asBoolean()).as("fears were not shared for this request").isFalse();
        JsonNode decision = answer.get("risks").get(1);
        assertThat(decision.get("obstacleType").asText()).isEqualTo("DECISION");
        assertThat(decision.get("severity").asText()).isEqualTo("MEDIUM");
        assertThat(decision.get("helperLabel").asText()).isEqualTo("Advisor");

        JsonNode resource = answer.get("resources").get(0);
        assertThat(resource.get("checkBeforeUse").asBoolean()).isTrue();
        assertThat(resource.get("name").asText()).doesNotContain("http");
        long requestId = answer.get("requestId").asLong();
        assertThat(jdbcTemplate.queryForObject("select suggested_items from ai_usage_logs where id = ?", Integer.class, requestId)).isEqualTo(4);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.HelpersRisksDraft.class));
        assertThat(system.getValue()).contains("[g1] Save a deposit").contains("[s1] Open a savings account")
                .doesNotContain("Sokha").doesNotContain("Dara").doesNotContain("Mortgage adviser");

        String other = consented(email("match-other"));
        send(post("/api/ai/plan/dreams/" + dreamId + "/helpers-and-risks"), other, Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void theFearJournalIsUsedOnlyWhenAskedAndOnlyForThisDream() throws Exception {
        String user = consented(email("fears"));
        long dreamId = dream(user);
        long otherDream = dream(user);
        fear(user, "Banks will say no", null);
        fear(user, "Moving away from family", otherDream);
        mockAnswer(new AiDrafts.HelpersRisksDraft(List.of(),
                List.of(new AiDrafts.RiskDraft("Rejection by banks", "d", "MOTIVATION", "MEDIUM", "Apply to three", "", true)), List.of()));

        send(post("/api/ai/plan/dreams/" + dreamId + "/helpers-and-risks"), user, Map.of("useFears", false))
                .andExpect(jsonPath("$.risks[0].fromFears").value(false));
        send(post("/api/ai/plan/dreams/" + dreamId + "/helpers-and-risks"), user, Map.of("useFears", true))
                .andExpect(jsonPath("$.risks[0].fromFears").value(true))
                .andExpect(jsonPath("$.risks[0].helperLabel").value("Accountability partner"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, times(2)).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.HelpersRisksDraft.class));
        assertThat(system.getAllValues().get(0)).doesNotContain("Banks will say no");
        assertThat(system.getAllValues().get(1)).contains("Banks will say no").doesNotContain("Moving away from family");
    }

    @Test
    void addingTheDraftLinksPartnersOnlyWhereFreeAndAddsNewOnesAndObstacles() throws Exception {
        String user = consented(email("add-helpers"));
        long dreamId = dream(user);
        long freePartner = partner(user, "Sokha Chan", "Mortgage adviser", "FINANCIAL", "ACTIVE");
        long busyPartner = partner(user, "Bopha Keo", "Accountant", "ADVISOR", "ACTIVE");
        long elsewhereGoal = goal(user, dream(user));
        long elsewhereStep = read(send(post("/api/steps"), user, Map.of("owner", "Me", "goalId", elsewhereGoal, "title", "Other",
                "sequenceNumber", 1, "complex", false, "priority", "HIGH", "targetDate", TODAY.plusWeeks(2).toString(),
                "status", "NOT_STARTED"))).get("id").asLong();
        jdbcTemplate.update("update partners set related_step_id = ?, related_goal_id = ? where id = ?", elsewhereStep, elsewhereGoal, busyPartner);

        Map<String, Object> step = new HashMap<>(Map.of("key", "s1", "title", "Compare loans", "owner", "Me",
                "targetDate", TODAY.plusWeeks(2).toString(), "priority", "HIGH", "complex", false, "tasks", List.of()));
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save a deposit", "priority", "HIGH", "steps", List.of(step)));
        Map<String, Object> newHelper = new HashMap<>(Map.of("key", "h3", "role", "Property lawyer", "supportType", "EXPERT",
                "linkKey", "s1", "requestId", planAnswer(user), "edited", false));
        Map<String, Object> content = Map.of("goals", List.of(goal), "helpers", List.of(
                Map.of("key", "h1", "role", "Mortgage adviser", "partnerId", freePartner, "linkKey", "s1"),
                Map.of("key", "h2", "role", "Accountant", "partnerId", busyPartner, "linkKey", "s1"),
                newHelper), "risks", List.of(Map.of("key", "r1", "title", "Contract has hidden fees", "obstacleType", "KNOWLEDGE",
                "severity", "HIGH", "solution", "Have it checked", "linkKey", "s1", "helperKey", "h3")));
        send(put("/api/ai/plan/drafts/" + dreamId), user, Map.of("stage", "REVIEW", "content", content)).andExpect(status().isOk());

        JsonNode added = read(mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isCreated()));
        long stepId = added.get("stepIds").get(0).asLong();
        assertThat(added.get("linkedPartnerIds")).hasSize(1);
        assertThat(added.get("linkedPartnerIds").get(0).asLong()).isEqualTo(freePartner);
        mockMvc.perform(get("/api/partners/" + freePartner).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.relatedStepId").value(stepId));
        // An existing link is never overwritten.
        mockMvc.perform(get("/api/partners/" + busyPartner).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.relatedStepId").value(elsewhereStep));

        long created = added.get("partnerIds").get(0).asLong();
        mockMvc.perform(get("/api/partners/" + created).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.name").value("Property lawyer"))
                .andExpect(jsonPath("$.status").value("TO_CONTACT"))
                .andExpect(jsonPath("$.supportType").value("EXPERT"))
                .andExpect(jsonPath("$.relatedDreamId").value(dreamId))
                .andExpect(jsonPath("$.relatedStepId").value(stepId));
        long obstacle = added.get("obstacleIds").get(0).asLong();
        mockMvc.perform(get("/api/obstacles/" + obstacle).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.relatedStepId").value(stepId))
                .andExpect(jsonPath("$.requiredPartnerId").value(created));

        // Undo archives what was created.
        send(post("/api/ai/plan/undo"), user, Map.of("goalIds", List.of(added.get("goalIds").get(0).asLong()),
                "partnerIds", List.of(created), "obstacleIds", List.of(obstacle))).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select archived from partners where id = ?", Boolean.class, created)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select archived from obstacles where id = ?", Boolean.class, obstacle)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select archived from partners where id = ?", Boolean.class, freePartner)).isFalse();
    }

    @Test
    void aDraftCannotLinkAnotherUsersPartner() throws Exception {
        String user = consented(email("foreign-partner"));
        long dreamId = dream(user);
        String other = consented(email("foreign-partner-other"));
        long theirs = partner(other, "Not yours", "Adviser", "ADVISOR", "ACTIVE");
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save", "priority", "HIGH", "steps", List.of()));
        send(put("/api/ai/plan/drafts/" + dreamId), user, Map.of("stage", "REVIEW", "content", Map.of("goals", List.of(goal),
                "helpers", List.of(Map.of("key", "h1", "role", "Adviser", "partnerId", theirs, "linkKey", "g1")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from goals where dream_id = ?", Integer.class, dreamId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select related_goal_id from partners where id = ?", Long.class, theirs)).isNull();

        send(post("/api/ai/plan/undo"), user, Map.of("partnerIds", List.of(theirs))).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private void mockAnswer(AiDrafts.HelpersRisksDraft draft) {
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.HelpersRisksDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(draft, "claude-opus-5-5", 10, 10, false));
    }

    /** A helpers answer to count the new helper against. */
    private long planAnswer(String user) throws Exception {
        long dreamId = dream(user);
        mockAnswer(new AiDrafts.HelpersRisksDraft(List.of(new AiDrafts.HelperDraft("Property lawyer", "EXPERT", "", "r")), List.of(), List.of()));
        return read(send(post("/api/ai/plan/dreams/" + dreamId + "/helpers-and-risks"), user, Map.of())).get("requestId").asLong();
    }

    private long partner(String token, String name, String role, String supportType, String partnerStatus) throws Exception {
        return read(send(post("/api/partners"), token, Map.of("name", name, "role", role, "supportType", supportType,
                "status", partnerStatus, "email", "hidden@example.com")).andExpect(status().isCreated())).get("id").asLong();
    }

    private void fear(String token, String text, Long dreamId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fear", text, "worstCase", "w", "likelihood", "LOW", "firstTest", "Ask once"));
        body.put("relatedDreamId", dreamId);
        send(post("/api/fear-entries"), token, body).andExpect(status().isCreated());
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
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Elsewhere", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(4).toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Helper Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
