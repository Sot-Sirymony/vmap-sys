package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AuthSession;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.AuthSessionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V10 phase 3: server-side sessions, short access tokens, refresh with
 * rotation and reuse detection, and strict token checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionFlowTests {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    // ---------- SEC-07-04: short access token and refresh ----------

    @Test
    void signInReturnsAShortAccessTokenAndARefreshToken() throws Exception {
        JsonNode session = register(uniqueEmail());

        assertThat(session.get("expiresIn").asLong()).isEqualTo(30 * 60L);
        assertThat(session.get("refreshToken").asText()).hasSizeGreaterThanOrEqualTo(43);
        assertWorks(session.get("token").asText());
    }

    @Test
    void refreshGivesANewPairAndTheNewAccessTokenWorks() throws Exception {
        JsonNode first = register(uniqueEmail());

        JsonNode second = read(refresh(first.get("refreshToken").asText()).andExpect(status().isOk()));

        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first.get("refreshToken").asText());
        assertWorks(second.get("token").asText());
        assertThat(second.get("email").asText()).isEqualTo(first.get("email").asText());
    }

    @Test
    void anOldRefreshTokenUsedAMomentLaterIsTwoTabsRacingNotTheft() throws Exception {
        JsonNode first = register(uniqueEmail());
        JsonNode second = read(refresh(first.get("refreshToken").asText()).andExpect(status().isOk()));

        refresh(first.get("refreshToken").asText()).andExpect(status().isConflict());

        // The session is still open: the newer tokens keep working.
        assertWorks(second.get("token").asText());
        refresh(second.get("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    void anOldRefreshTokenUsedLaterEndsTheSessionAsStolen() throws Exception {
        JsonNode first = register(uniqueEmail());
        JsonNode second = read(refresh(first.get("refreshToken").asText()).andExpect(status().isOk()));
        moveRotationIntoThePast(second.get("token").asText());

        refresh(first.get("refreshToken").asText())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your session has ended. Please sign in again."));

        // Both the thief's and the owner's copies stop working.
        assertRefused(second.get("token").asText());
        refresh(second.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownRefreshTokenIsRefused() throws Exception {
        refresh("not-a-real-refresh-token").andExpect(status().isUnauthorized());
    }

    @Test
    void aDisabledAccountCannotRefresh() throws Exception {
        String email = uniqueEmail();
        JsonNode session = register(email);
        AppUser user = appUserRepository.findByEmail(email).orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        appUserRepository.save(user);

        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    // ---------- SEC-07-03: ending sessions ----------

    @Test
    void logoutEndsTheSessionAtOnce() throws Exception {
        JsonNode session = register(uniqueEmail());

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", session.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());

        assertRefused(session.get("token").asText());
        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithAnUnknownTokenStillAnswers204() throws Exception {
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "unknown"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void changingThePasswordEndsEveryOtherSessionButKeepsThisOne() throws Exception {
        String email = uniqueEmail();
        JsonNode laptop = register(email);
        JsonNode phone = login(email, PASSWORD);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + laptop.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "BrandNewPass456"))))
                .andExpect(status().isNoContent());

        assertWorks(laptop.get("token").asText());
        refresh(laptop.get("refreshToken").asText()).andExpect(status().isOk());
        assertRefused(phone.get("token").asText());
        refresh(phone.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void rememberMeDecidesHowLongTheSessionLasts() throws Exception {
        String email = uniqueEmail();
        register(email);

        JsonNode remembered = login(email, PASSWORD, true);
        JsonNode tabOnly = login(email, PASSWORD, false);

        assertThat(sessionOf(remembered.get("token").asText()).getExpiresAt())
                .isAfter(Instant.now().plus(Duration.ofDays(29)));
        assertThat(sessionOf(tabOnly.get("token").asText()).getExpiresAt())
                .isBefore(Instant.now().plus(Duration.ofHours(13)));
    }

    // ---------- SEC-07-09: strict token checks ----------

    @Test
    void aSignedTokenWithoutASessionIsRefused() throws Exception {
        String token = Jwts.builder().issuer("vision-mapping").subject("someone@example.com")
                .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(appKey()).compact();
        assertRefused(token);
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() throws Exception {
        JsonNode session = register(uniqueEmail());
        Long sessionId = sessionOf(session.get("token").asText()).getId();
        String token = Jwts.builder().issuer("someone-else").subject(session.get("email").asText())
                .claim("sid", sessionId).expiration(Date.from(Instant.now().plusSeconds(600))).signWith(appKey()).compact();
        assertRefused(token);
    }

    @Test
    void aTokenWithoutAnExpiryIsRefused() throws Exception {
        JsonNode session = register(uniqueEmail());
        Long sessionId = sessionOf(session.get("token").asText()).getId();
        String token = Jwts.builder().issuer("vision-mapping").subject(session.get("email").asText())
                .claim("sid", sessionId).signWith(appKey()).compact();
        assertRefused(token);
    }

    @Test
    void anUnsignedAlgNoneTokenIsRefused() throws Exception {
        JsonNode session = register(uniqueEmail());
        Long sessionId = sessionOf(session.get("token").asText()).getId();
        String header = base64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64("{\"iss\":\"vision-mapping\",\"sub\":\"" + session.get("email").asText()
                + "\",\"sid\":" + sessionId + ",\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}");
        assertRefused(header + "." + payload + ".");
    }

    @Test
    void aTokenNamingSomeoneElsesSessionIsRefused() throws Exception {
        JsonNode mine = register(uniqueEmail());
        JsonNode theirs = register(uniqueEmail());
        Long theirSession = sessionOf(theirs.get("token").asText()).getId();
        String token = Jwts.builder().issuer("vision-mapping").subject(mine.get("email").asText())
                .claim("sid", theirSession).expiration(Date.from(Instant.now().plusSeconds(600))).signWith(appKey()).compact();
        assertRefused(token);
    }

    // ---------- helpers ----------

    private void assertWorks(String accessToken) throws Exception {
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + accessToken)).andExpect(status().isOk());
    }

    private void assertRefused(String accessToken) throws Exception {
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private JsonNode register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Session User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()));
    }

    private JsonNode login(String email, String password) throws Exception {
        return login(email, password, true);
    }

    private JsonNode login(String email, String password, boolean rememberMe) throws Exception {
        return read(mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.77." + (email.hashCode() & 0xff) + ".1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password, "rememberMe", rememberMe))))
                .andExpect(status().isOk()));
    }

    /** The session an access token names, read from the token's sid claim. */
    private AuthSession sessionOf(String accessToken) {
        Long sessionId = Jwts.parser().verifyWith(appKey()).build()
                .parseSignedClaims(accessToken).getPayload().get("sid", Long.class);
        return authSessionRepository.findById(sessionId).orElseThrow();
    }

    private void moveRotationIntoThePast(String accessToken) {
        AuthSession session = sessionOf(accessToken);
        session.setRotatedAt(Instant.now().minus(Duration.ofHours(1)));
        authSessionRepository.save(session);
    }

    private SecretKey appKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String uniqueEmail() {
        return "session-" + UUID.randomUUID() + "@example.com";
    }
}
