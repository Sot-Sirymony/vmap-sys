package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * FR-60 / BR-49 end-to-end: the three criticism-triage fields round-trip on
 * a PARTNER-type obstacle and never affect its status/severity — there is
 * no gate to verify here (unlike FR-62), only that nothing else changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObstacleCriticismTriageFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void criticismFieldsRoundTrip() throws Exception {
        String token = registerAndToken("criticism-roundtrip");

        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Harsh feedback on the proposal",
                                "obstacleType", "PARTNER",
                                "severity", "MEDIUM",
                                "status", "OPEN",
                                "criticismOverstated", "\"This is always how it goes with you.\"",
                                "criticismDelivery", "Raised voice, said in front of the team.",
                                "criticismSubstance", "The budget section really was missing a contingency line."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criticismOverstated").value("\"This is always how it goes with you.\""))
                .andExpect(jsonPath("$.criticismDelivery").value("Raised voice, said in front of the team."))
                .andExpect(jsonPath("$.criticismSubstance").value("The budget section really was missing a contingency line."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));
    }

    @Test
    void leavingAllThreeFieldsBlankHasNoEffect() throws Exception {
        String token = registerAndToken("criticism-blank");

        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Harsh feedback on the proposal",
                                "obstacleType", "PARTNER",
                                "severity", "MEDIUM",
                                "status", "OPEN"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criticismOverstated").doesNotExist())
                .andExpect(jsonPath("$.criticismDelivery").doesNotExist())
                .andExpect(jsonPath("$.criticismSubstance").doesNotExist());
    }

    @Test
    void criticismFieldsAreAvailableOnNonPartnerObstaclesToo() throws Exception {
        // FR-60.1 scopes the UI to PARTNER-type, but the backend never
        // enforces that restriction server-side, matching FR-54's precedent.
        String token = registerAndToken("criticism-non-partner");

        MvcResult result = mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Feedback from a mentor about a decision obstacle",
                                "obstacleType", "DECISION",
                                "severity", "LOW",
                                "status", "OPEN",
                                "criticismSubstance", "Worth reconsidering the timeline."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(json.get("criticismSubstance").asText())
                .isEqualTo("Worth reconsidering the timeline.");
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "FR-60 Test User",
                                "email", uniqueEmail(prefix),
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
