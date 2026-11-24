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

/** V31 v1 step 1 (DC-FR-12, DC-FR-15, DC-FR-16): AI help for the area and dream stages, before a dream exists. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DreamCoachIntakeFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final String NEXT_YEAR = LocalDate.now().plusYears(1).toString();

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
        String admin = register(email("intake-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'intake-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void aVisionStatementIsWordedFromTheParticipantsSentence() throws Exception {
        String user = consented(email("statement"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.VisionStatementText.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.VisionStatementText(
                        "My research changes how clinics in Cambodia use data."), "claude-opus-5-5", 10, 10, false));

        send(post("/api/ai/plan/vision-statement"), user, Map.of("areaName", "Career",
                "sentence", "I want my research to change how clinics in Cambodia use data."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statement").value("My research changes how clinics in Cambodia use data."))
                .andExpect(jsonPath("$.requestId").isNumber());
        assertThat(jdbcTemplate.queryForObject("select feature from ai_usage_logs where id = (select max(id) from ai_usage_logs)",
                String.class)).isEqualTo("COACH_AREA");
        // Nothing is saved.
        assertThat(jdbcTemplate.queryForObject("select count(*) from vision_areas where user_id = (select id from app_users "
                + "where email like 'statement-%')", Integer.class)).isZero();
    }

    @Test
    void theCoachAsksAboutMissingPartsThenRestatesWithTheParticipantsOwnDate() throws Exception {
        String user = consented(email("intake"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DreamIntakeDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.DreamIntakeDraft(false, "By when would you like that?",
                        "target_date", null, null, null), "claude-opus-5-5", 10, 10, false));
        send(post("/api/ai/plan/dream-intake"), user, Map.of("areaName", "Career", "words", "Get better at research with AI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.question").value("By when would you like that?"))
                .andExpect(jsonPath("$.field").value("TARGET_DATE"))
                .andExpect(jsonPath("$.title").isEmpty());

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DreamIntakeDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.DreamIntakeDraft(true, "", "",
                        "Become an AI-assisted public health researcher", "", "My mentor accepts my concept note"),
                        "claude-opus-5-5", 10, 10, false));
        List<Map<String, String>> answers = List.of(
                Map.of("question", "What would better look like?", "answer", "A concept note my mentor accepts", "field", "SUCCESS_DEFINITION"),
                Map.of("question", "By when?", "answer", NEXT_YEAR, "field", "TARGET_DATE"));
        send(post("/api/ai/plan/dream-intake"), user, Map.of("words", "Get better at research with AI", "answers", answers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.title").value("Become an AI-assisted public health researcher"))
                .andExpect(jsonPath("$.description").value("Get better at research with AI"))
                .andExpect(jsonPath("$.whyImportant").isEmpty())
                .andExpect(jsonPath("$.successDefinition").value("My mentor accepts my concept note"))
                .andExpect(jsonPath("$.targetDate").value(NEXT_YEAR));
    }

    @Test
    void noQuestionAfterThreeAnswersOrSuggestAnyway() throws Exception {
        String user = consented(email("forced"));
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DreamIntakeDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.DreamIntakeDraft(false, "One more question?", "", "", "", ""),
                        "claude-opus-5-5", 10, 10, false));
        List<Map<String, String>> three = List.of(
                Map.of("question", "q1", "answer", "a1"), Map.of("question", "q2", "answer", "next June", "field", "TARGET_DATE"),
                Map.of("question", "q3", "answer", "a3"));
        send(post("/api/ai/plan/dream-intake"), user, Map.of("words", "Run a marathon", "answers", three))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.question").isEmpty())
                // Claude gave no title, so the participant's words are used; a written date is not guessed.
                .andExpect(jsonPath("$.title").value("Run a marathon"))
                .andExpect(jsonPath("$.targetDate").isEmpty());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        send(post("/api/ai/plan/dream-intake"), user, Map.of("words", "Run a marathon", "suggestAnyway", true))
                .andExpect(jsonPath("$.done").value(true));
        verify(claudeClient, org.mockito.Mockito.times(2)).structured(anyString(), anyString(), anyString(), prompt.capture(),
                eq(AiDrafts.DreamIntakeDraft.class));
        assertThat(prompt.getValue()).contains("Do not ask a question");

        // Four answers are refused.
        send(post("/api/ai/plan/dream-intake"), user, Map.of("words", "x", "answers", List.of(three.get(0), three.get(0), three.get(0),
                three.get(0)))).andExpect(status().isBadRequest());
    }

    @Test
    void withoutConsentTheCoachSaysHowToTurnItOn() throws Exception {
        String user = register(email("no-consent"));
        send(post("/api/ai/plan/dream-intake"), user, Map.of("words", "Run a marathon")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Turn on AI consent in Account settings to use the Dream Coach."));
        send(post("/api/ai/plan/vision-statement"), user, Map.of("areaName", "Health", "sentence", "Strong"))
                .andExpect(status().isBadRequest());
        verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.DreamIntakeDraft.class));
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Intake Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
}
