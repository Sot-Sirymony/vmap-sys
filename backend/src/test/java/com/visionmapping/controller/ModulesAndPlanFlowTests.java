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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

/** V8 Phase 2 end-to-end: assessment, module unlocking (AC-03), guided plan, milestones. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModulesAndPlanFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final Map<String, Object> NO_ROWS = Map.of("rows", List.of());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void modulesFollowTheJourneyOrderAndStartLocked() throws Exception {
        String token = registerAndToken("order");
        JsonNode modules = read(mockMvc.perform(get("/api/modules").header("Authorization", "Bearer " + token)));

        List<Integer> numbers = new ArrayList<>();
        modules.forEach(module -> numbers.add(module.get("number").asInt()));
        assertThat(numbers).containsExactly(4, 1, 2, 3, 5, 6, 14, 15, 7, 8, 9, 10, 11, 12, 13);
        assertThat(modules.get(0).get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(modules.get(1).get("status").asText()).isEqualTo("LOCKED");
        modules.forEach(module -> assertThat(module.get("title").asText()).isNotBlank());
    }

    @Test
    void aLockedModuleCanBeNeitherReadNorCompleted() throws Exception {
        String token = registerAndToken("locked");
        mockMvc.perform(get("/api/modules/1").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        send(put("/api/modules/1/completion"), token, Map.of("rows", List.of(Map.of("belief", "b", "replacement", "r"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDataProvenModuleExplainsWhatIsMissingUntilTheDataExists() throws Exception {
        String token = registerAndToken("rule");
        mockMvc.perform(get("/api/modules/4").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.requirementMessage").value("Give at least one dream a letter rank first."));
        send(put("/api/modules/4/completion"), token, NO_ROWS).andExpect(status().isBadRequest());

        dream(token, "ACTIVE", "A");
        send(put("/api/modules/4/completion"), token, NO_ROWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.requirementMessage").doesNotExist());
    }

    @Test
    void aFormExerciseRejectsIncompleteRowsAndKeepsItsCompletionDateWhenEdited() throws Exception {
        String token = registerAndToken("form");
        dream(token, "ACTIVE", "A");
        send(put("/api/modules/4/completion"), token, NO_ROWS).andExpect(status().isOk());

        send(put("/api/modules/1/completion"), token, NO_ROWS).andExpect(status().isBadRequest());
        send(put("/api/modules/1/completion"), token, Map.of("rows", List.of(Map.of("belief", "only one")))).andExpect(status().isBadRequest());
        send(put("/api/modules/1/completion"), token, Map.of("rows", List.of(Map.of("belief", " ", "replacement", "r")))).andExpect(status().isBadRequest());
        send(put("/api/modules/1/completion"), token, Map.of("rows", List.of(Map.of("belief", "b", "replacement", "r", "extra", "x")))).andExpect(status().isBadRequest());

        JsonNode first = read(send(put("/api/modules/1/completion"), token, Map.of("rows", List.of(Map.of("belief", "b", "replacement", "r")))));
        JsonNode edited = read(send(put("/api/modules/1/completion"), token, Map.of("rows", List.of(Map.of("belief", "new", "replacement", "r")))));
        assertThat(edited.get("completedAt")).isEqualTo(first.get("completedAt"));
        assertThat(edited.get("rows").get(0).get("belief").asText()).isEqualTo("new");
    }

    @Test
    void allFifteenModulesCanBeCompletedInOrderWithoutAffectingAnotherUser() throws Exception {
        String token = registerAndToken("walk");
        long dreamId = dream(token, "ACTIVE", "A");
        complete(token, 4, NO_ROWS);
        complete(token, 1, rows(Map.of("belief", "b", "replacement", "r")));

        send(post("/api/fear-entries"), token, Map.of("fear", "F", "worstCase", "W", "likelihood", "LOW", "firstTest", "T")).andExpect(status().isCreated());
        complete(token, 2, NO_ROWS);
        complete(token, 3, rows(Map.of("source", "s", "validPoint", "v", "response", "r", "action", "a")));

        send(put("/api/modules/5/completion"), token, NO_ROWS).andExpect(status().isBadRequest());
        long goalId = goal(token, dreamId, false);
        step(token, goalId);
        complete(token, 5, NO_ROWS);
        complete(token, 6, rows(Map.of("gap", "g", "source", "s")));
        complete(token, 14, NO_ROWS);

        send(post("/api/dreams/" + dreamId + "/commitment"), token, Map.of("signedName", "Me", "startDate", TODAY.toString(), "firstAction", "Go")).andExpect(status().isOk());
        complete(token, 15, NO_ROWS);
        complete(token, 7, rows(Map.of("task", "t", "approach", "automate", "carrier", "c")));

        goal(token, dreamId, true);
        complete(token, 8, NO_ROWS);
        send(post("/api/partners"), token, Map.of("name", "P", "supportType", "MENTOR", "status", "TO_CONTACT")).andExpect(status().isCreated());
        complete(token, 9, NO_ROWS);
        send(put("/api/attitude-checkins/" + TODAY), token, Map.of("score", 4)).andExpect(status().isOk());
        complete(token, 10, NO_ROWS);

        send(put("/api/modules/11/completion"), token, rows(Map.of("pitch", "p", "practice", "x", "rating", "9"))).andExpect(status().isBadRequest());
        complete(token, 11, rows(Map.of("pitch", "p", "practice", "x", "rating", "4")));
        send(post("/api/habits?date=" + TODAY), token, Map.of("name", "Read")).andExpect(status().isCreated());
        complete(token, 12, NO_ROWS);
        complete(token, 13, NO_ROWS);

        read(mockMvc.perform(get("/api/modules").header("Authorization", "Bearer " + token)))
                .forEach(module -> assertThat(module.get("status").asText()).isEqualTo("COMPLETED"));

        String stranger = registerAndToken("walk-stranger");
        JsonNode theirs = read(mockMvc.perform(get("/api/modules").header("Authorization", "Bearer " + stranger)));
        assertThat(theirs.get(0).get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(theirs.get(1).get("status").asText()).isEqualTo("LOCKED");
    }

    @Test
    void assessmentComparesLatestToBaselineAndReplacesTheSameDay() throws Exception {
        String token = registerAndToken("assess");
        send(put("/api/chain-assessments"), token, assessment(TODAY.minusDays(100), 2, 2, 2, 2, 2, 2)).andExpect(status().isOk());
        mockMvc.perform(get("/api/chain-assessments").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.improvementPercent").doesNotExist())
                .andExpect(jsonPath("$.retakeSuggested").value(true));

        send(put("/api/chain-assessments"), token, assessment(TODAY, 1, 1, 1, 1, 1, 1)).andExpect(status().isOk());
        send(put("/api/chain-assessments"), token, assessment(TODAY, 3, 3, 3, 2, 2, 2)).andExpect(status().isOk());
        send(put("/api/chain-assessments"), token, assessment(TODAY, 6, 3, 3, 2, 2, 2)).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/chain-assessments").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.assessments.length()").value(2))
                .andExpect(jsonPath("$.improvementPercent").value(25.0))
                .andExpect(jsonPath("$.retakeSuggested").value(false));

        String stranger = registerAndToken("assess-stranger");
        mockMvc.perform(get("/api/chain-assessments").header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$.assessments.length()").value(0));
    }

    @Test
    void planStatusFollowsTheDreamAsItFillsIn() throws Exception {
        String token = registerAndToken("plan");
        long dreamId = dream(token, "ACTIVE", null);
        mockMvc.perform(get("/api/plan-status/" + dreamId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.planReady").value(false))
                .andExpect(jsonPath("$.stages[0].key").value("STATEMENT"))
                .andExpect(jsonPath("$.stages[0].done").value(true))
                .andExpect(jsonPath("$.stages[1].key").value("STRENGTHS"))
                .andExpect(jsonPath("$.stages[2].done").value(false))
                .andExpect(jsonPath("$.stages[5].done").value(false));

        long goalId = goal(token, dreamId, false);
        long stepId = step(token, goalId);
        send(post("/api/obstacles"), token, Map.of("relatedGoalId", goalId, "title", "Need funding", "obstacleType", "MONEY", "severity", "HIGH", "status", "OPEN"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/plan-status/" + dreamId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.planReady").value(true))
                .andExpect(jsonPath("$.stages[5].done").value(true));

        jdbcTemplate.update("update vision_steps set target_date = null where id = ?", stepId);
        mockMvc.perform(get("/api/plan-status/" + dreamId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.planReady").value(false))
                .andExpect(jsonPath("$.stages[4].detail").value("1 step still without a date."));

        String stranger = registerAndToken("plan-stranger");
        mockMvc.perform(get("/api/plan-status/" + dreamId).header("Authorization", "Bearer " + stranger)).andExpect(status().isNotFound());
    }

    @Test
    void milestonesBelongToTheirDreamAndOwner() throws Exception {
        String token = registerAndToken("milestone");
        String stranger = registerAndToken("milestone-stranger");
        long dreamId = dream(token, "ACTIVE", null);
        Map<String, Object> body = Map.of("dreamId", dreamId, "title", "First customer", "achievedOn", TODAY.toString());

        send(post("/api/milestones"), stranger, body).andExpect(status().isNotFound());
        long id = read(send(post("/api/milestones"), token, body).andExpect(status().isCreated())).get("id").asLong();

        mockMvc.perform(get("/api/milestones").param("dreamId", String.valueOf(dreamId)).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/milestones").param("dreamId", String.valueOf(dreamId)).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/milestones/" + id).header("Authorization", "Bearer " + stranger)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/milestones/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/milestones").param("dreamId", String.valueOf(dreamId)).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void permanentlyDeletingADreamRemovesItsMilestones() throws Exception {
        String token = registerAndToken("milestone-delete");
        long dreamId = dream(token, "ACTIVE", null);
        send(post("/api/milestones"), token, Map.of("dreamId", dreamId, "title", "M", "achievedOn", TODAY.toString())).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/dreams/" + dreamId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/dreams/" + dreamId + "/permanent").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from milestones where dream_id = ?", Long.class, dreamId)).isZero();
    }

    private void complete(String token, int number, Map<String, Object> body) throws Exception {
        send(put("/api/modules/" + number + "/completion"), token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private Map<String, Object> rows(Map<String, String> row) {
        return Map.of("rows", List.of(row));
    }

    private Map<String, Object> assessment(LocalDate takenOn, int settling, int fear, int criticism, int vision, int knowledge, int resources) {
        return Map.of("takenOn", takenOn.toString(), "settlingScore", settling, "fearScore", fear, "criticismScore", criticism,
                "visionScore", vision, "knowledgeScore", knowledge, "resourcesScore", resources);
    }

    private long dream(String token, String dreamStatus, String letterRank) throws Exception {
        long areaId = id(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "A dream", "whyImportant", "It matters",
                "successDefinition", "It is done", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", dreamStatus,
                "moonshot", false, "scheduleMode", "BOTTOM_UP"));
        body.put("letterRank", letterRank);
        return id(send(post("/api/dreams"), token, body));
    }

    private long goal(String token, long dreamId, boolean moonshot) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("dreamId", dreamId, "title", "A goal", "priority", "MEDIUM",
                "status", "NOT_STARTED", "moonshot", moonshot, "scheduleMode", "BOTTOM_UP"));
        if (moonshot) {
            body.put("moonshotVision", "Ten times bigger");
        }
        return id(send(post("/api/goals"), token, body));
    }

    private long step(String token, long goalId) throws Exception {
        return id(send(post("/api/steps"), token, Map.of("goalId", goalId, "title", "A step", "owner", "Me", "sequenceNumber", 1,
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
                                "fullName", "V8 Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
