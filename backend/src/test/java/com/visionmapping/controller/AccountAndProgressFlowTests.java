package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.LayoutProcessor;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.visionmapping.service.AccountServiceTables;
import com.visionmapping.service.ReminderService;
import com.visionmapping.service.mail.ReminderMailer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** V8 Phase 3 end-to-end: progress metrics, plan PDF, reminders, data export, account deletion. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountAndProgressFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReminderService reminderService;

    @MockitoBean
    private ReminderMailer reminderMailer;

    @Test
    void metricsStartUnmetAndFollowRealData() throws Exception {
        String token = register("metrics-" + System.nanoTime() + "@example.com");
        JsonNode start = read(mockMvc.perform(get("/api/progress-metrics").param("date", TODAY.toString()).header("Authorization", "Bearer " + token))).get("metrics");
        assertThat(start).hasSize(6);
        start.forEach(metric -> assertThat(metric.get("met").asBoolean()).isFalse());

        long dreamId = dream(token, "Own a home");
        long goalId = goal(token, dreamId);
        long stepId = step(token, goalId, TODAY);
        send(put("/api/steps/" + stepId), token, stepBody(goalId, TODAY, "COMPLETED")).andExpect(status().isOk());
        send(post("/api/obstacles"), token, Map.of("relatedDreamId", dreamId, "title", "Funding", "obstacleType", "MONEY", "severity", "HIGH", "status", "OPEN")).andExpect(status().isCreated());
        send(post("/api/partners"), token, Map.of("name", "P", "supportType", "MENTOR", "status", "AGREED")).andExpect(status().isCreated());
        send(post("/api/milestones"), token, Map.of("dreamId", dreamId, "title", "M", "achievedOn", TODAY.toString())).andExpect(status().isCreated());
        send(put("/api/chain-assessments"), token, assessment(TODAY.minusDays(30), 2)).andExpect(status().isOk());
        send(put("/api/chain-assessments"), token, assessment(TODAY, 3)).andExpect(status().isOk());

        JsonNode metrics = read(mockMvc.perform(get("/api/progress-metrics").param("date", TODAY.toString()).header("Authorization", "Bearer " + token))).get("metrics");
        metrics.forEach(metric -> assertThat(metric.get("met").asBoolean()).as(metric.get("id").asText()).isTrue());
        assertThat(metrics.get(2).get("value").asText()).isEqualTo("50.0%");
        assertThat(metrics.get(3).get("value").asText()).isEqualTo("1 of 1 (100%)");
    }

    @Test
    void planPdfContainsThePlanAndIsPrivate() throws Exception {
        String token = register("pdf-" + System.nanoTime() + "@example.com");
        long dreamId = dream(token, "Own a home");
        step(token, goal(token, dreamId), TODAY);

        byte[] pdf = mockMvc.perform(get("/api/dreams/" + dreamId + "/plan.pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        // The reader builds fonts too, and the app's global layout switch stops it finding Helvetica.
        LayoutProcessor.disable();
        try (PdfReader reader = new PdfReader(pdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Own a home", "It matters", "A goal", "A step", "Me");
        } finally {
            LayoutProcessor.enable();
        }

        String stranger = register("pdf-stranger-" + System.nanoTime() + "@example.com");
        mockMvc.perform(get("/api/dreams/" + dreamId + "/plan.pdf").header("Authorization", "Bearer " + stranger)).andExpect(status().isNotFound());
    }

    @Test
    void remindersAreOptInAndSendAtMostOnceADayAfterTheLocalHour() throws Exception {
        String email = "reminder-" + System.nanoTime() + "@example.com";
        String token = register(email);
        long userId = userId(email);
        step(token, goal(token, dream(token, "Own a home")), TODAY);
        ZoneId zone = ZoneId.of("Asia/Phnom_Penh");
        Instant morning = TODAY.atTime(8, 5).atZone(zone).toInstant();
        Instant early = TODAY.atTime(7, 50).atZone(zone).toInstant();
        clearInvocations(reminderMailer);

        reminderService.remindIfDue(userId, morning);
        verify(reminderMailer, never()).send(anyString(), anyString());

        send(put("/api/account/reminders"), token, Map.of("enabled", true, "timeZone", "Not/AZone")).andExpect(status().isBadRequest());
        send(put("/api/account/reminders"), token, Map.of("enabled", true, "timeZone", "Asia/Phnom_Penh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        reminderService.remindIfDue(userId, early);
        verify(reminderMailer, never()).send(anyString(), anyString());

        reminderService.remindIfDue(userId, morning);
        reminderService.remindIfDue(userId, morning.plusSeconds(900));
        verify(reminderMailer, times(1)).send(eq(email), contains("A step"));
    }

    @Test
    void remindersGoToTheChosenAddressAndFallBackToTheLoginEmail() throws Exception {
        String email = "chosen-" + System.nanoTime() + "@example.com";
        String token = register(email);
        long userId = userId(email);
        step(token, goal(token, dream(token, "Own a home")), TODAY);

        send(put("/api/account/reminders"), token, Map.of("enabled", true, "timeZone", "UTC", "reminderEmail", "not-an-email"))
                .andExpect(status().isBadRequest());
        send(put("/api/account/reminders"), token, Map.of("enabled", true, "timeZone", "UTC", "reminderEmail", " inbox@example.org "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminderEmail").value("inbox@example.org"))
                .andExpect(jsonPath("$.sendsTo").value("inbox@example.org"));
        clearInvocations(reminderMailer);
        reminderService.remindIfDue(userId, TODAY.atTime(9, 0).atZone(ZoneId.of("UTC")).toInstant());
        verify(reminderMailer, times(1)).send(eq("inbox@example.org"), contains("A step"));

        send(put("/api/account/reminders"), token, Map.of("enabled", true, "timeZone", "UTC", "reminderEmail", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminderEmail").doesNotExist())
                .andExpect(jsonPath("$.sendsTo").value(email));
    }

    @Test
    void aTestEmailIsRefusedWhenNoMailServerIsSetUp() throws Exception {
        String token = register("test-mail-" + System.nanoTime() + "@example.com");
        clearInvocations(reminderMailer);
        mockMvc.perform(post("/api/account/reminders/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No email server is set up yet, so a test email cannot be sent."));
        verify(reminderMailer, never()).send(anyString(), anyString());
    }

    @Test
    void aDayWithNothingToSaySendsNothing() throws Exception {
        String email = "quiet-" + System.nanoTime() + "@example.com";
        String token = register(email);
        send(post("/api/reviews"), token, Map.of("reviewType", "WEEKLY", "reviewDate", TODAY.atTime(7, 0).toString(), "summary", "Fine")).andExpect(status().isCreated());
        send(put("/api/account/reminders"), token, Map.of("enabled", true, "timeZone", "UTC")).andExpect(status().isOk());
        clearInvocations(reminderMailer);

        reminderService.remindIfDue(userId(email), TODAY.atTime(9, 0).atZone(ZoneId.of("UTC")).toInstant());

        verify(reminderMailer, never()).send(anyString(), anyString());
    }

    @Test
    void exportHoldsTheUsersDataButNeverThePasswordHash() throws Exception {
        String email = "export-" + System.nanoTime() + "@example.com";
        String token = register(email);
        String stranger = register("export-stranger-" + System.nanoTime() + "@example.com");
        dream(token, "Own a home");
        dream(stranger, "Someone else's dream");

        String body = mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.dreams.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Own a home").doesNotContain("Someone else").doesNotContainIgnoringCase("passwordHash");
    }

    @Test
    void deleteNeedsThePasswordAndRemovesOnlyThatUser() throws Exception {
        String email = "delete-" + System.nanoTime() + "@example.com";
        String token = register(email);
        String otherEmail = "delete-other-" + System.nanoTime() + "@example.com";
        String other = register(otherEmail);
        long otherDream = dream(other, "Keep me");
        long userId = userId(email);

        long dreamId = dream(token, "Own a home");
        long goalId = goal(token, dreamId);
        long second = goal(token, dreamId);
        long stepId = step(token, goalId, TODAY);
        Map<String, Object> task = Map.of("stepId", stepId, "title", "T", "owner", "Me", "priority", "HIGH", "dueDate", TODAY.toString(), "status", "NOT_STARTED", "progressPercent", 0);
        send(post("/api/tasks"), token, task).andExpect(status().isCreated());
        send(post("/api/partners"), token, Map.of("name", "P", "supportType", "MENTOR", "status", "ACTIVE", "relatedDreamId", dreamId)).andExpect(status().isCreated());
        send(post("/api/obstacles"), token, Map.of("relatedDreamId", dreamId, "title", "O", "obstacleType", "MONEY", "severity", "HIGH", "status", "OPEN")).andExpect(status().isCreated());
        send(post("/api/reviews"), token, Map.of("reviewType", "WEEKLY", "reviewDate", TODAY.atTime(7, 0).toString(), "summary", "S", "relatedDreamId", dreamId)).andExpect(status().isCreated());
        send(post("/api/gratitude-entries"), token, Map.of("category", "OTHER", "description", "G", "relatedDreamId", dreamId)).andExpect(status().isCreated());
        send(post("/api/fear-entries"), token, Map.of("fear", "F", "worstCase", "W", "likelihood", "LOW", "firstTest", "T", "relatedDreamId", dreamId)).andExpect(status().isCreated());
        send(post("/api/milestones"), token, Map.of("dreamId", dreamId, "title", "M", "achievedOn", TODAY.toString())).andExpect(status().isCreated());
        send(put("/api/attitude-checkins/" + TODAY), token, Map.of("score", 3)).andExpect(status().isOk());
        send(put("/api/chain-assessments"), token, assessment(TODAY, 3)).andExpect(status().isOk());
        send(post("/api/habits?date=" + TODAY), token, Map.of("name", "Read")).andExpect(status().isCreated());
        jdbcTemplate.update("insert into goal_synergy_links (user_id, goal_id, related_goal_id, created_at, updated_at) values (?, ?, ?, now(), now())", userId, goalId, second);

        send(post("/api/account/delete"), token, Map.of("password", "wrong-password")).andExpect(status().isBadRequest());
        assertThat(count("app_users", "id", userId)).isEqualTo(1);

        send(post("/api/account/delete"), token, Map.of("password", PASSWORD)).andExpect(status().isNoContent());

        assertThat(count("app_users", "id", userId)).isZero();
        for (String table : AccountServiceTables.names()) {
            assertThat(count(table, "user_id", userId)).as(table).isZero();
        }
        mockMvc.perform(get("/api/dreams/" + otherDream).header("Authorization", "Bearer " + other)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void everyTableHoldingUserDataIsCoveredByAccountDeletion() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.columns where lower(column_name) = 'user_id' and lower(table_schema) = 'public'", String.class);
        Set<String> found = new HashSet<>();
        tables.forEach(table -> found.add(table.toLowerCase()));
        assertThat(found).isNotEmpty();
        assertThat(new HashSet<>(AccountServiceTables.names())).isEqualTo(found);
    }

    private long count(String table, String column, long id) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column + " = ?", Long.class, id);
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
    }

    private Map<String, Object> assessment(LocalDate takenOn, int score) {
        return Map.of("takenOn", takenOn.toString(), "settlingScore", score, "fearScore", score, "criticismScore", score,
                "visionScore", score, "knowledgeScore", score, "resourcesScore", score);
    }

    private long dream(String token, String title) throws Exception {
        long areaId = id(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", title, "whyImportant", "It matters",
                "successDefinition", "It is done", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE",
                "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return id(send(post("/api/dreams"), token, body));
    }

    private long goal(String token, long dreamId) throws Exception {
        return id(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "A goal", "priority", "MEDIUM",
                "status", "NOT_STARTED", "moonshot", false, "scheduleMode", "BOTTOM_UP")));
    }

    private long step(String token, long goalId, LocalDate date) throws Exception {
        return id(send(post("/api/steps"), token, stepBody(goalId, date, "NOT_STARTED")));
    }

    private Map<String, Object> stepBody(long goalId, LocalDate date, String stepStatus) {
        return Map.of("goalId", goalId, "title", "A step", "owner", "Me", "sequenceNumber", 1, "complex", false,
                "priority", "MEDIUM", "targetDate", date.toString(), "status", stepStatus);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long id(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V8 Test User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
