package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.AiSettingsService;
import com.visionmapping.service.support.ClaudeKeyChecker;
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

/** V9 (AI-FR-24): the admin AI settings page, including the Claude API key. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiSettingsFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiSettingsService aiSettingsService;

    @MockitoBean
    private ClaudeKeyChecker keyChecker;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("update ai_settings set enabled = false, model = 'claude-opus-5-5', monthly_budget_usd = 20.00, api_key_encrypted = null, "
                + "api_key_last4 = null, last_test_at = null, last_test_ok = null, last_test_message = null");
    }

    @Test
    void onlyAnAdminSeesOrChangesTheSettings() throws Exception {
        String user = register("user");
        mockMvc.perform(get("/api/admin/ai-settings").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        send(put("/api/admin/ai-settings/api-key"), user, Map.of("apiKey", KEY)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/ai-settings")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/ai-settings").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.monthlyBudgetUsd").value(20.0))
                .andExpect(jsonPath("$.keySource").value("NONE"))
                .andExpect(jsonPath("$.models.length()").value(3));
    }

    @Test
    void aSavedKeyIsEncryptedNeverReturnedAndUsedByTheBackend() throws Exception {
        String admin = admin();
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", "not-a-claude-key-at-all-1234")).andExpect(status().isBadRequest());

        String body = send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", "  " + KEY + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keySource").value("DATABASE"))
                .andExpect(jsonPath("$.keyReadable").value(true))
                .andExpect(jsonPath("$.apiKeyLast4").value("wxyz"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(KEY);

        String stored = jdbcTemplate.queryForObject("select api_key_encrypted from ai_settings", String.class);
        assertThat(stored).startsWith("v1:").doesNotContain(KEY);
        assertThat(aiSettingsService.activeApiKey()).isEqualTo(KEY);

        mockMvc.perform(delete("/api/admin/ai-settings/api-key").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.keySource").value("NONE"))
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(aiSettingsService.activeApiKey()).isNull();
    }

    @Test
    void aiTurnsOnOnlyWithAKeyAndTheTestReportsTheResult() throws Exception {
        String admin = admin();
        Map<String, Object> on = Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20);
        send(put("/api/admin/ai-settings"), admin, on).andExpect(status().isBadRequest());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", false, "model", "gpt-4", "monthlyBudgetUsd", 20))
                .andExpect(status().isBadRequest());

        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-sonnet-5-5", "monthlyBudgetUsd", 15.5))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.monthlyBudgetUsd").value(15.5));

        when(keyChecker.check(anyString(), anyString())).thenReturn(new ClaudeKeyChecker.Result(true, "Connected."));
        mockMvc.perform(post("/api/admin/ai-settings/test").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.lastTestOk").value(true))
                .andExpect(jsonPath("$.lastTestMessage").value("Connected."));
        verify(keyChecker).check(eq(KEY), eq("claude-sonnet-5-5"));
    }

    @Test
    void aKeySealedWithAnotherEncryptionKeyIsReportedUnreadable() throws Exception {
        jdbcTemplate.update("update ai_settings set api_key_encrypted = 'v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', api_key_last4 = 'abcd'");
        mockMvc.perform(get("/api/admin/ai-settings").header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.keySource").value("DATABASE"))
                .andExpect(jsonPath("$.keyReadable").value(false));
        assertThat(aiSettingsService.activeApiKey()).isNull();
    }

    private String admin() throws Exception {
        String email = "admin-" + System.nanoTime() + "@example.com";
        String token = register(email);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", email);
        return token;
    }

    private String register(String prefix) throws Exception {
        String email = prefix.contains("@") ? prefix : prefix + "-" + System.nanoTime() + "@example.com";
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "AI Admin", "email", email, "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
