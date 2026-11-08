package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * BRD 13 EAC-04: the account data export keeps the same JSON — the same
 * sections in the same order, and the same fields and values in every
 * hand-built row. Written against the export before it moved from maps to
 * records, so it proves the move changed nothing a reader can see.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountExportShapeTests {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 10, 1, 9, 30, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;
    private long userId;
    private long dreamId;
    private long goalId;
    private long secondGoalId;

    @BeforeEach
    void seed() throws Exception {
        String email = "export-shape-" + System.nanoTime() + "@example.com";
        token = register(email);
        userId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
        long areaId = id(send(post("/api/vision-areas"), Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        Map<String, Object> dream = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Own a home", "whyImportant", "It matters",
                "successDefinition", "It is done", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE", "moonshot", false));
        dream.put("scheduleMode", "BOTTOM_UP");
        dreamId = id(send(post("/api/dreams"), dream));
        goalId = goal();
        secondGoalId = goal();
        send(post("/api/habits?date=" + DAY), Map.of("name", "Read")).andExpect(status().isCreated());
        send(put("/api/chain-assessments"), Map.of("takenOn", DAY.toString(), "settlingScore", 1, "fearScore", 2,
                "criticismScore", 3, "visionScore", 4, "knowledgeScore", 5, "resourcesScore", 1)).andExpect(status().isOk());
        send(post("/api/milestones"), Map.of("dreamId", dreamId, "title", "Saved the deposit", "achievedOn", DAY.toString(),
                "note", "First big step")).andExpect(status().isCreated());
        jdbcTemplate.update("insert into goal_synergy_links (user_id, goal_id, related_goal_id, note, created_at, updated_at)"
                + " values (?, ?, ?, ?, ?, ?)", userId, goalId, secondGoalId, "Both need savings", AT, AT);
        jdbcTemplate.update("insert into module_completions (user_id, module_number, rows_json, completed_at, coach_rating,"
                + " coach_feedback, coach_rated_at, created_at, updated_at) values (?, 1, ?, ?, 4, ?, ?, ?, ?)",
                userId, "[]", AT, "Good work", AT, AT, AT);
        jdbcTemplate.update("insert into ai_consent_events (user_id, action, created_at, updated_at) values (?, 'GIVEN', ?, ?)",
                userId, AT, AT);
        jdbcTemplate.update("insert into ai_conversations (user_id, title, created_at, updated_at) values (?, 'Coach chat', ?, ?)",
                userId, AT, AT);
        Long conversationId = jdbcTemplate.queryForObject("select max(id) from ai_conversations where user_id = ?", Long.class, userId);
        jdbcTemplate.update("insert into ai_messages (user_id, conversation_id, role, content, created_at, updated_at)"
                + " values (?, ?, 'USER', 'Hello coach', ?, ?)", userId, conversationId, AT, AT);
        jdbcTemplate.update("insert into plan_shares (user_id, dream_id, token_hash, message, expires_at, view_count, created_at,"
                + " updated_at) values (?, ?, ?, 'Have a look', ?, 3, ?, ?)", userId, dreamId, "h" + System.nanoTime(), AT, AT, AT);
    }

    @Test
    void sectionsKeepTheirNamesAndOrder() throws Exception {
        JsonNode export = export();

        assertThat(fieldNames(export)).containsExactly(
                "account", "coachComments", "visionAreas", "dreams", "goals", "steps", "tasks", "partners",
                "communicationMessages", "reviews", "obstacles", "progressLogs", "gratitudeEntries", "fearEntries",
                "attitudeCheckIns", "habits", "assessments", "moduleCompletions", "milestones", "idealPartnerProfiles",
                "issueReports", "goalSynergyLinks", "aiConsentEvents", "aiMessages", "planShares");
        assertThat(fieldNames(export.get("account"))).containsExactly(
                "fullName", "email", "reminderEmail", "aiConsentAt", "cohort", "shareJournalsWithCoach", "createdAt");
        assertThat(export.toString()).doesNotContainIgnoringCase("passwordHash").doesNotContainIgnoringCase("tokenHash");
    }

    @Test
    void handBuiltRowsKeepTheirFieldsAndValues() throws Exception {
        JsonNode export = export();

        assertRow(export, "habits", Map.of("name", "\"Read\"", "archived", "false"),
                "archived", "checkedDates", "name");
        assertRow(export, "assessments", Map.of("takenOn", "\"2026-10-01\"", "settling", "1", "fear", "2", "criticism", "3",
                        "vision", "4", "knowledge", "5", "resources", "1"),
                "criticism", "fear", "knowledge", "resources", "settling", "takenOn", "vision");
        assertRow(export, "moduleCompletions", Map.of("moduleNumber", "1", "rowsJson", "\"[]\"", "coachRating", "4",
                        "coachFeedback", "\"Good work\""),
                "coachFeedback", "coachRatedAt", "coachRating", "completedAt", "moduleNumber", "rowsJson");
        assertRow(export, "milestones", Map.of("dreamId", String.valueOf(dreamId), "title", "\"Saved the deposit\"",
                        "achievedOn", "\"2026-10-01\"", "note", "\"First big step\"", "archived", "false"),
                "achievedOn", "archived", "dreamId", "note", "title");
        assertRow(export, "goalSynergyLinks", Map.of("goalId", String.valueOf(goalId), "relatedGoalId", String.valueOf(secondGoalId),
                        "note", "\"Both need savings\""),
                "goalId", "note", "relatedGoalId");
        assertRow(export, "aiConsentEvents", Map.of("action", "\"GIVEN\""), "action", "at");
        assertRow(export, "aiMessages", Map.of("conversationTitle", "\"Coach chat\"", "role", "\"USER\"", "content", "\"Hello coach\""),
                "content", "conversationId", "conversationTitle", "createdAt", "role");
        assertRow(export, "planShares", Map.of("dreamId", String.valueOf(dreamId), "partnerId", "null", "message", "\"Have a look\"",
                        "revokedAt", "null", "viewCount", "3"),
                "createdAt", "dreamId", "expiresAt", "message", "partnerId", "revokedAt", "viewCount");
        assertThat(export.get("aiConsentEvents").get(0).get("at").asText())
                .isEqualTo(export.get("aiMessages").get(0).get("createdAt").asText());
    }

    private void assertRow(JsonNode export, String section, Map<String, String> values, String... fields) {
        JsonNode rows = export.get(section);
        assertThat(rows.size()).as(section).isEqualTo(1);
        JsonNode row = rows.get(0);
        assertThat(new TreeSet<>(fieldNames(row))).as(section + " fields").containsExactly(fields);
        values.forEach((field, json) -> assertThat(row.get(field).toString()).as(section + "." + field).isEqualTo(json));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private JsonNode export() throws Exception {
        String body = mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private long goal() throws Exception {
        return id(send(post("/api/goals"), Map.of("dreamId", dreamId, "title", "A goal", "priority", "MEDIUM",
                "status", "NOT_STARTED", "moonshot", false, "scheduleMode", "BOTTOM_UP")));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long id(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Export Shape User", "email", email,
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
