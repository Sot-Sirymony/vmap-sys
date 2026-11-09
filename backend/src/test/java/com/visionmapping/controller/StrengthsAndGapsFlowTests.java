package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V8.1 Phase 5 end-to-end: the Strengths and Gaps profile (FR-79 to FR-84, AC-08 to AC-12). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StrengthsAndGapsFlowTests {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anItemCannotBeSavedWithoutEvidence() throws Exception {
        String token = registerAndToken("evidence");
        long dreamId = dream(token);
        send(post("/api/self-assessment-items"), token, item(dreamId, "STRENGTH", "Builds trust", " ", "HIGH"))
                .andExpect(status().isBadRequest());
        send(post("/api/self-assessment-items"), token, item(dreamId, "STRENGTH", "Builds trust", "Kept 9 of 10 clients", "HIGH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MANUAL"));
    }

    @Test
    void theProfileIsCompleteOnlyWhenEveryHighGapIsCoveredAndNeverHoldsBackThePlan() throws Exception {
        String token = registerAndToken("complete");
        long dreamId = dream(token);
        long goalId = goal(token, dreamId);
        step(token, goalId);

        JsonNode status = read(mockMvc.perform(get("/api/plan-status/" + dreamId).header("Authorization", "Bearer " + token)));
        assertThat(status.get("planReady").asBoolean()).isTrue();
        assertThat(status.get("stages").get(1).get("key").asText()).isEqualTo("STRENGTHS");
        assertThat(status.get("stages").get(1).get("done").asBoolean()).isFalse();

        id(send(post("/api/self-assessment-items"), token, item(dreamId, "STRENGTH", "Builds trust", "Kept clients", "HIGH")));
        long gap = id(send(post("/api/self-assessment-items"), token, item(dreamId, "WEAKNESS", "Bookkeeping", "Missed a filing", "HIGH")));
        id(send(post("/api/self-assessment-items"), token, item(dreamId, "WEAKNESS", "Public speaking", "Avoid meetings", "LOW")));

        JsonNode profile = profile(token, dreamId);
        assertThat(profile.get("complete").asBoolean()).isFalse();
        assertThat(profile.get("uncoveredGaps").asInt()).isEqualTo(1);
        assertThat(profile.get("whatIBring").size()).isEqualTo(1);
        assertThat(profile.get("whatILack").size()).isEqualTo(1);
        assertThat(profile.get("recordedOnly").size()).isEqualTo(1);
        mockMvc.perform(get("/api/plan-status/" + dreamId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.planReady").value(true))
                .andExpect(jsonPath("$.stages[1].detail").value("1 important gap still without a way to cover it."));

        send(put("/api/self-assessment-items/" + gap + "/cover"), token, Map.of("method", "ACCEPT")).andExpect(status().isBadRequest());
        send(put("/api/self-assessment-items/" + gap + "/cover"), token, Map.of("method", "ACCEPT", "note", "My accountant handles it"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverMethod").value("ACCEPT"));
        send(put("/api/self-assessment-items/" + gap + "/cover"), token, Map.of("method", "ACCEPT", "note", "Again"))
                .andExpect(status().isBadRequest());

        assertThat(profile(token, dreamId).get("complete").asBoolean()).isTrue();
        mockMvc.perform(get("/api/plan-status/" + dreamId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.stages[1].done").value(true));
    }

    @Test
    void onlyAHighRelevanceWeaknessCanBeCovered() throws Exception {
        String token = registerAndToken("only-high");
        long dreamId = dream(token);
        long low = id(send(post("/api/self-assessment-items"), token, item(dreamId, "WEAKNESS", "Spelling", "Typos", "LOW")));
        long strength = id(send(post("/api/self-assessment-items"), token, item(dreamId, "STRENGTH", "Sales", "Top seller", "HIGH")));
        send(put("/api/self-assessment-items/" + low + "/cover"), token, Map.of("method", "ACCEPT", "note", "x")).andExpect(status().isBadRequest());
        send(put("/api/self-assessment-items/" + strength + "/cover"), token, Map.of("method", "ACCEPT", "note", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void eachWayToCoverCreatesItsLinkedRecordAndRemovingTheGapCanRemoveIt() throws Exception {
        String token = registerAndToken("linked");
        long dreamId = dream(token);
        long goalId = goal(token, dreamId);

        long partnerGap = highGap(token, dreamId, "Bookkeeping");
        JsonNode partnerCover = read(send(put("/api/self-assessment-items/" + partnerGap + "/cover"), token,
                Map.of("method", "PARTNER", "partnerName", "Part-time bookkeeper", "partnerSupportType", "TECHNICAL")).andExpect(status().isOk()));
        long partnerId = partnerCover.get("linkedPartnerId").asLong();
        mockMvc.perform(get("/api/partners/" + partnerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.name").value("Part-time bookkeeper"))
                .andExpect(jsonPath("$.status").value("TO_CONTACT"))
                .andExpect(jsonPath("$.relatedDreamId").value(dreamId));

        long learnGap = highGap(token, dreamId, "Online marketing");
        send(put("/api/self-assessment-items/" + learnGap + "/cover"), token, Map.of("method", "LEARN")).andExpect(status().isBadRequest());
        JsonNode learnCover = read(send(put("/api/self-assessment-items/" + learnGap + "/cover"), token,
                Map.of("method", "LEARN", "goalId", goalId, "targetDate", TODAY.plusDays(30).toString())).andExpect(status().isOk()));
        long stepId = learnCover.get("linkedStepId").asLong();
        mockMvc.perform(get("/api/steps/" + stepId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.title").value("Learn: Online marketing"))
                .andExpect(jsonPath("$.owner").value("V8.1 Test User"))
                .andExpect(jsonPath("$.targetDate").value(TODAY.plusDays(30).toString()));

        long resourceGap = highGap(token, dreamId, "Starting money");
        send(put("/api/self-assessment-items/" + resourceGap + "/cover"), token,
                Map.of("method", "RESOURCE", "note", "Small business grant")).andExpect(status().isOk());
        long delegateGap = highGap(token, dreamId, "Social media posting");
        send(put("/api/self-assessment-items/" + delegateGap + "/cover"), token,
                Map.of("method", "DELEGATE", "note", "A scheduling tool")).andExpect(status().isOk());
        assertThat(moduleRows(dreamId, 6)).contains("Starting money", "Small business grant");
        assertThat(moduleRows(dreamId, 7)).contains("Social media posting", "A scheduling tool");

        // Keeping the partner when the gap goes.
        send(delete("/api/self-assessment-items/" + partnerGap), token, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedItemRemoved").value(false));
        mockMvc.perform(get("/api/partners/" + partnerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.archived").value(false));

        // Removing the step with the gap.
        send(delete("/api/self-assessment-items/" + learnGap + "?removeLinked=true"), token, Map.of())
                .andExpect(jsonPath("$.linkedItemRemoved").value(true));
        mockMvc.perform(get("/api/steps/" + stepId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.archived").value(true));

        // The only row in module 6 is kept, so the module is never taken back to unfinished.
        send(delete("/api/self-assessment-items/" + resourceGap + "?removeLinked=true"), token, Map.of())
                .andExpect(jsonPath("$.linkedItemRemoved").value(false))
                .andExpect(jsonPath("$.note").isNotEmpty());
        assertThat(moduleRows(dreamId, 6)).contains("Starting money");
    }

    @Test
    void learnIsLimitedToTwoGapsAtATime() throws Exception {
        String token = registerAndToken("learn-limit");
        long dreamId = dream(token);
        long goalId = goal(token, dreamId);
        Map<String, Object> learn = Map.of("method", "LEARN", "goalId", goalId, "targetDate", TODAY.plusDays(10).toString());
        send(put("/api/self-assessment-items/" + highGap(token, dreamId, "One") + "/cover"), token, learn).andExpect(status().isOk());
        send(put("/api/self-assessment-items/" + highGap(token, dreamId, "Two") + "/cover"), token, learn).andExpect(status().isOk());
        long third = highGap(token, dreamId, "Three");
        send(put("/api/self-assessment-items/" + third + "/cover"), token, learn)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("another way")));
        send(put("/api/self-assessment-items/" + third + "/cover"), token, Map.of("method", "PARTNER", "partnerName", "A trainer"))
                .andExpect(status().isOk());
        assertThat(profile(token, dreamId).get("learnInUse").asInt()).isEqualTo(2);
    }

    @Test
    void anOutsideViewTheUserDisagreesWithIsFlagged() throws Exception {
        String token = registerAndToken("outside");
        long dreamId = dream(token);
        Map<String, Object> body = new HashMap<>(item(dreamId, "STRENGTH", "Good listener", "Friends say so", "MEDIUM"));
        body.put("outsideBy", "My manager");
        body.put("outsideView", "You interrupt in meetings");
        body.put("outsideAgrees", false);
        send(post("/api/self-assessment-items"), token, body)
                .andExpect(jsonPath("$.outsideMismatch").value(true))
                .andExpect(jsonPath("$.outsideBy").value("My manager"));

        body.put("outsideView", " ");
        send(post("/api/self-assessment-items"), token, body)
                .andExpect(jsonPath("$.outsideMismatch").value(false))
                .andExpect(jsonPath("$.outsideBy").doesNotExist());
    }

    @Test
    void aRetakeShowsWhatChangedSinceTheEarlierSave() throws Exception {
        String token = registerAndToken("retake");
        long dreamId = dream(token);
        send(post("/api/strengths-profiles/" + dreamId + "/snapshots"), token, Map.of()).andExpect(status().isBadRequest());

        long gap = highGap(token, dreamId, "Bookkeeping");
        id(send(post("/api/self-assessment-items"), token, item(dreamId, "STRENGTH", "Sales", "Top seller", "MEDIUM")));
        JsonNode first = read(send(post("/api/strengths-profiles/" + dreamId + "/snapshots"), token, Map.of()).andExpect(status().isOk()));
        assertThat(first.get("lastSavedOn").asText()).isEqualTo(TODAY.toString());
        assertThat(first.get("changes").size()).isZero();

        // Pretend that save was 90 days ago: the retake is now suggested.
        jdbcTemplate.update("update self_assessment_snapshots set taken_on = ? where dream_id = ?", TODAY.minusDays(90), dreamId);
        assertThat(profile(token, dreamId).get("retakeSuggested").asBoolean()).isTrue();

        send(put("/api/self-assessment-items/" + gap + "/cover"), token, Map.of("method", "ACCEPT", "note", "Fine")).andExpect(status().isOk());
        id(send(post("/api/self-assessment-items"), token, item(dreamId, "WEAKNESS", "Patience", "Rush things", "LOW")));
        JsonNode retake = read(send(post("/api/strengths-profiles/" + dreamId + "/snapshots"), token, Map.of()).andExpect(status().isOk()));
        assertThat(retake.get("comparedWith").asText()).isEqualTo(TODAY.minusDays(90).toString());
        assertThat(retake.get("retakeSuggested").asBoolean()).isFalse();
        JsonNode changes = retake.get("changes");
        assertThat(changes.size()).isEqualTo(2);
        assertThat(changes.toString()).contains("\"type\":\"CHANGED\"", "\"coverAfter\":\"ACCEPT\"", "\"type\":\"ADDED\"", "Patience");
    }

    @Test
    void anotherUserCannotSeeOrUseTheProfile() throws Exception {
        String token = registerAndToken("owner");
        String stranger = registerAndToken("stranger");
        long dreamId = dream(token);
        long gap = highGap(token, dreamId, "Bookkeeping");
        mockMvc.perform(get("/api/strengths-profiles/" + dreamId).header("Authorization", "Bearer " + stranger)).andExpect(status().isNotFound());
        send(put("/api/self-assessment-items/" + gap + "/cover"), stranger, Map.of("method", "ACCEPT", "note", "x")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/self-assessment-items").header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private long highGap(String token, long dreamId, String description) throws Exception {
        return id(send(post("/api/self-assessment-items"), token, item(dreamId, "WEAKNESS", description, "A real example", "HIGH")));
    }

    private Map<String, Object> item(long dreamId, String kind, String description, String evidence, String relevance) {
        return Map.of("dreamId", dreamId, "kind", kind, "description", description, "evidence", evidence, "relevance", relevance);
    }

    private JsonNode profile(String token, long dreamId) throws Exception {
        return read(mockMvc.perform(get("/api/strengths-profiles/" + dreamId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private String moduleRows(long dreamId, int moduleNumber) {
        return jdbcTemplate.queryForObject("select rows_json from module_completions where module_number = ? "
                + "and user_id = (select user_id from dreams where id = ?)", String.class, moduleNumber, dreamId);
    }

    private long dream(String token) throws Exception {
        long areaId = id(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        return id(send(post("/api/dreams"), token, Map.of("visionAreaId", areaId, "title", "Start a business", "whyImportant", "Freedom",
                "successDefinition", "Ten clients", "dreamType", "LONG_TERM", "priority", "HIGH", "status", "ACTIVE",
                "moonshot", false, "scheduleMode", "BOTTOM_UP")));
    }

    private long goal(String token, long dreamId) throws Exception {
        return id(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Find clients", "priority", "MEDIUM",
                "status", "NOT_STARTED", "moonshot", false, "scheduleMode", "BOTTOM_UP")));
    }

    private void step(String token, long goalId) throws Exception {
        id(send(post("/api/steps"), token, Map.of("goalId", goalId, "title", "A step", "owner", "Me", "sequenceNumber", 1,
                "complex", false, "priority", "MEDIUM", "targetDate", TODAY.plusDays(3).toString(), "status", "NOT_STARTED")));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long id(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private String registerAndToken(String prefix) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "V8.1 Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
