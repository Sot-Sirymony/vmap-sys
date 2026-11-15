package com.visionmapping.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Builds a small plan (area → dream → goal → step) through the API for flow
 * tests. V17 BL-FR-01 requires a new obstacle to sit on a step or task, so
 * tests that only care about other obstacle rules use {@link #step} to get one.
 */
public final class PlanFixture {

    private PlanFixture() {
    }

    /** The ids of one freshly created plan branch. */
    public record Branch(long areaId, long dreamId, long goalId, long stepId) {
    }

    public static Branch branch(MockMvc mockMvc, ObjectMapper objectMapper, String token) throws Exception {
        long area = createdId(mockMvc, objectMapper, token, "/api/vision-areas", Map.of("name", "Area", "description", "d",
                "priority", "HIGH", "status", "ACTIVE"));
        long dream = createdId(mockMvc, objectMapper, token, "/api/dreams", Map.of("visionAreaId", area, "title", "Fixture dream",
                "description", "d", "whyImportant", "w", "successDefinition", "s", "dreamType", "LONG_TERM",
                "priority", "HIGH", "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE",
                "scheduleMode", "BOTTOM_UP"));
        long goal = createdId(mockMvc, objectMapper, token, "/api/goals", Map.of("dreamId", dream, "title", "Fixture goal",
                "description", "d", "successCriteria", "s", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(5).toString(), "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP"));
        long step = createdId(mockMvc, objectMapper, token, "/api/steps", Map.of("owner", "Me", "goalId", goal,
                "title", "Fixture step", "description", "d", "sequenceNumber", 1, "complex", false, "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(4).toString(), "status", "NOT_STARTED"));
        return new Branch(area, dream, goal, step);
    }

    /** A step the given user owns. */
    public static long step(MockMvc mockMvc, ObjectMapper objectMapper, String token) throws Exception {
        return branch(mockMvc, objectMapper, token).stepId();
    }

    public static long createdId(MockMvc mockMvc, ObjectMapper objectMapper, String token, String path,
                                 Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
