package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
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

/** V34 (BRD 34): the participant's own inner blockers, their habits, weekly answers, coach view and export. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InnerBlockerFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final String PATH = "/api/v1/inner-blockers";
    private static final LocalDate TODAY = LocalDate.now();
    /** The V34 types that hold blocker text; the draft's READY/SKIPPED flag (checkInnerBlockers) is not one. */
    private static final java.util.regex.Pattern READS_BLOCKERS =
            java.util.regex.Pattern.compile("\\bInnerBlocker(Checkin)?(Service|Repository|Response)?\\b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aBlockerIsAddedEditedListedAndScopedToItsOwner() throws Exception {
        String user = register(email("owner"));
        long dreamId = dream(user);
        JsonNode created = read(send(post(PATH), user, blocker("Inconsistency", "Big bursts, then weeks of nothing",
                "Never miss twice; 15 minutes counts", "persistenceScore", dreamId))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.containsString(PATH + "/"))));
        long id = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("dreamTitle").asText()).isEqualTo("Learn to code");
        assertThat(created.get("chainKey").asText()).isEqualTo("persistenceScore");

        send(put(PATH + "/" + id), user, blocker("Inconsistency", "", "Never miss twice", "", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showsUp").doesNotExist())
                .andExpect(jsonPath("$.chainKey").doesNotExist())
                .andExpect(jsonPath("$.dreamId").doesNotExist());

        // §2.1: a dream's blockers plus those for all dreams.
        send(post(PATH), user, blocker("Tutorial trap", "Certificates, no projects", "End each goal with my own project", null, dreamId))
                .andExpect(status().isCreated());
        String otherDream = String.valueOf(dream(user));
        mockMvc.perform(get(PATH).param("dreamId", otherDream).param("includeAllDreams", "true").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].blocker").value("Inconsistency"));
        mockMvc.perform(get(PATH).param("dreamId", String.valueOf(dreamId)).param("includeAllDreams", "true").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.content.length()").value(2));

        // BR-34-1: another user sees nothing and gets 404.
        String other = register(email("other"));
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        send(put(PATH + "/" + id), other, blocker("Mine now", null, "Nope", null, null)).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.content.length()").value(0));
        // BR-34-2: the dream must be the caller's own.
        send(post(PATH), other, blocker("Fear", null, "Write code first", null, dreamId)).andExpect(status().isNotFound());
    }

    @Test
    void theFieldsAreCheckedAndTheRetiredChainsCannotBeChosen() throws Exception {
        String user = register(email("checks"));
        send(post(PATH), user, blocker("", null, "Practice", null, null)).andExpect(status().isBadRequest());
        send(post(PATH), user, blocker("Blocker", null, " ", null, null)).andExpect(status().isBadRequest());
        send(post(PATH), user, blocker("x".repeat(121), null, "Practice", null, null)).andExpect(status().isBadRequest());
        send(post(PATH), user, blocker("Blocker", "y".repeat(501), "Practice", null, null)).andExpect(status().isBadRequest());
        send(post(PATH), user, blocker("Blocker", null, "z".repeat(301), null, null)).andExpect(status().isBadRequest());
        send(post(PATH), user, blocker("Blocker", null, "Practice", "knowledgeScore", null)).andExpect(status().isBadRequest());
        send(post(PATH), user, blocker("x".repeat(120), "y".repeat(500), "z".repeat(300), "fearScore", null)).andExpect(status().isCreated());
    }

    @Test
    void atMostTenAreActiveAndResolvingOrArchivingMakesRoom() throws Exception {
        String user = register(email("limit"));
        long first = 0;
        for (int index = 0; index < 10; index++) {
            long id = read(send(post(PATH), user, blocker("Blocker " + index, null, "Practice", null, null))
                    .andExpect(status().isCreated())).get("id").asLong();
            first = index == 0 ? id : first;
        }
        send(post(PATH), user, blocker("Eleventh", null, "Practice", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You have 10 active inner blockers. Resolve or archive one first."));

        send(patch(PATH + "/" + first + "/status"), user, Map.of("status", "RESOLVED", "stopHabit", false))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
        long eleventh = read(send(post(PATH), user, blocker("Eleventh", null, "Practice", null, null))
                .andExpect(status().isCreated())).get("id").asLong();
        // Reopening needs room again.
        send(patch(PATH + "/" + first + "/status"), user, Map.of("status", "ACTIVE", "stopHabit", false)).andExpect(status().isConflict());

        mockMvc.perform(post(PATH + "/" + eleventh + "/archive").header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        mockMvc.perform(get(PATH).param("archived", "true").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.content[0].blocker").value("Eleventh"));
        mockMvc.perform(get(PATH).param("status", "RESOLVED").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.content.length()").value(1));
        send(patch(PATH + "/" + first + "/status"), user, Map.of("status", "ACTIVE", "stopHabit", false))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
        mockMvc.perform(post(PATH + "/" + eleventh + "/restore").header("Authorization", "Bearer " + user)).andExpect(status().isConflict());
    }

    @Test
    void aPracticeBecomesADailyHabitThatStopsOnlyWhenAsked() throws Exception {
        String user = register(email("habit"));
        long id = read(send(post(PATH), user, blocker("Inconsistency", null, "Never miss twice", null, null))).get("id").asLong();

        JsonNode linked = read(send(post(PATH + "/" + id + "/habit?date=" + TODAY), user, Map.of("name", "Never miss twice"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.containsString("/api/v1/habits/"))));
        long habitId = linked.get("habitId").asLong();
        assertThat(linked.get("habitName").asText()).isEqualTo("Never miss twice");
        assertThat(linked.get("habitStreak").asInt()).isZero();
        send(post(PATH + "/" + id + "/habit?date=" + TODAY), user, Map.of("name", "Again")).andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/habits/" + habitId + "/checks/" + TODAY).header("Authorization", "Bearer " + user)).andExpect(status().isOk());
        mockMvc.perform(get(PATH + "/" + id).param("date", TODAY.toString()).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.habitStreak").value(1));

        // BR-34-5: archiving without stopHabit keeps the habit.
        mockMvc.perform(post(PATH + "/" + id + "/archive").header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        assertThat(habitArchived(habitId)).isFalse();
        mockMvc.perform(post(PATH + "/" + id + "/restore").header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        send(patch(PATH + "/" + id + "/status"), user, Map.of("status", "RESOLVED", "stopHabit", true))
                .andExpect(jsonPath("$.habitArchived").value(true));
        assertThat(habitArchived(habitId)).isTrue();
        // A resolved blocker cannot become a habit.
        send(post(PATH + "/" + id + "/habit?date=" + TODAY), user, Map.of("name", "Again")).andExpect(status().isBadRequest());
    }

    @Test
    void weeklyReviewsAskWhetherThePracticeHelped() throws Exception {
        String user = register(email("weekly"));
        long id = read(send(post(PATH), user, blocker("Fear of not being good enough", null, "One hour of code per hour of video", null, null)))
                .get("id").asLong();
        long daily = review(user, "DAILY", TODAY);
        send(put("/api/v1/reviews/" + daily + "/inner-blocker-checkins"), user, checkins(id, "YES")).andExpect(status().isBadRequest());

        long lastWeek = review(user, "WEEKLY", TODAY.minusDays(7));
        long thisWeek = review(user, "WEEKLY", TODAY);
        send(put("/api/v1/reviews/" + lastWeek + "/inner-blocker-checkins"), user, checkins(id, "NO"))
                .andExpect(jsonPath("$.checkins[0].answer").value("NO"));
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.practiceNotHelping").value(false));
        send(put("/api/v1/reviews/" + thisWeek + "/inner-blocker-checkins"), user, checkins(id, "YES")).andExpect(status().isOk());
        // A new answer replaces the old one for the same review.
        send(put("/api/v1/reviews/" + thisWeek + "/inner-blocker-checkins"), user, checkins(id, "NO"))
                .andExpect(jsonPath("$.checkins.length()").value(1));
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.lastAnswers[0]").value("NO"))
                .andExpect(jsonPath("$.lastAnswers.length()").value(2))
                .andExpect(jsonPath("$.practiceNotHelping").value(true));
        mockMvc.perform(get("/api/v1/reviews/" + thisWeek + "/inner-blocker-checkins").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.checkins[0].innerBlockerId").value(id));

        // Leaving a blocker out removes its answer for that review.
        send(put("/api/v1/reviews/" + thisWeek + "/inner-blocker-checkins"), user, Map.of("checkins", List.of()))
                .andExpect(jsonPath("$.checkins.length()").value(0));
        send(put("/api/v1/reviews/" + thisWeek + "/inner-blocker-checkins"), user,
                Map.of("checkins", List.of(Map.of("innerBlockerId", id, "answer", "YES"), Map.of("innerBlockerId", id, "answer", "NO"))))
                .andExpect(status().isBadRequest());

        // BR-34-1: another user's review or blocker answers 404.
        String other = register(email("weekly-other"));
        long otherWeek = review(other, "WEEKLY", TODAY);
        send(put("/api/v1/reviews/" + thisWeek + "/inner-blocker-checkins"), other, checkins(id, "YES")).andExpect(status().isNotFound());
        send(put("/api/v1/reviews/" + otherWeek + "/inner-blocker-checkins"), other, checkins(id, "YES")).andExpect(status().isNotFound());
    }

    @Test
    void aCoachSeesBlockersOnlyWhenJournalsAreSharedAndTheExportHoldsThem() throws Exception {
        String adminEmail = email("admin");
        String admin = register(adminEmail);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        String coachEmail = email("coach");
        String coach = register(coachEmail);
        JsonNode cohort = read(send(post("/api/admin/cohorts"), admin, Map.of("name", "Cohort", "coachEmail", coachEmail)));
        String participantEmail = email("participant");
        String participant = register(participantEmail);
        send(post("/api/cohort/join"), participant, Map.of("joinCode", cohort.get("joinCode").asText())).andExpect(status().isOk());
        long blockerId = read(send(post(PATH), participant, blocker("Comparing myself", "Feeling behind", "Compare with my own log", null, null)))
                .get("id").asLong();
        long participantId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, participantEmail);

        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + coach))
                .andExpect(jsonPath("$.innerBlockers").doesNotExist());
        send(put("/api/cohort/sharing"), participant, Map.of("shareJournalsWithCoach", true)).andExpect(status().isOk());
        mockMvc.perform(get("/api/coach/participants/" + participantId).header("Authorization", "Bearer " + coach))
                .andExpect(jsonPath("$.innerBlockers[0].blocker").value("Comparing myself"));
        // A coach cannot change a participant's blocker (D-6), and an admin cannot read it (D-23-3).
        send(put(PATH + "/" + blockerId), coach, blocker("Changed", null, "Changed", null, null)).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + blockerId).header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound());

        long week = review(participant, "WEEKLY", TODAY);
        send(put("/api/v1/reviews/" + week + "/inner-blocker-checkins"), participant, checkins(blockerId, "PARTLY")).andExpect(status().isOk());
        JsonNode export = read(mockMvc.perform(get("/api/v1/account/export").header("Authorization", "Bearer " + participant))
                .andExpect(status().isOk()));
        assertThat(export.get("innerBlockers").get(0).get("practice").asText()).isEqualTo("Compare with my own log");
        assertThat(export.get("innerBlockerCheckins").get(0).get("answer").asText()).isEqualTo("PARTLY");
    }

    /** BR-34-7: no AI service reads inner blockers, so their text can never reach a prompt. */
    @Test
    void noAiServiceReadsInnerBlockers() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/visionmapping/service/ai"))) {
            List<Path> readers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> READS_BLOCKERS.matcher(read(path)).find())
                    .toList();
            assertThat(readers).isEmpty();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean habitArchived(long habitId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select archived from habits where id = ?", Boolean.class, habitId));
    }

    private static Map<String, Object> checkins(long blockerId, String answer) {
        return Map.of("checkins", List.of(Map.of("innerBlockerId", blockerId, "answer", answer)));
    }

    private static Map<String, Object> blocker(String blocker, String showsUp, String practice, String chainKey, Long dreamId) {
        Map<String, Object> body = new HashMap<>();
        body.put("blocker", blocker);
        body.put("showsUp", showsUp);
        body.put("practice", practice);
        body.put("chainKey", chainKey);
        body.put("dreamId", dreamId);
        return body;
    }

    private long review(String token, String type, LocalDate day) throws Exception {
        return read(send(post("/api/v1/reviews"), token, Map.of("reviewType", type, "reviewDate", day.atTime(9, 0).toString(), "summary", "Week"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/v1/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Learn to code", "whyImportant", "It matters",
                "successDefinition", "A job offer", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE",
                "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/v1/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
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

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V34 Test User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
