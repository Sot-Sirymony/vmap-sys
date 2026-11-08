package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * BRD 13 EAC-03: status changes bind straight to the status enum, still accept
 * the spellings older clients send, and refuse an unknown value with HTTP 400
 * naming the allowed values.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusLabelFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private long dreamId;
    private long taskId;

    @BeforeEach
    void createPlan() throws Exception {
        token = registerAndToken();
        long areaId = postAndId("/api/vision-areas", Map.of(
                "name", "Research", "priority", "HIGH", "status", "ACTIVE"));
        dreamId = postAndId("/api/dreams", Map.of(
                "visionAreaId", areaId,
                "title", "Build research skill",
                "whyImportant", "Supports long term work",
                "successDefinition", "Concept note reviewed",
                "dreamType", "LONG_TERM",
                "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(),
                "status", "IDEA",
                "scheduleMode", "BOTTOM_UP"));
        long goalId = postAndId("/api/goals", Map.of(
                "dreamId", dreamId,
                "title", "Prepare concept note",
                "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(2).toString(),
                "status", "NOT_STARTED",
                "scheduleMode", "BOTTOM_UP"));
        long stepId = postAndId("/api/steps", Map.of(
                "owner", "Me", "goalId", goalId,
                "title", "Search literature",
                "sequenceNumber", 1,
                "complex", false,
                "priority", "HIGH",
                "targetDate", LocalDate.now().plusWeeks(2).toString(),
                "status", "NOT_STARTED"));
        taskId = postAndId("/api/tasks", Map.of(
                "stepId", stepId,
                "title", "Search PubMed",
                "owner", "Me",
                "priority", "HIGH",
                "dueDate", LocalDate.now().plusDays(7).toString(),
                "status", "NOT_STARTED",
                "progressPercent", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"In Progress", "in-progress", "IN_PROGRESS", " in progress "})
    void everyAcceptedSpellingGivesTheSameStatus(String label) throws Exception {
        patchStatus("/api/tasks/" + taskId + "/status", label)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void anUnknownStatusIsRefusedWithTheAllowedValues() throws Exception {
        patchStatus("/api/tasks/" + taskId + "/status", "Doing")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid status: Doing. Allowed values: NOT_STARTED, IN_PROGRESS, WAITING, BLOCKED, COMPLETED, PAUSED."));
    }

    @Test
    void aVeryLongUnknownStatusIsShortenedInTheMessage() throws Exception {
        patchStatus("/api/tasks/" + taskId + "/status", "x".repeat(500))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid status: " + "x".repeat(40) + "…. Allowed values: NOT_STARTED, IN_PROGRESS, WAITING, BLOCKED, COMPLETED, PAUSED."));
    }

    @Test
    void anEmptyStatusIsReportedAsMissing() throws Exception {
        patchStatus("/api/tasks/" + taskId + "/status", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status: must not be null"));
    }

    @Test
    void eachResourceBindsItsOwnStatusEnum() throws Exception {
        patchStatus("/api/dreams/" + dreamId + "/status", "paused")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
        patchStatus("/api/dreams/" + dreamId + "/status", "IN_PROGRESS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid status: IN_PROGRESS. Allowed values: IDEA, ACTIVE, PAUSED, COMPLETED, ARCHIVED."));
    }

    @Test
    void theBlockedRuleStillAppliesToStatusChanges() throws Exception {
        patchStatus("/api/tasks/" + taskId + "/status", "Blocked")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Blocked tasks must include a blocker reason."));
    }

    private ResultActions patchStatus(String path, String status) throws Exception {
        return mockMvc.perform(patch(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", status, "manualOverride", false))));
    }

    private String registerAndToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Status Label User",
                                "email", "status-label-" + System.nanoTime() + "@example.com",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long postAndId(String path, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
