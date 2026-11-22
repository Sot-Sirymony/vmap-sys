package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** V21 phase 1 (TF-FR-02, TF-FR-13, TFC-13, TFC-15): authenticator codes against the RFCs' own vectors. */
class TotpCodesTest {

    /** RFC 6238 Appendix B uses this 20-byte ASCII key for SHA-1. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String RFC_KEY_BASE32 = TotpCodes.base32(RFC_KEY);

    @ParameterizedTest
    @CsvSource({
            "59, 94287082",
            "1111111109, 07081804",
            "1111111111, 14050471",
            "1234567890, 89005924",
            "2000000000, 69279037",
            "20000000000, 65353130"
    })
    void tfc13_rfc6238AppendixBVectorsPass(long unixTime, String expected) {
        long step = TotpCodes.step(Instant.ofEpochSecond(unixTime));
        assertThat(TotpCodes.code(RFC_KEY, step, 8)).isEqualTo(expected);
        // The 6-digit code apps show is the same number, last six digits.
        assertThat(TotpCodes.code(RFC_KEY_BASE32, step)).isEqualTo(expected.substring(2));
    }

    @ParameterizedTest
    @CsvSource(value = {"'',''", "f,MY", "fo,MZXQ", "foo,MZXW6", "foob,MZXW6YQ", "fooba,MZXW6YTB", "foobar,MZXW6YTBOI"})
    void base32MatchesRfc4648AndReadsBack(String plain, String encoded) {
        assertThat(TotpCodes.base32(plain.getBytes(StandardCharsets.US_ASCII))).isEqualTo(encoded);
        assertThat(new String(TotpCodes.fromBase32(encoded), StandardCharsets.US_ASCII)).isEqualTo(plain);
    }

    @Test
    void aTypedKeyIsReadIgnoringCaseSpacesAndDashes() {
        assertThat(TotpCodes.fromBase32("mzxw 6ytb-oi==")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> TotpCodes.fromBase32("MZXW1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tfFr13_oneStepEitherSideIsAccepted_twoStepsAreNot() {
        String secret = TotpCodes.newSecret();
        long now = 58_000_000L;
        assertThat(TotpCodes.matchingStep(secret, TotpCodes.code(secret, now), now, null)).hasValue(now);
        assertThat(TotpCodes.matchingStep(secret, TotpCodes.code(secret, now - 1), now, null)).hasValue(now - 1);
        assertThat(TotpCodes.matchingStep(secret, TotpCodes.code(secret, now + 1), now, null)).hasValue(now + 1);
        assertThat(TotpCodes.matchingStep(secret, TotpCodes.code(secret, now - 2), now, null)).isEmpty();
        assertThat(TotpCodes.matchingStep(secret, TotpCodes.code(secret, now + 2), now, null)).isEmpty();
    }

    @Test
    void tfc15_aCodeThatWasAlreadyAcceptedCannotBeUsedAgain() {
        String secret = TotpCodes.newSecret();
        long now = 58_000_000L;
        String code = TotpCodes.code(secret, now);
        assertThat(TotpCodes.matchingStep(secret, code, now, now)).as("same step, already used").isEmpty();
        assertThat(TotpCodes.matchingStep(secret, TotpCodes.code(secret, now - 1), now, now)).as("an earlier step").isEmpty();
        assertThat(TotpCodes.matchingStep(secret, TotpCodes.code(secret, now + 1), now, now)).hasValue(now + 1);
    }

    @Test
    void typedCodesAreCleanedAndMalformedOnesRefused() {
        String secret = TotpCodes.newSecret();
        long now = 58_000_000L;
        String code = TotpCodes.code(secret, now);
        assertThat(TotpCodes.matchingStep(secret, code.substring(0, 3) + " " + code.substring(3), now, null)).hasValue(now);
        assertThat(TotpCodes.matchingStep(secret, null, now, null)).isEmpty();
        assertThat(TotpCodes.matchingStep(secret, "12345", now, null)).isEmpty();
        assertThat(TotpCodes.matchingStep(secret, "abcdef", now, null)).isEmpty();
    }

    @Test
    void tfFr02_aNewSecretIs160BitsAndTheAddressIsWhatAppsRead() {
        String secret = TotpCodes.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(TotpCodes.fromBase32(secret)).hasSize(20);
        assertThat(TotpCodes.newSecret()).isNotEqualTo(secret);
        assertThat(TotpCodes.otpauthUri("Vision Mapping", "ana+test@gmail.com", "JBSWY3DPEHPK3PXP")).isEqualTo(
                "otpauth://totp/Vision%20Mapping:ana%2Btest%40gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Vision%20Mapping"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
