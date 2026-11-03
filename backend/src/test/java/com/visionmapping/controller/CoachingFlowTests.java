package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V9 Phase 1: cohorts, join codes, the coach workspace and its access rules (D-6, D-7, D-8). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachingFlowTests {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyAnAdminManagesCohortsAndNamingACoachMakesThemOne() throws Exception {
        String admin = admin();
        String coachEmail = email("coach");
        register(coachEmail, null);
        String user = register(email("user"), null);

        send(post("/api/admin/cohorts"), user, Map.of("name", "Pilot")).andExpect(status().isForbidden());
        send(post("/api/admin/cohorts"), admin, Map.of("name", "Pilot", "coachEmail", "nobody@example.com"))
                .andExpect(status().isBadRequest());
        JsonNode cohort = read(send(post("/api/admin/cohorts"), admin, Map.of("name", "Pilot", "coachEmail", coachEmail.toUpperCase()))
                .andExpect(status().isCreated()));

        assertThat(cohort.get("joinCode").asText()).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(cohort.get("coachEmail").asText()).isEqualTo(coachEmail);
        assertThat(jdbcTemplate.queryForObject("select role from app_users where email = ?", String.class, coachEmail)).isEqualTo("COACH");

        String oldCode = cohort.get("joinCode").asText();
        JsonNode renewed = read(mockMvc.perform(post("/api/admin/cohorts/" + cohort.get("id").asLong() + "/join-code")
                .header("Authorization", "Bearer " + admin)));
        assertThat(renewed.get("joinCode").asText()).isNotEqualTo(oldCode);
        send(post("/api/cohort/join"), user, Map.of("joinCode", oldCode)).andExpect(status().isBadRequest());
    }

    @Test
    void aJoinCodeWorksAtRegistrationOrLaterAndAWrongOneCreatesNoAccount() throws Exception {
        Cohort cohort = cohortWithCoach();
        String badEmail = email("bad-code");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "X", "email", badEmail, "password", PASSWORD, "joinCode", "NOPE1234"))))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_users where email = ?", Integer.class, badEmail)).isZero();

        String early = register(email("early"), cohort.code.toLowerCase());
        mockMvc.perform(get("/api/cohort").header("Authorization", "Bearer " + early))
                .andExpect(jsonPath("$.cohortName").value("Cohort " + cohort.code))
                .andExpect(jsonPath("$.coachName").value("Coach"))
                .andExpect(jsonPath("$.shareJournalsWithCoach").value(false));

        String late = register(email("late"), null);
        send(post("/api/cohort/join"), late, Map.of("joinCode", cohort.code)).andExpect(jsonPath("$.cohortId").value(cohort.id));
        mockMvc.perform(post("/api/cohort/leave").header("Authorization", "Bearer " + late)).andExpect(jsonPath("$.cohortId").doesNotExist());
    }

    @Test
    void aCoachSeesOnlyTheirOwnCohortAndOthersSeeNothing() throws Exception {
        Cohort mine = cohortWithCoach();
        Cohort theirs = cohortWithCoach();
        String participantEmail = email("participant");
        String participant = register(participantEmail, mine.code);
        long dreamId = dream(participant);
        String stranger = register(email("stranger"), theirs.code);
        long participantId = userId(participantEmail);

        JsonNode list = read(mockMvc.perform(get("/api/coach/participants").header("Authorization", "Bearer " + mine.coachToken)));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("email").asText()).isEqualTo(participantEmail);
        assertThat(list.get(0).get("metrics").get("metrics")).hasSize(6);

        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + mine.coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dreams[0].id").value(dreamId));
        // Another cohort's coach, a participant, and a stranger cannot read it.
        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + theirs.coachToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/coach/participants").header("Authorization", "Bearer " + stranger)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + participant))
                .andExpect(status().isForbidden());

        // Archiving the cohort ends the coach's access.
        mockMvc.perform(post("/api/admin/cohorts/" + mine.id + "/archive").header("Authorization", "Bearer " + mine.adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + mine.coachToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void journalsStayPrivateUntilTheParticipantSharesThem() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("journal");
        String participant = register(participantEmail, cohort.code);
        send(post("/api/fear-entries"), participant, Map.of("fear", "Nobody buys it", "worstCase", "Lose a month", "likelihood", "MEDIUM", "firstTest", "Ask five people"))
                .andExpect(status().isCreated());
        long participantId = userId(participantEmail);

        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + cohort.coachToken))
                .andExpect(jsonPath("$.journalsShared").value(false))
                .andExpect(jsonPath("$.fearEntries").doesNotExist())
                .andExpect(jsonPath("$.attitudeCheckIns").doesNotExist());

        send(put("/api/cohort/sharing"), participant, Map.of("shareJournalsWithCoach", true)).andExpect(status().isOk());
        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + cohort.coachToken))
                .andExpect(jsonPath("$.journalsShared").value(true))
                .andExpect(jsonPath("$.fearEntries[0].fear").value("Nobody buys it"));
    }

    @Test
    void aCoachCommentsOnTheParticipantsOwnPlanAndOnlyTheyReadIt() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("commented");
        String participant = register(participantEmail, cohort.code);
        long dreamId = dream(participant);
        String other = register(email("other"), null);
        long otherDream = dream(other);
        long participantId = userId(participantEmail);
        String path = "/api/coach/participants/" + participantId + "/comments";

        send(post(path), cohort.coachToken, Map.of("targetType", "DREAM", "targetId", otherDream, "body", "Hi"))
                .andExpect(status().isBadRequest());
        long commentId = read(send(post(path), cohort.coachToken, Map.of("targetType", "DREAM", "targetId", dreamId, "body", " Clear why. "))
                .andExpect(status().isCreated())).get("id").asLong();
        send(post(path), cohort.coachToken, Map.of("targetType", "GENERAL", "body", "Good first week")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/coach-comments").header("Authorization", "Bearer " + participant))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].targetTitle").value("Own a home"))
                .andExpect(jsonPath("$[1].body").value("Clear why."))
                .andExpect(jsonPath("$[1].coachName").value("Coach"));
        mockMvc.perform(post("/api/coach-comments/" + commentId + "/read").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/coach-comments/" + commentId + "/read").header("Authorization", "Bearer " + participant))
                .andExpect(jsonPath("$.readAt").exists());
    }

    @Test
    void aCoachRatesThePitchTheParticipantSeesItAndAChangedPitchClearsIt() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("pitcher");
        String participant = register(participantEmail, cohort.code);
        long participantId = userId(participantEmail);
        String ratingPath = "/api/coach/participants/" + participantId + "/pitch-rating";

        send(put(ratingPath), cohort.coachToken, Map.of("rating", 4)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + cohort.coachToken))
                .andExpect(jsonPath("$.pitch").doesNotExist());

        // Every earlier module done, so module 11 is open; then the participant saves a pitch.
        jdbcTemplate.update("insert into module_completions (user_id, module_number, completed_at, created_at, updated_at) "
                + "select ?, module_number, current_timestamp, current_timestamp, current_timestamp from learning_modules "
                + "where module_number <> 11", participantId);
        Map<String, String> pitch = Map.of("pitch", "Help me test my menu", "practice", "Said it to my sister", "rating", "3");
        send(put("/api/modules/11/completion"), participant, Map.of("rows", java.util.List.of(pitch))).andExpect(status().isOk());

        send(put(ratingPath), cohort.coachToken, Map.of("rating", 6)).andExpect(status().isBadRequest());
        send(put(ratingPath), register(email("stranger"), null), Map.of("rating", 4)).andExpect(status().isForbidden());
        send(put(ratingPath), cohort.coachToken, Map.of("rating", 4, "feedback", " Lead with what they gain. "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachRating").value(4))
                .andExpect(jsonPath("$.coachName").value("Coach"));

        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + cohort.coachToken))
                .andExpect(jsonPath("$.pitch.rows[0].pitch").value("Help me test my menu"))
                .andExpect(jsonPath("$.pitch.coachFeedback").value("Lead with what they gain."));
        mockMvc.perform(get("/api/modules/11").header("Authorization", "Bearer " + participant))
                .andExpect(jsonPath("$.coachRating").value(4))
                .andExpect(jsonPath("$.coachFeedback").value("Lead with what they gain."));

        // Saving the same pitch keeps the rating; a changed pitch needs a new one.
        send(put("/api/modules/11/completion"), participant, Map.of("rows", java.util.List.of(pitch)))
                .andExpect(jsonPath("$.coachRating").value(4));
        Map<String, String> changed = Map.of("pitch", "A sharper ask", "practice", "Recorded it", "rating", "4");
        send(put("/api/modules/11/completion"), participant, Map.of("rows", java.util.List.of(changed)))
                .andExpect(jsonPath("$.coachRating").doesNotExist());
    }

    @Test
    void sevenQuietDaysFlagAParticipantAtRisk() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("quiet");
        register(participantEmail, cohort.code);
        jdbcTemplate.update("update app_users set created_at = created_at - interval '8' day where email = ?", participantEmail);

        JsonNode row = read(mockMvc.perform(get("/api/coach/participants").header("Authorization", "Bearer " + cohort.coachToken))).get(0);
        assertThat(row.get("atRisk").asBoolean()).isTrue();
        assertThat(row.get("daysInactive").asLong()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void deletingAParticipantOrTheirCoachKeepsEverythingElseWorking() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("leaving");
        String participant = register(participantEmail, cohort.code);
        long participantId = userId(participantEmail);
        send(post("/api/coach/participants/" + participantId + "/comments"), cohort.coachToken, Map.of("targetType", "GENERAL", "body", "Hi"))
                .andExpect(status().isCreated());

        send(post("/api/account/delete"), cohort.coachToken, Map.of("password", PASSWORD)).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/coach-comments").header("Authorization", "Bearer " + participant))
                .andExpect(jsonPath("$[0].coachName").doesNotExist());
        mockMvc.perform(get("/api/cohort").header("Authorization", "Bearer " + participant))
                .andExpect(jsonPath("$.coachName").doesNotExist());

        send(post("/api/account/delete"), participant, Map.of("password", PASSWORD)).andExpect(status().is2xxSuccessful());
        assertThat(jdbcTemplate.queryForObject("select count(*) from coach_comments where user_id = ?", Integer.class, participantId)).isZero();
    }

    private record Cohort(long id, String code, String coachToken, String adminToken) {
    }

    private Cohort cohortWithCoach() throws Exception {
        String admin = admin();
        String coachEmail = email("coach");
        String coachToken = register(coachEmail, null);
        jdbcTemplate.update("update app_users set full_name = 'Coach' where email = ?", coachEmail);
        JsonNode created = read(send(post("/api/admin/cohorts"), admin, Map.of("name", "Cohort", "coachEmail", coachEmail)));
        long id = created.get("id").asLong();
        String code = created.get("joinCode").asText();
        send(put("/api/admin/cohorts/" + id), admin, Map.of("name", "Cohort " + code, "coachEmail", coachEmail)).andExpect(status().isOk());
        return new Cohort(id, code, coachToken, admin);
    }

    private String admin() throws Exception {
        String adminEmail = email("admin");
        String token = register(adminEmail, null);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        return token;
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE"))).get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Own a home", "whyImportant", "It matters",
                "successDefinition", "It is done", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE",
                "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
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

    private String register(String email, String joinCode) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "V9 Test User", "email", email, "password", PASSWORD));
        if (joinCode != null) {
            body.put("joinCode", joinCode);
        }
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
