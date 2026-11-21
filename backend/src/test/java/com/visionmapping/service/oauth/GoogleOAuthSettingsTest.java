package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** V14 phase 1 (GA-FR-01, GA-FR-02, GA-FR-04): the Google settings and the on/off switch. */
class GoogleOAuthSettingsTest {

    private static final String REDIRECT = "http://localhost:8080/api/auth/oauth/google/callback";

    @Test
    void gaFr02_onOnlyWhenBothClientIdAndSecretAreSet() {
        assertThat(settings("id.apps.googleusercontent.com", "secret").enabled()).isTrue();
        assertThat(settings("", "secret").enabled()).isFalse();
        assertThat(settings("id.apps.googleusercontent.com", "  ").enabled()).isFalse();
        assertThat(settings(null, null).enabled()).isFalse();
    }

    @Test
    void gaFr01_valuesAreTrimmedAndTheFrontendUrlLosesItsTrailingSlash() {
        GoogleOAuthSettings settings = new GoogleOAuthSettings(" id ", " secret ", " " + REDIRECT + " ", "https://app.example.com/");
        assertThat(settings.clientId()).isEqualTo("id");
        assertThat(settings.clientSecret()).isEqualTo("secret");
        assertThat(settings.redirectUri()).isEqualTo(REDIRECT);
        assertThat(settings.frontendUrl()).isEqualTo("https://app.example.com");
    }

    @Test
    void gaFr04_theSecretIsNeverInToString() {
        String text = settings("id.apps.googleusercontent.com", "GOCSPX-very-secret").toString();
        assertThat(text).contains("enabled=true").contains("id.apps.googleusercontent.com").doesNotContain("GOCSPX-very-secret");
    }

    private static GoogleOAuthSettings settings(String clientId, String clientSecret) {
        return new GoogleOAuthSettings(clientId, clientSecret, REDIRECT, "http://localhost:5173");
    }
}
