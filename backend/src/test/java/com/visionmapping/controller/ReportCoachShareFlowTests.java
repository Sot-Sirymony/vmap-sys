package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * V19 RM-FR-50 (D-19-3): a participant shares a final report with their coach,
 * read-only, within the V9 D-6 rules, and unsharing, archiving or leaving the
 * cohort removes access at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportCoachShareFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rmFr50_aFinalReportSharedWithTheCoachIsReadOnlyAndUnsharingEndsAccessAtOnce() throws Exception {
        Cohort cohort = cohortWithCoach();
        String participantEmail = email("coach-share");
        String participant = register(participantEmail, cohort.code);
        long participantId = userId(participantEmail);
        PlanFixture.branch(mockMvc, objectMapper, participant);
        long id = created(send(post("/api/reports/saved"), participant, Map.of())).get("id").asLong();
        send(put("/api/reports/saved/" + id + "/commentary"), participant, Map.of("highlights", "- Venue booked", "nothingNeeded", false))
                .andExpect(status().isOk());

        JsonNode status = read(send(get("/api/reports/saved/" + id + "/coach-share"), participant, Map.of()).andExpect(status().isOk()));
        assertThat(status.get("available").asBoolean()).isTrue();
        assertThat(status.get("coachName").asText()).isEqualTo("Coach");
        assertThat(status.get("shared").asBoolean()).isFalse();
        // Drafts are never shared (D-19-3).
        send(put("/api/reports/saved/" + id + "/coach-share"), participant, Map.of()).andExpect(status().isConflict());

        send(post("/api/reports/saved/" + id + "/finalize"), participant, Map.of()).andExpect(status().isOk());
        JsonNode shared = read(send(put("/api/reports/saved/" + id + "/coach-share"), participant,
                Map.of("sections", List.of("COMMENTARY"))).andExpect(status().isOk()));
        assertThat(shared.get("shared").asBoolean()).isTrue();
        assertThat(shared.get("sections")).extracting(JsonNode::asText).containsExactly("COMMENTARY");

        String base = "/api/coach/participants/" + participantId + "/reports";
        JsonNode list = read(send(get(base), cohort.coachToken, Map.of()).andExpect(status().isOk()));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("reportId").asLong()).isEqualTo(id);
        assertThat(list.get(0).get("code").asText()).isEqualTo("R-001");
        JsonNode view = read(send(get(base + "/" + id), cohort.coachToken, Map.of()).andExpect(status().isOk()));
        assertThat(view.get("commentary").get("highlights").asText()).isEqualTo("- Venue booked");
        assertThat(view.get("report").get("goals")).as("goal health was not shared").isEmpty();
        assertThat(view.get("expiresAt").isNull()).isTrue();
        assertThat(view.get("rows").isNull()).isTrue();

        // Another cohort's coach, the participant and a stranger cannot read it; there is nothing a coach can change.
        Cohort other = cohortWithCoach();
        send(get(base), other.coachToken, Map.of()).andExpect(status().isNotFound());
        send(get(base + "/" + id), other.coachToken, Map.of()).andExpect(status().isNotFound());
        send(get(base + "/" + id), participant, Map.of()).andExpect(status().isForbidden());
        send(put("/api/reports/saved/" + id + "/coach-share"), cohort.coachToken, Map.of()).andExpect(status().isNotFound());

        JsonNode unshared = read(send(delete("/api/reports/saved/" + id + "/coach-share"), participant, Map.of()).andExpect(status().isOk()));
        assertThat(unshared.get("shared").asBoolean()).isFalse();
        send(get(base + "/" + id), cohort.coachToken, Map.of()).andExpect(status().isNotFound());
        assertThat(read(send(get(base), cohort.coachToken, Map.of()))).isEmpty();
    }

    @Test
    void rmFr50_rmFr51_archivingOrLeavingTheCohortStopsTheShareAndOnlyACohortMemberCanShare() throws Exception {
        String loner = register(email("coach-share-loner"), null);
        long lonerReport = finalReport(loner);
        JsonNode none = read(send(get("/api/reports/saved/" + lonerReport + "/coach-share"), loner, Map.of()).andExpect(status().isOk()));
        assertThat(none.get("available").asBoolean()).isFalse();
        assertThat(none.get("coachName").isNull()).isTrue();
        send(put("/api/reports/saved/" + lonerReport + "/coach-share"), loner, Map.of()).andExpect(status().isBadRequest());

        Cohort cohort = cohortWithCoach();
        String participantEmail = email("coach-share-archive");
        String participant = register(participantEmail, cohort.code);
        long participantId = userId(participantEmail);
        long id = finalReport(participant);
        send(put("/api/reports/saved/" + id + "/coach-share"), participant, Map.of()).andExpect(status().isOk());
        String base = "/api/coach/participants/" + participantId + "/reports/" + id;
        JsonNode view = read(send(get(base), cohort.coachToken, Map.of()).andExpect(status().isOk()));
        assertThat(view.get("sections")).extracting(JsonNode::asText).containsExactlyInAnyOrder("COMMENTARY", "GOAL_HEALTH", "CHANGES");

        send(patch("/api/reports/saved/" + id + "/archive"), participant, Map.of()).andExpect(status().isOk());
        send(get(base), cohort.coachToken, Map.of()).andExpect(status().isNotFound());
        send(patch("/api/reports/saved/" + id + "/restore"), participant, Map.of()).andExpect(status().isOk());
        // Restoring does not share it again.
        send(get(base), cohort.coachToken, Map.of()).andExpect(status().isNotFound());

        send(put("/api/reports/saved/" + id + "/coach-share"), participant, Map.of()).andExpect(status().isOk());
        send(post("/api/cohort/leave"), participant, Map.of()).andExpect(status().isOk());
        send(post("/api/cohort/join"), participant, Map.of("joinCode", cohort.code)).andExpect(status().isOk());
        send(get(base), cohort.coachToken, Map.of()).andExpect(status().isNotFound());
        assertThat(read(send(get("/api/reports/saved/" + id + "/coach-share"), participant, Map.of())).get("shared").asBoolean()).isFalse();
    }

    private long finalReport(String token) throws Exception {
        PlanFixture.branch(mockMvc, objectMapper, token);
        long id = created(send(post("/api/reports/saved"), token, Map.of())).get("id").asLong();
        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());
        return id;
    }

    private record Cohort(long id, String code, String coachToken) {
    }

    private Cohort cohortWithCoach() throws Exception {
        String adminEmail = email("coach-share-admin");
        String admin = register(adminEmail, null);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        String coachEmail = email("coach-share-coach");
        String coachToken = register(coachEmail, null);
        jdbcTemplate.update("update app_users set full_name = 'Coach' where email = ?", coachEmail);
        JsonNode created = read(send(post("/api/admin/cohorts"), admin, Map.of("name", "Cohort", "coachEmail", coachEmail)));
        return new Cohort(created.get("id").asLong(), created.get("joinCode").asText(), coachToken);
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private JsonNode created(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated()));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String email, String joinCode) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "V19 Coach Share", "email", email, "password", PASSWORD));
        if (joinCode != null) {
            body.put("joinCode", joinCode);
        }
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
