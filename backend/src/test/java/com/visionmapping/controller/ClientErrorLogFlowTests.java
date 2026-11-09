package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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

/** Diagnostics: failed browser requests are recorded per page, kept to their owner, and readable by admins. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientErrorLogFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aFailureIsRecordedWithoutItsQueryStringAndShownOnlyToItsOwner() throws Exception {
        String owner = register("owner-" + System.nanoTime() + "@example.com");
        String other = register("other-" + System.nanoTime() + "@example.com");

        record(owner, entry("/self-assessment-items?dreamId=177&includeArchived=true", 404))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/client-errors?page=strengths").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].path").value("/self-assessment-items"))
                .andExpect(jsonPath("$[0].status").value(404))
                .andExpect(jsonPath("$[0].message").value("No endpoint for /api/strengths-profiles/177"))
                .andExpect(jsonPath("$[0].reference").value("a1b2c3d4e5f6"))
                .andExpect(jsonPath("$[0].userId").doesNotExist());
        mockMvc.perform(get("/api/client-errors?page=other-page").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/client-errors?page=strengths").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anUnreachableServerIsStatusZeroAndABadEntryIsRefused() throws Exception {
        String token = register("zero-" + System.nanoTime() + "@example.com");
        record(token, entry("/strengths-profiles/1", 0)).andExpect(status().isNoContent());

        Map<String, Object> badMethod = entry("/strengths-profiles/1", 500);
        badMethod.put("method", "TRACE");
        record(token, badMethod).andExpect(status().isBadRequest());
        Map<String, Object> badPage = entry("/strengths-profiles/1", 500);
        badPage.put("page", "Strengths Page!");
        record(token, badPage).andExpect(status().isBadRequest());
        Map<String, Object> badStatus = entry("/strengths-profiles/1", 700);
        record(token, badStatus).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/client-errors?page=strengths").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value(0));
    }

    @Test
    void overTheHourlyLimitAnEntryIsDroppedQuietly() throws Exception {
        String email = "limit-" + System.nanoTime() + "@example.com";
        String token = register(email);
        Long userId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);
        Timestamp now = Timestamp.from(Instant.now());
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.update("insert into client_error_logs (user_id, occurred_at, page, method, path, status)"
                    + " values (?, ?, 'flood', 'GET', '/x', 500)", userId, now);
        }

        record(token, entry("/strengths-profiles/1", 500)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/client-errors?page=strengths").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyAnAdminReadsEveryonesFailures() throws Exception {
        String user = register("plain-" + System.nanoTime() + "@example.com");
        String adminEmail = "admin-" + System.nanoTime() + "@example.com";
        String admin = register(adminEmail);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        record(user, entry("/strengths-profiles/42", 404)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/client-errors").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(get("/api/admin/client-errors?page=strengths&status=404&size=100")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var entries = objectMapper.readTree(body).get("entries");
        assertThat(entries.size()).isGreaterThanOrEqualTo(1);
        assertThat(entries.get(0).get("path").asText()).isEqualTo("/strengths-profiles/42");
        assertThat(entries.get(0).get("userId").isNull()).isFalse();
    }

    private Map<String, Object> entry(String path, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("page", "strengths");
        body.put("method", "GET");
        body.put("path", path);
        body.put("status", status);
        body.put("message", "No endpoint for /api/strengths-profiles/177");
        body.put("reference", "a1b2c3d4e5f6");
        body.put("appVersion", "1.0.0");
        return body;
    }

    private ResultActions record(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/client-errors")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Diagnostics User",
                                "email", email,
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
