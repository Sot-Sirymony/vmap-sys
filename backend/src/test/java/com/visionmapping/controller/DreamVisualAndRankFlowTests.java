package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-56 (BR-45 image link format) and FR-57 (BR-46 single-letter rank,
 * ties allowed) end-to-end through the real DTO validation pipeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DreamVisualAndRankFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void wellFormedImageUrlRoundTrips() throws Exception {
        String token = registerAndToken("dream-image-ok");
        long areaId = createArea(token);

        MvcResult result = mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("imageUrl", "https://example.com/dream.jpg")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/dream.jpg"))
                .andReturn();

        long dreamId = idOf(result);
        mockMvc.perform(put("/api/dreams/" + dreamId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("imageUrl", "https://example.com/dream.jpg")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/dream.jpg"));
    }

    @Test
    void nonUrlImageValueIsRejected() throws Exception {
        String token = registerAndToken("dream-image-bad");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("imageUrl", "not-a-url")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void javascriptUriIsRejected() throws Exception {
        String token = registerAndToken("dream-image-js");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("imageUrl", "javascript:alert(1)")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankImageUrlIsAlwaysValid() throws Exception {
        String token = registerAndToken("dream-image-blank");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void singleUppercaseLetterRankRoundTrips() throws Exception {
        String token = registerAndToken("dream-rank-ok");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("letterRank", "A")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.letterRank").value("A"));
    }

    @Test
    void twoDreamsInTheSameAreaCanShareTheSameLetterRank() throws Exception {
        String token = registerAndToken("dream-rank-ties");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("letterRank", "A")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("letterRank", "A")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.letterRank").value("A"));
    }

    @Test
    void lowercaseLetterRankIsRejected() throws Exception {
        String token = registerAndToken("dream-rank-lowercase");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("letterRank", "a")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multiCharacterLetterRankIsRejected() throws Exception {
        String token = registerAndToken("dream-rank-multi");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of("letterRank", "AA")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankLetterRankIsAlwaysValid() throws Exception {
        String token = registerAndToken("dream-rank-blank");
        long areaId = createArea(token);

        mockMvc.perform(post("/api/dreams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dreamBody(areaId, Map.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.letterRank").doesNotExist());
    }

    private long createArea(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vision-areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "FR-56/57 Area",
                                "priority", "HIGH",
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private Map<String, Object> dreamBody(long areaId, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("visionAreaId", areaId);
        body.put("title", "A dream needing a visual or rank");
        body.put("dreamType", "LONG_TERM");
        body.put("priority", "HIGH");
        body.put("status", "IDEA");
        body.put("moonshot", false);
        body.put("scheduleMode", "BOTTOM_UP");
        body.putAll(extra);
        return body;
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "FR-56/57 Test User",
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
