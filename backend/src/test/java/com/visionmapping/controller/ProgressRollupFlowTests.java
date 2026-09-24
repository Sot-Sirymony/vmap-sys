package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * The progress roll-up against a real database, rather than against mocks.
 *
 * <p>The roll-up is an aggregate query that runs immediately after the write it
 * reflects, so it is only correct if the pending write is visible to it. That is
 * what JPQL's flush-before-query gives us, and it is the one thing a mocked
 * repository cannot demonstrate — hence an end-to-end test for arithmetic that
 * looks trivial.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgressRollupFlowTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void completingOneOfTwoTasksAveragesIntoTheStepAndGoal() throws Exception {
        String token = registerAndToken();
        long goalId = createGoal(token);
        long stepId = createStep(token, goalId);
        long taskA = createTask(token, stepId, "First", 40);
        createTask(token, stepId, "Second", 60);

        // 40 and 60 to start with.
        assertProgress(token, "/api/steps/" + stepId, 50.0);
        assertProgress(token, "/api/goals/" + goalId, 50.0);

        // Completing the first forces it to 100, so the step is (100 + 60) / 2.
        // Reading 50.00 here would mean the aggregate ran before the write landed.
        mockMvc.perform(patch("/api/tasks/" + taskA + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk());

        assertProgress(token, "/api/steps/" + stepId, 80.0);
        assertProgress(token, "/api/goals/" + goalId, 80.0);
    }

    @Test
    void completingEveryTaskCompletesTheStepAndTheGoal() throws Exception {
        String token = registerAndToken();
        long goalId = createGoal(token);
        long stepId = createStep(token, goalId);
        long onlyTask = createTask(token, stepId, "Only", 0);

        mockMvc.perform(patch("/api/tasks/" + onlyTask + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/steps/" + stepId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/goals/" + goalId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    /** Compared as a number, since the JSON carries 50.0 where the column holds 50.00. */
    private void assertProgress(String token, String path, double expected) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(expected));
    }

    private long createGoal(String token) throws Exception {
        long area = createdId("/api/vision-areas", token, Map.of("name", "Area", "description", "d",
                "priority", "HIGH", "status", "ACTIVE"));
        long dream = createdId("/api/dreams", token, Map.of("visionAreaId", area, "title", "Dream", "description", "d",
                "whyImportant", "w", "successDefinition", "s", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP"));
        return createdId("/api/goals", token, Map.of("dreamId", dream, "title", "Goal", "description", "d",
                "successCriteria", "s", "priority", "HIGH", "targetDate", LocalDate.now().plusMonths(2).toString(),
                "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"));
    }

    private long createStep(String token, long goalId) throws Exception {
        return createdId("/api/steps", token, Map.of("goalId", goalId, "title", "Step", "description", "d",
                "sequenceNumber", 1, "complex", false, "priority", "HIGH",
                "targetDate", LocalDate.now().plusWeeks(2).toString(), "status", "NOT_STARTED"));
    }

    private long createTask(String token, long stepId, String title, int progress) throws Exception {
        return createdId("/api/tasks", token, Map.of("stepId", stepId, "title", title, "description", "d",
                "owner", "Me", "priority", "MEDIUM", "dueDate", LocalDate.now().plusDays(5).toString(),
                "status", "IN_PROGRESS", "progressPercent", progress));
    }

    private String registerAndToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Rollup Test",
                                "email", "rollup-" + System.nanoTime() + "@example.com", "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createdId(String path, String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
