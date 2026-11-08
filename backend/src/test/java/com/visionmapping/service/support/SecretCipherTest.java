package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecretCipherTest {

    @Test
    void roundTripsAndUsesAFreshIvEachTime() {
        SecretCipher cipher = new SecretCipher("settings-key", "jwt");
        String first = cipher.encrypt("sk-ant-secret");
        assertThat(first).startsWith("v1:").doesNotContain("sk-ant-secret");
        assertThat(cipher.encrypt("sk-ant-secret")).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).contains("sk-ant-secret");
    }

    @Test
    void anotherKeyOrDamagedValueGivesNullNotAnError() {
        String sealed = new SecretCipher("one", "jwt").encrypt("sk-ant-secret");
        assertThat(new SecretCipher("two", "jwt").decrypt(sealed)).isEmpty();
        assertThat(new SecretCipher("one", "jwt").decrypt("v1:not-base64!")).isEmpty();
        assertThat(new SecretCipher("one", "jwt").decrypt(null)).isEmpty();
        assertThat(new SecretCipher("one", "jwt").decrypt("v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).isEmpty();
    }

    @Test
    void fallsBackToTheJwtSecretWhenNoSettingsKeyIsSet() {
        String sealed = new SecretCipher("", "jwt-secret").encrypt("value");
        assertThat(new SecretCipher(null, "jwt-secret").decrypt(sealed)).contains("value");
    }

    private static final String STRONG_KEY = "a-settings-key-that-is-long-enough-1234";
    private static final String JWT = "a-jwt-secret-that-is-also-long-enough-5678";

    /** V10 (SEC-04-03): the Claude key saved before V10 still works after the new variable is set. */
    @Test
    void readsValuesSealedWithTheOldJwtDerivedKey() {
        String sealedBeforeV10 = new SecretCipher("", JWT).encrypt("sk-ant-old");

        SecretCipher afterV10 = new SecretCipher(STRONG_KEY, JWT);

        assertThat(afterV10.decrypt(sealedBeforeV10)).contains("sk-ant-old");
        // New values use the new key only: the JWT secret alone cannot open them.
        String sealedAfter = afterV10.encrypt("sk-ant-new");
        assertThat(new SecretCipher("", JWT).decrypt(sealedAfter)).isEmpty();
    }

    @Test
    void productionRefusesToStartWithoutASeparateStrongKey() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new SecretCipher("", JWT, prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be set");
        assertThatThrownBy(() -> new SecretCipher("too-short", JWT, prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32");
        assertThatThrownBy(() -> new SecretCipher(JWT, JWT, prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("different from JWT_SECRET");
        assertThat(new SecretCipher(STRONG_KEY, JWT, prod).encrypt("x")).startsWith("v1:");
    }

    @Test
    void localProfileStillAllowsTheFallback() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");

        assertThat(new SecretCipher("", JWT, local).decrypt(new SecretCipher("", JWT).encrypt("v"))).contains("v");
    }
}
