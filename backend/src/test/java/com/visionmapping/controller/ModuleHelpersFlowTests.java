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

/** V15 Phase 4 (AH-FR-13 to AH-FR-22): the Modules helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModuleHelpersFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.systemDefault());
    /** Module numbers in program order. */
    private static final int[] ORDER = {4, 1, 2, 3, 5, 6, 14, 15, 7, 8, 9, 10, 11, 12, 13};

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
        String admin = register(email("module-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'module-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void whichModuleNextFollowsTheRuleWithoutAi() throws Exception {
        String user = register(email("module-rule")); // no AI consent
        JsonNode first = next(user);
        assertThat(first.get("moduleNumber").asInt()).isEqualTo(4);
        assertThat(first.get("rule").asText()).isEqualTo("PROGRAM_ORDER");
        assertThat(first.get("source").asText()).isEqualTo("RULE");

        // A low chain whose module is open is named, with the score in the reason.
        complete("module-rule", 4);
        assessment(user, 2, 4, 4, 4, 4, 4);
        JsonNode low = next(user);
        assertThat(low.get("moduleNumber").asInt()).isEqualTo(1);
        assertThat(low.get("rule").asText()).isEqualTo("LOW_CHAIN");
        assertThat(low.get("reason").asText()).contains("Settling for average 2 of 5");

        // A low chain whose module is still locked does not change the program order.
        complete("module-rule", 1);
        assessment(user, 4, 4, 4, 4, 4, 1);
        JsonNode locked = next(user);
        assertThat(locked.get("moduleNumber").asInt()).isEqualTo(2);
        assertThat(locked.get("rule").asText()).isEqualTo("PROGRAM_ORDER");

        complete("module-rule", 2, 3, 5, 6, 14, 15, 7, 8, 9, 10, 11, 12, 13);
        JsonNode done = next(user);
        assertThat(done.get("rule").asText()).isEqualTo("ALL_DONE");
        assertThat(done.get("moduleNumber").isNull()).isTrue();
        verifyNoInteractions(claudeClient);
    }

    @Test
    void theJourneySummaryCountsStagesAndChainChanges() throws Exception {
        String user = register(email("module-journey"));
        complete("module-journey", 4, 1, 2);
        jdbcTemplate.update("insert into chain_assessments (user_id, taken_on, settling_score, fear_score, criticism_score, vision_score, "
                + "knowledge_score, resources_score, created_at, updated_at) select id, ?, 2, 1, 3, 2, 3, 2, current_timestamp, "
                + "current_timestamp from app_users where email like 'module-journey-%'", TODAY.minusDays(60));
        assessment(user, 3, 3, 3, 4, 3, 2);

        JsonNode journey = read(mockMvc.perform(get("/api/modules/journey").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(journey.get("done").asInt()).isEqualTo(3);
        assertThat(journey.get("total").asInt()).isEqualTo(15);
        assertThat(journey.get("assessments").asInt()).isEqualTo(2);
        assertThat(journey.get("nextStage").asText()).isEqualTo("BREAK_CHAINS");
        assertThat(journey.get("stages").get(0).get("stage").asText()).isEqualTo("BREAK_CHAINS");
        assertThat(journey.get("stages").get(0).get("done").asInt()).isEqualTo(3);
        assertThat(journey.get("stages").get(0).get("total").asInt()).isEqualTo(6);
        JsonNode fear = journey.get("chains").get(1);
        assertThat(fear.get("key").asText()).isEqualTo("fearScore");
        assertThat(fear.get("first").asInt()).isEqualTo(1);
        assertThat(fear.get("latest").asInt()).isEqualTo(3);
    }

    @Test
    void aiWhichNextOnlyEverNamesAnOpenModule() throws Exception {
        String user = consented(email("module-ai-next"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleNextDraft.class)))
                .thenReturn(structured(new AiDrafts.ModuleNextDraft(9, "Partners would help.")));
        JsonNode outside = read(send(post("/api/ai/modules/next"), user, Map.of()).andExpect(status().isOk()));
        // Module 9 is locked: the rule answer is shown instead.
        assertThat(outside.get("moduleNumber").asInt()).isEqualTo(4);
        assertThat(outside.get("source").asText()).isEqualTo("RULE");
        assertThat(outside.get("requestId").asLong()).isPositive();

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleNextDraft.class)))
                .thenReturn(structured(new AiDrafts.ModuleNextDraft(4, "You have no dreams yet. This module helps you name them.")));
        JsonNode open = read(send(post("/api/ai/modules/next"), user, Map.of()).andExpect(status().isOk()));
        assertThat(open.get("moduleNumber").asInt()).isEqualTo(4);
        assertThat(open.get("source").asText()).isEqualTo("AI");
        assertThat(open.get("reason").asText()).startsWith("You have no dreams yet.");
        assertThat(jdbcTemplate.queryForObject("select feature from ai_usage_logs where id = ?", String.class,
                open.get("requestId").asLong())).isEqualTo("MODULE_NEXT");
    }

    @Test
    void draftUsesTheAnswersGivenInPlaceAndRefineKeepsEditedRows() throws Exception {
        String user = consented(email("module-refine"));
        complete("module-refine", 4);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleHelpDraft.class)))
                .thenReturn(structured(new AiDrafts.ModuleHelpDraft(List.of(), null, List.of(
                        row("Claude belief one", "Claude replacement one"), row("Claude belief two", "Claude replacement two")),
                        null, List.of())));

        JsonNode draft = read(send(post("/api/ai/modules/1/help"), user, Map.of("mode", "DRAFT", "language", "en",
                "answers", List.of(Map.of("question", "What do you tell yourself?", "answer", "I am too old to learn research")))));
        assertThat(draft.get("rows")).hasSize(2);
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.ModuleHelpDraft.class));
        assertThat(system.getValue()).contains("I am too old to learn research");

        // Row 1 is the participant's own edit, so it comes back exactly as they wrote it.
        JsonNode refined = read(send(post("/api/ai/modules/1/help"), user, Map.of("mode", "REFINE", "language", "en",
                "refine", "SHORTER", "keepRows", List.of(0),
                "rows", List.of(Map.of("belief", "My own belief", "replacement", "My own replacement"),
                        Map.of("belief", "Draft two", "replacement", "Draft two replacement"))))
                .andExpect(status().isOk()));
        assertThat(refined.get("rows").get(0).get("belief").asText()).isEqualTo("My own belief");
        assertThat(refined.get("rows").get(1).get("belief").asText()).isEqualTo("Claude belief two");
        // A refine needs a choice or a note; nothing is ever saved.
        send(post("/api/ai/modules/1/help"), user, Map.of("mode", "REFINE", "rows", List.of(Map.of("belief", "x", "replacement", "y"))))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("select count(*) from module_completions where module_number = 1 and user_id = "
                + "(select id from app_users where email like 'module-refine-%')", Integer.class)).isZero();
    }

    @Test
    void explainAndCheckBeforeSaving() throws Exception {
        String user = consented(email("module-explain"));
        complete("module-explain", 4);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleExplainDraft.class)))
                .thenReturn(structured(new AiDrafts.ModuleExplainDraft("A belief can hold you back.", "It frees the plan.", "Sophea thought...")));
        JsonNode explain = read(send(post("/api/ai/modules/4/help"), user, Map.of("mode", "EXPLAIN")).andExpect(status().isOk()));
        assertThat(explain.get("explanation").asText()).isEqualTo("A belief can hold you back.");
        assertThat(explain.get("whyItMatters").asText()).isEqualTo("It frees the plan.");
        assertThat(explain.get("example").asText()).isEqualTo("Sophea thought...");

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleCheckDraft.class)))
                .thenReturn(structured(new AiDrafts.ModuleCheckDraft(List.of(
                        new AiDrafts.ModuleCheckItemDraft(1, "replacement", "Say what you will believe instead."),
                        new AiDrafts.ModuleCheckItemDraft(4, "belief", "Row out of range."),
                        new AiDrafts.ModuleCheckItemDraft(1, "secret", "Not a field.")))));
        JsonNode check = read(send(post("/api/ai/modules/1/help"), user, Map.of("mode", "CHECK",
                "rows", List.of(Map.of("belief", "I am too old", "replacement", "")))).andExpect(status().isOk()));
        assertThat(check.get("checks")).hasSize(1);
        assertThat(check.get("checks").get(0).get("row").asInt()).isZero();
        assertThat(check.get("checks").get(0).get("field").asText()).isEqualTo("replacement");
        // A module done on another page has nothing to check or refine.
        send(post("/api/ai/modules/4/help"), user, Map.of("mode", "CHECK")).andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private JsonNode next(String token) throws Exception {
        return read(mockMvc.perform(get("/api/modules/next").header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private void assessment(String token, int settling, int fear, int criticism, int vision, int knowledge, int resources) throws Exception {
        send(put("/api/chain-assessments"), token, Map.of("takenOn", TODAY.toString(), "settlingScore", settling, "fearScore", fear,
                "criticismScore", criticism, "visionScore", vision, "knowledgeScore", knowledge, "resourcesScore", resources))
                .andExpect(status().isOk());
    }

    /** Marks modules done for the user whose email starts with prefix. */
    private void complete(String prefix, int... numbers) {
        for (int number : numbers) {
            jdbcTemplate.update("insert into module_completions (user_id, module_number, completed_at, created_at, updated_at) "
                    + "select id, ?, current_timestamp, current_timestamp, current_timestamp from app_users where email like ?",
                    number, prefix + "-%");
        }
        assertThat(ORDER).hasSize(15);
    }

    private static AiDrafts.RowDraft row(String belief, String replacement) {
        return new AiDrafts.RowDraft(List.of(new AiDrafts.FieldValue("belief", belief), new AiDrafts.FieldValue("replacement", replacement)));
    }

    private static <T> ClaudeClient.Structured<T> structured(T value) {
        return new ClaudeClient.Structured<>(value, "claude-opus-5-5", 10, 10, false);
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Module Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
