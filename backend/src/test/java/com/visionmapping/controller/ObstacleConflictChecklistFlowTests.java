package com.visionmapping.controller;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-62 / BR-51 end-to-end: a PARTNER-type obstacle cannot move to Resolved
 * with an incomplete conflict engagement checklist, but "No" answers never
 * block it — only a blank one does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObstacleConflictChecklistFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void incompleteChecklistBlocksResolved() throws Exception {
        String token = registerAndToken("checklist-incomplete");

        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(obstacleBody("RESOLVED", Map.of(
                                "rootCause", "Underestimated the timeline",
                                "conflictNoCharacterAttacks", true,
                                "conflictStayedOnIncident", true,
                                "conflictNoThreatsOrSarcasm", true
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Resolved PARTNER obstacles must also complete all four conflict engagement checklist items."));
    }

    @Test
    void completeChecklistAllowsResolvedEvenWithANoAnswer() throws Exception {
        String token = registerAndToken("checklist-complete-with-no");

        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(obstacleBody("RESOLVED", Map.of(
                                "rootCause", "Underestimated the timeline",
                                "conflictNoCharacterAttacks", true,
                                "conflictStayedOnIncident", false,
                                "conflictNoThreatsOrSarcasm", true,
                                "conflictDefinedWinWin", true
                        )))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.conflictStayedOnIncident").value(false));
    }

    @Test
    void nonPartnerObstacleIsUnaffectedByTheChecklist() throws Exception {
        String token = registerAndToken("checklist-non-partner");

        Map<String, Object> body = new HashMap<>();
        body.put("title", "A time-management obstacle");
        body.put("obstacleType", "TIME");
        body.put("severity", "MEDIUM");
        body.put("status", "RESOLVED");
        body.put("rootCause", "Underestimated the timeline");

        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void updatingAnExistingObstacleToResolvedStillEnforcesTheChecklist() throws Exception {
        String token = registerAndToken("checklist-update");

        MvcResult created = mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(obstacleBody("OPEN", Map.of("rootCause", "Underestimated the timeline")))))
                .andExpect(status().isCreated())
                .andReturn();
        long obstacleId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/obstacles/" + obstacleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(obstacleBody("RESOLVED", Map.of("rootCause", "Underestimated the timeline")))))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> obstacleBody(String status, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Disagreement with a mentor");
        body.put("obstacleType", "PARTNER");
        body.put("severity", "MEDIUM");
        body.put("status", status);
        body.putAll(extra);
        return body;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "FR-62 Test User",
                                "email", uniqueEmail(prefix),
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return readJson(result).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
