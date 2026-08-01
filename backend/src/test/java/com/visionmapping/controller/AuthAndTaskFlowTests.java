package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndTaskFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 401, not 403. The distinction is the point: 403 would claim the caller is
     * known and merely lacks a permission, when in fact there are no credentials
     * at all. Only 401 tells a client to authenticate and retry.
     */
    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void malformedJwtIsRejectedCleanlyInsteadOfCausingServerError() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer not-a-valid-jwt-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtSignedWithAnotherKeyIsRejectedCleanlyInsteadOfCausingServerError() throws Exception {
        String tamperedToken = Jwts.builder()
                .subject("someone@example.com")
                .signWith(Keys.hmacShaKeyFor("a-completely-different-signing-key-not-used-by-the-app".getBytes()))
                .compact();

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerReturnsJwtToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Test User",
                                "email", uniqueEmail("register"),
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void blockedTaskRequiresBlockerReason() throws Exception {
        String token = registerAndToken("blocked-task");
        long areaId = postAndId("/api/vision-areas", token, Map.of(
                "name", "Research",
                "description", "Research development",
                "priority", "HIGH",
                "status", "ACTIVE"
        ));
        long dreamId = postAndId("/api/dreams", token, Map.of(
                "visionAreaId", areaId,
                "title", "Build research skill",
                "description", "Improve research execution",
                "whyImportant", "Supports long term work",
                "successDefinition", "Concept note reviewed",
                "dreamType", "LONG_TERM",
                "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(),
                "status", "ACTIVE"
        ));
        long goalId = postAndId("/api/goals", token, Map.of(
                "dreamId", dreamId,
                "title", "Prepare concept note",
                "description", "Write a complete concept note",
                "successCriteria", "Mentor approves topic",
                "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(2).toString(),
                "status", "NOT_STARTED"
        ));
        long stepId = postAndId("/api/steps", token, Map.of(
                "goalId", goalId,
                "title", "Select topic",
                "description", "Choose a focused topic",
                "sequenceNumber", 1,
                "complex", true,
                "priority", "HIGH",
                "targetDate", LocalDate.now().plusWeeks(2).toString(),
                "status", "NOT_STARTED"
        ));

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stepId", stepId,
                                "title", "Ask mentor for topic feedback",
                                "description", "Request a short review",
                                "owner", "Test User",
                                "priority", "HIGH",
                                "dueDate", LocalDate.now().plusDays(7).toString(),
                                "status", "BLOCKED",
                                "progressPercent", 10,
                                "nextAction", "Prepare topic options"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Blocked tasks must include a blocker reason."));
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Flow Test User",
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
