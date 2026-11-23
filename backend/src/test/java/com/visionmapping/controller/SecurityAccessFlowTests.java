package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.security.PermissionService;
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

/** V24 (BRD 24.0.0) phase 1: the settings, the counts and the public method list (SA-FR-01 to SA-FR-03). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PermissionService permissions;

    @AfterEach
    void restoreCoach() {
        jdbc.update("delete from role_permissions where role_key = 'COACH' and permission = 'VIEW_SECURITY_LOG'");
        permissions.evict();
    }

    @Test
    void theDefaultsMatchTodaysBehaviourAndThePublicListNeedsNoToken() throws Exception {
        JsonNode offered = read(mockMvc.perform(get("/api/auth/sign-in-methods")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(offered.get("password").asBoolean()).isTrue();
        // Google is not configured in the test profile, so it is never offered (GA-FR-02).
        assertThat(offered.get("google").asBoolean()).isFalse();

        JsonNode view = view(admin());
        assertThat(view.get("passwordSignIn").asBoolean()).isTrue();
        assertThat(view.get("googleSignIn").asBoolean()).isTrue();
        assertThat(view.get("twoFactorScope").asText()).isEqualTo("PRIVILEGED");
        assertThat(view.get("googleAvailable").asBoolean()).isFalse();
        assertThat(view.get("canChange").asBoolean()).isTrue();
    }

    @Test
    void countsLeaveOutAdminsAndSessionsRememberHowTheyWereOpened() throws Exception {
        String admin = admin();
        JsonNode before = view(admin).get("password");

        String email = "sa-participant-" + UUID.randomUUID() + "@example.com";
        register(email);
        login(email);

        JsonNode after = view(admin).get("password");
        assertThat(after.get("accounts").asLong()).isEqualTo(before.get("accounts").asLong() + 1);
        assertThat(after.get("onlyWay").asLong()).isEqualTo(before.get("onlyWay").asLong() + 1);
        // Sign-up and sign-in each opened a password session.
        assertThat(after.get("openSessions").asLong()).isEqualTo(before.get("openSessions").asLong() + 2);
        assertThat(jdbc.queryForList("select s.sign_in_method from auth_sessions s join app_users u on u.id = s.user_id "
                + "where u.email = ?", String.class, email)).containsOnly("PASSWORD");

        // Making a second admin does not change the counts (D-24-5).
        JsonNode beforeAdmin = view(admin).get("password");
        admin();
        assertThat(view(admin).get("password").get("accounts").asLong()).isEqualTo(beforeAdmin.get("accounts").asLong());
    }

    @Test
    void viewSecurityLogReadsButCannotChangeAndParticipantsAreRefused() throws Exception {
        String participant = register("sa-user-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(get("/api/admin/security-access").header("Authorization", "Bearer " + participant))
                .andExpect(status().isForbidden());

        jdbc.update("insert into role_permissions (role_key, permission) values ('COACH', 'VIEW_SECURITY_LOG')");
        permissions.evict();
        String coachEmail = "sa-coach-" + UUID.randomUUID() + "@example.com";
        register(coachEmail);
        jdbc.update("update app_users set role = 'COACH' where email = ?", coachEmail);
        JsonNode view = view(login(coachEmail));
        assertThat(view.get("canChange").asBoolean()).isFalse();
    }

    private JsonNode view(String token) throws Exception {
        return read(mockMvc.perform(get("/api/admin/security-access").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String admin() throws Exception {
        String email = "sa-admin-" + UUID.randomUUID() + "@example.com";
        register(email);
        jdbc.update("update app_users set role = 'ADMIN' where email = ?", email);
        return login(email);
    }

    private String register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Security Access", "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.93." + (NEXT_IP.incrementAndGet() % 250) + ".1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
