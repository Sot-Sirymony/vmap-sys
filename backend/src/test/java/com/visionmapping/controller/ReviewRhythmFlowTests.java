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
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

/** V31 v1 step 3 (DC-FR-48 to DC-FR-50): review rhythms, due reviews, saving with the plan, and the Dream Coach rules. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewRhythmFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now();
    private static final String WEEKDAY = TODAY.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();

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
        String admin = register(email("rhythm-admin"));
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'rhythm-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void aRhythmIsSavedPrivateAndChecked() throws Exception {
        String user = register(email("rhythm"));
        long dreamId = dream(user);
        mockMvc.perform(get("/api/dreams/" + dreamId + "/review-rhythm").header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());
        send(put("/api/dreams/" + dreamId + "/review-rhythm"), user, Map.of("daily", true, "weeklyDay", "FRI", "monthlyDay", 1, "quarterly", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyDay").value("FRI"));
        send(put("/api/dreams/" + dreamId + "/review-rhythm"), user, Map.of("daily", false, "weeklyDay", "", "quarterly", true))
                .andExpect(jsonPath("$.weeklyDay").isEmpty())
                .andExpect(jsonPath("$.monthlyDay").isEmpty())
                .andExpect(jsonPath("$.quarterly").value(true));
        assertThat(jdbcTemplate.queryForObject("select count(*) from dream_review_rhythms where dream_id = ?", Integer.class, dreamId)).isEqualTo(1);

        send(put("/api/dreams/" + dreamId + "/review-rhythm"), user, Map.of("weeklyDay", "XYZ")).andExpect(status().isBadRequest());
        send(put("/api/dreams/" + dreamId + "/review-rhythm"), user, Map.of("monthlyDay", 30)).andExpect(status().isBadRequest());
        String other = register(email("rhythm-other"));
        mockMvc.perform(get("/api/dreams/" + dreamId + "/review-rhythm").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        send(put("/api/dreams/" + dreamId + "/review-rhythm"), other, Map.of("daily", true)).andExpect(status().isNotFound());
    }

    @Test
    void reviewsAreDueUntilOneOfThatKindIsWritten() throws Exception {
        String user = register(email("due"));
        long dreamId = dream(user);
        send(put("/api/dreams/" + dreamId + "/review-rhythm"), user, Map.of("daily", true, "weeklyDay", WEEKDAY,
                "monthlyDay", Math.min(TODAY.getDayOfMonth(), 28), "quarterly", true)).andExpect(status().isOk());

        JsonNode due = read(mockMvc.perform(get("/api/reviews/due").header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(due.findValuesAsText("reviewType")).containsExactlyInAnyOrder("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY");
        assertThat(due.get(0).get("dreamTitle").asText()).isEqualTo("Become a researcher");

        send(post("/api/reviews"), user, Map.of("reviewType", "DAILY", "reviewDate", TODAY.atTime(9, 0).toString(), "relatedDreamId", dreamId,
                "summary", "Done")).andExpect(status().isCreated());
        due = read(mockMvc.perform(get("/api/reviews/due").header("Authorization", "Bearer " + user)));
        assertThat(due.findValuesAsText("reviewType")).containsExactlyInAnyOrder("WEEKLY", "MONTHLY", "QUARTERLY");

        // An archived dream's rhythm makes nothing due.
        jdbcTemplate.update("update dreams set archived = true where id = ?", dreamId);
        assertThat(read(mockMvc.perform(get("/api/reviews/due").header("Authorization", "Bearer " + user)))).isEmpty();
    }

    @Test
    void theRhythmIsSavedWithThePlanAndTheCoachRulesReachClaude() throws Exception {
        String user = consented(email("with-plan"));
        long dreamId = dream(user);
        Map<String, Object> step = Map.of("key", "s1", "title", "Open an account", "owner", "Me", "targetDate", TODAY.plusWeeks(1).toString(),
                "priority", "MEDIUM", "complex", false, "tasks", List.of());
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save a deposit", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(2).toString(), "successCriteria", "Deposit saved", "steps", List.of(step)));
        send(put("/api/ai/plan/drafts/" + dreamId), user, Map.of("stage", "REVIEW", "content", Map.of("goals", List.of(goal),
                "innerBlockers", "READY", "reviewRhythm", Map.of("daily", false, "weeklyDay", "SUN", "monthlyDay", 15, "quarterly", false))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/plan/drafts/" + dreamId + "/add").header("Authorization", "Bearer " + user))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/dreams/" + dreamId + "/review-rhythm").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.weeklyDay").value("SUN"))
                .andExpect(jsonPath("$.monthlyDay").value(15));

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(), "What comes first?"), "claude-opus-5-5", 10, 10, false));
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions"), user, Map.of("title", "Save a deposit")).andExpect(status().isOk());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), system.capture(), anyString(), eq(AiDrafts.StepDrafts.class));
        assertThat(system.getValue()).contains("Dream Coach method").contains("No guessing");
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Become a researcher", "whyImportant", "Impact",
                "successDefinition", "Accepted", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        body.put("targetDate", TODAY.plusMonths(6).toString());
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private String consented(String email) throws Exception {
        String token = register(email);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Rhythm Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
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
