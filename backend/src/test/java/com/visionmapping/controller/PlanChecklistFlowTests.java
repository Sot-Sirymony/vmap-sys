package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** V35 (D-35-6, BR-35-1, BR-35-2): the Inner blockers row of the plan checklist. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanChecklistFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final String INNER = "$.stages[?(@.key == 'INNER')]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theInnerBlockersRowCountsOnlyTheUsersActiveBlockersForThisDreamAndNeverGatesThePlan() throws Exception {
        String user = register("checklist");
        long dreamId = dream(user);
        long otherDreamId = dream(user);

        checklist(user, dreamId)
                .andExpect(jsonPath("$.stages[2].key").value("INNER"))
                .andExpect(jsonPath(INNER + ".done").value(false))
                .andExpect(jsonPath(INNER + ".detail").value("Name what might hold you back inside, with one short practice."));

        // Another dream's blocker and another user's blocker do not count.
        blocker(user, otherDreamId);
        String stranger = register("checklist-stranger");
        blocker(stranger, null);
        checklist(user, dreamId).andExpect(jsonPath(INNER + ".done").value(false));

        // A blocker for all dreams counts.
        long forAll = blocker(user, null);
        checklist(user, dreamId)
                .andExpect(jsonPath(INNER + ".done").value(true))
                .andExpect(jsonPath(INNER + ".detail").value("1 inner blocker named."))
                .andExpect(jsonPath("$.planReady").value(false));

        // Resolved and archived blockers do not count.
        send(patch("/api/v1/inner-blockers/" + forAll + "/status"), user, Map.of("status", "RESOLVED", "stopHabit", false))
                .andExpect(status().isOk());
        long forThis = blocker(user, dreamId);
        blocker(user, dreamId);
        checklist(user, dreamId).andExpect(jsonPath(INNER + ".detail").value("2 inner blockers named."));
        mockMvc.perform(post("/api/v1/inner-blockers/" + forThis + "/archive").header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());
        checklist(user, dreamId).andExpect(jsonPath(INNER + ".detail").value("1 inner blocker named."));
    }

    private ResultActions checklist(String token, long dreamId) throws Exception {
        return mockMvc.perform(get("/api/v1/plan-status/" + dreamId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long blocker(String token, Long dreamId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("blocker", "Inconsistency", "practice", "Never miss twice"));
        body.put("dreamId", dreamId);
        return read(send(post("/api/v1/inner-blockers"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long dream(String token) throws Exception {
        long areaId = read(send(post("/api/v1/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Learn to code", "whyImportant", "It matters",
                "successDefinition", "A job offer", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE",
                "moonshot", false));
        body.put("scheduleMode", "BOTTOM_UP");
        return read(send(post("/api/v1/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String prefix) throws Exception {
        String email = prefix + "-" + System.nanoTime() + "@example.com";
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V35 Test User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
