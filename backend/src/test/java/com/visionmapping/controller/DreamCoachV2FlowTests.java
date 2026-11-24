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
import org.junit.jupiter.api.AfterEach;
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

/** V31 v2 step 1: admin owner roles (DC-FR-66), the coach's guided-plan indicator (DC-FR-67) and the wrong-language retry (EC-16). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DreamCoachV2FlowTests {

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

    private String admin;

    @BeforeEach
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        String adminEmail = email("v2-admin");
        admin = register(adminEmail, null);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', monthly_budget_usd = 20.00, daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @AfterEach
    void noExtraRoles() {
        jdbcTemplate.update("update ai_settings set owner_roles = null");
    }

    @Test
    void adminRolesWidenTheOwnerCheckButNeverAllowNames() throws Exception {
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20,
                "ownerRoles", "Translator, Program officer\nEnumerator")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerRoles").value("Translator, Program officer\nEnumerator"));
        String user = consented(email("roles"), null);
        long dreamId = dream(user);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.StepDrafts(List.of(
                        step("Translate the survey", "Khmer translator"), step("Approve the budget", "Program officer"),
                        step("Collect answers", "Enumerators"), step("Check the data", "Dr. Translator")), ""), "claude-opus-5-5", 10, 10, false));

        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions"), user, Map.of("title", "Run a survey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].owner").value("Khmer translator"))
                .andExpect(jsonPath("$.steps[1].owner").value("Program officer"))
                // Whole words only: "Enumerators" is not the role "Enumerator".
                .andExpect(jsonPath("$.steps[2].owner").isEmpty())
                .andExpect(jsonPath("$.steps[3].owner").isEmpty());

        // Blank removes the list; left out keeps it.
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(jsonPath("$.ownerRoles").value("Translator, Program officer\nEnumerator"));
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20,
                "ownerRoles", " ")).andExpect(jsonPath("$.ownerRoles").isEmpty());
    }

    @Test
    void aCoachSeesWhichPlansWereBuiltWithTheGuideAndNothingElse() throws Exception {
        String adminEmail = email("v2-cohort-admin");
        String cohortAdmin = register(adminEmail, null);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        String coachEmail = email("v2-coach");
        String coach = register(coachEmail, null);
        JsonNode cohort = read(send(post("/api/admin/cohorts"), cohortAdmin, Map.of("name", "Cohort", "coachEmail", coachEmail)));
        String participantEmail = email("v2-participant");
        String participant = register(participantEmail, cohort.get("joinCode").asText());
        long participantId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, participantEmail);
        long guided = dream(participant);
        long manual = dream(participant);

        Map<String, Object> step = Map.of("key", "s1", "title", "Open an account", "owner", "Me", "targetDate", TODAY.plusWeeks(1).toString(),
                "priority", "MEDIUM", "complex", false, "tasks", List.of());
        Map<String, Object> goal = new HashMap<>(Map.of("key", "g1", "title", "Save a deposit", "priority", "HIGH",
                "targetDate", TODAY.plusMonths(2).toString(), "successCriteria", "Deposit saved", "steps", List.of(step)));
        send(put("/api/ai/plan/drafts/" + guided), participant, Map.of("stage", "REVIEW", "content", Map.of("goals", List.of(goal))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/plan/drafts/" + guided + "/add").header("Authorization", "Bearer " + participant))
                .andExpect(status().isCreated());

        JsonNode detail = read(mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + coach))
                .andExpect(status().isOk()));
        assertThat(detail.get("guidePlans")).hasSize(1);
        assertThat(detail.get("guidePlans").get(0).get("dreamId").asLong()).isEqualTo(guided);
        assertThat(detail.get("guidePlans").get(0).get("finishedAt").asText()).isNotBlank();
        assertThat(detail.get("guidePlans").findValues("dreamId").stream().map(JsonNode::asLong).toList()).containsExactly(guided)
                .doesNotContain(manual);
        assertThat(detail.get("guidePlans").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("dreamId", "finishedAt");
    }

    @Test
    void aPlanAnswerInTheWrongLanguageIsAskedForOnceMore() throws Exception {
        String user = consented(email("language"), null);
        long dreamId = dream(user);
        AiDrafts.StepDrafts english = new AiDrafts.StepDrafts(List.of(step("Search the literature for recent studies on clinic data", "Me")), "");
        AiDrafts.StepDrafts khmer = new AiDrafts.StepDrafts(List.of(step("ស្វែងរកឯកសារស្រាវជ្រាវ", "Me")), "");
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(english, "claude-opus-5-5", 10, 10, false))
                .thenReturn(new ClaudeClient.Structured<>(khmer, "claude-opus-5-5", 10, 10, false));

        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions?language=km"), user, Map.of("title", "Research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].title").value("ស្វែងរកឯកសារស្រាវជ្រាវ"));
        ArgumentCaptor<String> task = ArgumentCaptor.forClass(String.class);
        verify(claudeClient, times(2)).structured(anyString(), anyString(), anyString(), task.capture(), eq(AiDrafts.StepDrafts.class));
        assertThat(task.getAllValues().get(1)).contains("write every text field in Khmer");

        // An English answer to an English request is not asked again.
        reset(claudeClient);
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(english, "claude-opus-5-5", 10, 10, false));
        send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions?language=en"), user, Map.of("title", "Research"))
                .andExpect(status().isOk());
        verify(claudeClient, times(1)).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.StepDrafts.class));
    }

    private static AiDrafts.StepDraft step(String title, String owner) {
        return new AiDrafts.StepDraft(title, "d", owner, TODAY.plusWeeks(2).toString(), "r", "p", "r");
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

    private String consented(String email, String joinCode) throws Exception {
        String token = register(email, joinCode);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private String register(String email, String joinCode) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "V2 Tester", "email", email, "password", "Steady-Harbor-Lamp-42"));
        if (joinCode != null) {
            body.put("joinCode", joinCode);
        }
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
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
