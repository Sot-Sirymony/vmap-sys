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
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V17 phase 3 (BRD 17.0.0): a gap on a step, or an obstacle, becomes a "find
 * help" step, optionally with a To Contact partner, and the plan check asks who
 * could help with a step that is missing a skill.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GapStepFlowTests {

    private static final String TARGET = LocalDate.now().plusMonths(4).toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void stepSavesAndClearsItsGapKind() throws Exception {
        String token = register("gap-kind");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);

        JsonNode saved = read(send(put("/api/steps/" + plan.stepId()), token,
                stepBody(plan.goalId(), Map.of("resources", "no statistics skills", "gapKind", "SKILL")))
                .andExpect(status().isOk()));
        assertThat(saved.get("gapKind").asText()).isEqualTo("SKILL");

        // Left out keeps it.
        JsonNode kept = read(send(put("/api/steps/" + plan.stepId()), token, stepBody(plan.goalId(), Map.of()))
                .andExpect(status().isOk()));
        assertThat(kept.get("gapKind").asText()).isEqualTo("SKILL");

        // Blank clears it.
        JsonNode cleared = read(send(put("/api/steps/" + plan.stepId()), token, stepBody(plan.goalId(), Map.of("gapKind", "")))
                .andExpect(status().isOk()));
        assertThat(cleared.get("gapKind").isNull()).isTrue();

        send(put("/api/steps/" + plan.stepId()), token, stepBody(plan.goalId(), Map.of("gapKind", "NOT_A_KIND")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blc10_aStepGapBecomesAStepBeforeItWithAPartner() throws Exception {
        String token = register("gap-step");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long later = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/steps", stepBody(plan.goalId(),
                Map.of("title", "Later step", "sequenceNumber", 2)));

        // No gap written yet: nothing to turn into a step.
        send(post("/api/steps/" + plan.stepId() + "/gap-step"), token, gapBody(Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Write what is missing on this step first."));

        send(put("/api/steps/" + plan.stepId()), token,
                stepBody(plan.goalId(), Map.of("resources", "no statistics skills", "gapKind", "SKILL")))
                .andExpect(status().isOk());

        JsonNode result = read(send(post("/api/steps/" + plan.stepId() + "/gap-step"), token, gapBody(Map.of(
                "title", "Find a technical expert or trainer for no statistics skills",
                "createPartner", true, "partnerSupportType", "TECHNICAL")))
                .andExpect(status().isCreated()));

        JsonNode step = result.get("step");
        assertThat(step.get("title").asText()).isEqualTo("Find a technical expert or trainer for no statistics skills");
        assertThat(step.get("goalId").asLong()).isEqualTo(plan.goalId());
        assertThat(step.get("sequenceNumber").asInt()).isEqualTo(1);
        assertThat(step.get("gapKind").asText()).isEqualTo("SKILL");
        assertThat(step.get("coversStepId").asLong()).isEqualTo(plan.stepId());
        assertThat(step.get("coversStepCode").asText()).startsWith("S-");

        JsonNode partner = result.get("partner");
        assertThat(partner.get("status").asText()).isEqualTo("TO_CONTACT");
        assertThat(partner.get("supportType").asText()).isEqualTo("TECHNICAL");
        assertThat(partner.get("relatedStepId").asLong()).isEqualTo(step.get("id").asLong());
        assertThat(step.get("createdPartnerId").asLong()).isEqualTo(partner.get("id").asLong());

        // The original step and the later one moved down to make room.
        assertThat(stepField(token, plan.stepId(), "sequenceNumber").asInt()).isEqualTo(2);
        assertThat(stepField(token, later, "sequenceNumber").asInt()).isEqualTo(3);
    }

    @Test
    void blc21_archivingAFindHelpStepCanArchiveItsPartner() throws Exception {
        String token = register("gap-archive");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        send(put("/api/steps/" + plan.stepId()), token, stepBody(plan.goalId(), Map.of("resources", "no budget", "gapKind", "MONEY")))
                .andExpect(status().isOk());
        JsonNode result = read(send(post("/api/steps/" + plan.stepId() + "/gap-step"), token,
                gapBody(Map.of("createPartner", true))).andExpect(status().isCreated()));
        long stepId = result.get("step").get("id").asLong();
        long partnerId = result.get("partner").get("id").asLong();
        // Left out, the server suggests the helper type from the gap kind.
        assertThat(result.get("partner").get("supportType").asText()).isEqualTo("FINANCIAL");

        mockMvc.perform(delete("/api/steps/" + stepId + "?removePartner=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode partner = read(mockMvc.perform(get("/api/partners/" + partnerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(partner.get("archived").asBoolean()).isTrue();
    }

    @Test
    void blc11_anUnplacedObstacleBecomesAStepInTheChosenGoal() throws Exception {
        String token = register("gap-obstacle");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long obstacleId = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No budget",
                "obstacleType", "MONEY", "severity", "HIGH", "status", "OPEN", "relatedStepId", plan.stepId()));
        // An obstacle from before V17, on the dream only.
        jdbcTemplate.update("update obstacles set related_step_id = null, related_goal_id = null, related_dream_id = ? where id = ?",
                plan.dreamId(), obstacleId);

        send(post("/api/obstacles/" + obstacleId + "/convert-to-step"), token, gapBody(Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose the goal this step belongs to."));

        JsonNode result = read(send(post("/api/obstacles/" + obstacleId + "/convert-to-step"), token,
                gapBody(Map.of("goalId", plan.goalId(), "title", "Find a sponsor"))).andExpect(status().isCreated()));

        long newStep = result.get("step").get("id").asLong();
        assertThat(result.get("step").get("sequenceNumber").asInt()).isEqualTo(2);
        assertThat(result.get("step").get("gapKind").asText()).isEqualTo("MONEY");
        assertThat(result.get("step").get("coversObstacleId").asLong()).isEqualTo(obstacleId);
        assertThat(result.get("partner").isNull()).isTrue();
        JsonNode obstacle = result.get("obstacle");
        assertThat(obstacle.get("relatedStepId").asLong()).isEqualTo(newStep);
        assertThat(obstacle.get("relatedGoalId").asLong()).isEqualTo(plan.goalId());
        assertThat(obstacle.get("relatedDreamId").asLong()).isEqualTo(plan.dreamId());
        assertThat(obstacle.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(obstacle.get("placement").asText()).isEqualTo("STEP");
    }

    @Test
    void aPartnerConflictIsNotTurnedIntoAStep() throws Exception {
        String token = register("gap-exempt");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long obstacleId = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "Argument",
                "obstacleType", "PARTNER", "severity", "HIGH", "status", "OPEN", "conflictIncident", "We disagreed"));

        send(post("/api/obstacles/" + obstacleId + "/convert-to-step"), token, gapBody(Map.of("goalId", plan.goalId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blFr22_thePlanCheckAsksWhoCouldHelpUntilHelpIsPlanned() throws Exception {
        String token = register("gap-check");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        send(put("/api/steps/" + plan.stepId()), token,
                stepBody(plan.goalId(), Map.of("resources", "no statistics skills", "gapKind", "SKILL")))
                .andExpect(status().isOk());

        JsonNode before = planCheck(token, plan.dreamId());
        assertThat(issueKinds(before)).contains("GAP_NEEDS_HELP");
        String code = stepField(token, plan.stepId(), "code").asText();
        assertThat(before.toString()).contains("Step " + code + " is missing a skill. Who could help?");

        send(post("/api/steps/" + plan.stepId() + "/gap-step"), token, gapBody(Map.of())).andExpect(status().isCreated());

        assertThat(issueKinds(planCheck(token, plan.dreamId()))).doesNotContain("GAP_NEEDS_HELP");
    }

    @Test
    void anObstacleOnAStepBecomesAStepJustBeforeItWithANamedPartner() throws Exception {
        String token = register("gap-obstacle-placed");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long obstacleId = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No survey tool",
                "obstacleType", "SYSTEM", "severity", "HIGH", "status", "OPEN", "relatedStepId", plan.stepId()));

        JsonNode result = read(send(post("/api/obstacles/" + obstacleId + "/convert-to-step"), token, gapBody(Map.of(
                "gapKind", "KNOWLEDGE", "createPartner", true, "partnerName", "Dr. Chan", "description", "Ask about tools")))
                .andExpect(status().isCreated()));

        JsonNode step = result.get("step");
        assertThat(step.get("sequenceNumber").asInt()).isEqualTo(1);
        assertThat(step.get("coversStepId").asLong()).isEqualTo(plan.stepId());
        assertThat(step.get("gapKind").asText()).isEqualTo("KNOWLEDGE");
        assertThat(step.get("description").asText()).isEqualTo("Ask about tools");
        assertThat(result.get("partner").get("name").asText()).isEqualTo("Dr. Chan");
        assertThat(result.get("partner").get("supportType").asText()).isEqualTo("MENTOR");
        assertThat(stepField(token, plan.stepId(), "sequenceNumber").asInt()).isEqualTo(2);
    }

    @Test
    void closedObstaclesAndArchivedStepsAreNotTurnedIntoSteps() throws Exception {
        String token = register("gap-refused");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long resolved = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "Old worry",
                "obstacleType", "TIME", "severity", "LOW", "status", "RESOLVED", "rootCause", "Too many meetings",
                "relatedStepId", plan.stepId()));
        send(post("/api/obstacles/" + resolved + "/convert-to-step"), token, gapBody(Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This blocker is already closed, so it needs no step."));

        send(put("/api/steps/" + plan.stepId()), token, stepBody(plan.goalId(), Map.of("resources", "no budget")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/steps/" + plan.stepId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        send(post("/api/steps/" + plan.stepId() + "/gap-step"), token, gapBody(Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Restore this step before planning help for it."));
    }

    @Test
    void archivingAFindHelpStepKeepsItsPartnerUnlessAsked() throws Exception {
        String token = register("gap-archive-keep");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long created = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/steps", stepBody(plan.goalId(),
                Map.of("title", "Second", "sequenceNumber", 2, "resources", "no data", "gapKind", "PARTNER")));
        assertThat(stepField(token, created, "gapKind").asText()).isEqualTo("PARTNER");
        JsonNode result = read(send(post("/api/steps/" + created + "/gap-step"), token, gapBody(Map.of("createPartner", true)))
                .andExpect(status().isCreated()));
        // A partner gap has no suggested helper type.
        assertThat(result.get("partner").get("supportType").asText()).isEqualTo("OTHER");
        assertThat(result.get("partner").get("name").asText()).startsWith("To find: ");

        mockMvc.perform(delete("/api/steps/" + result.get("step").get("id").asLong()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        JsonNode partner = read(mockMvc.perform(get("/api/partners/" + result.get("partner").get("id").asLong())
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
        assertThat(partner.get("archived").asBoolean()).isFalse();
    }

    private JsonNode planCheck(String token, long dreamId) throws Exception {
        return read(mockMvc.perform(post("/api/ai/plan/dreams/" + dreamId + "/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private static java.util.List<String> issueKinds(JsonNode check) {
        java.util.List<String> kinds = new java.util.ArrayList<>();
        check.get("issues").forEach(issue -> kinds.add(issue.get("kind").asText()));
        return kinds;
    }

    private JsonNode stepField(String token, long stepId, String field) throws Exception {
        return read(mockMvc.perform(get("/api/steps/" + stepId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).get(field);
    }

    private static Map<String, Object> stepBody(long goalId, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("owner", "Me", "goalId", goalId, "title", "Fixture step",
                "sequenceNumber", 1, "complex", false, "priority", "HIGH", "targetDate", TARGET, "status", "NOT_STARTED"));
        body.putAll(extra);
        return body;
    }

    private static Map<String, Object> gapBody(Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("title", "Find help", "owner", "Me", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(), "createPartner", false));
        body.putAll(extra);
        return body;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        MvcResult done = result.andReturn();
        return objectMapper.readTree(done.getResponse().getContentAsString());
    }

    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V17 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
