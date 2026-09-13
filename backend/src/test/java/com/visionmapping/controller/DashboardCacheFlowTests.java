package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.TaskItemRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-30: the dashboard summary is cached per user (on the test profile's
 * in-memory cache), any service write evicts it (AC-1), and one user's cached
 * payload is never served to another (BR-20).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardCacheFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskItemRepository taskItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void dashboardIsServedFromCacheAndEvictedByServiceWrites() throws Exception {
        String token = registerAndToken("cache-evict");
        long taskId = createTaskChain(token, "cache-evict");

        // First read fills the cache.
        dashboard(token)
                .andExpect(jsonPath("$.activeTasks").value(1))
                .andExpect(jsonPath("$.completedTasks").value(0));

        // Complete the task behind the cache's back (plain repository write —
        // no service, so no eviction). A stale answer here proves the second
        // read came from the cache, not the database.
        completeDirectlyInDatabase(taskId);
        dashboard(token)
                .andExpect(jsonPath("$.completedTasks").value(0));

        // Any service write evicts: the next read recomputes and now sees
        // both the new task and the completion done behind the cache's back.
        postTask(token, "Second task", taskId);
        dashboard(token)
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.activeTasks").value(1));
    }

    @Test
    void cachedDashboardIsNeverSharedAcrossUsers() throws Exception {
        String tokenA = registerAndToken("cache-user-a");
        postAndId("/api/vision-areas", tokenA, Map.of(
                "name", "Career",
                "description", "Career development",
                "priority", "HIGH",
                "status", "ACTIVE"
        ));

        // A's read caches a payload with one vision area.
        dashboard(tokenA).andExpect(jsonPath("$.totalVisionAreas").value(1));

        // B calls the same endpoint with the same (empty) parameters; a key
        // without the user id would return A's cached payload.
        String tokenB = registerAndToken("cache-user-b");
        dashboard(tokenB).andExpect(jsonPath("$.totalVisionAreas").value(0));

        // And B's read did not overwrite A's entry.
        dashboard(tokenA).andExpect(jsonPath("$.totalVisionAreas").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions dashboard(String token) throws Exception {
        return mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void completeDirectlyInDatabase(long taskId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                taskItemRepository.findById(taskId).orElseThrow().setStatus(WorkStatus.COMPLETED));
    }

    /** Area → dream → goal → step → one NOT_STARTED task; returns the task id. */
    private long createTaskChain(String token, String prefix) throws Exception {
        long areaId = postAndId("/api/vision-areas", token, Map.of(
                "name", "Research " + prefix,
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
                "status", "ACTIVE",
                "scheduleMode", "BOTTOM_UP"
        ));
        long goalId = postAndId("/api/goals", token, Map.of(
                "dreamId", dreamId,
                "title", "Prepare concept note",
                "description", "Write a complete concept note",
                "successCriteria", "Mentor approves topic",
                "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(2).toString(),
                "status", "NOT_STARTED",
                "scheduleMode", "BOTTOM_UP"
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
        return postAndId("/api/tasks", token, Map.of(
                "stepId", stepId,
                "title", "Search literature",
                "description", "Collect starting articles",
                "owner", "Test User",
                "priority", "HIGH",
                "dueDate", LocalDate.now().plusDays(7).toString(),
                "status", "NOT_STARTED",
                "progressPercent", 0
        ));
    }

    private void postTask(String token, String title, long siblingTaskId) throws Exception {
        long stepId = taskItemRepository.findById(siblingTaskId).orElseThrow().getStep().getId();
        postAndId("/api/tasks", token, Map.of(
                "stepId", stepId,
                "title", title,
                "description", "Follow-up work",
                "owner", "Test User",
                "priority", "MEDIUM",
                "dueDate", LocalDate.now().plusDays(10).toString(),
                "status", "NOT_STARTED",
                "progressPercent", 0
        ));
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Cache Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
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
}
