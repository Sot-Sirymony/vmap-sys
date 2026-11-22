package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.service.stepup.StepUpCodes;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V21 phase 1 (TF-FR-04, TF-FR-15, TF-FR-21, TF-NFR-01): recovery codes, tokens and hashes. */
class TwoFactorCodesTest {

    @Test
    void tfFr04_tenRecoveryCodesWithoutLookAlikeCharacters() {
        List<String> codes = TwoFactorCodes.newRecoveryCodes();
        assertThat(codes).hasSize(10).doesNotHaveDuplicates()
                .allSatisfy(code -> assertThat(code).matches("[2-9A-HJKMNP-Z]{5}-[2-9A-HJKMNP-Z]{5}"));
        assertThat(new HashSet<>(TwoFactorCodes.newRecoveryCodes())).doesNotContainAnyElementsOf(codes);
    }

    @Test
    void tfFr15_aTypedRecoveryCodeIsCleanedOrRefused() {
        assertThat(TwoFactorCodes.normaliseRecoveryCode(" abcde-fghjk ")).isEqualTo("ABCDEFGHJK");
        assertThat(TwoFactorCodes.normaliseRecoveryCode("ABCDE FGHJK")).isEqualTo("ABCDEFGHJK");
        assertThat(TwoFactorCodes.normaliseRecoveryCode("ABCDE-FGHJ0")).as("0 is not in the alphabet").isNull();
        assertThat(TwoFactorCodes.normaliseRecoveryCode("ABCDE-FGHJ")).as("too short").isNull();
        assertThat(TwoFactorCodes.normaliseRecoveryCode(null)).isNull();
    }

    @Test
    void tfNfr01_codesAreKeptAsAKeyedHashApartFromStepUpHashes() {
        String hash = TwoFactorCodes.hmac("ABCDEFGHJK", "server-secret");
        assertThat(hash).hasSize(64).isEqualTo(TwoFactorCodes.hmac("ABCDEFGHJK", "server-secret"));
        assertThat(TwoFactorCodes.hmac("ABCDEFGHJK", "another-secret")).isNotEqualTo(hash);
        assertThat(TwoFactorCodes.hmac("123456", "server-secret")).as("never equal to a V20 step-up hash")
                .isNotEqualTo(StepUpCodes.hash("123456", "server-secret"));
        assertThat(TwoFactorCodes.sameHash(hash, TwoFactorCodes.hmac("ABCDEFGHJK", "server-secret"))).isTrue();
        assertThat(TwoFactorCodes.sameHash(hash, TwoFactorCodes.hmac("ABCDEFGHJM", "server-secret"))).isFalse();
    }

    @Test
    void tfFr21_tokensAre256BitsAndStoredAsSha256() {
        String token = TwoFactorCodes.newToken();
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(TwoFactorCodes.newToken());
        assertThat(TwoFactorCodes.sha256(token)).hasSize(64).isNotEqualTo(token);
    }

    @Test
    void propertiesNeverShowTheSecret() {
        TwoFactorProperties properties = new TwoFactorProperties(true, "very-secret-value", 5, 30, 14);
        assertThat(properties.toString()).doesNotContain("very-secret-value");
        assertThat(properties.hash("ABCDEFGHJK")).isEqualTo(TwoFactorCodes.hmac("ABCDEFGHJK", "very-secret-value"));
        assertThat(properties.challengeLifetime()).hasMinutes(5);
        assertThat(properties.trustedDeviceLifetime()).hasDays(30);
        assertThat(properties.gracePeriod()).hasDays(14);
    }
}
