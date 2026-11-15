package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V18 phase 1 (BRD 18.0.0): the Management Report's Summary follows the
 * BRD's definitions (overdue, blocked items, goal health, next action), its
 * filter, and the participant's own data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementReportFlowTests {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theSummaryCountsTasksBlockersAndGoalHealthForEveryDream() throws Exception {
        String token = register("report-all");
        Branch stuck = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch done = PlanFixture.branch(mockMvc, objectMapper, token);
        long late = task(token, stuck.stepId(), Map.of("title", "Book the room", "priority", "HIGH",
                "dueDate", TODAY.minusDays(3).toString(), "nextAction", "Call the hall manager"));
        task(token, stuck.stepId(), Map.of("title", "Print forms", "status", "BLOCKED", "blockerReason", "Printer broken"));
        // Created last: a Completed first task would complete its step and goal through the roll-up (rule 11).
        task(token, stuck.stepId(), Map.of("title", "Draft survey", "status", "COMPLETED", "progressPercent", 100));
        long critical = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No budget",
                "obstacleType", "MONEY", "severity", "CRITICAL", "status", "OPEN", "relatedStepId", stuck.stepId()));
        jdbcTemplate.update("update goals set status = 'COMPLETED' where id = ?", done.goalId());

        JsonNode report = summary(token, "");

        assertThat(report.get("scope").get("kind").asText()).isEqualTo("ALL");
        assertThat(report.get("asOf").asText()).isEqualTo(TODAY.toString());
        JsonNode byStatus = report.get("tasksByStatus");
        assertThat(byStatus.get("COMPLETED").asLong()).isEqualTo(1);
        assertThat(byStatus.get("BLOCKED").asLong()).isEqualTo(1);
        assertThat(byStatus.get("NOT_STARTED").asLong()).isEqualTo(1);
        assertThat(byStatus.get("PAUSED").asLong()).isZero();
        JsonNode headline = report.get("headline");
        assertThat(headline.get("totalTasks").asLong()).isEqualTo(3);
        assertThat(headline.get("overdueTasks").asLong()).isEqualTo(1);
        assertThat(headline.get("blockedTasks").asLong()).isEqualTo(1);
        assertThat(headline.get("openBlockers").asLong()).isEqualTo(1);
        assertThat(headline.get("goalsByHealth").get("RED").asLong()).isEqualTo(1);
        assertThat(headline.get("goalsByHealth").get("DONE").asLong()).isEqualTo(1);
        assertThat(report.get("blockersByKind").get("MONEY").asLong()).isEqualTo(1);

        JsonNode high = priority(report, "HIGH");
        assertThat(high.get("overdue").asLong()).isEqualTo(1);

        // Red first; the overdue task's own next action is the goal's next action.
        JsonNode red = report.get("goals").get(0);
        assertThat(red.get("goalId").asLong()).isEqualTo(stuck.goalId());
        assertThat(red.get("health").asText()).isEqualTo("RED");
        assertThat(red.get("overdueTasks").asLong()).isEqualTo(1);
        assertThat(red.get("blockedItems").asLong()).isEqualTo(2);
        assertThat(red.get("nextAction").asText()).isEqualTo("Call the hall manager");
        assertThat(report.get("goals").get(1).get("health").asText()).isEqualTo("DONE");

        // Most urgent first: the Critical blocker, then the Blocked task, then the overdue High task.
        JsonNode attention = report.get("attention");
        assertThat(attention.get(0).get("kind").asText()).isEqualTo("CRITICAL_BLOCKER");
        assertThat(attention.get(0).get("itemId").asLong()).isEqualTo(critical);
        assertThat(attention.get(1).get("kind").asText()).isEqualTo("BLOCKED_TASK");
        assertThat(attention.get(2).get("kind").asText()).isEqualTo("OVERDUE_URGENT");
        assertThat(attention.get(2).get("itemId").asLong()).isEqualTo(late);
        assertThat(attention.get(2).get("context").asText()).isEqualTo("Fixture goal › Fixture step");
    }

    @Test
    void goalHealthIsAmberForAnOpenBlockerOrSlippingScheduleAndGreenOtherwise() throws Exception {
        String token = register("report-health");
        Branch amber = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch slipping = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch green = PlanFixture.branch(mockMvc, objectMapper, token);
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "Unsure about the method",
                "obstacleType", "KNOWLEDGE", "severity", "LOW", "status", "OPEN", "relatedStepId", amber.stepId()));
        // Created long ago, almost due, and still at 0%: far behind the time elapsed.
        jdbcTemplate.update("update goals set created_at = ? where id = ?",
                java.sql.Timestamp.valueOf(TODAY.minusYears(2).atStartOfDay()), slipping.goalId());

        JsonNode goals = summary(token, "?dreamId=" + amber.dreamId()).get("goals");
        assertThat(goals).hasSize(1);
        assertThat(goals.get(0).get("health").asText()).isEqualTo("AMBER");
        assertThat(summary(token, "?dreamId=" + slipping.dreamId()).get("goals").get(0).get("health").asText()).isEqualTo("AMBER");
        JsonNode greenReport = summary(token, "?dreamId=" + green.dreamId());
        assertThat(greenReport.get("goals").get(0).get("health").asText()).isEqualTo("GREEN");
        assertThat(greenReport.get("scope").get("kind").asText()).isEqualTo("DREAM");
        assertThat(greenReport.get("scope").get("label").asText()).isEqualTo("Fixture dream");
    }

    @Test
    void theAreaFilterAndUnplacedBlockersFollowTheScope() throws Exception {
        String token = register("report-area");
        Branch inArea = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch other = PlanFixture.branch(mockMvc, objectMapper, token);
        long obstacle = PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "Old worry",
                "obstacleType", "TIME", "severity", "MEDIUM", "status", "OPEN", "relatedStepId", inArea.stepId()));
        jdbcTemplate.update("update obstacles set related_step_id = null, related_goal_id = null, related_dream_id = ? where id = ?",
                inArea.dreamId(), obstacle);

        JsonNode area = summary(token, "?areaId=" + inArea.areaId());
        assertThat(area.get("scope").get("kind").asText()).isEqualTo("AREA");
        assertThat(area.get("goals")).hasSize(1);
        assertThat(area.get("unplacedObstacles").asLong()).isEqualTo(1);
        assertThat(summary(token, "?areaId=" + other.areaId()).get("unplacedObstacles").asLong()).isZero();
        assertThat(summary(token, "").get("goals")).hasSize(2);
    }

    @Test
    void aStepWithAKnowledgeGapAndNoHelpNeedsAttention() throws Exception {
        String token = register("report-gap");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        send(put("/api/steps/" + plan.stepId()), token, Map.of("owner", "Me", "goalId", plan.goalId(), "title", "Fixture step",
                "sequenceNumber", 1, "complex", false, "priority", "HIGH", "targetDate", TODAY.plusMonths(4).toString(),
                "status", "NOT_STARTED", "resources", "No statistics skills", "gapKind", "SKILL")).andExpect(status().isOk());

        JsonNode attention = summary(token, "").get("attention");
        assertThat(attention.get(0).get("kind").asText()).isEqualTo("GAP_NEEDS_HELP");
        assertThat(attention.get(0).get("itemId").asLong()).isEqualTo(plan.stepId());
    }

    @Test
    void mrFr14_theTrendFollowsProgressOnTasksInScopeOnly() throws Exception {
        String token = register("report-trend");
        Branch moving = PlanFixture.branch(mockMvc, objectMapper, token);
        Branch still = PlanFixture.branch(mockMvc, objectMapper, token);
        long taskId = task(token, moving.stepId(), Map.of("title", "Draft the survey"));
        task(token, still.stepId(), Map.of("title", "Untouched"));
        send(put("/api/tasks/" + taskId), token, Map.of("stepId", moving.stepId(), "title", "Draft the survey", "owner", "Me",
                "priority", "MEDIUM", "dueDate", TODAY.plusMonths(2).toString(), "status", "IN_PROGRESS", "progressPercent", 60))
                .andExpect(status().isOk());

        JsonNode trend = summary(token, "?dreamId=" + moving.dreamId()).get("trend");
        assertThat(trend).isNotEmpty();
        JsonNode latest = trend.get(trend.size() - 1);
        assertThat(latest.get("progress").decimalValue()).isEqualByComparingTo("60");
        assertThat(LocalDate.parse(latest.get("weekEnd").asText())).isAfterOrEqualTo(TODAY);
        // The other dream's task never moved, so it has no history to show.
        assertThat(summary(token, "?dreamId=" + still.dreamId()).get("trend")).isEmpty();
    }

    @Test
    void onlyOneScopeAndOnlyTheParticipantsOwnRecords() throws Exception {
        String owner = register("report-owner");
        String other = register("report-other");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, owner);

        mockMvc.perform(get("/api/reports/management?areaId=" + plan.areaId() + "&dreamId=" + plan.dreamId())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose either a vision area or a dream, not both."));
        mockMvc.perform(get("/api/reports/management?dreamId=" + plan.dreamId()).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/management?areaId=" + plan.areaId()).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        // With all dreams, another user sees an empty report, never the owner's plan.
        JsonNode empty = summary(other, "");
        assertThat(empty.get("goals")).isEmpty();
        assertThat(empty.get("headline").get("overallProgress").decimalValue()).isEqualByComparingTo("0");
        mockMvc.perform(get("/api/reports/management")).andExpect(status().isUnauthorized());
    }

    private JsonNode priority(JsonNode report, String priority) {
        for (JsonNode row : report.get("openTasksByPriority")) {
            if (row.get("priority").asText().equals(priority)) {
                return row;
            }
        }
        throw new AssertionError("no row for " + priority);
    }

    private JsonNode summary(String token, String query) throws Exception {
        return read(mockMvc.perform(get("/api/reports/management" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    private long task(String token, long step, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task", "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        return PlanFixture.createdId(mockMvc, objectMapper, token, "/api/tasks", body);
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
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V18 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
