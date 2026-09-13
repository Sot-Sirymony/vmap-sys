package com.visionmapping.controller;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-55 / BR-44 end-to-end: unlike DreamServiceTest's mocked repository, these
 * run the real {@code findCounselorsForDream} JPQL query — the query that
 * actually caught the implicit-inner-join bug where a partner linked directly
 * to a dream (relatedGoal null) was silently excluded from Gate B.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DreamDecisionGateFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void neitherGateMetRejectsActivation() throws Exception {
        String token = registerAndToken("gate-neither");
        MoonshotDream dream = createMoonshotDream(token, "CRITICAL");

        mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dream.requestBody("ACTIVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "This is a high-priority moonshot dream. Before moving it to Active, either complete the eight-item decision checklist, or link at least two Advisor/Mentor partners to it (directly or via a goal)."));
    }

    @Test
    void completingTheEightItemChecklistClearsTheGate() throws Exception {
        String token = registerAndToken("gate-checklist");
        MoonshotDream dream = createMoonshotDream(token, "CRITICAL");

        Map<String, Object> body = dream.requestBody("ACTIVE");
        for (String field : new String[] {
                "decisionSkippedResearch", "decisionAssumedNoChange", "decisionTrustedUnverifiedClaim",
                "decisionJudgedByAppearance", "decisionUnderTimePressure", "decisionNoOutsideInput",
                "decisionChasedEasyReward", "decisionDismissedDisagreeingAdvice",
        }) {
            body.put(field, false);
        }

        mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.decisionGateClearedAt").isNotEmpty());
    }

    /**
     * The regression case: two Advisor partners linked directly to the dream
     * (relatedGoal left null) must count toward Gate B. This is exactly the
     * shape the pre-fix JPQL implicit inner join on relatedGoal was dropping.
     */
    @Test
    void twoCounselorsLinkedDirectlyToTheDreamClearTheGate() throws Exception {
        String token = registerAndToken("gate-direct-counselors");
        MoonshotDream dream = createMoonshotDream(token, "HIGH");
        createCounselor(token, "Direct Advisor One", "ADVISOR", Map.of("relatedDreamId", dream.id()));
        createCounselor(token, "Direct Advisor Two", "MENTOR", Map.of("relatedDreamId", dream.id()));

        mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dream.requestBody("ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.decisionGateClearedAt").isNotEmpty());
    }

    /**
     * The other join branch: counselors linked through one of the dream's own
     * goals (relatedDream left null on the partner) must also count.
     */
    @Test
    void twoCounselorsLinkedViaAGoalClearTheGate() throws Exception {
        String token = registerAndToken("gate-goal-counselors");
        MoonshotDream dream = createMoonshotDream(token, "HIGH");
        long goalId = postAndId("/api/goals", token, Map.of(
                "dreamId", dream.id(),
                "title", "A goal under the moonshot dream",
                "priority", "HIGH",
                "status", "NOT_STARTED",
                "scheduleMode", "BOTTOM_UP"
        ));
        createCounselor(token, "Goal Advisor One", "ADVISOR", Map.of("relatedGoalId", goalId));
        createCounselor(token, "Goal Advisor Two", "MENTOR", Map.of("relatedGoalId", goalId));

        mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dream.requestBody("ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.decisionGateClearedAt").isNotEmpty());
    }

    @Test
    void onlyOneCounselorIsNotEnough() throws Exception {
        String token = registerAndToken("gate-one-counselor");
        MoonshotDream dream = createMoonshotDream(token, "HIGH");
        createCounselor(token, "Solo Advisor", "ADVISOR", Map.of("relatedDreamId", dream.id()));

        mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dream.requestBody("ACTIVE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gateDoesNotReFireOnceClearedAcrossAStatusRoundTrip() throws Exception {
        String token = registerAndToken("gate-round-trip");
        MoonshotDream dream = createMoonshotDream(token, "HIGH");
        createCounselor(token, "Advisor One", "ADVISOR", Map.of("relatedDreamId", dream.id()));
        createCounselor(token, "Advisor Two", "MENTOR", Map.of("relatedDreamId", dream.id()));

        mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dream.requestBody("ACTIVE"))))
                .andExpect(status().isOk());

        MvcResult paused = mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dream.requestBody("PAUSED"))))
                .andExpect(status().isOk())
                .andReturn();
        String clearedAt = objectMapper.readTree(paused.getResponse().getContentAsString())
                .get("decisionGateClearedAt").asText();

        mockMvc.perform(put("/api/dreams/" + dream.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dream.requestBody("ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionGateClearedAt").value(clearedAt));
    }

    private record MoonshotDream(long id, long visionAreaId, String priority) {
        Map<String, Object> requestBody(String status) {
            Map<String, Object> body = new HashMap<>();
            body.put("visionAreaId", visionAreaId);
            body.put("title", "FR-55 Moonshot Dream");
            body.put("dreamType", "LONG_TERM");
            body.put("priority", priority);
            body.put("targetDate", LocalDate.now().plusMonths(6).toString());
            body.put("status", status);
            body.put("moonshot", true);
            body.put("moonshotVision", "Aim big");
            body.put("scheduleMode", "BOTTOM_UP");
            return body;
        }
    }

    private MoonshotDream createMoonshotDream(String token, String priority) throws Exception {
        long areaId = postAndId("/api/vision-areas", token, Map.of(
                "name", "FR-55 Area",
                "priority", "HIGH",
                "status", "ACTIVE"
        ));
        Map<String, Object> body = new HashMap<>();
        body.put("visionAreaId", areaId);
        body.put("title", "FR-55 Moonshot Dream");
        body.put("dreamType", "LONG_TERM");
        body.put("priority", priority);
        body.put("targetDate", LocalDate.now().plusMonths(6).toString());
        body.put("status", "IDEA");
        body.put("moonshot", true);
        body.put("moonshotVision", "Aim big");
        body.put("scheduleMode", "BOTTOM_UP");
        long dreamId = postAndId("/api/dreams", token, body);
        return new MoonshotDream(dreamId, areaId, priority);
    }

    private void createCounselor(String token, String name, String supportType, Map<String, Object> relation) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(
                "name", name,
                "role", "Advisor",
                "supportType", supportType,
                "status", "ACTIVE"
        ));
        body.putAll(relation);
        mockMvc.perform(post("/api/partners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated());
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "FR-55 Test User",
                                "email", uniqueEmail(prefix),
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long postAndId(String path, String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
