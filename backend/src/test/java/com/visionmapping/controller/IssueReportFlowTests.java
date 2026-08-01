package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** FR-38: raising a report and reading it back through the real web/security/JPA stack. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IssueReportFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listMyReportsIsEmptyThenReturnsCreatedReport() throws Exception {
        String token = registerAndToken("issue-empty");

        mockMvc.perform(get("/api/issue-reports").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        createReport(token, "BUG", "Save button does nothing", "HIGH");

        mockMvc.perform(get("/api/issue-reports").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("IR-001"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    /**
     * 403, and deliberately not 401: this caller *is* authenticated, they simply
     * lack the ADMIN role. Anonymous callers get 401 instead — the two answers
     * mean different things and a client acts differently on each, so this test
     * and {@code protectedEndpointRejectsAnonymousRequest} guard the pair.
     */
    @Test
    void nonAdminGettingTheWholeQueueIsForbiddenNotAServerError() throws Exception {
        String token = registerAndToken("issue-forbidden");

        mockMvc.perform(get("/api/issue-reports/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** The same endpoint, with no credentials at all, is a 401 rather than a 403. */
    @Test
    void anonymousCallerGetsUnauthorizedRatherThanForbidden() throws Exception {
        mockMvc.perform(get("/api/issue-reports/all"))
                .andExpect(status().isUnauthorized());
    }

    /** A call to an endpoint the backend doesn't have must read as 404, not a 500 "Unexpected server error". */
    @Test
    void callingAnUnmappedEndpointReturnsNotFoundNotServerError() throws Exception {
        String token = registerAndToken("issue-unmapped");

        mockMvc.perform(get("/api/no-such-endpoint").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private void createReport(String token, String type, String title, String severity) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reportType", type);
        body.put("title", title);
        body.put("description", "Steps to reproduce…");
        body.put("severity", severity);
        body.put("contextRoute", "/tasks");
        body.put("appVersion", "4.0.0");
        mockMvc.perform(post("/api/issue-reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Issue Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
