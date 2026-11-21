package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.ReminderService;
import com.visionmapping.service.ai.AiDrafts;
import com.visionmapping.service.ai.ClaudeClient;
import com.visionmapping.service.mail.ReminderMailer;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
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

/**
 * V19 phase 5 (BRD 19.0.0): Draft with AI for the commentary (RM-FR-29) and
 * the report reminder (RM-FR-49), through the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportPhase5FlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("UTC"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReminderService reminderService;

    @MockitoBean
    private ClaudeClient claudeClient;

    @MockitoBean
    private ReminderMailer reminderMailer;

    @BeforeEach
    void aiOnWithAKey() throws Exception {
        reset(claudeClient);
        String admin = register("report-ai-admin");
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'report-ai-admin-%'");
        jdbcTemplate.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, monthly_budget_usd = 20.00, "
                + "daily_message_limit = 50");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void rmFr29_draftWithAiReadsOnlyTheReportFactsAndFillsOnlyEmptySections() throws Exception {
        String token = register("report-ai");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        task(token, plan.stepId(), Map.of("title", "Book the hall"));
        long id = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        send(put("/api/reports/saved/" + id + "/commentary"), token, Map.of("risks", "- Venue may cancel", "nothingNeeded", false)).andExpect(status().isOk());

        // Consent first (V15): nothing reaches Claude without it.
        send(post("/api/reports/saved/" + id + "/commentary-draft?mode=AI"), token, Map.of()).andExpect(status().isBadRequest());
        verifyNoInteractions(claudeClient);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());

        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ReportCommentaryAiDraft.class)))
                .thenReturn(new ClaudeClient.Structured<>(new AiDrafts.ReportCommentaryAiDraft(
                        "1. Started the venue work\n\n* Kept the plan moving", "- Should not replace mine", "", "- Book the hall this week"),
                        "claude-opus-5-5", 10, 10, false));
        JsonNode draft = read(send(post("/api/reports/saved/" + id + "/commentary-draft?mode=AI&language=en"), token, Map.of())
                .andExpect(status().isOk()));
        assertThat(draft.get("highlights").asText()).isEqualTo("- Started the venue work\n- Kept the plan moving");
        assertThat(draft.get("risks").isNull()).as("risks already has text").isTrue();
        assertThat(draft.get("decisionsNeeded").isNull()).as("nothing to suggest").isTrue();
        assertThat(draft.get("nextFocus").asText()).isEqualTo("- Book the hall this week");

        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).structured(anyString(), anyString(), data.capture(), prompt.capture(), eq(AiDrafts.ReportCommentaryAiDraft.class));
        assertThat(data.getValue()).contains("Fixture goal: ", "% done", "Rule-based notes, Focus for next period", "Book the hall")
                .doesNotContain("Venue may cancel");
        assertThat(prompt.getValue()).contains("never invent", "Answer in English");
        Integer logged = jdbcTemplate.queryForObject("select count(*) from ai_usage_logs where feature = 'REPORT_DRAFT'", Integer.class);
        assertThat(logged).isPositive();

        send(post("/api/reports/saved/" + id + "/commentary-draft?mode=MAGIC"), token, Map.of()).andExpect(status().isBadRequest());
        String stranger = register("report-ai-stranger");
        send(put("/api/ai/consent"), stranger, Map.of("consent", true)).andExpect(status().isOk());
        send(post("/api/reports/saved/" + id + "/commentary-draft?mode=AI"), stranger, Map.of()).andExpect(status().isNotFound());

        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());
        send(post("/api/reports/saved/" + id + "/commentary-draft?mode=AI"), token, Map.of()).andExpect(status().isConflict());
        verify(claudeClient, times(1)).structured(anyString(), anyString(), anyString(), anyString(), eq(AiDrafts.ReportCommentaryAiDraft.class));
    }

    @Test
    void rmFr49_aWeeklyReportReminderShowsUntilAReportIsFinalisedAndEmailsOnItsDay() throws Exception {
        String email = "report-reminder-" + System.nanoTime() + "@example.com";
        String token = registerWith(email);
        JsonNode off = read(mockMvc.perform(get("/api/reports/saved/reminder").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(off.get("due").asBoolean()).isFalse();
        assertThat(off.get("cadence").asText()).isEqualTo("NONE");

        send(put("/api/account/reminders"), token, Map.of("enabled", false, "timeZone", "UTC", "reportReminder", "WEEKLY"))
                .andExpect(status().isBadRequest());
        int weekday = TODAY.getDayOfWeek().getValue();
        JsonNode settings = read(send(put("/api/account/reminders"), token,
                Map.of("enabled", false, "timeZone", "UTC", "reportReminder", "WEEKLY", "reportReminderDay", weekday))
                .andExpect(status().isOk()));
        assertThat(settings.get("reportReminder").asText()).isEqualTo("WEEKLY");
        assertThat(settings.get("reportReminderDay").asInt()).isEqualTo(weekday);
        // Saving the daily reminder alone keeps the report reminder.
        send(put("/api/account/reminders"), token, Map.of("enabled", false, "timeZone", "UTC")).andExpect(status().isOk());

        JsonNode due = reminder(token);
        assertThat(due.get("due").asBoolean()).isTrue();
        assertThat(due.get("dueOn").asText()).isEqualTo(TODAY.toString());
        assertThat(due.get("latestFinalId").isNull()).isTrue();

        // The email goes out on the day through the reminder job, though daily reminders are off.
        Long userId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
        clearInvocations(reminderMailer);
        reminderService.remindIfDue(userId, TODAY.atTime(9, 0).atZone(ZoneId.of("UTC")).toInstant());
        verify(reminderMailer, times(1)).send(eq(email), contains("prepare your weekly report"));
        verify(reminderMailer, never()).send(anyString(), contains("Open your day"));

        long id = created(send(post("/api/reports/saved"), token, Map.of())).get("id").asLong();
        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());
        JsonNode done = reminder(token);
        assertThat(done.get("due").asBoolean()).isFalse();
        assertThat(done.get("latestFinalId").asLong()).isEqualTo(id);

        JsonNode monthly = read(send(put("/api/account/reminders"), token,
                Map.of("enabled", false, "timeZone", "UTC", "reportReminder", "MONTHLY", "reportReminderDay", 3))
                .andExpect(status().isOk()));
        assertThat(monthly.get("reportReminder").asText()).isEqualTo("MONTHLY");
        assertThat(monthly.get("reportReminderDay").isNull()).as("only weekly keeps a weekday").isTrue();
        send(put("/api/account/reminders"), token, Map.of("enabled", false, "timeZone", "UTC", "reportReminder", "DAILY"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode reminder(String token) throws Exception {
        return read(mockMvc.perform(get("/api/reports/saved/reminder").header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    private long task(String token, long step, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task", "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        return PlanFixture.createdId(mockMvc, objectMapper, token, "/api/tasks", body);
    }

    private JsonNode created(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated()));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String prefix) throws Exception {
        return registerWith(prefix + "-" + System.nanoTime() + "@example.com");
    }

    private String registerWith(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V19 Phase Five", "email", email,
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
