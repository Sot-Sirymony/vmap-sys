package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V10 Phase 7 (SEC-06-02, SEC-06-03, SEC-07-06): repetition limits answer 429
 * with Retry-After, and common passwords are refused, through the real API.
 * Each test uses its own network address so the counts do not mix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.rate-limit.registration-per-ip=2",
        "app.security.rate-limit.join-code-per-key=3",
        "app.security.rate-limit.shared-plan-per-ip=2",
        "app.security.rate-limit.excel-import-per-user=1"})
class RateLimitFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationIsLimitedPerNetworkAddress() throws Exception {
        register("203.0.113.10", "first").andExpect(status().isCreated());
        register("203.0.113.10", "second").andExpect(status().isCreated());
        register("203.0.113.10", "third")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.reference").isNotEmpty());
        register("203.0.113.11", "elsewhere").andExpect(status().isCreated());
    }

    @Test
    void joinCodeGuessesAreLimitedPerAccount() throws Exception {
        String token = token(register("203.0.113.20", "joiner"));
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/cohort/join").with(from("203.0.113.2" + attempt)).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"joinCode\":\"GUESS00" + attempt + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/cohort/join").with(from("203.0.113.29")).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"joinCode\":\"GUESS009\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void openingShareLinksIsLimitedPerNetworkAddress() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get("/api/shared-plans/not-a-real-token").with(from("203.0.113.30"))).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/shared-plans/not-a-real-token").with(from("203.0.113.30")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void excelImportIsLimitedPerAccount() throws Exception {
        String token = token(register("203.0.113.40", "importer"));
        MockMultipartFile notAWorkbook = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/excel/import")
                .file(notAWorkbook).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/excel/import")
                        .file(notAWorkbook).header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aCommonPasswordIsRefusedAtRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(from("203.0.113.50")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Common", "email",
                                "common-" + System.nanoTime() + "@example.com", "password", "Password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("too common")));
    }

    private ResultActions register(String ip, String prefix) throws Exception {
        return mockMvc.perform(post("/api/auth/register").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", "Rate Test", "email",
                        prefix + "-" + System.nanoTime() + "@example.com", "password", PASSWORD))));
    }

    private String token(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("token").asText();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
