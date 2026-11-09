package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.AuthSessionService;
import com.visionmapping.service.FearEntryService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * V10 Phase 7 (SEC-10-01, SEC-10-02, SEC-10-06, SAC-14, SAC-15): when
 * something fails inside the server, the request is refused, and the client
 * sees a safe message and a reference, never SQL, a class name or a stack trace.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorHandlingFlowTests {

    private static final String SECRET_DETAIL = "SELECT password_hash FROM app_users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private AuthSessionService authSessionService;

    @MockitoSpyBean
    private FearEntryService fearEntryService;

    @Test
    void whenTheSessionCannotBeCheckedTheRequestIsRefusedEvenOnAPublicEndpoint() throws Exception {
        String token = registerAndToken("closed");
        doThrow(new CannotGetJdbcConnectionException("database down: " + SECRET_DETAIL))
                .when(authSessionService).activeSession(any());

        for (String path : new String[] {"/api/fear-entries", "/api/dashboard", "/api/health"}) {
            MockHttpServletResponse response = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as(path).isEqualTo(503);
            assertSafe(response);
        }
    }

    @Test
    void anUnexpectedErrorHidesItsCauseAndGivesAReference() throws Exception {
        String token = registerAndToken("unexpected");
        doThrow(new IllegalStateException("org.hibernate.Boom at " + SECRET_DETAIL)).when(fearEntryService).listFearEntries(any());

        MockHttpServletResponse response = mockMvc.perform(get("/api/fear-entries").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertSafe(response);
        assertThat(body(response).get("message").asText()).contains("reference");
    }

    @Test
    void anUnreachableDatabaseAnswers503NotA500() throws Exception {
        String token = registerAndToken("database");
        doThrow(new DataAccessResourceFailureException("connection refused: " + SECRET_DETAIL))
                .when(fearEntryService).listFearEntries(any());

        MockHttpServletResponse response = mockMvc.perform(get("/api/fear-entries").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertSafe(response);
    }

    @Test
    void everyKindOfErrorCarriesAReference() throws Exception {
        String token = registerAndToken("kinds");
        MockHttpServletResponse unauthenticated = mockMvc.perform(get("/api/fear-entries")).andReturn().getResponse();
        MockHttpServletResponse notFound = mockMvc.perform(get("/api/dreams/999999").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        MockHttpServletResponse forbidden = mockMvc.perform(get("/api/admin/ui-text").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        MockHttpServletResponse invalid = mockMvc.perform(post("/api/fear-entries").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fear\":")).andReturn().getResponse();

        assertThat(unauthenticated.getStatus()).isEqualTo(401);
        assertThat(notFound.getStatus()).isEqualTo(404);
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(invalid.getStatus()).isEqualTo(400);
        for (MockHttpServletResponse response : new MockHttpServletResponse[] {unauthenticated, notFound, forbidden, invalid}) {
            assertSafe(response);
        }
    }

    private void assertSafe(MockHttpServletResponse response) throws Exception {
        String content = response.getContentAsString();
        assertThat(body(response).get("reference").asText()).matches("[0-9a-f]{12}");
        assertThat(content).doesNotContain("SELECT", "password_hash", "org.hibernate", "Exception", "\tat ", "trace");
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    private String registerAndToken(String prefix) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Error Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }
}
