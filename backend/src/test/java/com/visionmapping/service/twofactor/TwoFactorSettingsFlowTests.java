package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
 * V21 phase 3 (BRD 21.0.0, TF-FR-01 to TF-FR-09, TF-FR-23; TFC-01, TFC-08,
 * TFC-10): setting up, changing and turning off two-factor sign-in, and the
 * trusted devices list. "Verify it's you" is off in the test profile; its own
 * tests prove the protected actions ask for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorSettingsFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void tfc01_settingUpAnAuthenticatorTurnsItOnWithTenRecoveryCodes_andSignInThenAsksForIt() throws Exception {
        String email = email("tf-setup");
        String token = register(email);
        JsonNode off = read(send(get("/api/account/two-factor"), token).andExpect(status().isOk()));
        assertThat(off.get("enabled").asBoolean()).isFalse();
        assertThat(off.get("emailMethodAvailable").asBoolean()).isTrue();
        assertThat(off.get("canTurnOff").asBoolean()).isTrue();

        JsonNode setup = read(send(post("/api/account/two-factor/totp"), token).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))));
        String secret = setup.get("secret").asText();
        assertThat(setup.get("otpauthUri").asText()).startsWith("otpauth://totp/Vision%20Mapping:").contains("secret=" + secret);
        assertThat(read(send(get("/api/account/two-factor"), token)).get("enabled").asBoolean())
                .as("TF-FR-03: nothing changes before the code").isFalse();

        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", "000000")).andExpect(status().isBadRequest());
        JsonNode codes = read(send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", currentCode(secret)))
                .andExpect(status().isOk()));
        assertThat(codes.get("recoveryCodes")).hasSize(10);

        JsonNode on = read(send(get("/api/account/two-factor"), token));
        assertThat(on.get("enabled").asBoolean()).isTrue();
        assertThat(on.get("method").asText()).isEqualTo("TOTP");
        assertThat(on.get("recoveryCodesLeft").asInt()).isEqualTo(10);
        assertThat(events(email, "TWO_FACTOR_ENABLED")).containsExactly("turned on with an authenticator app");
        JsonNode login = read(login(email));
        assertThat(login.get("status").asText()).isEqualTo("TWO_FACTOR_REQUIRED");
    }

    @Test
    void tfFr03_aNewAuthenticatorWaitsForItsCode_whileTheOldOneKeepsWorking() throws Exception {
        String email = email("tf-switch-app");
        String token = register(email);
        String oldSecret = setUpAuthenticator(token);

        String newSecret = read(send(post("/api/account/two-factor/totp"), token)).get("secret").asText();
        assertThat(read(send(get("/api/account/two-factor"), token)).get("method").asText()).isEqualTo("TOTP");
        String challenge = read(login(email)).get("challengeToken").asText();
        verify(challenge, TotpCodes.code(oldSecret, TotpCodes.step(clock.instant()) + 1)).andExpect(status().isOk());

        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", currentCode(newSecret))).andExpect(status().isOk());
        assertThat(events(email, "TWO_FACTOR_METHOD_CHANGED")).containsExactly("set up on a new authenticator app");
    }

    @Test
    void tfFr05_tfc08_participantsMayUseEmailCodes_coachesAndAdminsMayNot() throws Exception {
        String email = email("tf-email-method");
        String token = register(email);
        JsonNode codes = read(send(post("/api/account/two-factor/email"), token).andExpect(status().isOk()));
        assertThat(codes.get("recoveryCodes")).hasSize(10);
        assertThat(read(send(get("/api/account/two-factor"), token)).get("method").asText()).isEqualTo("EMAIL");
        assertThat(read(login(email)).get("method").asText()).isEqualTo("EMAIL");

        String coachEmail = email("tf-coach");
        String coachToken = register(coachEmail);
        jdbcTemplate.update("update app_users set role = 'COACH' where email = ?", coachEmail);
        assertThat(read(send(get("/api/account/two-factor"), coachToken)).get("emailMethodAvailable").asBoolean()).isFalse();
        send(post("/api/account/two-factor/email"), coachToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Coach and admin accounts must use an authenticator app."));
    }

    @Test
    void tfFr07_newRecoveryCodesReplaceTheOldOnes() throws Exception {
        String email = email("tf-new-codes");
        String token = register(email);
        String secret = setUpAuthenticator(token);
        JsonNode fresh = read(send(post("/api/account/two-factor/recovery-codes"), token).andExpect(status().isOk()));
        assertThat(fresh.get("recoveryCodes")).hasSize(10);
        assertThat(events(email, "RECOVERY_CODES_REGENERATED")).hasSize(1);

        String challenge = read(login(email)).get("challengeToken").asText();
        mockMvc.perform(post("/api/auth/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", challenge, "recoveryCode", fresh.get("recoveryCodes").get(0).asText()))))
                .andExpect(status().isOk());
        assertThat(secret).isNotBlank();
    }

    @Test
    void tfFr08_turningOffRemovesEverything_andIsRefusedForACoachPastTheirDate() throws Exception {
        String email = email("tf-off");
        String token = register(email);
        setUpAuthenticator(token);
        send(post("/api/account/two-factor/disable"), token).andExpect(status().isNoContent());
        assertThat(read(send(get("/api/account/two-factor"), token)).get("enabled").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from two_factor_recovery_codes r join app_users u on u.id = r.user_id"
                + " where u.email = ?", Integer.class, email)).isZero();
        assertThat(read(login(email)).get("token").asText()).as("signs in with the password alone again").isNotBlank();

        String coachEmail = email("tf-coach-off");
        String coachToken = register(coachEmail);
        setUpAuthenticator(coachToken);
        jdbcTemplate.update("update app_users set role = 'COACH', two_factor_due_at = ? where email = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(60)), coachEmail);
        assertThat(read(send(get("/api/account/two-factor"), coachToken)).get("canTurnOff").asBoolean()).isFalse();
        send(post("/api/account/two-factor/disable"), coachToken).andExpect(status().isBadRequest());
    }

    @Test
    void tfFr23_trustedDevicesAreListedAndRemoved_onlyByTheirOwner() throws Exception {
        String email = email("tf-list");
        String token = register(email);
        String secret = setUpAuthenticator(token);
        String challenge = read(login(email)).get("challengeToken").asText();
        mockMvc.perform(post("/api/auth/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) Version/18.1 Mobile Safari/604.1")
                .content(json(Map.of("challengeToken", challenge, "code", TotpCodes.code(secret, TotpCodes.step(clock.instant()) + 1),
                        "rememberDevice", true)))).andExpect(status().isOk());

        JsonNode devices = read(send(get("/api/account/two-factor"), token)).get("trustedDevices");
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).get("label").asText()).isEqualTo("Safari on iOS");
        assertThat(devices.get(0).has("tokenHash")).isFalse();
        long id = devices.get(0).get("id").asLong();

        String otherToken = register(email("tf-list-other"));
        send(delete("/api/account/trusted-devices/" + id), otherToken).andExpect(status().isNotFound());
        send(delete("/api/account/trusted-devices/" + id), token).andExpect(status().isNoContent());
        assertThat(read(send(get("/api/account/two-factor"), token)).get("trustedDevices")).isEmpty();
        send(delete("/api/account/trusted-devices"), token).andExpect(status().isNoContent());
    }

    private String setUpAuthenticator(String token) throws Exception {
        String secret = read(send(post("/api/account/two-factor/totp"), token)).get("secret").asText();
        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", currentCode(secret))).andExpect(status().isOk());
        return secret;
    }

    private String currentCode(String secret) {
        return TotpCodes.code(secret, TotpCodes.step(clock.instant()));
    }

    private ResultActions verify(String challenge, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("challengeToken", challenge, "code", code))));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", PASSWORD))));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private List<String> events(String email, String type) {
        return new ArrayList<>(jdbcTemplate.queryForList("select e.detail from security_events e join app_users u on u.id = e.actor_id"
                + " where u.email = ? and e.event_type = ? order by e.id", String.class, email, type));
    }

    private String register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Two Factor", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
