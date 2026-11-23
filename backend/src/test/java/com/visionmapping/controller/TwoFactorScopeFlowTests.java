package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.PermissionService;
import com.visionmapping.service.twofactor.TotpCodes;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** V24 (BRD 24.0.0) phase 3: two-step sign-in required for everyone (SA-FR-14, D-24-4). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorScopeFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PermissionService permissions;

    @AfterEach
    void restoreDefaults() {
        jdbc.update("update security_settings set two_factor_scope = 'PRIVILEGED', updated_by_id = null where id = 1");
        users.clearParticipantTwoFactorDueDates(permissions.privilegedRoleKeys());
    }

    @Test
    void everyoneGivesParticipantsFourteenDaysThenOnlySetUpUntilTheyDoIt() throws Exception {
        String admin = admin();
        String participant = unique("scope-participant");
        String participantToken = register(participant);

        update(admin, "EVERYONE").andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorScope").value("EVERYONE"));

        Instant due = dueAt(participant);
        assertThat(due).isBetween(clock.instant().plus(Duration.ofDays(13)), clock.instant().plus(Duration.ofDays(15)));
        // Before the date: still free to use the app.
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + participantToken)).andExpect(status().isOk());
        // A new account gets its date at sign-up.
        String newcomer = unique("scope-newcomer");
        register(newcomer);
        assertThat(dueAt(newcomer)).isNotNull();

        // After the date: only setting it up is allowed.
        jdbc.update("update app_users set two_factor_due_at = ? where email = ?", Timestamp.from(clock.instant().minusSeconds(60)), participant);
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + participantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_SETUP_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Set up two-step sign-in to continue. Every account needs it."));
        setUpAuthenticator(participantToken);
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + participantToken)).andExpect(status().isOk());
        send(get("/api/account/two-factor"), participantToken).andExpect(jsonPath("$.canTurnOff").value(false));

        assertThat(jdbc.queryForObject("select detail from security_events where event_type = 'SECURITY_ACCESS_CHANGED' "
                + "order by id desc limit 1", String.class)).contains("two-step PRIVILEGED -> ", "two-step EVERYONE", "set-up dates given");
    }

    @Test
    void switchingBackClearsParticipantsDatesButKeepsCoachesAndNeverTurnsTwoStepOff() throws Exception {
        String admin = admin();
        String participant = unique("back-participant");
        register(participant);
        String enrolled = unique("back-enrolled");
        String enrolledToken = register(enrolled);
        String coach = unique("back-coach");
        register(coach);
        jdbc.update("update app_users set role = 'COACH', two_factor_due_at = ? where email = ?",
                Timestamp.from(clock.instant().plus(Duration.ofDays(3))), coach);
        update(admin, "EVERYONE").andExpect(status().isOk());
        setUpAuthenticator(enrolledToken);

        update(admin, "PRIVILEGED").andExpect(status().isOk());

        assertThat(dueAt(participant)).isNull();
        assertThat(dueAt(coach)).isNotNull();
        send(get("/api/account/two-factor"), enrolledToken)
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.canTurnOff").value(true));
        assertThat(jdbc.queryForObject("select detail from security_events where event_type = 'SECURITY_ACCESS_CHANGED' "
                + "order by id desc limit 1", String.class)).contains("set-up dates cleared");
    }

    private Instant dueAt(String email) {
        Timestamp due = jdbc.queryForObject("select two_factor_due_at from app_users where email = ?", Timestamp.class, email);
        return due == null ? null : due.toInstant();
    }

    private void setUpAuthenticator(String token) throws Exception {
        String secret = read(send(post("/api/account/two-factor/totp"), token)).get("secret").asText();
        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", TotpCodes.code(secret, TotpCodes.step(clock.instant()))))
                .andExpect(status().isOk());
    }

    private ResultActions update(String token, String scope) throws Exception {
        return mockMvc.perform(put("/api/admin/security-access").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "passwordSignIn", true, "googleSignIn", true, "twoFactorScope", scope, "reason", "Testing the two-step scope"))));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token,
                               Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String admin() throws Exception {
        String email = unique("scope-admin");
        register(email);
        jdbc.update("update app_users set role = 'ADMIN' where email = ?", email);
        return login(email);
    }

    private String register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", "Scope Test", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private String login(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.95." + (NEXT_IP.incrementAndGet() % 250) + ".1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())).get("token").asText();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
