package com.visionmapping.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.TaskItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * The dashboard trend only needs log rows inside its window plus each task's
 * last row from before it (the value the trend carries forward from). Anything
 * older can never be "the latest as of" a day in the window, so the query must
 * leave it out, while never dropping a carried-forward value.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgressLogTrendWindowQueryTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProgressLogRepository progressLogRepository;
    @Autowired private TaskItemRepository taskItemRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void loadsWindowRowsAndEachTasksLastRowBeforeTheWindowOnly() throws Exception {
        String token = registerAndToken();
        long stepId = createStep(token);
        long taskA = createTask(token, stepId, "A");
        long taskB = createTask(token, stepId, "B");
        Instant now = Instant.now();
        Instant windowStart = now.minus(83, ChronoUnit.DAYS);

        List<Long> loaded = new TransactionTemplate(transactionManager).execute(tx -> {
            TaskItem a = taskItemRepository.findById(taskA).orElseThrow();
            TaskItem b = taskItemRepository.findById(taskB).orElseThrow();
            log(a, 10, now.minus(200, ChronoUnit.DAYS));   // superseded pre-window history: dropped
            long aCarried = log(a, 40, now.minus(100, ChronoUnit.DAYS)).getId(); // last before window: kept
            long aInside1 = log(a, 60, now.minus(10, ChronoUnit.DAYS)).getId();
            long aInside2 = log(a, 80, now.minus(1, ChronoUnit.DAYS)).getId();
            log(b, 5, now.minus(300, ChronoUnit.DAYS));    // dropped
            long bCarried = log(b, 25, now.minus(95, ChronoUnit.DAYS)).getId(); // only-before-window task: kept
            Long userId = a.getUser().getId();
            List<Long> expected = List.of(aCarried, aInside1, aInside2, bCarried);
            List<Long> ids = progressLogRepository.findTrendWindow(userId, windowStart).stream()
                    .map(ProgressLog::getId).toList();
            assertThat(ids).containsExactlyInAnyOrderElementsOf(expected);
            return ids;
        });

        assertThat(loaded).hasSize(4);
    }

    private ProgressLog log(TaskItem task, int after, Instant at) {
        return progressLogRepository.save(ProgressLog.builder()
                .user(task.getUser()).relatedTask(task)
                .progressPercentBefore(BigDecimal.ZERO).progressPercentAfter(BigDecimal.valueOf(after))
                .loggedAt(at).archived(false).build());
    }

    private String registerAndToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Window Test",
                                "email", "window-" + System.nanoTime() + "@example.com", "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createStep(String token) throws Exception {
        long area = createdId("/api/vision-areas", token, Map.of("name", "Area", "description", "d", "priority", "HIGH", "status", "ACTIVE"));
        long dream = createdId("/api/dreams", token, Map.of("visionAreaId", area, "title", "Dream", "description", "d",
                "whyImportant", "w", "successDefinition", "s", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP"));
        long goal = createdId("/api/goals", token, Map.of("dreamId", dream, "title", "Goal", "description", "d", "successCriteria", "s",
                "priority", "HIGH", "targetDate", LocalDate.now().plusMonths(2).toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"));
        return createdId("/api/steps", token, Map.of("goalId", goal, "title", "Step", "description", "d", "sequenceNumber", 1,
                "complex", false, "priority", "HIGH", "targetDate", LocalDate.now().plusWeeks(2).toString(), "status", "NOT_STARTED"));
    }

    private long createTask(String token, long stepId, String title) throws Exception {
        return createdId("/api/tasks", token, Map.of("stepId", stepId, "title", title, "description", "d", "owner", "Me",
                "priority", "MEDIUM", "dueDate", LocalDate.now().plusDays(5).toString(), "status", "NOT_STARTED", "progressPercent", 0));
    }

    private long createdId(String path, String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
