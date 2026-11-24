package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V29 (BRD 29): an optional reason saved with archived dreams, goals, steps and tasks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveReasonFlowTests {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void arc01_aGoalsReasonGoesToWhatTheCascadeNewlyArchives() throws Exception {
        String user = register("reason-goal");
        long goalId = goal(user, dream(user));
        long stepId = step(user, goalId);
        long openTask = task(user, stepId, "Draft it");
        long earlierTask = task(user, stepId, "Old idea");
        archive(user, "/api/tasks/" + earlierTask, "Not needed any more").andExpect(status().isNoContent());

        archive(user, "/api/goals/" + goalId, "  Replaced by a new goal  ").andExpect(status().isNoContent());

        assertThat(field(user, "/api/goals/" + goalId, "archiveReason")).isEqualTo("Replaced by a new goal");
        assertThat(field(user, "/api/goals/" + goalId, "archived")).isEqualTo("true");
        assertThat(field(user, "/api/steps/" + stepId, "archiveReason")).isEqualTo("Replaced by a new goal");
        assertThat(field(user, "/api/tasks/" + openTask, "archiveReason")).isEqualTo("Replaced by a new goal");
        // BR-29-3: an item archived earlier keeps its own reason.
        assertThat(field(user, "/api/tasks/" + earlierTask, "archiveReason")).isEqualTo("Not needed any more");
    }

    @Test
    void aDreamsReasonReachesItsGoals() throws Exception {
        String user = register("reason-dream");
        long dreamId = dream(user);
        long goalId = goal(user, dreamId);

        archive(user, "/api/dreams/" + dreamId, "Done another way").andExpect(status().isNoContent());

        assertThat(field(user, "/api/dreams/" + dreamId, "archiveReason")).isEqualTo("Done another way");
        assertThat(field(user, "/api/goals/" + goalId, "archiveReason")).isEqualTo("Done another way");
    }

    @Test
    void arc02_noReasonArchivesAsBefore() throws Exception {
        String user = register("reason-none");
        long stepId = step(user, goal(user, dream(user)));
        long blank = task(user, stepId, "Blank reason");
        long noBody = task(user, stepId, "No body");
        long oldWay = task(user, stepId, "Old endpoint");

        archive(user, "/api/tasks/" + blank, "   ").andExpect(status().isNoContent());
        mockMvc.perform(post("/api/tasks/" + noBody + "/archive").header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/tasks/" + oldWay).header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());

        for (long id : new long[] {blank, noBody, oldWay}) {
            assertThat(field(user, "/api/tasks/" + id, "archived")).isEqualTo("true");
            assertThat(field(user, "/api/tasks/" + id, "archiveReason")).isNull();
        }
    }

    @Test
    void arc03_restoringClearsTheReasonOnTheItemAndTheParentsItBringsBack() throws Exception {
        String user = register("reason-restore");
        long goalId = goal(user, dream(user));
        long stepId = step(user, goalId);
        long taskId = task(user, stepId, "Draft it");
        archive(user, "/api/goals/" + goalId, "Paused for now").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/tasks/" + taskId + "/restore").header("Authorization", "Bearer " + user))
                .andExpect(status().is2xxSuccessful());

        assertThat(field(user, "/api/tasks/" + taskId, "archiveReason")).isNull();
        assertThat(field(user, "/api/steps/" + stepId, "archiveReason")).isNull();
        assertThat(field(user, "/api/goals/" + goalId, "archiveReason")).isNull();
        assertThat(field(user, "/api/goals/" + goalId, "archived")).isEqualTo("false");
    }

    @Test
    void archivingAgainKeepsTheFirstReason() throws Exception {
        String user = register("reason-again");
        long stepId = step(user, goal(user, dream(user)));
        archive(user, "/api/steps/" + stepId, "First reason").andExpect(status().isNoContent());
        archive(user, "/api/steps/" + stepId, "Second reason").andExpect(status().isNoContent());
        assertThat(field(user, "/api/steps/" + stepId, "archiveReason")).isEqualTo("First reason");
    }

    @Test
    void arc07_aLongReasonIsRefusedAndAnotherUsersItemIsNotFound() throws Exception {
        String user = register("reason-rules");
        long taskId = task(user, step(user, goal(user, dream(user))), "Draft it");
        archive(user, "/api/tasks/" + taskId, "x".repeat(1001)).andExpect(status().isBadRequest());
        assertThat(field(user, "/api/tasks/" + taskId, "archived")).isEqualTo("false");

        String other = register("reason-other");
        archive(other, "/api/tasks/" + taskId, "Not mine").andExpect(status().isNotFound());
    }

    private ResultActions archive(String token, String path, String reason) throws Exception {
        return send(post(path + "/archive"), token, Map.of("reason", reason));
    }

    private String field(String token, String path, String name) throws Exception {
        JsonNode node = read(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()))
                .get(name);
        return node == null || node.isNull() ? null : node.asText();
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Work", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Lead a team", "whyImportant", "Growth",
                "successDefinition", "A team of five", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "HIGH",
                "status", "ACTIVE", "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long goal(String token, long dreamId) throws Exception {
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Hire two people", "description", "d",
                "successCriteria", "s", "priority", "HIGH", "targetDate", TODAY.plusMonths(4).toString(),
                "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP")).andExpect(status().isCreated())).get("id").asLong();
    }

    private long step(String token, long goalId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("owner", "Me", "goalId", goalId, "title", "Write the job post",
                "description", "d", "sequenceNumber", 1, "complex", false, "priority", "HIGH",
                "targetDate", TODAY.plusMonths(2).toString(), "status", "IN_PROGRESS"));
        return read(send(post("/api/steps"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long task(String token, long stepId, String title) throws Exception {
        return read(send(post("/api/tasks"), token, Map.of("stepId", stepId, "title", title, "owner", "Me", "priority", "HIGH",
                "dueDate", TODAY.plusMonths(1).toString(), "status", "NOT_STARTED", "progressPercent", 0))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String prefix) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Archive Tester",
                                "email", prefix + "-" + System.nanoTime() + "@example.com", "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
