package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-59 end-to-end: the gratitude log's own CRUD, its dashboard summary
 * (FR-59.2), the Review-prompt integration (FR-59.3), and the completion-time
 * contribution nudge's backing endpoints (FR-59.4). All diagnostic (BR-48) —
 * these tests check visibility and round-tripping, not any gate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GratitudeEntryFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdEntryRoundTripsAndListsOnlyTheOwnersOwn() throws Exception {
        String owner = registerAndToken("gratitude-owner");
        String stranger = registerAndToken("gratitude-stranger");

        mockMvc.perform(post("/api/gratitude-entries")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "PERSON",
                                "description", "A friend who checked in this week"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("PERSON"))
                .andExpect(jsonPath("$.description").value("A friend who checked in this week"));

        mockMvc.perform(get("/api/gratitude-entries").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/gratitude-entries").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void archivingRemovesAnEntryFromTheDefaultListing() throws Exception {
        String token = registerAndToken("gratitude-archive");
        long entryId = createEntry(token, "GIFT", "An unexpected gift");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/gratitude-entries/" + entryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/gratitude-entries").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/gratitude-entries?includeArchived=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void dashboardGratitudeCardReflectsRecentEntries() throws Exception {
        String token = registerAndToken("gratitude-dashboard");
        createEntry(token, "HEALTH", "Recovered fully from a cold");

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gratitude.countThisWeek").value(1))
                .andExpect(jsonPath("$.gratitude.recent.length()").value(1))
                .andExpect(jsonPath("$.gratitude.recent[0].description").value("Recovered fully from a cold"));
    }

    @Test
    void answeringTheReviewGratitudePromptCreatesAnEntry() throws Exception {
        String token = registerAndToken("gratitude-review");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reviewBody("A genuinely good week"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/gratitude-entries").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("OTHER"))
                .andExpect(jsonPath("$[0].description").value("A genuinely good week"));
    }

    @Test
    void leavingTheReviewGratitudePromptBlankCreatesNothing() throws Exception {
        String token = registerAndToken("gratitude-review-blank");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reviewBody(null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/gratitude-entries").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dreamHasLinkedPartnerReflectsActualLinkage() throws Exception {
        String token = registerAndToken("gratitude-dream-nudge");
        long areaId = postAndId("/api/vision-areas", token, Map.of(
                "name", "Career", "priority", "HIGH", "status", "ACTIVE"));
        long dreamId = postAndId("/api/dreams", token, dreamBody(areaId));

        mockMvc.perform(get("/api/dreams/" + dreamId + "/has-linked-partner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("false"));

        mockMvc.perform(post("/api/partners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "A mentor", "supportType", "MENTOR", "status", "ACTIVE",
                                "relatedDreamId", dreamId
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dreams/" + dreamId + "/has-linked-partner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("true"));
    }

    @Test
    void goalHasLinkedPartnerReflectsActualLinkage() throws Exception {
        String token = registerAndToken("gratitude-goal-nudge");
        long areaId = postAndId("/api/vision-areas", token, Map.of(
                "name", "Career", "priority", "HIGH", "status", "ACTIVE"));
        long dreamId = postAndId("/api/dreams", token, dreamBody(areaId));
        long goalId = postAndId("/api/goals", token, Map.of(
                "dreamId", dreamId, "title", "A goal", "priority", "HIGH", "status", "NOT_STARTED",
                "scheduleMode", "BOTTOM_UP"
        ));

        mockMvc.perform(get("/api/goals/" + goalId + "/has-linked-partner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("false"));

        mockMvc.perform(post("/api/partners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "A mentor", "supportType", "MENTOR", "status", "ACTIVE",
                                "relatedGoalId", goalId
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/goals/" + goalId + "/has-linked-partner")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("true"));
    }

    private Map<String, Object> dreamBody(long areaId) {
        Map<String, Object> body = new HashMap<>();
        body.put("visionAreaId", areaId);
        body.put("title", "A dream needing a nudge check");
        body.put("dreamType", "LONG_TERM");
        body.put("priority", "MEDIUM");
        body.put("status", "IDEA");
        body.put("moonshot", false);
        body.put("scheduleMode", "BOTTOM_UP");
        return body;
    }

    private Map<String, Object> reviewBody(String gratitudeNote) {
        Map<String, Object> body = new HashMap<>();
        body.put("reviewType", "WEEKLY");
        body.put("reviewDate", LocalDateTime.now().toString());
        body.put("summary", "Weekly summary");
        if (gratitudeNote != null) {
            body.put("gratitudeNote", gratitudeNote);
        }
        return body;
    }

    private long createEntry(String token, String category, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/gratitude-entries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("category", category, "description", description))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long postAndId(String path, String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "FR-59 Test User",
                                "email", uniqueEmail(prefix),
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
