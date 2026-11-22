package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.audit.SecurityAlertService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V21 phase 4 (BRD 21.0.0, TF-FR-28 to TF-FR-37, TF-FR-41; TFC-07, TFC-09,
 * TFC-12, TFC-19): two-step sign-in required for admins and coaches, the admin
 * report and the admin reset.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorRequirementFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TwoFactorRequirementService requirement;

    @Autowired
    private SecurityAlertService alerts;

    @Autowired
    private Clock clock;

    @Test
    void tfc07_tfc19_aCoachPastTheirDateCanOnlySetUpTwoStepSignIn() throws Exception {
        String email = email("tf-req-coach");
        String token = register(email);
        role(email, "COACH", clock.instant().minusSeconds(60));

        send(get("/api/vision-areas"), token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_SETUP_REQUIRED"));
        send(get("/api/dashboard"), token).andExpect(status().isForbidden());
        JsonNode panel = read(send(get("/api/account/two-factor"), token).andExpect(status().isOk()));
        assertThat(panel.get("dueAt").isNull()).isFalse();
        assertThat(panel.get("emailMethodAvailable").asBoolean()).as("D-21-1").isFalse();

        String secret = read(send(post("/api/account/two-factor/totp"), token).andExpect(status().isOk())).get("secret").asText();
        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", TotpCodes.code(secret, TotpCodes.step(clock.instant()))))
                .andExpect(status().isOk());
        send(get("/api/vision-areas"), token).andExpect(status().isOk());
    }

    @Test
    void tfFr33_beforeTheDateACoachMaySkip_andTheSkipIsLogged() throws Exception {
        String email = email("tf-req-skip");
        String token = register(email);
        role(email, "COACH", clock.instant().plus(Duration.ofDays(10)));
        send(get("/api/vision-areas"), token).andExpect(status().isOk());
        send(post("/api/account/two-factor/skip"), token).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select detail from security_events e join app_users u on u.id = e.actor_id"
                + " where u.email = ? and e.event_type = 'TWO_FACTOR_SETUP_SKIPPED'", String.class, email)).isEqualTo("9 days left");
    }

    @Test
    void tfFr32_tfFr37_datesAreGivenToAdminsAndCoachesAtStartUpAndOnPromotion_andTakenFromParticipants() throws Exception {
        String coachEmail = email("tf-req-start");
        register(coachEmail);
        jdbcTemplate.update("update app_users set role = 'COACH', two_factor_due_at = null where email = ?", coachEmail);
        String userEmail = email("tf-req-demoted");
        register(userEmail);
        jdbcTemplate.update("update app_users set two_factor_due_at = ? where email = ?", Timestamp.from(clock.instant()), userEmail);

        requirement.assignMissingDueDates();

        Instant due = appUserRepository.findByEmail(coachEmail).orElseThrow().getTwoFactorDueAt();
        assertThat(Duration.between(clock.instant(), due).toDays()).isBetween(13L, 14L);
        assertThat(appUserRepository.findByEmail(userEmail).orElseThrow().getTwoFactorDueAt()).isNull();

        AppUser promoted = appUserRepository.findByEmail(userEmail).orElseThrow();
        promoted.setRole(com.visionmapping.entity.enums.UserRole.COACH);
        requirement.roleGiven(promoted);
        assertThat(promoted.getTwoFactorDueAt()).isAfter(clock.instant().plus(Duration.ofDays(13)));
    }

    @Test
    void tfc09_anAdminResetsACoachsTwoStepSignIn_safely() throws Exception {
        String adminEmail = email("tf-admin");
        String adminToken = register(adminEmail);
        String adminSecret = setUp(adminToken);
        role(adminEmail, "ADMIN", null);
        String coachEmail = email("tf-reset-coach");
        String coachToken = register(coachEmail);
        setUp(coachToken);
        role(coachEmail, "COACH", null);
        Long coachId = appUserRepository.findByEmail(coachEmail).orElseThrow().getId();
        Long adminId = appUserRepository.findByEmail(adminEmail).orElseThrow().getId();

        JsonNode report = read(send(get("/api/admin/two-factor"), adminToken).andExpect(status().isOk()));
        assertThat(report.findValuesAsText("email")).contains(coachEmail, adminEmail);

        send(post("/api/admin/users/" + coachId + "/two-factor/reset"), coachToken, Map.of("reason", "x"))
                .andExpect(status().isForbidden());
        send(post("/api/admin/users/" + adminId + "/two-factor/reset"), adminToken, Map.of("reason", "lost phone"))
                .andExpect(status().isBadRequest());
        send(post("/api/admin/users/" + coachId + "/two-factor/reset"), adminToken, Map.of("reason", "Lost phone; called her on 012 345 678"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select count(*) from two_factor_settings where user_id = ?", Integer.class, coachId)).isZero();
        // TF-FR-29: the coach's sessions ended.
        send(get("/api/vision-areas"), coachToken).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("select detail from security_events where event_type = 'TWO_FACTOR_RESET_BY_ADMIN'"
                + " and target_id = ?", String.class, coachId)).isEqualTo("Lost phone; called her on 012 345 678");
        assertThat(alerts.recentAlerts()).anyMatch(alert -> alert.contains("reset the two-step sign-in of user " + coachId));
        Instant due = appUserRepository.findByEmail(coachEmail).orElseThrow().getTwoFactorDueAt();
        assertThat(Duration.between(clock.instant(), due).toHours()).as("TF-FR-30: three days").isBetween(71L, 72L);
        assertThat(read(login(coachEmail)).get("token").asText()).as("signs in, then must set up again").isNotBlank();
        send(post("/api/admin/users/" + coachId + "/two-factor/reset"), adminToken, Map.of("reason", "again"))
                .andExpect(status().isBadRequest());
        assertThat(adminSecret).isNotBlank();
    }

    @Test
    void tfNfr06_aCoachTurningItOffBeforeTheirDateRaisesAnAlert() throws Exception {
        String email = email("tf-off-alert");
        String token = register(email);
        setUp(token);
        role(email, "COACH", clock.instant().plus(Duration.ofDays(5)));
        send(post("/api/account/two-factor/disable"), token).andExpect(status().isNoContent());
        Long id = appUserRepository.findByEmail(email).orElseThrow().getId();
        assertThat(alerts.recentAlerts()).anyMatch(alert -> alert.contains("turned off by coach user " + id));
    }

    @Test
    void tfFr41_tfc12_theExportShowsTwoStepSignInButNoSecret() throws Exception {
        String email = email("tf-export");
        String token = register(email);
        String secret = setUp(token);
        JsonNode export = read(send(get("/api/account/export"), token).andExpect(status().isOk()));
        JsonNode section = export.get("twoFactor");
        assertThat(section.get("enabled").asBoolean()).isTrue();
        assertThat(section.get("method").asText()).isEqualTo("TOTP");
        assertThat(section.get("recoveryCodesLeft").asInt()).isEqualTo(10);
        assertThat(export.toString()).doesNotContain(secret);
    }

    private String setUp(String token) throws Exception {
        String secret = read(send(post("/api/account/two-factor/totp"), token)).get("secret").asText();
        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", TotpCodes.code(secret, TotpCodes.step(clock.instant()))))
                .andExpect(status().isOk());
        return secret;
    }

    private void role(String email, String role, Instant due) {
        jdbcTemplate.update("update app_users set role = ?, two_factor_due_at = ? where email = ?", role,
                due == null ? null : Timestamp.from(due), email);
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", "Two Factor", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
