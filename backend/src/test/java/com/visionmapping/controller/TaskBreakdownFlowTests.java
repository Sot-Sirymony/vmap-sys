package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.support.PlanFixture;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V17 phase 4 (BRD 17.0.0): a stuck task is broken into smaller tasks under
 * the same step, leaving Blocked keeps a "What changed?" note, and each move
 * into Blocked is counted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskBreakdownFlowTests {

    private static final String DUE = LocalDate.now().plusMonths(2).toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void blc12_aBlockedTaskSplitIntoThreeWithReplaceKeepsEverything() throws Exception {
        String token = register("split-replace");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long first = task(token, step, Map.of("title", "First"));
        long stuck = task(token, step, Map.of("title", "Write the whole report", "status", "BLOCKED",
                "blockerReason", "Too big to start", "owner", "Dara", "priority", "CRITICAL"));
        long last = task(token, step, Map.of("title", "Last"));

        JsonNode result = read(send(post("/api/tasks/" + stuck + "/split"), token, Map.of("originalOutcome", "REPLACE",
                "rows", List.of(row("Outline the sections"), row("Draft the summary"), row("Draft the findings"))))
                .andExpect(status().isCreated()));

        JsonNode created = result.get("created");
        assertThat(created).hasSize(3);
        List<String> codes = new ArrayList<>();
        for (JsonNode task : created) {
            assertThat(task.get("stepId").asLong()).isEqualTo(step);
            assertThat(task.get("status").asText()).isEqualTo("NOT_STARTED");
            // Owner and priority come from the original when a row leaves them out.
            assertThat(task.get("owner").asText()).isEqualTo("Dara");
            assertThat(task.get("priority").asText()).isEqualTo("CRITICAL");
            codes.add(task.get("code").asText());
        }
        JsonNode original = result.get("original");
        assertThat(original.get("archived").asBoolean()).isTrue();
        assertThat(original.get("description").asText()).contains("Split into " + codes.get(0) + " to " + codes.get(2));

        // Right after the original in the step's order: first, (original), three new, last.
        assertThat(sortOrder(token, first)).isLessThan(sortOrder(token, stuck));
        assertThat(sortOrder(token, created.get(0).get("id").asLong())).isEqualTo(sortOrder(token, stuck) + 1);
        assertThat(sortOrder(token, created.get(2).get("id").asLong())).isLessThan(sortOrder(token, last));

        // Nothing deleted: the original can still be read.
        mockMvc.perform(get("/api/tasks/" + stuck).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void keepLeavesTheOriginalAsItIs() throws Exception {
        String token = register("split-keep");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long parent = task(token, step, Map.of("title", "Plan the event"));

        JsonNode result = read(send(post("/api/tasks/" + parent + "/split"), token, Map.of("originalOutcome", "KEEP",
                "rows", List.of(row("Pick a date"), row("Book the room")))).andExpect(status().isCreated()));

        assertThat(result.get("original").get("archived").asBoolean()).isFalse();
        assertThat(result.get("original").get("status").asText()).isEqualTo("NOT_STARTED");
    }

    @Test
    void aSplitNeedsTwoToFiveRows() throws Exception {
        String token = register("split-rows");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long parent = task(token, step, Map.of("title", "Plan the event"));

        send(post("/api/tasks/" + parent + "/split"), token, Map.of("originalOutcome", "KEEP", "rows", List.of(row("Only one"))))
                .andExpect(status().isBadRequest());
        send(post("/api/tasks/" + parent + "/split"), token, Map.of("originalOutcome", "KEEP", "rows",
                List.of(row("1"), row("2"), row("3"), row("4"), row("5"), row("6")))).andExpect(status().isBadRequest());
    }

    @Test
    void blc13_leavingBlockedSavesTheChangeNoteInTheProgressLog() throws Exception {
        String token = register("change-note");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long stuck = task(token, step, Map.of("title", "Analyse the data", "status", "BLOCKED", "blockerReason", "No data yet"));

        send(patch("/api/tasks/" + stuck + "/status"), token, Map.of("status", "IN_PROGRESS", "manualOverride", false,
                "changeNote", "Got the data from Dara")).andExpect(status().isOk());

        assertThat(notesFor(token, stuck)).contains("Got the data from Dara");
    }

    @Test
    void aChangeNoteIsKeptOnlyWhenTheTaskLeavesBlocked() throws Exception {
        String token = register("change-note-ignored");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long open = task(token, step, Map.of("title", "Call the bank"));

        send(patch("/api/tasks/" + open + "/status"), token, Map.of("status", "IN_PROGRESS", "manualOverride", false,
                "changeNote", "Not from Blocked")).andExpect(status().isOk());

        assertThat(notesFor(token, open)).doesNotContain("Not from Blocked");
    }

    @Test
    void blFr28_eachMoveIntoBlockedIsCounted() throws Exception {
        String token = register("block-count");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long taskId = task(token, step, Map.of("title", "Find a venue", "status", "BLOCKED", "blockerReason", "No budget"));

        assertThat(blockedCount(token, taskId)).isEqualTo(1);
        // Staying Blocked through an edit is not a new block.
        send(put("/api/tasks/" + taskId), token, taskBody(step, Map.of("title", "Find a venue", "status", "BLOCKED",
                "blockerReason", "Still no budget"))).andExpect(status().isOk());
        assertThat(blockedCount(token, taskId)).isEqualTo(1);

        send(patch("/api/tasks/" + taskId + "/status"), token, Map.of("status", "IN_PROGRESS", "manualOverride", false))
                .andExpect(status().isOk());
        send(patch("/api/tasks/" + taskId + "/status"), token, Map.of("status", "BLOCKED", "manualOverride", false))
                .andExpect(status().isOk());
        send(patch("/api/tasks/" + taskId + "/status"), token, Map.of("status", "WAITING", "manualOverride", false))
                .andExpect(status().isOk());
        send(patch("/api/tasks/" + taskId + "/status"), token, Map.of("status", "BLOCKED", "manualOverride", false))
                .andExpect(status().isOk());

        assertThat(blockedCount(token, taskId)).isEqualTo(3);
    }

    @Test
    void splitRowsFollowTheTasksPageOrderAndKeepTheirOwnOwnerAndPriority() throws Exception {
        String token = register("split-list-order");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long first = task(token, step, Map.of("title", "First"));
        long parent = task(token, step, Map.of("title", "Plan the event"));
        long last = task(token, step, Map.of("title", "Last"));
        send(patch("/api/tasks/list-order"), token, Map.of("orderedIds", List.of(first, parent, last)))
                .andExpect(status().isNoContent());

        JsonNode result = read(send(post("/api/tasks/" + parent + "/split"), token, Map.of("originalOutcome", "KEEP", "rows",
                List.of(Map.of("title", "Pick a date", "dueDate", DUE, "owner", "Sok", "priority", "LOW"), row("Book the room"))))
                .andExpect(status().isCreated()));

        JsonNode pick = result.get("created").get(0);
        assertThat(pick.get("owner").asText()).isEqualTo("Sok");
        assertThat(pick.get("priority").asText()).isEqualTo("LOW");
        assertThat(pick.get("listOrder").asInt()).isEqualTo(2);
        assertThat(result.get("created").get(1).get("listOrder").asInt()).isEqualTo(3);
        assertThat(taskField(token, last, "listOrder").asInt()).isEqualTo(4);
    }

    @Test
    void anArchivedTaskIsNotSplit() throws Exception {
        String token = register("split-archived");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long parent = task(token, step, Map.of("title", "Old task"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/tasks/" + parent)
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        send(post("/api/tasks/" + parent + "/split"), token, Map.of("originalOutcome", "KEEP",
                "rows", List.of(row("One"), row("Two")))).andExpect(status().isBadRequest());
    }

    @Test
    void theTaskFormKeepsAChangeNoteWhenTheTaskLeavesBlocked() throws Exception {
        String token = register("change-note-form");
        long step = PlanFixture.step(mockMvc, objectMapper, token);
        long stuck = task(token, step, Map.of("title", "Book a venue", "status", "BLOCKED", "blockerReason", "No reply"));

        send(put("/api/tasks/" + stuck), token, taskBody(step, Map.of("title", "Book a venue", "status", "IN_PROGRESS",
                "progressPercent", 20, "changeNote", "Called them instead of emailing"))).andExpect(status().isOk());

        assertThat(notesFor(token, stuck)).contains("Called them instead of emailing");
    }

    private int blockedCount(String token, long taskId) throws Exception {
        return taskField(token, taskId, "blockedCount").asInt();
    }

    private int sortOrder(String token, long taskId) throws Exception {
        return taskField(token, taskId, "sortOrder").asInt();
    }

    private JsonNode taskField(String token, long taskId, String field) throws Exception {
        return read(mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).get(field);
    }

    private List<String> notesFor(String token, long taskId) throws Exception {
        JsonNode logs = read(mockMvc.perform(get("/api/progress-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        List<String> notes = new ArrayList<>();
        logs.forEach(log -> {
            if (log.get("relatedTaskId").asLong() == taskId && !log.get("note").isNull()) {
                notes.add(log.get("note").asText());
            }
        });
        return notes;
    }

    private long task(String token, long step, Map<String, Object> extra) throws Exception {
        return PlanFixture.createdId(mockMvc, objectMapper, token, "/api/tasks", taskBody(step, extra));
    }

    private static Map<String, Object> taskBody(long step, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task", "owner", "Me", "priority", "HIGH",
                "dueDate", DUE, "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        return body;
    }

    private static Map<String, Object> row(String title) {
        return Map.of("title", title, "dueDate", LocalDate.now().plusDays(10).toString());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        MvcResult done = result.andReturn();
        return objectMapper.readTree(done.getResponse().getContentAsString());
    }

    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V17 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
