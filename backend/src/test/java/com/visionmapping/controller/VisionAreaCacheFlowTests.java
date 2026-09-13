package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.repository.VisionAreaRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-30 extended beyond the dashboard: a service's own list/get reads are
 * cached too (on the test profile's in-memory cache), any write through any
 * service evicts them (the eviction aspect is class-wide, not per-cache), and
 * one user's cached payload is never served to another (BR-20).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VisionAreaCacheFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VisionAreaRepository visionAreaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void listAndGetAreServedFromCacheAndEvictedByAnyServiceWrite() throws Exception {
        String token = registerAndToken("va-cache");
        long areaId = postAndId(token, "Career");

        // First reads fill the list and single-entity caches.
        list(token).andExpect(jsonPath("$[0].name").value("Career"));
        getArea(token, areaId).andExpect(jsonPath("$.name").value("Career"));

        // Renamed directly in the database — no service call, so no eviction.
        // A stale name here proves both reads above came from the cache.
        renameDirectlyInDatabase(areaId, "Career (renamed)");
        list(token).andExpect(jsonPath("$[0].name").value("Career"));
        getArea(token, areaId).andExpect(jsonPath("$.name").value("Career"));

        // Any service write — even creating an unrelated dream — evicts the
        // whole user namespace, so both caches recompute on the next read.
        postDreamUnder(token, areaId);
        list(token).andExpect(jsonPath("$[0].name").value("Career (renamed)"));
        getArea(token, areaId).andExpect(jsonPath("$.name").value("Career (renamed)"));
    }

    @Test
    void cachedVisionAreasAreNeverSharedAcrossUsers() throws Exception {
        String tokenA = registerAndToken("va-user-a");
        postAndId(tokenA, "A's Area");

        // A's read caches a payload with one vision area.
        list(tokenA).andExpect(jsonPath("$.length()").value(1));

        // B calls the same endpoint with the same (empty) parameters; a key
        // without the user id would return A's cached payload.
        String tokenB = registerAndToken("va-user-b");
        list(tokenB).andExpect(jsonPath("$.length()").value(0));

        // And B's read did not overwrite A's entry.
        list(tokenA).andExpect(jsonPath("$.length()").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions list(String token) throws Exception {
        return mockMvc.perform(get("/api/vision-areas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions getArea(String token, long id) throws Exception {
        return mockMvc.perform(get("/api/vision-areas/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void renameDirectlyInDatabase(long areaId, String newName) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                visionAreaRepository.findById(areaId).orElseThrow().setName(newName));
    }

    private void postDreamUnder(String token, long areaId) throws Exception {
        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "visionAreaId", areaId,
                                "title", "Trigger eviction",
                                "description", "Any write evicts the user's cache",
                                "whyImportant", "Proves eviction is class-wide",
                                "successDefinition", "Next read recomputes",
                                "dreamType", "SHORT_TERM",
                                "priority", "MEDIUM",
                                "targetDate", java.time.LocalDate.now().plusMonths(1).toString(),
                                "status", "ACTIVE",
                                "scheduleMode", "BOTTOM_UP"
                        ))))
                .andExpect(status().isCreated());
    }

    private long postAndId(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vision-areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", "Career development",
                                "priority", "HIGH",
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Cache Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
