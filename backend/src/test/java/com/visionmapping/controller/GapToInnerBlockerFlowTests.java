package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.hamcrest.Matchers;
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

/** V36 (D-36-3 to D-36-5, BR-36-1 to BR-36-4): moving a six-chain weakness to inner blockers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GapToInnerBlockerFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aSixChainWeaknessMovesInOneStepAndKeepsWhatItsCoverCreated() throws Exception {
        String user = register("move");
        long dreamId = dream(user);
        long gap = item(user, dreamId, "WEAKNESS", "I give up when progress is slow.", "SIX_CHAIN");
        long partnerId = read(send(put("/api/v1/self-assessment-items/" + gap + "/cover"), user,
                Map.of("method", "PARTNER", "partnerName", "Study buddy", "partnerSupportType", "EMOTIONAL")).andExpect(status().isOk()))
                .get("linkedPartnerId").asLong();

        JsonNode moved = read(send(post("/api/v1/self-assessment-items/" + gap + "/move-to-inner-blocker"), user,
                blocker("I give up when progress is slow.", "Stopped two courses halfway", "Never miss twice", "persistenceScore", dreamId))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.containsString("/api/v1/inner-blockers/"))));
        assertThat(moved.get("chainKey").asText()).isEqualTo("persistenceScore");
        assertThat(moved.get("dreamId").asLong()).isEqualTo(dreamId);

        assertThat(archived(gap)).isTrue();
        mockMvc.perform(get("/api/v1/partners/" + partnerId).header("Authorization", "Bearer " + user)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inner-blockers").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.content[0].blocker").value("I give up when progress is slow."));

        // An archived item can't move again, but it can be restored.
        send(post("/api/v1/self-assessment-items/" + gap + "/move-to-inner-blocker"), user, blocker("Again", null, "Again", null, null))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/self-assessment-items/" + gap + "/restore").header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());
        assertThat(archived(gap)).isFalse();
    }

    @Test
    void onlyTheOwnersSixChainWeaknessMoves() throws Exception {
        String user = register("move-rules");
        long dreamId = dream(user);
        long strength = item(user, dreamId, "STRENGTH", "I explain things clearly.", "MANUAL");
        long manual = item(user, dreamId, "WEAKNESS", "No experience with databases.", "MANUAL");
        long workStyle = item(user, dreamId, "WEAKNESS", "I can lose sight of the big picture.", "WORK_STYLE");
        for (long id : new long[] {strength, manual, workStyle}) {
            send(post("/api/v1/self-assessment-items/" + id + "/move-to-inner-blocker"), user, blocker("X", null, "Y", null, dreamId))
                    .andExpect(status().isBadRequest());
            assertThat(archived(id)).isFalse();
        }

        long gap = item(user, dreamId, "WEAKNESS", "Fear of failing stops me from trying.", "SIX_CHAIN");
        String stranger = register("move-stranger");
        send(post("/api/v1/self-assessment-items/" + gap + "/move-to-inner-blocker"), stranger, blocker("X", null, "Y", null, null))
                .andExpect(status().isNotFound());
        // An invalid blocker leaves the item as it was.
        send(post("/api/v1/self-assessment-items/" + gap + "/move-to-inner-blocker"), user, blocker("Fear", null, " ", null, dreamId))
                .andExpect(status().isBadRequest());
        assertThat(archived(gap)).isFalse();
        assertThat(blockerCount(user)).isZero();
    }

    @Test
    void theTenActiveLimitRefusesTheMoveAndLeavesTheItem() throws Exception {
        String user = register("move-limit");
        long dreamId = dream(user);
        for (int index = 0; index < 10; index++) {
            send(post("/api/v1/inner-blockers"), user, blocker("Blocker " + index, null, "Practice", null, null)).andExpect(status().isCreated());
        }
        long gap = item(user, dreamId, "WEAKNESS", "I tend to settle for what is comfortable.", "SIX_CHAIN");
        send(post("/api/v1/self-assessment-items/" + gap + "/move-to-inner-blocker"), user, blocker("Settling", null, "Stretch one goal", null, dreamId))
                .andExpect(status().isConflict());
        assertThat(archived(gap)).isFalse();
        assertThat(blockerCount(user)).isEqualTo(10);
    }

    private boolean archived(long itemId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select archived from self_assessment_items where id = ?", Boolean.class, itemId));
    }

    private long blockerCount(String token) throws Exception {
        return read(mockMvc.perform(get("/api/v1/inner-blockers").header("Authorization", "Bearer " + token))).get("totalElements").asLong();
    }

    private long item(String token, long dreamId, String kind, String description, String source) throws Exception {
        return read(send(post("/api/v1/self-assessment-items"), token, Map.of("dreamId", dreamId, "kind", kind, "description", description,
                "evidence", "A real example", "relevance", "HIGH", "source", source)).andExpect(status().isCreated())).get("id").asLong();
    }

    private static Map<String, Object> blocker(String blocker, String showsUp, String practice, String chainKey, Long dreamId) {
        Map<String, Object> body = new HashMap<>();
        body.put("blocker", blocker);
        body.put("showsUp", showsUp);
        body.put("practice", practice);
        body.put("chainKey", chainKey);
        body.put("dreamId", dreamId);
        return body;
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
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V36 Test User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
