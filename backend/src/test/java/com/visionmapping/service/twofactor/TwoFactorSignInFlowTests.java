package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.TwoFactorRecoveryCode;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.TwoFactorMethod;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.TwoFactorRecoveryCodeRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.mail.StepUpMailer.Delivery;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V21 phase 2 (BRD 21.0.0, TF-FR-10 to TF-FR-26; TFC-01 to TFC-05, TFC-14 to
 * TFC-18): the second step of password sign-in. 2FA is switched on through the
 * repositories, since the setup pages come in phase 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorSignInFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserTwoFactorRepository settingsRepository;

    @Autowired
    private TwoFactorRecoveryCodeRepository recoveryRepository;

    @Autowired
    private TotpSecretVault vault;

    @Autowired
    private TwoFactorProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockitoBean
    private StepUpMailer mailer;

    @BeforeEach
    void mailWorks() {
        reset(mailer);
        when(mailer.delivery()).thenReturn(Delivery.EMAIL);
    }

    @Test
    void tfc02_withoutTwoFactorSignInIsUnchanged() throws Exception {
        String email = email("tf-none");
        register(email);
        JsonNode answer = read(login(email, null).andExpect(status().isOk()));
        assertThat(answer.get("token").asText()).isNotBlank();
        assertThat(answer.has("status")).isFalse();
        assertThat(answer.has("deviceToken")).isFalse();
    }

    @Test
    void tfc01_tfc14_tfc15_theAuthenticatorCodeOpensTheSession_andTheSameCodeNeverWorksTwice() throws Exception {
        String email = email("tf-totp");
        AppUser user = register(email);
        String secret = enableTotp(user);

        JsonNode challenge = read(login(email, null).andExpect(status().isOk()));
        assertThat(challenge.get("status").asText()).isEqualTo("TWO_FACTOR_REQUIRED");
        assertThat(challenge.get("method").asText()).isEqualTo("TOTP");
        assertThat(challenge.has("token")).as("TFC-14: no session before the second step").isFalse();
        assertThat(challenge.has("refreshToken")).isFalse();
        String token = challenge.get("challengeToken").asText();
        assertThat(jdbcTemplate.queryForObject("select token_hash from sign_in_challenges where user_id = ?", String.class, user.getId()))
                .as("only the hash is kept").hasSize(64).isNotEqualTo(token);

        verifyCode(token, "000000", null, false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_WRONG_CODE"))
                .andExpect(jsonPath("$.triesLeft").value(4));
        String code = currentCode(secret);
        JsonNode session = read(verifyCode(token, code, null, false).andExpect(status().isOk()));
        assertThat(session.get("token").asText()).isNotBlank();
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + session.get("token").asText()))
                .andExpect(status().isOk());
        assertThat(events(user.getId(), "LOGIN_SUCCEEDED")).contains("remember me, two-factor");
        // A completed challenge.
        verifyCode(token, code, null, false).andExpect(status().isGone());

        String second = read(login(email, null)).get("challengeToken").asText();
        // TFC-15: the same code again.
        verifyCode(second, code, null, false).andExpect(status().isBadRequest());
    }

    @Test
    void tfc16_fiveWrongCodesEndTheChallenge_andCountTowardTheSignInLock() throws Exception {
        String email = email("tf-guess");
        enableTotp(register(email));
        String token = read(login(email, null)).get("challengeToken").asText();
        for (int left = 4; left >= 0; left--) {
            verifyCode(token, "000000", null, false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.triesLeft").value(left));
        }
        verifyCode(token, "000000", null, false).andExpect(status().isGone());
        login(email, null).andExpect(status().isTooManyRequests());
        assertThat(jdbcTemplate.queryForObject("select count(*) from security_events where event_type = 'TWO_FACTOR_LOCKED' and actor_id = ?",
                Integer.class, appUserRepository.findByEmail(email).orElseThrow().getId())).isEqualTo(1);
    }

    @Test
    void tfc17_unknownExpiredAndOtherAccountsChallengesAreRefused() throws Exception {
        verifyCode("not-a-real-challenge", "123456", null, false).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_ENDED"));

        String email = email("tf-expired");
        AppUser user = register(email);
        String secret = enableTotp(user);
        String token = read(login(email, null)).get("challengeToken").asText();
        jdbcTemplate.update("update sign_in_challenges set expires_at = ? where user_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user.getId());
        verifyCode(token, currentCode(secret), null, false).andExpect(status().isGone());

        // A challenge is bound to its own account: another account's code cannot complete it.
        String otherEmail = email("tf-other");
        String otherSecret = enableTotp(register(otherEmail));
        String mine = read(login(email, null)).get("challengeToken").asText();
        verifyCode(mine, currentCode(otherSecret), null, false).andExpect(status().isBadRequest());
    }

    @Test
    void tfc05_aRecoveryCodeWorksOnce_andThePersonIsTold() throws Exception {
        String email = email("tf-recovery");
        AppUser user = register(email);
        enableTotp(user);
        List<String> codes = addRecoveryCodes(user);

        String token = read(login(email, null)).get("challengeToken").asText();
        verifyCode(token, null, codes.get(0).toLowerCase().replace("-", " "), false).andExpect(status().isOk());
        assertThat(events(user.getId(), "RECOVERY_CODE_USED")).containsExactly("9 left");
        assertThat(recoveryRepository.countByUser_IdAndUsedAtIsNull(user.getId())).isEqualTo(9);

        String again = read(login(email, null)).get("challengeToken").asText();
        verifyCode(again, null, codes.get(0), false).andExpect(status().isBadRequest());
        verifyCode(again, null, codes.get(1), false).andExpect(status().isOk());
    }

    @Test
    void tfc03_anEmailCodeSignsIn_aNewCodeReplacesTheOld_andOnlyOnceAMinute() throws Exception {
        String email = email("tf-email");
        AppUser user = register(email);
        settingsRepository.saveAndFlush(UserTwoFactor.builder().userId(user.getId()).method(TwoFactorMethod.EMAIL)
                .enabledAt(clock.instant()).build());

        JsonNode challenge = read(login(email, null).andExpect(status().isOk()));
        assertThat(challenge.get("method").asText()).isEqualTo("EMAIL");
        assertThat(challenge.get("sentTo").asText()).contains("***@e").doesNotContain(email);
        assertThat(challenge.get("emailUnavailable").asBoolean()).isFalse();
        String token = challenge.get("challengeToken").asText();
        String first = sentCode(1);

        mockMvc.perform(post("/api/auth/two-factor/resend").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("challengeToken", token)))).andExpect(status().isOk());
        String second = sentCode(2);
        mockMvc.perform(post("/api/auth/two-factor/resend").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("challengeToken", token)))).andExpect(status().isTooManyRequests());

        if (!first.equals(second)) {
            // The old code.
            verifyCode(token, first, null, false).andExpect(status().isBadRequest());
        }
        verifyCode(token, second, null, false).andExpect(status().isOk());
    }

    @Test
    void threatModel_aKnownPasswordCannotFloodTheMailbox_fiveSignInCodesAnHour() throws Exception {
        String email = email("tf-flood");
        AppUser user = register(email);
        settingsRepository.saveAndFlush(UserTwoFactor.builder().userId(user.getId()).method(TwoFactorMethod.EMAIL)
                .enabledAt(clock.instant()).build());
        for (int i = 0; i < 5; i++) {
            assertThat(read(login(email, null)).get("emailUnavailable").asBoolean()).isFalse();
        }
        JsonNode sixth = read(login(email, null).andExpect(status().isOk()));
        assertThat(sixth.get("emailUnavailable").asBoolean()).as("the sixth within the hour is not sent").isTrue();
        verify(mailer, times(5)).sendSignInCode(eq(email), anyString(), anyLong());
    }

    @Test
    void tfFr38_withoutMailAnEmailAccountIsToldToUseARecoveryCode() throws Exception {
        when(mailer.delivery()).thenReturn(Delivery.NONE);
        String email = email("tf-nomail");
        AppUser user = register(email);
        settingsRepository.saveAndFlush(UserTwoFactor.builder().userId(user.getId()).method(TwoFactorMethod.EMAIL)
                .enabledAt(clock.instant()).build());
        List<String> codes = addRecoveryCodes(user);

        JsonNode challenge = read(login(email, null));
        assertThat(challenge.get("emailUnavailable").asBoolean()).isTrue();
        verifyCode(challenge.get("challengeToken").asText(), "123456", null, false).andExpect(status().isBadRequest());
        verifyCode(challenge.get("challengeToken").asText(), null, codes.get(0), false).andExpect(status().isOk());
    }

    @Test
    void tfc04_tfc18_aRememberedBrowserSkipsTheSecondStep_onlyForItsAccount_untilThePasswordChanges() throws Exception {
        String email = email("tf-device");
        AppUser user = register(email);
        String secret = enableTotp(user);

        String token = read(login(email, null)).get("challengeToken").asText();
        JsonNode session = read(verifyCode(token, currentCode(secret), null, true).andExpect(status().isOk()));
        String device = session.get("deviceToken").asText();
        assertThat(device).hasSize(43);
        assertThat(jdbcTemplate.queryForObject("select label from trusted_devices where user_id = ?", String.class, user.getId()))
                .isEqualTo("Chrome on macOS");

        JsonNode direct = read(login(email, device).andExpect(status().isOk()));
        assertThat(direct.get("token").asText()).as("no second step on the remembered browser").isNotBlank();

        String otherEmail = email("tf-device-other");
        enableTotp(register(otherEmail));
        assertThat(read(login(otherEmail, device)).get("status").asText()).as("TFC-18: another account").isEqualTo("TWO_FACTOR_REQUIRED");

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + direct.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "Quiet-River-Stone-77"))))
                .andExpect(status().isNoContent());
        JsonNode afterChange = read(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "Quiet-River-Stone-77", "deviceToken", device)))));
        assertThat(afterChange.get("status").asText()).as("TF-FR-24").isEqualTo("TWO_FACTOR_REQUIRED");
    }

    @Test
    void tfFr20_aDisabledAccountCannotFinishItsSecondStep() throws Exception {
        String email = email("tf-disabled");
        AppUser user = register(email);
        String secret = enableTotp(user);
        String token = read(login(email, null)).get("challengeToken").asText();
        jdbcTemplate.update("update app_users set status = 'DISABLED' where id = ?", user.getId());
        verifyCode(token, currentCode(secret), null, false).andExpect(status().isGone());
    }

    private String enableTotp(AppUser user) {
        String secret = TotpCodes.newSecret();
        settingsRepository.saveAndFlush(UserTwoFactor.builder().userId(user.getId()).method(TwoFactorMethod.TOTP)
                .totpSecret(vault.seal(secret)).enabledAt(clock.instant()).build());
        return secret;
    }

    private List<String> addRecoveryCodes(AppUser user) {
        List<String> codes = TwoFactorCodes.newRecoveryCodes();
        for (String code : codes) {
            recoveryRepository.save(TwoFactorRecoveryCode.builder().user(user)
                    .codeHash(properties.hash(TwoFactorCodes.normaliseRecoveryCode(code))).createdAt(clock.instant()).build());
        }
        return codes;
    }

    private String currentCode(String secret) {
        return TotpCodes.code(secret, TotpCodes.step(clock.instant()));
    }

    private String sentCode(int calls) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(calls)).sendSignInCode(anyString(), code.capture(), anyLong());
        return code.getAllValues().get(calls - 1);
    }

    private ResultActions login(String email, String deviceToken) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("email", email, "password", PASSWORD));
        if (deviceToken != null) {
            body.put("deviceToken", deviceToken);
        }
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions verifyCode(String token, String code, String recoveryCode, boolean remember) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("challengeToken", token, "rememberDevice", remember));
        if (code != null) {
            body.put("code", code);
        }
        if (recoveryCode != null) {
            body.put("recoveryCode", recoveryCode);
        }
        return mockMvc.perform(post("/api/auth/two-factor/verify").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 Chrome/131.0 Safari/537.36")
                .content(json(body)));
    }

    private List<String> events(Long userId, String type) {
        return jdbcTemplate.queryForList("select detail from security_events where actor_id = ? and event_type = ? order by id",
                String.class, userId, type);
    }

    private AppUser register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Two Factor", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        return appUserRepository.findByEmail(email).orElseThrow();
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
