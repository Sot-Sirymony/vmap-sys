package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.ErrorResponse;
import com.visionmapping.exception.GlobalExceptionHandler;
import com.visionmapping.repository.AppUserRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * V10 phase 1: disabled accounts, sign-in limits, security headers and the
 * upload limit, end to end through the real security filter chain.
 *
 * Each test signs in from its own made-up IP (X-Forwarded-For), so the per-IP
 * limit of one test never affects another in the shared Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MultipartProperties multipartProperties;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    // ---------- SEC-01-05: disabled accounts ----------

    @Test
    void disabledAccountCannotSignIn() throws Exception {
        String ip = uniqueIp();
        String email = register();
        setStatus(email, UserStatus.DISABLED);

        login(email, PASSWORD, ip)
                .andExpect(status().isUnauthorized())
                // Same answer as a wrong password: the status is not revealed.
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void tokenIssuedBeforeDisablingStopsWorking() throws Exception {
        String email = register();
        String token = token(login(email, PASSWORD, uniqueIp()).andExpect(status().isOk()));
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        setStatus(email, UserStatus.DISABLED);

        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- SEC-07-01, SEC-07-02: sign-in limits ----------

    @Test
    void fiveWrongPasswordsLockTheEmailEvenForTheRightPassword() throws Exception {
        String ip = uniqueIp();
        String email = register();
        for (int i = 0; i < 5; i++) {
            login(email, "WrongPassword1", ip).andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(
                        "Too many sign-in attempts for this email. Try again in 15 minutes, or reset your password."));
        // The lock follows the email, not the network.
        login(email, PASSWORD, uniqueIp()).andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownEmailIsLockedTheSameWaySoRegistrationIsNotRevealed() throws Exception {
        String ip = uniqueIp();
        String nobody = "nobody-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            login(nobody, "WrongPassword1", ip).andExpect(status().isUnauthorized());
        }

        login(nobody, "WrongPassword1", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "Too many sign-in attempts for this email. Try again in 15 minutes, or reset your password."));
    }

    @Test
    void successfulSignInClearsEarlierFailures() throws Exception {
        String ip = uniqueIp();
        String email = register();
        for (int i = 0; i < 4; i++) {
            login(email, "WrongPassword1", ip).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, ip).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login(email, "WrongPassword1", ip).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, ip).andExpect(status().isOk());
    }

    // ---------- SEC-02-01, SEC-02-02: security headers ----------

    @Test
    void apiSendsSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void errorAnswersCarryTheHeadersToo() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    // ---------- SEC-02-07: upload limit ----------

    @Test
    void uploadLimitIsTenMegabytes() {
        assertThat(multipartProperties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(multipartProperties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(11));
    }

    /**
     * MockMvc does not parse multipart bodies through Tomcat, so the size check
     * cannot be triggered here; the handler that answers it is tested directly.
     */
    @Test
    void tooLargeUploadIsAnswered413WithAClearMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/excel/import");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(DataSize.ofMegabytes(10).toBytes()), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().message()).contains("10 MB");
    }

    // ---------- helpers ----------

    private String register() throws Exception {
        String email = "sec-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Security Test", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String token(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private void setStatus(String email, UserStatus status) {
        AppUser user = appUserRepository.findByEmail(email).orElseThrow();
        user.setStatus(status);
        appUserRepository.save(user);
    }

    private static String uniqueIp() {
        int n = NEXT_IP.incrementAndGet();
        return "10.99." + (n / 250) + "." + (n % 250 + 1);
    }
}
