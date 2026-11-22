package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.SignInChallenge;
import com.visionmapping.entity.TrustedDevice;
import com.visionmapping.entity.TwoFactorRecoveryCode;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.entity.enums.TwoFactorMethod;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.SignInChallengeRepository;
import com.visionmapping.repository.TrustedDeviceRepository;
import com.visionmapping.repository.TwoFactorRecoveryCodeRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * V21 phase 1 (BRD 21.0.0): the new tables run on the real migrations, the
 * secret is stored sealed, tokens are unique, the daily clean-up removes only
 * what has ended, and deleting an account removes every two-factor row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorDataFlowTests {

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
    private SignInChallengeRepository challengeRepository;

    @Autowired
    private TrustedDeviceRepository deviceRepository;

    @Autowired
    private TotpSecretVault vault;

    @Autowired
    private TwoFactorProperties properties;

    @Autowired
    private TwoFactorCleanupJob cleanupJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void tfNfr01_theAuthenticatorSecretIsStoredSealedAndOpensAgain() throws Exception {
        AppUser user = user("tf-sealed");
        String secret = TotpCodes.newSecret();
        settingsRepository.saveAndFlush(UserTwoFactor.builder().userId(user.getId()).method(TwoFactorMethod.TOTP)
                .totpSecret(vault.seal(secret)).enabledAt(now()).build());

        String stored = jdbcTemplate.queryForObject("select totp_secret from two_factor_settings where user_id = ?", String.class, user.getId());
        assertThat(stored).startsWith("v1:").doesNotContain(secret);
        UserTwoFactor read = settingsRepository.findById(user.getId()).orElseThrow();
        assertThat(read.isEnabled()).isTrue();
        assertThat(vault.open(read.getTotpSecret())).hasValue(secret);
        assertThat(vault.open("v1:damaged")).isEmpty();
    }

    @Test
    void tfFr04_recoveryCodesAreStoredAsHashesAndCountedWhileUnused() throws Exception {
        AppUser user = user("tf-recovery");
        for (String code : TwoFactorCodes.newRecoveryCodes()) {
            recoveryRepository.save(TwoFactorRecoveryCode.builder().user(user)
                    .codeHash(properties.hash(TwoFactorCodes.normaliseRecoveryCode(code))).createdAt(now()).build());
        }
        TwoFactorRecoveryCode first = recoveryRepository.findByUser_IdAndUsedAtIsNull(user.getId()).get(0);
        first.setUsedAt(now());
        recoveryRepository.saveAndFlush(first);

        assertThat(recoveryRepository.countByUser_IdAndUsedAtIsNull(user.getId())).isEqualTo(9);
        assertThat(jdbcTemplate.queryForList("select code_hash from two_factor_recovery_codes where user_id = ?", String.class, user.getId()))
                .hasSize(10).allSatisfy(hash -> assertThat(hash).hasSize(64));
        assertThat(recoveryRepository.deleteByUserId(user.getId())).isEqualTo(10);
    }

    @Test
    void challengeAndDeviceTokensAreUnique() throws Exception {
        AppUser user = user("tf-unique");
        String hash = TwoFactorCodes.sha256(TwoFactorCodes.newToken());
        challengeRepository.saveAndFlush(challenge(user, hash, now().plus(Duration.ofMinutes(5))));
        assertThatThrownBy(() -> challengeRepository.saveAndFlush(challenge(user, hash, now().plus(Duration.ofMinutes(5)))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(challengeRepository.findByTokenHash(hash)).isPresent();

        String deviceHash = TwoFactorCodes.sha256(TwoFactorCodes.newToken());
        deviceRepository.saveAndFlush(device(user, deviceHash, now().plus(Duration.ofDays(30))));
        assertThatThrownBy(() -> deviceRepository.saveAndFlush(device(user, deviceHash, now().plus(Duration.ofDays(30)))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tfFr24_removingAllDevicesHidesThemFromTheList() throws Exception {
        AppUser user = user("tf-devices");
        deviceRepository.save(device(user, TwoFactorCodes.sha256(TwoFactorCodes.newToken()), now().plus(Duration.ofDays(30))));
        deviceRepository.save(device(user, TwoFactorCodes.sha256(TwoFactorCodes.newToken()), now().plus(Duration.ofDays(30))));
        assertThat(deviceRepository.findByUser_IdAndRemovedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(user.getId(), now())).hasSize(2);

        assertThat(deviceRepository.removeAllForUser(user.getId(), now())).isEqualTo(2);
        assertThat(deviceRepository.findByUser_IdAndRemovedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(user.getId(), now())).isEmpty();
    }

    @Test
    void theDailyCleanUpRemovesOnlyWhatHasEnded() throws Exception {
        AppUser stale = user("tf-clean-stale");
        AppUser fresh = user("tf-clean-fresh");
        settingsRepository.saveAndFlush(UserTwoFactor.builder().userId(stale.getId()).method(TwoFactorMethod.TOTP).totpSecret("v1:x").build());
        settingsRepository.saveAndFlush(UserTwoFactor.builder().userId(fresh.getId()).method(TwoFactorMethod.TOTP).totpSecret("v1:x").build());
        jdbcTemplate.update("update two_factor_settings set created_at = ? where user_id = ?",
                java.sql.Timestamp.from(now().minus(Duration.ofMinutes(16))), stale.getId());
        SignInChallenge oldChallenge = challengeRepository.save(challenge(stale, TwoFactorCodes.sha256("a" + System.nanoTime()), now().minus(Duration.ofDays(2))));
        SignInChallenge openChallenge = challengeRepository.save(challenge(stale, TwoFactorCodes.sha256("b" + System.nanoTime()), now().plus(Duration.ofMinutes(5))));
        TrustedDevice oldDevice = deviceRepository.save(device(stale, TwoFactorCodes.sha256("c" + System.nanoTime()), now().minus(Duration.ofDays(8))));
        TrustedDevice liveDevice = deviceRepository.save(device(stale, TwoFactorCodes.sha256("d" + System.nanoTime()), now().plus(Duration.ofDays(30))));

        cleanupJob.deleteOld();

        assertThat(settingsRepository.findById(stale.getId())).as("unfinished setup older than 15 minutes").isEmpty();
        assertThat(settingsRepository.findById(fresh.getId())).isPresent();
        assertThat(challengeRepository.findById(oldChallenge.getId())).isEmpty();
        assertThat(challengeRepository.findById(openChallenge.getId())).isPresent();
        assertThat(deviceRepository.findById(oldDevice.getId())).isEmpty();
        assertThat(deviceRepository.findById(liveDevice.getId())).isPresent();
    }

    @Test
    void tfFr42_deletingTheAccountRemovesEveryTwoFactorRow() throws Exception {
        String email = email("tf-delete");
        String token = register(email);
        AppUser user = appUserRepository.findByEmail(email).orElseThrow();
        settingsRepository.save(UserTwoFactor.builder().userId(user.getId()).method(TwoFactorMethod.EMAIL).enabledAt(now()).build());
        recoveryRepository.save(TwoFactorRecoveryCode.builder().user(user).codeHash(properties.hash("ABCDEFGHJK")).createdAt(now()).build());
        challengeRepository.save(challenge(user, TwoFactorCodes.sha256(TwoFactorCodes.newToken()), now().plus(Duration.ofMinutes(5))));
        deviceRepository.saveAndFlush(device(user, TwoFactorCodes.sha256(TwoFactorCodes.newToken()), now().plus(Duration.ofDays(30))));

        mockMvc.perform(post("/api/account/delete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        for (String table : new String[] {"two_factor_settings", "two_factor_recovery_codes", "sign_in_challenges", "trusted_devices"}) {
            assertThat(jdbcTemplate.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, user.getId()))
                    .as(table).isZero();
        }
    }

    private SignInChallenge challenge(AppUser user, String tokenHash, Instant expiresAt) {
        return SignInChallenge.builder().user(user).tokenHash(tokenHash).method(TwoFactorMethod.TOTP).rememberMe(true)
                .returnPath("/").origin(SignInOrigin.PASSWORD).expiresAt(expiresAt).createdAt(now()).build();
    }

    private TrustedDevice device(AppUser user, String tokenHash, Instant expiresAt) {
        return TrustedDevice.builder().user(user).tokenHash(tokenHash).label("Chrome on macOS").createdAt(now())
                .lastUsedAt(now()).expiresAt(expiresAt).build();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private AppUser user(String prefix) throws Exception {
        String email = email(prefix);
        register(email);
        return appUserRepository.findByEmail(email).orElseThrow();
    }

    private String register(String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Two Factor", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
