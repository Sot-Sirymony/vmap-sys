package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretCipherTest {

    @Test
    void roundTripsAndUsesAFreshIvEachTime() {
        SecretCipher cipher = new SecretCipher("settings-key", "jwt");
        String first = cipher.encrypt("sk-ant-secret");
        assertThat(first).startsWith("v1:").doesNotContain("sk-ant-secret");
        assertThat(cipher.encrypt("sk-ant-secret")).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("sk-ant-secret");
    }

    @Test
    void anotherKeyOrDamagedValueGivesNullNotAnError() {
        String sealed = new SecretCipher("one", "jwt").encrypt("sk-ant-secret");
        assertThat(new SecretCipher("two", "jwt").decrypt(sealed)).isNull();
        assertThat(new SecretCipher("one", "jwt").decrypt("v1:not-base64!")).isNull();
        assertThat(new SecretCipher("one", "jwt").decrypt(null)).isNull();
        assertThat(new SecretCipher("one", "jwt").decrypt("v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).isNull();
    }

    @Test
    void fallsBackToTheJwtSecretWhenNoSettingsKeyIsSet() {
        String sealed = new SecretCipher("", "jwt-secret").encrypt("value");
        assertThat(new SecretCipher(null, "jwt-secret").decrypt(sealed)).isEqualTo("value");
    }
}
