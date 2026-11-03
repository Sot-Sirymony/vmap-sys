package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.ai.AiDrafts;
import com.visionmapping.service.ai.ClaudeClient;
import java.time.LocalDate;
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

/** V9 Phase 7: module help and the assessment explanation. Drafts never save or unlock anything (AC-09). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiModuleFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";

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
        String adminEmail = email("module-admin");
        String admin = register(adminEmail);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 20");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void helpToStartGivesQuestionsAndAnExampleAndAFormlessModuleCannotBeDrafted() throws Exception {
        String user = consented(email("start"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleHelpDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ModuleHelpDraft(
                        List.of("Which area matters most?", "What would you do with no limits?", "When?", "Too many"),
                        "Dara dreams of a bakery by 2028.", List.of(), "", List.of()), "claude-opus-5-5", 10, 10, false));

        // Module 4 opens first and is proven by ranked dreams, not a form.
        help(user, 4, "START", List.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.example").value("Dara dreams of a bakery by 2028."))
                .andExpect(jsonPath("$.rows.length()").value(0));
        help(user, 4, "DRAFT", List.of()).andExpect(status().isBadRequest());
        help(user, 4, "FEEDBACK", List.of()).andExpect(status().isBadRequest());
        // Module 1 stays locked until module 4 is done.
        help(user, 1, "START", List.of()).andExpect(status().isBadRequest());
    }

    @Test
    void aDraftFillsOnlyTheModulesTextFieldsAndSavesNothing() throws Exception {
        String email = email("draft");
        String user = consented(email);
        unlockAllBut(email, 11);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleHelpDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ModuleHelpDraft(List.of(), "", List.of(
                        new AiDrafts.RowDraft(List.of(new AiDrafts.FieldValue("pitch", "Help me test my menu"),
                                new AiDrafts.FieldValue("rating", "5"), new AiDrafts.FieldValue("password", "x")))),
                        "", List.of()), "claude-opus-5-5", 10, 10, false));

        JsonNode answer = read(help(user, 11, "DRAFT", List.of()).andExpect(status().isOk()));
        JsonNode row = answer.get("rows").get(0);
        assertThat(row.get("pitch").asText()).isEqualTo("Help me test my menu");
        assertThat(row.get("practice").asText()).isEmpty();
        assertThat(row.get("rating").asText()).isEmpty();
        assertThat(row.has("password")).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from module_completions c join app_users u on u.id = c.user_id "
                + "where u.email = ? and c.module_number = 11", Integer.class, email)).isZero();
    }

    @Test
    void feedbackSeesOnlyTheExercisesOwnFieldsAndTheFearModuleSeesTheFearLog() throws Exception {
        String email = email("feedback");
        String user = consented(email);
        unlockAllBut(email, 11);
        send(post("/api/fear-entries"), user, Map.of("fear", "Nobody buys my bread", "worstCase", "x", "likelihood", "LOW",
                "firstTest", "Sell ten loaves")).andExpect(status().isCreated());
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ModuleHelpDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ModuleHelpDraft(List.of(), "", List.of(),
                        "A clear start.", List.of("Name the ask")), "claude-opus-5-5", 10, 10, false));

        help(user, 11, "FEEDBACK", List.of(Map.of("pitch", "Buy my bread", "practice", "", "secret", "do not send")))
                .andExpect(jsonPath("$.feedback").value("A clear start."))
                .andExpect(jsonPath("$.improvements[0]").value("Name the ask"));
        help(user, 2, "START", List.of()).andExpect(status().isOk());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, org.mockito.Mockito.times(2))
                .structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.ModuleHelpDraft.class));
        assertThat(system.getAllValues().get(0)).contains("pitch = Buy my bread").doesNotContain("do not send")
                .contains("one specific ask");
        assertThat(system.getAllValues().get(1)).contains("Nobody buys my bread").contains("smallest safe first test");
    }

    @Test
    void theAssessmentExplanationNeedsAnAssessmentAndNamesOnlyARealModule() throws Exception {
        String user = consented(email("assess"));
        mockMvc.perform(post("/api/ai/assessment/explain").header("Authorization", "Bearer " + user)).andExpect(status().isBadRequest());
        verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AssessmentExplanationDraft.class));

        send(put("/api/chain-assessments"), user, Map.of("takenOn", LocalDate.now().toString(), "settlingScore", 4, "fearScore", 1,
                "criticismScore", 3, "visionScore", 5, "knowledgeScore", 2, "resourcesScore", 3)).andExpect(status().is2xxSuccessful());
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AssessmentExplanationDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.AssessmentExplanationDraft("Fear holds you back most.", 2,
                        "Testing fears comes first."), "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/assessment/explain").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.focusModule").value(2))
                .andExpect(jsonPath("$.focusTitle").value("Test Your Fears"));

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.AssessmentExplanationDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.AssessmentExplanationDraft("x", 99, "y"), "claude-opus-5-5", 10, 10, false));
        mockMvc.perform(post("/api/ai/assessment/explain").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.focusModule").doesNotExist());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, org.mockito.Mockito.times(2))
                .structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.AssessmentExplanationDraft.class));
        assertThat(system.getValue()).contains("Fear of failure").contains(": 1\n");
    }

    private void unlockAllBut(String email, int moduleNumber) {
        long userId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
        jdbcTemplate.update("insert into module_completions (user_id, module_number, completed_at, created_at, updated_at) "
                + "select ?, module_number, current_timestamp, current_timestamp, current_timestamp from learning_modules "
                + "where module_number <> ?", userId, moduleNumber);
    }

    private ResultActions help(String token, int module, String mode, List<Map<String, String>> rows) throws Exception {
        return send(post("/api/ai/modules/" + module + "/help"), token, Map.of("mode", mode, "rows", rows, "language", "en"));
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Module Tester", "email", email, "password", "Password123"))))
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
