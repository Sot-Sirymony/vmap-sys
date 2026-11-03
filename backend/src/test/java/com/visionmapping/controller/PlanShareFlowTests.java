package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V9 Phase 2 (v0.1 FR-10): read-only plan links for a partner or mentor. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanShareFlowTests {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aPartnerSeesOnlyTheSharedDreamsPlanWithoutSigningIn() throws Exception {
        String owner = register(email("owner"), "Sokha Chan");
        long dreamId = dream(owner, "Open a bakery");
        long goalId = goal(owner, dreamId);
        step(owner, goalId);
        dream(owner, "A private dream");
        long partnerId = read(send(post("/api/partners"), owner, Map.of("name", "Dara Mentor", "role", "Advisor",
                "supportType", "MENTOR", "status", "ACTIVE", "email", "dara@example.com"))).get("id").asLong();

        JsonNode created = read(send(post("/api/plan-shares"), owner,
                Map.of("dreamId", dreamId, "partnerId", partnerId, "message", "Could you check my first steps?"))
                .andExpect(status().isCreated()));
        String token = created.get("token").asText();
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(created.get("open").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select token_hash from plan_shares where id = ?", String.class,
                created.get("id").asLong())).isNotEqualTo(token).hasSize(64);

        mockMvc.perform(get("/api/shared-plans/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerFirstName").value("Sokha"))
                .andExpect(jsonPath("$.partnerName").value("Dara Mentor"))
                .andExpect(jsonPath("$.message").value("Could you check my first steps?"))
                .andExpect(jsonPath("$.dreamTitle").value("Open a bakery"))
                .andExpect(jsonPath("$.goals.length()").value(1))
                .andExpect(jsonPath("$.goals[0].steps[0].title").value("Write the menu"))
                // Data minimisation: no owner, no email, nothing from other dreams.
                .andExpect(jsonPath("$.goals[0].steps[0].owner").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.whyImportant").doesNotExist());

        mockMvc.perform(get("/api/plan-shares?dreamId=" + dreamId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].viewCount").value(1))
                .andExpect(jsonPath("$[0].partnerName").value("Dara Mentor"));
    }

    @Test
    void nobodyElseCanShareOrStopSomeoneElsesPlan() throws Exception {
        String owner = register(email("owner"), "Owner");
        String other = register(email("other"), "Other");
        long dreamId = dream(owner, "Mine");
        long shareId = read(send(post("/api/plan-shares"), owner, Map.of("dreamId", dreamId))).get("id").asLong();

        send(post("/api/plan-shares"), other, Map.of("dreamId", dreamId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/plan-shares/" + shareId + "/revoke").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/plan-shares?dreamId=" + dreamId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/plan-shares").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.length()").value(0));
        // Managing shares needs an account; only opening one does not.
        mockMvc.perform(get("/api/plan-shares")).andExpect(status().isUnauthorized());
    }

    @Test
    void aStoppedExpiredOrArchivedLinkAnswersTheSameNotFound() throws Exception {
        String owner = register(email("owner"), "Owner");
        long dreamId = dream(owner, "Stops");

        JsonNode stopped = read(send(post("/api/plan-shares"), owner, Map.of("dreamId", dreamId)));
        mockMvc.perform(post("/api/plan-shares/" + stopped.get("id").asLong() + "/revoke").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.open").value(false));
        mockMvc.perform(get("/api/shared-plans/" + stopped.get("token").asText())).andExpect(status().isNotFound());

        JsonNode expired = read(send(post("/api/plan-shares"), owner, Map.of("dreamId", dreamId, "expiresInDays", 7)));
        jdbcTemplate.update("update plan_shares set expires_at = dateadd('DAY', -1, current_timestamp) where id = ?",
                expired.get("id").asLong());
        mockMvc.perform(get("/api/shared-plans/" + expired.get("token").asText())).andExpect(status().isNotFound());

        JsonNode archived = read(send(post("/api/plan-shares"), owner, Map.of("dreamId", dreamId)));
        mockMvc.perform(delete("/api/dreams/" + dreamId).header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/shared-plans/" + archived.get("token").asText())).andExpect(status().isNotFound());
        send(post("/api/plan-shares"), owner, Map.of("dreamId", dreamId)).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/shared-plans/not-a-real-token")).andExpect(status().isNotFound());
        send(post("/api/plan-shares"), owner, Map.of("dreamId", dreamId, "expiresInDays", 400)).andExpect(status().isBadRequest());
    }

    @Test
    void permanentlyDeletingTheDreamOrThePartnerKeepsEverythingConsistent() throws Exception {
        String owner = register(email("owner"), "Owner");
        long dreamId = dream(owner, "Goes away");
        long partnerId = read(send(post("/api/partners"), owner, Map.of("name", "Label only", "role", "Friend",
                "supportType", "OTHER", "status", "ACTIVE"))).get("id").asLong();
        send(post("/api/plan-shares"), owner, Map.of("dreamId", dreamId, "partnerId", partnerId)).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/partners/" + partnerId).header("Authorization", "Bearer " + owner));
        mockMvc.perform(delete("/api/partners/" + partnerId + "/permanent").header("Authorization", "Bearer " + owner));
        mockMvc.perform(delete("/api/dreams/" + dreamId).header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/dreams/" + dreamId + "/permanent").header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select count(*) from plan_shares where dream_id = ?", Integer.class, dreamId)).isZero();
    }

    private long dream(String token, String title) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE"))).get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", title, "whyImportant", "Private reason",
                "successDefinition", "It is done", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE",
                "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long goal(String token, long dreamId) throws Exception {
        return read(send(post("/api/goals"), token, Map.of("dreamId", dreamId, "title", "Plan the shop", "description", "d",
                "successCriteria", "s", "priority", "HIGH", "targetDate", LocalDate.now().plusMonths(2).toString(),
                "status", "IN_PROGRESS", "scheduleMode", "BOTTOM_UP")).andExpect(status().isCreated())).get("id").asLong();
    }

    private void step(String token, long goalId) throws Exception {
        send(post("/api/steps"), token, Map.of("owner", "Sokha", "goalId", goalId, "title", "Write the menu", "description", "d",
                "sequenceNumber", 1, "complex", false, "priority", "HIGH",
                "targetDate", LocalDate.now().plusWeeks(2).toString(), "status", "NOT_STARTED")).andExpect(status().isCreated());
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String email, String fullName) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", fullName, "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
