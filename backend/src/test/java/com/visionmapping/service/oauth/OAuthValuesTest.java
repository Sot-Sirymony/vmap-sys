package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** V14 phase 2 (GA-FR-06 to GA-FR-08, GA-SEC-01, GA-SEC-08). */
class OAuthValuesTest {

    @Test
    void gaSec01_pkceChallengeMatchesTheRfc7636Example() {
        assertThat(OAuthValues.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void gaFr06_randomValuesAreUrlSafeLongAndDifferent() {
        String verifier = OAuthValues.random(OAuthValues.VERIFIER_BYTES);
        assertThat(verifier).hasSize(86).matches("[A-Za-z0-9_-]+");
        assertThat(OAuthValues.random(OAuthValues.RANDOM_BYTES)).hasSize(43).isNotEqualTo(OAuthValues.random(OAuthValues.RANDOM_BYTES));
        assertThat(OAuthValues.sha256Hex("state")).hasSize(64).isNotEqualTo("state");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/dreams", "/dreams/12?tab=goals", "/tasks#today"})
    void gaFr08_inAppPathsAreKept(String path) {
        assertThat(OAuthValues.safeReturnPath(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "dreams", "//evil.example", "/\\evil.example", "https://evil.example", "/a b", "/a\\b",
            "javascript:alert(1)", "/\tx"})
    void gaFr08_anythingElseBecomesTheHomePage(String path) {
        assertThat(OAuthValues.safeReturnPath(path)).isEqualTo("/");
        assertThat(OAuthValues.safeReturnPath(null)).isEqualTo("/");
        assertThat(OAuthValues.safeReturnPath("/" + "a".repeat(OAuthValues.MAX_RETURN_PATH))).isEqualTo("/");
    }
}
