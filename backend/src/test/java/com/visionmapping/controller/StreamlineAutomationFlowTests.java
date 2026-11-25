package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.ReminderService;
import com.visionmapping.service.mail.ReminderMailer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** V32 phase 4 (BRD 32 D-32-9 to D-32-12): reminders, the cycle-time rule, the weekly nudge and saved dismissals. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StreamlineAutomationFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReminderService reminderService;

    @MockitoBean
    private ReminderMailer reminderMailer;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private record Plan(String token, String email, long goalId, List<Long> stepIds, List<Long> taskIds) {
    }

    @Test
    void sfc10_remindMeRemindsOnItsDayOnTheTodayPageAndInTheEmail() throws Exception {
        Plan plan = plan(3, 3);
        long review = reviewAtStage5(plan);
        JsonNode items = review(plan, review).get("items");
        long first = itemFor(items, "taskId", plan.taskIds().get(0));
        long second = itemFor(items, "taskId", plan.taskIds().get(1));
        long third = itemFor(items, "taskId", plan.taskIds().get(2));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int weekday = today.getDayOfWeek().getValue();

        JsonNode reminded = item(send(put("/api/streamline-reviews/" + review + "/items/" + first), plan.token(),
                Map.of("automation", "REMIND", "remindCadence", "WEEKLY", "remindDay", weekday)), first);
        assertThat(reminded.get("remindCadence").asText()).isEqualTo("WEEKLY");
        assertThat(reminded.get("remindDay").asInt()).isEqualTo(weekday);

        // Without a choice it reminds weekly on Monday; then daily once chosen.
        JsonNode defaulted = item(send(put("/api/streamline-reviews/" + review + "/items/" + second), plan.token(),
                Map.of("automation", "REMIND")), second);
        assertThat(defaulted.get("remindCadence").asText()).isEqualTo("WEEKLY");
        assertThat(defaulted.get("remindDay").asInt()).isEqualTo(1);
        JsonNode daily = item(send(put("/api/streamline-reviews/" + review + "/items/" + second), plan.token(),
                Map.of("remindCadence", "DAILY")), second);
        assertThat(daily.get("remindDay").isNull()).isTrue();
        // How often needs "Remind me" first.
        send(put("/api/streamline-reviews/" + review + "/items/" + third), plan.token(), Map.of("remindCadence", "DAILY"))
                .andExpect(status().isBadRequest());

        assertThat(todayCodes(plan, today)).containsExactlyInAnyOrder(code(items, first), code(items, second));

        send(put("/api/account/reminders"), plan.token(), Map.of("enabled", true, "timeZone", "UTC", "streamlineNudges", false))
                .andExpect(status().isOk());
        clearInvocations(reminderMailer);
        reminderService.remindIfDue(userId(plan.email()), today.atTime(9, 0).toInstant(ZoneOffset.UTC));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(reminderMailer).send(eq(plan.email()), body.capture());
        assertThat(body.getValue()).contains("You asked to be reminded about:", code(items, first), code(items, second))
                .doesNotContain("Streamline suggestion");

        // A completed task stops reminding.
        jdbc.update("update task_items set status = 'COMPLETED', progress_percent = 100 where id = ?", plan.taskIds().get(0));
        assertThat(todayCodes(plan, today)).containsExactly(code(items, second));
    }

    @Test
    void undoingABatchPutsBackTheReminderSchedule() throws Exception {
        Plan plan = plan(3, 2);
        long review = reviewAtStage5(plan);
        long task = itemFor(review(plan, review).get("items"), "taskId", plan.taskIds().get(0));
        JsonNode result = read(send(post("/api/streamline-reviews/" + review + "/apply-batch"), plan.token(), Map.of("stage", 5,
                "actions", List.of(Map.of("type", "UPDATE", "itemId", task, "update", Map.of("automation", "REMIND", "remindCadence", "DAILY")))))
                .andExpect(status().isOk()));
        assertThat(jdbc.queryForObject("select remind_cadence from streamline_items where id = ?", String.class, task)).isEqualTo("DAILY");

        send(post("/api/streamline-reviews/" + review + "/batches/" + result.get("batchId").asLong() + "/undo"), plan.token(), Map.of())
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select remind_cadence from streamline_items where id = ?", String.class, task)).isNull();
        assertThat(jdbc.queryForObject("select automation from streamline_items where id = ?", String.class, task)).isNull();
    }

    @Test
    void sfc11_aStepWhoseCycleTimeDoubledIsACandidateCountingOnlyTasksWithAStart() throws Exception {
        Plan plan = plan(1, 6);
        long step = plan.stepIds().get(0);
        Instant now = Instant.now();
        for (int index = 0; index < 6; index++) {
            boolean recent = index < 3;
            Instant done = recent ? now.minus(Duration.ofDays(1 + index)) : now.minus(Duration.ofDays(120 + index));
            Instant started = done.minus(Duration.ofDays(recent ? 4 : 1));
            jdbc.update("update task_items set status = 'COMPLETED', progress_percent = 100, started_at = ?, completed_at = ? where id = ?",
                    Timestamp.from(started), Timestamp.from(done), plan.taskIds().get(index));
        }

        JsonNode candidate = candidateFor(plan, "STEP", step);
        assertThat(candidate).isNotNull();
        assertThat(candidate.get("reason").asText()).isEqualTo("CYCLE_TIME_DOUBLED");
        assertThat(candidate.get("detail").asText()).isEqualTo("Tasks now take 4.0 days, up from 1.0 days");

        // BR-32-9: a task with no start time is left out, so only 2 earlier tasks remain: no candidate.
        jdbc.update("update task_items set started_at = null where id = ?", plan.taskIds().get(5));
        assertThat(candidateFor(plan, "STEP", step)).isNull();
    }

    @Test
    void sfc12_oneNudgeAWeekTheSameGoalAtMostOnceIn30DaysAndItCanBeSwitchedOff() throws Exception {
        Plan plan = plan(8, 0);
        long user = userId(plan.email());

        JsonNode nudge = dashboardNudge(plan);
        assertThat(nudge.get("anchorType").asText()).isEqualTo("GOAL");
        assertThat(nudge.get("anchorId").asLong()).isEqualTo(plan.goalId());
        assertThat(nudge.get("reason").asText()).isEqualTo("MANY_STEPS");
        assertThat(nudges(user)).isEqualTo(1);
        // The same nudge for the rest of the week, not another one.
        assertThat(dashboardNudge(plan).get("anchorId").asLong()).isEqualTo(plan.goalId());
        assertThat(nudges(user)).isEqualTo(1);

        send(put("/api/account/reminders"), plan.token(), Map.of("enabled", true, "timeZone", "UTC")).andExpect(status().isOk());
        clearInvocations(reminderMailer);
        reminderService.remindIfDue(user, LocalDate.now(ZoneOffset.UTC).atTime(9, 0).toInstant(ZoneOffset.UTC));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(reminderMailer).send(anyString(), body.capture());
        assertThat(body.getValue()).contains("Streamline suggestion:", "8 open steps", "Streamline it?");
        assertThat(nudges(user)).isEqualTo(1);

        // A week later: the goal was nudged within 30 days, and nothing else qualifies.
        jdbc.update("update streamline_nudges set sent_at = ? where user_id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(8))), user);
        assertThat(dashboardNudge(plan)).isNull();
        // After 30 days it may come back.
        jdbc.update("update streamline_nudges set sent_at = ? where user_id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(31))), user);
        assertThat(dashboardNudge(plan)).isNotNull();
        assertThat(nudges(user)).isEqualTo(2);

        send(put("/api/account/reminders"), plan.token(), Map.of("enabled", true, "timeZone", "UTC", "streamlineNudges", false))
                .andExpect(status().isOk());
        assertThat(read(mockMvc.perform(get("/api/account/reminders").header("Authorization", "Bearer " + plan.token())))
                .get("streamlineNudges").asBoolean()).isFalse();
        assertThat(dashboardNudge(plan)).isNull();
    }

    @Test
    void sfc13_aDismissedCandidateStaysHiddenEverywhereFor90DaysAndOnlyOwnGoalsCanBeDismissed() throws Exception {
        Plan plan = plan(8, 0);
        assertThat(candidateFor(plan, "GOAL", plan.goalId())).isNotNull();

        send(post("/api/streamline-reviews/candidate-dismissals"), plan.token(),
                Map.of("dismissals", List.of(Map.of("anchorType", "GOAL", "anchorId", plan.goalId(), "rule", "MANY_STEPS"))))
                .andExpect(status().isOk());
        assertThat(candidateFor(plan, "GOAL", plan.goalId())).isNull();
        JsonNode stored = read(mockMvc.perform(get("/api/streamline-reviews/candidate-dismissals")
                .header("Authorization", "Bearer " + plan.token())).andExpect(status().isOk()));
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).get("rule").asText()).isEqualTo("MANY_STEPS");

        jdbc.update("update streamline_candidate_dismissals set dismissed_at = ? where anchor_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(91))), plan.goalId());
        assertThat(candidateFor(plan, "GOAL", plan.goalId())).isNotNull();

        Plan other = plan(1, 0);
        send(post("/api/streamline-reviews/candidate-dismissals"), other.token(),
                Map.of("dismissals", List.of(Map.of("anchorType", "GOAL", "anchorId", plan.goalId(), "rule", "MANY_STEPS"))))
                .andExpect(status().isNotFound());
        send(post("/api/streamline-reviews/candidate-dismissals"), plan.token(),
                Map.of("dismissals", List.of(Map.of("anchorType", "TASK", "anchorId", plan.goalId(), "rule", "MANY_STEPS"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sfc14_aCoachReadsTheReviewButReachesNoneOfTheV32Controls() throws Exception {
        String adminEmail = "streamline-admin-" + UUID.randomUUID() + "@example.com";
        String admin = register(adminEmail);
        jdbc.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        String coachEmail = "streamline-coach-" + UUID.randomUUID() + "@example.com";
        String coach = register(coachEmail);
        String joinCode = read(send(post("/api/admin/cohorts"), admin, Map.of("name", "Streamline cohort", "coachEmail", coachEmail))
                .andExpect(status().isCreated())).get("joinCode").asText();
        String participantEmail = "streamline-member-" + UUID.randomUUID() + "@example.com";
        Plan plan = planFor(register(participantEmail, joinCode), participantEmail, 3, 2);
        long review = reviewAtStage5(plan);
        long task = itemFor(review(plan, review).get("items"), "taskId", plan.taskIds().get(0));
        long batch = read(send(post("/api/streamline-reviews/" + review + "/apply-batch"), plan.token(), Map.of("stage", 5,
                "actions", List.of(Map.of("type", "UPDATE", "itemId", task, "update", Map.of("automation", "REMIND"))))))
                .get("batchId").asLong();
        long participant = userId(participantEmail);

        // D-27-10: the coach reads the review, reminder schedule included.
        JsonNode seen = read(mockMvc.perform(get("/api/coach/participants/" + participant + "/streamline-reviews/" + review)
                .header("Authorization", "Bearer " + coach)).andExpect(status().isOk()));
        assertThat(code(seen.get("items"), task)).isNotBlank();

        // D-32-13: none of the V32 controls answer for the coach.
        String base = "/api/streamline-reviews/" + review;
        mockMvc.perform(get(base + "/stages/5/suggestions").header("Authorization", "Bearer " + coach)).andExpect(status().isNotFound());
        mockMvc.perform(get(base + "/drafts").header("Authorization", "Bearer " + coach)).andExpect(status().isNotFound());
        mockMvc.perform(get(base + "/batches").header("Authorization", "Bearer " + coach)).andExpect(status().isNotFound());
        send(post(base + "/batches/" + batch + "/undo"), coach, Map.of()).andExpect(status().isNotFound());
        send(post(base + "/draft-all"), coach, Map.of()).andExpect(status().isNotFound());
        send(post(base + "/apply-batch"), coach, Map.of("stage", 5, "actions", List.of(Map.of("type", "DELETE", "itemId", task))))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select undone_at from streamline_apply_batches where id = ?", java.sql.Timestamp.class, batch)).isNull();
    }

    // ---------- helpers ----------

    private JsonNode dashboardNudge(Plan plan) throws Exception {
        // The dashboard is cached for 10 minutes; these tests change the database directly.
        org.springframework.cache.Cache cache = cacheManager.getCache(com.visionmapping.config.CacheConfig.DASHBOARD_CACHE);
        if (cache != null) {
            cache.clear();
        }
        JsonNode nudge = read(mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + plan.token())).andExpect(status().isOk()))
                .get("streamline").get("nudge");
        return nudge == null || nudge.isNull() ? null : nudge;
    }

    private int nudges(long userId) {
        return jdbc.queryForObject("select count(*) from streamline_nudges where user_id = ?", Integer.class, userId);
    }

    private JsonNode candidateFor(Plan plan, String type, long id) throws Exception {
        for (JsonNode candidate : read(mockMvc.perform(get("/api/streamline-reviews/candidates")
                .header("Authorization", "Bearer " + plan.token())).andExpect(status().isOk()))) {
            if (candidate.get("anchorType").asText().equals(type) && candidate.get("anchorId").asLong() == id) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> todayCodes(Plan plan, LocalDate date) throws Exception {
        List<String> codes = new ArrayList<>();
        read(mockMvc.perform(get("/api/daily-plan").param("date", date.toString()).header("Authorization", "Bearer " + plan.token()))
                .andExpect(status().isOk())).get("streamlineReminders").forEach(reminder -> codes.add(reminder.get("code").asText()));
        return codes;
    }

    private JsonNode item(ResultActions result, long itemId) throws Exception {
        for (JsonNode item : read(result.andExpect(status().isOk())).get("items")) {
            if (item.get("id").asLong() == itemId) {
                return item;
            }
        }
        throw new AssertionError("No item " + itemId);
    }

    private static String code(JsonNode items, long itemId) {
        for (JsonNode item : items) {
            if (item.get("id").asLong() == itemId) {
                return item.get("code").asText();
            }
        }
        throw new AssertionError("No item " + itemId);
    }

    private long reviewAtStage5(Plan plan) throws Exception {
        long id = read(send(post("/api/streamline-reviews"), plan.token(), Map.of("goalId", plan.goalId())).andExpect(status().isCreated()))
                .get("id").asLong();
        List<Long> ids = new ArrayList<>();
        review(plan, id).get("items").forEach(item -> ids.add(item.get("id").asLong()));
        send(put("/api/streamline-reviews/" + id + "/items/bulk"), plan.token(),
                Map.of("itemIds", ids, "requirementSource", "SELF", "requirementOwner", "Me", "verdict", "NEEDED")).andExpect(status().isOk());
        send(post("/api/streamline-reviews/" + id + "/stages/1/complete"), plan.token(), Map.of()).andExpect(status().isOk());
        for (int stage = 2; stage <= 4; stage++) {
            send(post("/api/streamline-reviews/" + id + "/stages/" + stage + "/not-needed"), plan.token(),
                    Map.of("reason", "Nothing to change at this stage")).andExpect(status().isOk());
        }
        return id;
    }

    private JsonNode review(Plan plan, long id) throws Exception {
        return read(mockMvc.perform(get("/api/streamline-reviews/" + id).header("Authorization", "Bearer " + plan.token()))
                .andExpect(status().isOk()));
    }

    private static long itemFor(JsonNode items, String field, long id) {
        for (JsonNode item : items) {
            if (item.hasNonNull(field) && item.get(field).asLong() == id) {
                return item.get("id").asLong();
            }
        }
        throw new AssertionError("No item for " + field + " " + id);
    }

    private long userId(String email) {
        return jdbc.queryForObject("select id from app_users where email = ?", Long.class, email);
    }

    /** A goal with the given number of steps; the first step has the given number of tasks. */
    private Plan plan(int steps, int tasksInFirstStep) throws Exception {
        String email = "streamline-auto-" + UUID.randomUUID() + "@example.com";
        return planFor(register(email), email, steps, tasksInFirstStep);
    }

    private Plan planFor(String token, String email, int steps, int tasksInFirstStep) throws Exception {
        long area = id(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        long dream = id(send(post("/api/dreams"), token, Map.of("visionAreaId", area, "title", "Own a home", "whyImportant", "It matters",
                "successDefinition", "Keys in hand", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP")));
        long goal = id(send(post("/api/goals"), token, Map.of("dreamId", dream, "title", "Save a deposit", "priority", "HIGH",
                "status", "IN_PROGRESS", "moonshot", false, "scheduleMode", "BOTTOM_UP")));
        List<Long> stepIds = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();
        for (int index = 1; index <= steps; index++) {
            stepIds.add(id(send(post("/api/steps"), token, Map.of("goalId", goal, "title", "Step " + index, "owner", "Me",
                    "sequenceNumber", index, "complex", false, "priority", "MEDIUM", "targetDate", LocalDate.now().plusDays(30).toString(),
                    "status", "NOT_STARTED"))));
        }
        for (int index = 1; index <= tasksInFirstStep; index++) {
            Map<String, Object> task = new HashMap<>(Map.of("stepId", stepIds.get(0), "title", "Task " + index, "owner", "Me",
                    "priority", "MEDIUM", "dueDate", LocalDate.now().plusDays(10).toString(), "status", "NOT_STARTED", "progressPercent", 0));
            taskIds.add(id(send(post("/api/tasks"), token, task)));
        }
        return new Plan(token, email, goal, stepIds, taskIds);
    }

    private long id(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String email) throws Exception {
        return register(email, null);
    }

    private String register(String email, String joinCode) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "Streamline User", "email", email, "password", PASSWORD));
        if (joinCode != null) {
            body.put("joinCode", joinCode);
        }
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
