package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
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

/**
 * V17 phase 5 (BRD 17.0.0, BL-FR-30, BL-FR-31, BLC-14): the chain check asks
 * the method's six chains; older takes keep their knowledge and resources
 * scores, and the trend compares only the chains both takes measured.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChainListFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void blc14_aNewTakeAsksTheSixChainsAndTheTrendComparesSharedOnes() throws Exception {
        String email = "chains-" + System.nanoTime() + "@example.com";
        String token = register(email);
        Long userId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
        LocalDate today = LocalDate.now();
        // A take from before V17: the four shared chains at 2, knowing how 5 and resources 1.
        jdbcTemplate.update("insert into chain_assessments (user_id, taken_on, settling_score, fear_score, criticism_score,"
                + " vision_score, knowledge_score, resources_score, created_at, updated_at) values (?, ?, 2, 2, 2, 2, 5, 1, ?, ?)",
                userId, java.sql.Date.valueOf(today.minusDays(100)), java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now()));

        // The old shape (knowledge and resources, no priorities or persistence) is refused.
        send(put("/api/chain-assessments"), token, Map.of("takenOn", today.toString(), "settlingScore", 3, "fearScore", 3,
                "criticismScore", 3, "visionScore", 3, "knowledgeScore", 3, "resourcesScore", 3))
                .andExpect(status().isBadRequest());

        JsonNode saved = read(send(put("/api/chain-assessments"), token, Map.of("takenOn", today.toString(),
                "settlingScore", 3, "fearScore", 3, "criticismScore", 3, "prioritiesScore", 1, "persistenceScore", 1,
                "visionScore", 3)).andExpect(status().isOk()));
        assertThat(saved.get("prioritiesScore").asInt()).isEqualTo(1);
        assertThat(saved.get("knowledgeScore").isNull()).isTrue();

        JsonNode summary = read(mockMvc.perform(get("/api/chain-assessments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        JsonNode old = summary.get("assessments").get(0);
        assertThat(old.get("knowledgeScore").asInt()).isEqualTo(5);
        assertThat(old.get("prioritiesScore").isNull()).isTrue();
        // Shared chains went from 2 to 3: +50%. The new low priorities and persistence scores do not drag it down,
        // and the old knowledge score of 5 does not either.
        assertThat(summary.get("improvementPercent").decimalValue()).isEqualByComparingTo("50.0");
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token,
                               Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V17 Test", "email", email,
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
