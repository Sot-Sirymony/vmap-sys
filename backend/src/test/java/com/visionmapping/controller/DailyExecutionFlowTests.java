package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V8 Phase 1 end-to-end: required step fields (AC-02), the daily plan's due
 * filter (AC-04), partner stages, journals, habits, the commitment, and that
 * each stays private to its owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailyExecutionFlowTests {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void stepWithoutOwnerOrDateCannotBeSaved() throws Exception {
        String token = registerAndToken("step-rules");
        long goalId = goal(token, dream(token, "Why it matters"));

        send(post("/api/steps"), token, stepBody(goalId, "No owner", null, TODAY.toString())).andExpect(status().isBadRequest());
        send(post("/api/steps"), token, stepBody(goalId, "No date", "Me", null)).andExpect(status().isBadRequest());
        send(post("/api/steps"), token, stepBody(goalId, "Complete", "Me", TODAY.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner").value("Me"));
    }

    @Test
    void anOldUndatedStepCanChangeStatusButNotBeSavedInFull() throws Exception {
        String token = registerAndToken("old-step");
        long goalId = goal(token, dream(token, "Why it matters"));
        long stepId = step(token, goalId, "Old step", TODAY.plusDays(3));
        jdbcTemplate.update("update vision_steps set target_date = null where id = ?", stepId);

        send(patch("/api/steps/" + stepId + "/status"), token, Map.of("status", "IN_PROGRESS", "manualOverride", false))
                .andExpect(status().isOk());
        send(put("/api/steps/" + stepId), token, stepBody(goalId, "Renamed", "Me", null)).andExpect(status().isBadRequest());
    }

    @Test
    void dailyPlanShowsOnlyDueOrOverdueActionableWorkAndOnlyTheOwners() throws Exception {
        String token = registerAndToken("daily");
        String stranger = registerAndToken("daily-stranger");
        long goalId = goal(token, dream(token, "Why it matters"));
        long overdueStep = step(token, goalId, "Overdue step", TODAY.plusDays(30));
        jdbcTemplate.update("update vision_steps set target_date = ? where id = ?", TODAY.minusDays(1), overdueStep);
        step(token, goalId, "Due today", TODAY);
        step(token, goalId, "Due later", TODAY.plusDays(1));
        long doneStep = step(token, goalId, "Done", TODAY);
        send(patch("/api/steps/" + doneStep + "/status"), token, Map.of("status", "COMPLETED", "manualOverride", false)).andExpect(status().isOk());
        long laterStep = step(token, goalId, "Holder", TODAY.plusDays(30));
        Map<String, Object> task = new HashMap<>(Map.of("stepId", laterStep, "title", "Due task", "owner", "Me",
                "priority", "CRITICAL", "dueDate", TODAY.toString(), "status", "NOT_STARTED", "progressPercent", 0));
        send(post("/api/tasks"), token, task).andExpect(status().isCreated());

        mockMvc.perform(get("/api/daily-plan").param("date", TODAY.toString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].title").value("Due task"))
                .andExpect(jsonPath("$.items[0].kind").value("TASK"))
                .andExpect(jsonPath("$.items[1].title").value("Overdue step"))
                .andExpect(jsonPath("$.items[1].overdue").value(true))
                .andExpect(jsonPath("$.items[2].title").value("Due today"))
                .andExpect(jsonPath("$.items[2].overdue").value(false))
                .andExpect(jsonPath("$.weeklyReviewDue").value(true));

        mockMvc.perform(get("/api/daily-plan").param("date", TODAY.toString()).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void weeklyReviewStopsBeingDueOnceOneIsRecentlySaved() throws Exception {
        String token = registerAndToken("weekly");
        send(post("/api/reviews"), token, Map.of("reviewType", "WEEKLY", "reviewDate", TODAY.atTime(9, 0).toString(), "summary", "Good week"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/daily-plan").param("date", TODAY.toString()).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.weeklyReviewDue").value(false));
        mockMvc.perform(get("/api/daily-plan").param("date", TODAY.plusDays(7).toString()).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.weeklyReviewDue").value(true));
    }

    @Test
    void partnerCanMoveToAgreed() throws Exception {
        String token = registerAndToken("partner-stage");
        long partnerId = idOf(send(post("/api/partners"), token, Map.of("name", "A Mentor", "supportType", "MENTOR", "status", "TO_CONTACT")));

        send(patch("/api/partners/" + partnerId + "/status"), token, Map.of("status", "AGREED", "manualOverride", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGREED"));
    }

    @Test
    void aMissingRequiredParameterIsABadRequestNotAServerError() throws Exception {
        String token = registerAndToken("missing-param");
        send(post("/api/habits"), token, Map.of("name", "Read"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'date'."));
    }

    @Test
    void fearEntriesStayPrivate() throws Exception {
        String token = registerAndToken("fear");
        String stranger = registerAndToken("fear-stranger");
        long id = idOf(send(post("/api/fear-entries"), token, Map.of(
                "fear", "Nobody buys it", "worstCase", "Lose a month", "likelihood", "MEDIUM", "firstTest", "Ask five people")));

        mockMvc.perform(get("/api/fear-entries").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].createdAt").exists());
        mockMvc.perform(get("/api/fear-entries").header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/fear-entries/" + id).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void permanentlyDeletingADreamKeepsItsFearEntry() throws Exception {
        String token = registerAndToken("fear-dream");
        long dreamId = dream(token, "Why it matters");
        send(post("/api/fear-entries"), token, Map.of("fear", "F", "worstCase", "W", "likelihood", "LOW", "firstTest", "T", "relatedDreamId", dreamId))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/dreams/" + dreamId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/dreams/" + dreamId + "/permanent").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fear-entries").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].relatedDreamId").doesNotExist());
    }

    @Test
    void permanentlyDeletingADreamOrGoalKeepsLinkedGratitudeAndSynergyDoesNotBlockIt() throws Exception {
        String token = registerAndToken("gratitude-link");
        long dreamId = dream(token, "Why it matters");
        long goalId = goal(token, dreamId);
        long otherGoal = goal(token, dreamId);
        send(post("/api/gratitude-entries"), token, Map.of("category", "OTHER", "description", "On the dream", "relatedDreamId", dreamId)).andExpect(status().isCreated());
        send(post("/api/gratitude-entries"), token, Map.of("category", "OTHER", "description", "On the goal", "relatedGoalId", goalId)).andExpect(status().isCreated());
        long userId = jdbcTemplate.queryForObject("select user_id from goals where id = ?", Long.class, goalId);
        jdbcTemplate.update("insert into goal_synergy_links (user_id, goal_id, related_goal_id, created_at, updated_at) values (?, ?, ?, now(), now())", userId, goalId, otherGoal);

        mockMvc.perform(delete("/api/goals/" + goalId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/goals/" + goalId + "/permanent").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/dreams/" + dreamId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/dreams/" + dreamId + "/permanent").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/gratitude-entries").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].relatedDreamId").doesNotExist())
                .andExpect(jsonPath("$[0].relatedGoalId").doesNotExist())
                .andExpect(jsonPath("$[1].relatedDreamId").doesNotExist())
                .andExpect(jsonPath("$[1].relatedGoalId").doesNotExist());
    }

    @Test
    void dailyPlanCountsOldStepsWithNoDate() throws Exception {
        String token = registerAndToken("undated");
        long stepId = step(token, goal(token, dream(token, "Why it matters")), "Old step", TODAY.plusDays(3));
        jdbcTemplate.update("update vision_steps set target_date = null where id = ?", stepId);

        mockMvc.perform(get("/api/daily-plan").param("date", TODAY.toString()).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.undatedStepCount").value(1));
    }

    @Test
    void attitudeCheckInReplacesTheSameDayAndRejectsTheFuture() throws Exception {
        String token = registerAndToken("attitude");
        send(put("/api/attitude-checkins/" + TODAY), token, Map.of("score", 2)).andExpect(status().isOk());
        send(put("/api/attitude-checkins/" + TODAY), token, Map.of("score", 4, "note", "Better")).andExpect(status().isOk());
        send(put("/api/attitude-checkins/" + TODAY), token, Map.of("score", 9)).andExpect(status().isBadRequest());
        send(put("/api/attitude-checkins/" + TODAY.plusDays(5)), token, Map.of("score", 3)).andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/attitude-checkins").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(4));
    }

    @Test
    void habitsAreCappedAtThreeAndCheckingBuildsAStreak() throws Exception {
        String token = registerAndToken("habit");
        String stranger = registerAndToken("habit-stranger");
        String date = "?date=" + TODAY;
        long first = idOf(send(post("/api/habits" + date), token, Map.of("name", "Read")));
        send(post("/api/habits" + date), token, Map.of("name", "Walk")).andExpect(status().isCreated());
        send(post("/api/habits" + date), token, Map.of("name", "Plan")).andExpect(status().isCreated());
        send(post("/api/habits" + date), token, Map.of("name", "Fourth")).andExpect(status().is4xxClientError());

        mockMvc.perform(put("/api/habits/" + first + "/checks/" + TODAY.minusDays(1)).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(put("/api/habits/" + first + "/checks/" + TODAY).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.currentStreak").value(2))
                .andExpect(jsonPath("$.checkedOnDate").value(true));
        // Checking twice is harmless: the day is either kept or it is not.
        mockMvc.perform(put("/api/habits/" + first + "/checks/" + TODAY).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.currentStreak").value(2));
        mockMvc.perform(delete("/api/habits/" + first + "/checks/" + TODAY).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.checkedOnDate").value(false))
                .andExpect(jsonPath("$.currentStreak").value(1));
        mockMvc.perform(put("/api/habits/" + first + "/checks/" + TODAY).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void commitmentNeedsAReasonAndActivatesAnIdea() throws Exception {
        String token = registerAndToken("commit");
        Map<String, Object> commitment = Map.of("signedName", "Me", "startDate", TODAY.toString(), "firstAction", "Call the bank");

        long noReason = dream(token, null);
        send(post("/api/dreams/" + noReason + "/commitment"), token, commitment).andExpect(status().is4xxClientError());

        long dreamId = dream(token, "Why it matters");
        send(post("/api/dreams/" + dreamId + "/commitment"), token, commitment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.commitmentFirstAction").value("Call the bank"))
                .andExpect(jsonPath("$.committedAt").exists());

        String stranger = registerAndToken("commit-stranger");
        send(post("/api/dreams/" + dreamId + "/commitment"), stranger, commitment).andExpect(status().isNotFound());
    }

    private long dream(String token, String whyImportant) throws Exception {
        long areaId = idOf(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "A dream", "dreamType", "LONG_TERM",
                "priority", "MEDIUM", "status", "IDEA", "moonshot", false, "scheduleMode", "BOTTOM_UP"));
        body.put("whyImportant", whyImportant);
        return idOf(send(post("/api/dreams"), token, body));
    }

    private long goal(String token, long dreamId) throws Exception {
        return idOf(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "A goal", "priority", "MEDIUM",
                "status", "NOT_STARTED", "moonshot", false, "scheduleMode", "BOTTOM_UP")));
    }

    private long step(String token, long goalId, String title, LocalDate date) throws Exception {
        return idOf(send(post("/api/steps"), token, stepBody(goalId, title, "Me", date.toString())));
    }

    private Map<String, Object> stepBody(long goalId, String title, String owner, String targetDate) {
        Map<String, Object> body = new HashMap<>(Map.of("goalId", goalId, "title", title, "sequenceNumber", 1,
                "complex", false, "priority", "MEDIUM", "status", "NOT_STARTED"));
        body.put("owner", owner);
        body.put("targetDate", targetDate);
        return body;
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long idOf(ResultActions result) throws Exception {
        String content = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("id").asLong();
    }

    private String registerAndToken(String prefix) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "V8 Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
