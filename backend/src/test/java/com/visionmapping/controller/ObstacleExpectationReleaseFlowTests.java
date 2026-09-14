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
 * FR-61 / BR-50 end-to-end: the expectation fields round-trip, and the
 * release action is a genuine one-time stamp through the real HTTP path,
 * not just the mocked-repository unit tests in ObstacleServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObstacleExpectationReleaseFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void expectationFieldsRoundTrip() throws Exception {
        String token = registerAndToken("expectation-roundtrip");

        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Missed the deadline together",
                                "obstacleType", "PARTNER",
                                "severity", "MEDIUM",
                                "status", "OPEN",
                                "conflictExpectation", "That they'd flag delays early.",
                                "conflictExpectationAgreed", "UNSURE"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conflictExpectation").value("That they'd flag delays early."))
                .andExpect(jsonPath("$.conflictExpectationAgreed").value("UNSURE"))
                .andExpect(jsonPath("$.expectationReleasedAt").doesNotExist());
    }

    @Test
    void invalidAgreementValueIsRejected() throws Exception {
        String token = registerAndToken("expectation-bad-enum");

        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Missed the deadline together",
                                "obstacleType", "PARTNER",
                                "severity", "MEDIUM",
                                "status", "OPEN",
                                "conflictExpectationAgreed", "MAYBE"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void releasingSetsATimestampThatNeverReFires() throws Exception {
        String token = registerAndToken("expectation-release");
        long obstacleId = createObstacle(token);

        MvcResult first = mockMvc.perform(post("/api/obstacles/" + obstacleId + "/release-expectation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String firstStamp = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("expectationReleasedAt").asText();
        org.assertj.core.api.Assertions.assertThat(firstStamp).isNotBlank();

        mockMvc.perform(post("/api/obstacles/" + obstacleId + "/release-expectation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectationReleasedAt").value(firstStamp));
    }

    @Test
    void releasingNeverChangesStatusOrSeverity() throws Exception {
        String token = registerAndToken("expectation-no-side-effects");
        long obstacleId = createObstacle(token);

        mockMvc.perform(post("/api/obstacles/" + obstacleId + "/release-expectation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));
    }

    private long createObstacle(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Missed the deadline together",
                                "obstacleType", "PARTNER",
                                "severity", "MEDIUM",
                                "status", "OPEN"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "FR-61 Test User",
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
