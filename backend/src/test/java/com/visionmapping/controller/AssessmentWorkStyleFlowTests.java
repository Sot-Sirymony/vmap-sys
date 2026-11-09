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

/** V15 Phase 5 (AH-FR-23 to AH-FR-30, AH-FR-59, AH-FR-60, AH-FR-68 to AH-FR-77, AH-FR-79): Assessment and Work Style. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssessmentWorkStyleFlowTests {

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
        String admin = register(email("style-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'style-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    // ---------- Assessment ----------

    @Test
    void theRetakeReminderNeedsFourWeeksAndAModuleCompletedSince() throws Exception {
        String user = register(email("style-retake"));
        takeOn("style-retake", TODAY.minusDays(30), 3);
        assertThat(summary(user).get("retakeAfterModule").asBoolean()).isFalse();
        completeOn("style-retake", 4, TODAY.minusDays(10));
        assertThat(summary(user).get("retakeAfterModule").asBoolean()).isTrue();

        String recent = register(email("style-retake-recent"));
        takeOn("style-retake-recent", TODAY.minusDays(10), 3);
        completeOn("style-retake-recent", 4, TODAY.minusDays(2));
        assertThat(summary(recent).get("retakeAfterModule").asBoolean()).isFalse();
        verifyNoInteractions(claudeClient);
    }

    @Test
    void explainMyResultAddsLowChainNotesAndTheTrendAcrossEveryTake() throws Exception {
        String user = consented(email("style-explain"));
        takeOn("style-explain", TODAY.minusDays(90), 3);
        takeOn("style-explain", TODAY.minusDays(45), 3);
        assessment(user, 2, 1, 3, 4, 4, 4);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AssessmentExplanationDraft.class)))
                .thenReturn(structured(new AiDrafts.AssessmentExplanationDraft("Mixed.", 2, "Fear is lowest.", List.of(
                        new AiDrafts.ChainNoteDraft("fearScore", "Trying uncertain things is rare for now."),
                        new AiDrafts.ChainNoteDraft("visionScore", "Not low, so dropped."),
                        new AiDrafts.ChainNoteDraft("settlingScore", "Comfort often wins.")), "Steady over three takes.")));

        JsonNode answer = read(send(post("/api/ai/assessment/explain"), user, Map.of()).andExpect(status().isOk()));
        List<String> keys = new ArrayList<>();
        answer.get("lowChains").forEach(note -> keys.add(note.get("key").asText()));
        assertThat(keys).containsExactly("fearScore", "settlingScore");
        assertThat(answer.get("lowChains").get(0).get("score").asInt()).isEqualTo(1);
        assertThat(answer.get("trend").asText()).isEqualTo("Steady over three takes.");
        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), data.capture(), prompt.capture(), eq(AiDrafts.AssessmentExplanationDraft.class));
        assertThat(data.getValue()).contains("Every take", TODAY.minusDays(90).toString());
        assertThat(prompt.getValue()).contains("never label the person");
    }

    @Test
    void practiseThisChainCoversEachChainScoredThreeOrLessWithTheProgramsAction() throws Exception {
        String user = consented(email("style-practice"));
        send(post("/api/ai/assessment/practice"), user, Map.of()).andExpect(status().isBadRequest());
        assessment(user, 4, 2, 5, 3, 4, 5);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PracticeDrafts.class)))
                .thenReturn(structured(new AiDrafts.PracticeDrafts(List.of(
                        new AiDrafts.PracticeDraft("fearScore", "Log one fear and plan a tiny test.", "Weekly fear test"),
                        new AiDrafts.PracticeDraft("criticismScore", "Not low, dropped.", "x"),
                        new AiDrafts.PracticeDraft("visionScore", "Rewrite your top dream's success definition.", "")))));

        JsonNode answer = read(send(post("/api/ai/assessment/practice"), user, Map.of()).andExpect(status().isOk()));
        assertThat(answer.get("practices")).hasSize(2);
        assertThat(answer.get("practices").get(0).get("chainKey").asText()).isEqualTo("fearScore");
        assertThat(answer.get("practices").get(0).get("habit").asText()).isEqualTo("Weekly fear test");
        assertThat(answer.get("practices").get(1).get("chainKey").asText()).isEqualTo("visionScore");
        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), data.capture(), anyString(), eq(AiDrafts.PracticeDrafts.class));
        assertThat(data.getValue()).contains("in the Journal, log one fear", "on the Dreams page, rewrite")
                .doesNotContain("criticismScore (");

        // Nothing at 3 or less: no AI request at all.
        String strong = consented(email("style-practice-strong"));
        assessment(strong, 4, 4, 4, 5, 5, 4);
        JsonNode none = read(send(post("/api/ai/assessment/practice"), strong, Map.of()).andExpect(status().isOk()));
        assertThat(none.get("practices")).isEmpty();
    }

    @Test
    void whatChangedComparesTheLastTwoTakesWithTheModulesInBetween() throws Exception {
        String user = consented(email("style-changed"));
        send(post("/api/ai/assessment/what-changed"), user, Map.of()).andExpect(status().isBadRequest());
        takeOn("style-changed", TODAY.minusDays(40), 2);
        completeOn("style-changed", 4, TODAY.minusDays(20));
        assessment(user, 3, 3, 3, 4, 3, 3);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.WhatChangedDraft.class)))
                .thenReturn(structured(new AiDrafts.WhatChangedDraft("Your vision score rose after See Your Dream Clearly.")));
        JsonNode answer = read(send(post("/api/ai/assessment/what-changed"), user, Map.of()).andExpect(status().isOk()));
        assertThat(answer.get("summary").asText()).startsWith("Your vision score rose");
        assertThat(answer.get("modulesBetween").get(0).asText()).isEqualTo("See Your Dream Clearly");
    }

    // ---------- Work Style ----------

    @Test
    void theUseMyWorkStyleSwitchControlsWhetherOtherAiHelpSeesTheStyle() throws Exception {
        String user = consented(email("style-switch"));
        quiz(user);
        assertThat(profile(user).get("aiUseWorkStyle").asBoolean()).isFalse();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleHelpDraft.class)))
                .thenReturn(structured(new AiDrafts.ModuleHelpDraft(List.of("Q?"), "Example", List.of(), null, List.of())));

        send(post("/api/ai/modules/4/help"), user, Map.of("mode", "START")).andExpect(status().isOk());
        send(put("/api/work-style-profile/ai-use"), user, Map.of("enabled", true)).andExpect(status().isOk());
        assertThat(profile(user).get("aiUseWorkStyle").asBoolean()).isTrue();
        send(post("/api/ai/modules/4/help"), user, Map.of("mode", "START")).andExpect(status().isOk());

        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, org.mockito.Mockito.times(2))
                .structured(anyString(), anyString(), data.capture(), anyString(), eq(AiDrafts.ModuleHelpDraft.class));
        // AHC-27: off, no style; on, only the dominant and secondary style, never the pole counts.
        assertThat(data.getAllValues().get(0)).doesNotContain("work style", "DRIVER");
        assertThat(data.getAllValues().get(1)).contains("own work style as DRIVER").doesNotContain("fast 6", "deliberate 2");
    }

    @Test
    void balanceMyPartnersCountsStylesAndMissingComplementsWithoutAi() throws Exception {
        String user = register(email("style-balance"));
        quiz(user); // DRIVER: complemented by a PLANNER
        partner(user, "Dr Lina", "CONNECTOR", null);
        partner(user, "Mr Dara", null, null);
        JsonNode balance = read(mockMvc.perform(get("/api/work-style-profile/partner-balance").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(balance.get("dominant").asText()).isEqualTo("DRIVER");
        assertThat(balance.get("complementary").get(0).asText()).isEqualTo("PLANNER");
        assertThat(balance.get("missing").get(0).asText()).isEqualTo("PLANNER");
        assertThat(balance.get("counts").get("CONNECTOR").asLong()).isEqualTo(1);
        assertThat(balance.get("noStyle").asLong()).isEqualTo(1);
        assertThat(balance.get("partners")).hasSize(2);
        verifyNoInteractions(claudeClient);
    }

    @Test
    void workStyleHelpersExplainSuggestPracticesAndTipsWithoutPartnerNames() throws Exception {
        String user = consented(email("style-help"));
        send(post("/api/ai/work-style/help"), user, Map.of("mode", "EXPLAIN")).andExpect(status().isBadRequest());
        quiz(user);
        long styled = partner(user, "Dr Secret Mentor", "STEADIER", "SHARED_VISION");
        long plain = partner(user, "Mr Plain", null, null);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StyleTextDraft.class)))
                .thenReturn(structured(new AiDrafts.StyleTextDraft("You chose fast decisions in 6 of 8 questions.")));
        JsonNode explain = read(send(post("/api/ai/work-style/help"), user, Map.of("mode", "EXPLAIN")).andExpect(status().isOk()));
        assertThat(explain.get("text").asText()).startsWith("You chose fast decisions");

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StylePracticeDrafts.class)))
                .thenReturn(structured(new AiDrafts.StylePracticeDrafts(List.of(
                        new AiDrafts.StylePracticeDraft("Date your next three steps.", "undated_steps", "Date steps first"),
                        new AiDrafts.StylePracticeDraft("Call someone.", "PHONE", ""),
                        new AiDrafts.StylePracticeDraft("Three", "TASKS", ""),
                        new AiDrafts.StylePracticeDraft("Four", "TODAY", "")))));
        JsonNode tips = read(send(post("/api/ai/work-style/help"), user, Map.of("mode", "TIPS")).andExpect(status().isOk()));
        assertThat(tips.get("practices")).hasSize(3);
        assertThat(tips.get("practices").get(0).get("action").asText()).isEqualTo("UNDATED_STEPS");
        assertThat(tips.get("practices").get(1).get("action").isNull()).isTrue();

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StyleTipsDraft.class)))
                .thenReturn(structured(new AiDrafts.StyleTipsDraft(List.of("Acknowledge the relationship first.", "Allow time to decide."))));
        JsonNode partnerTips = read(send(post("/api/ai/work-style/help"), user, Map.of("mode", "PARTNER", "partnerId", styled,
                "partnerNotes", "Likes time to think")).andExpect(status().isOk()));
        assertThat(partnerTips.get("tips")).hasSize(2);
        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), data.capture(), anyString(), eq(AiDrafts.StyleTipsDraft.class));
        assertThat(data.getValue()).contains("estimates their style as STEADIER", "Likes time to think")
                .doesNotContain("Dr Secret Mentor", "secret@example.com");

        send(post("/api/ai/work-style/help"), user, Map.of("mode", "PARTNER", "partnerId", plain)).andExpect(status().isBadRequest());
        String other = register(email("style-help-other"));
        send(post("/api/ai/work-style/help"), other, Map.of("mode", "PARTNER", "partnerId", styled)).andExpect(status().isNotFound());
    }

    @Test
    void adaptToThisPartnerSendsOnlyStyleAndMotivatorValuesAndChangesThreeFields() throws Exception {
        String user = consented(email("style-adapt"));
        quiz(user);
        long styled = partner(user, "Dr Secret Mentor", "PLANNER", "RECOGNITION");
        long plain = partner(user, "Mr Plain", null, null);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.PageAssistantDraft.class)))
                .thenReturn(structured(new AiDrafts.PageAssistantDraft("", List.of(
                        new AiDrafts.PageChangeDraft("callToAction", "Could we fix a date: Tuesday 14 January?", "Planners like a clear date."),
                        new AiDrafts.PageChangeDraft("benefitToPartner", "You will be credited as co-author.", "Recognition."),
                        new AiDrafts.PageChangeDraft("request", "Changed", "Not one of the three fields.")))));

        JsonNode answer = read(send(post("/api/ai/communication/help"), user, Map.of("mode", "ADAPT",
                "form", Map.of("partnerId", String.valueOf(styled), "request", "Review my note")))
                .andExpect(status().isOk()));
        List<String> fields = new ArrayList<>();
        answer.get("changes").forEach(change -> fields.add(change.get("field").asText()));
        assertThat(fields).containsExactly("callToAction", "benefitToPartner");
        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), data.capture(), anyString(), eq(AiDrafts.PageAssistantDraft.class));
        // AHC-22: style and motivator values, the name and role already sent, never email or notes; no own style while the switch is off.
        assertThat(data.getValue()).contains("partner's work style as PLANNER", "primary motivator", "RECOGNITION")
                .doesNotContain("secret@example.com", "Private partner notes", "own work style");
        assertThat(jdbcTemplate.queryForObject("select feature from ai_usage_logs where id = ?", String.class,
                answer.get("requestId").asLong())).isEqualTo("MESSAGE_ADAPT");

        send(post("/api/ai/communication/help"), user, Map.of("mode", "ADAPT", "form", Map.of("partnerId", String.valueOf(plain))))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private JsonNode summary(String token) throws Exception {
        return read(mockMvc.perform(get("/api/chain-assessments").header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private JsonNode profile(String token) throws Exception {
        return read(mockMvc.perform(get("/api/work-style-profile").header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private void quiz(String token) throws Exception {
        send(put("/api/work-style-profile"), token, Map.of("paceFastScore", 6, "paceDeliberateScore", 2, "focusTaskScore", 6,
                "focusPeopleScore", 2)).andExpect(status().isOk());
    }

    private long partner(String token, String name, String style, String motivator) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "role", "Research lead", "email", "secret@example.com",
                "notes", "Private partner notes", "supportType", "MENTOR", "status", "ACTIVE"));
        if (style != null) {
            body.put("workStyleType", style);
        }
        if (motivator != null) {
            body.put("primaryMotivator", motivator);
        }
        return read(send(post("/api/partners"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private void assessment(String token, int settling, int fear, int criticism, int vision, int knowledge, int resources) throws Exception {
        send(put("/api/chain-assessments"), token, Map.of("takenOn", TODAY.toString(), "settlingScore", settling, "fearScore", fear,
                "criticismScore", criticism, "visionScore", vision, "knowledgeScore", knowledge, "resourcesScore", resources))
                .andExpect(status().isOk());
    }

    private void takeOn(String prefix, LocalDate day, int score) {
        jdbcTemplate.update("insert into chain_assessments (user_id, taken_on, settling_score, fear_score, criticism_score, vision_score, "
                + "knowledge_score, resources_score, created_at, updated_at) select id, ?, ?, ?, ?, ?, ?, ?, current_timestamp, "
                + "current_timestamp from app_users where email like ?", day, score, score, score, score, score, score, prefix + "-%");
    }

    private void completeOn(String prefix, int module, LocalDate day) {
        jdbcTemplate.update("insert into module_completions (user_id, module_number, completed_at, created_at, updated_at) "
                + "select id, ?, ?, current_timestamp, current_timestamp from app_users where email like ?",
                module, java.sql.Timestamp.valueOf(day.atTime(12, 0)), prefix + "-%");
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
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Style Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
