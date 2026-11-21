package com.visionmapping.service.oauth;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * V14 (BRD 14.0.0, GA-FR-01, GA-FR-02, GA-FR-04, GA-SEC-11, GA-SEC-12): the
 * Google sign-in settings, read once from the environment.
 *
 * <p>The feature is on only when both {@code GOOGLE_CLIENT_ID} and
 * {@code GOOGLE_CLIENT_SECRET} are set. The client secret never leaves this
 * package: it has no public accessor, is left out of {@link #toString()}, and
 * is never logged.
 *
 * <p>Google's three addresses are settings only so the tests can point them at
 * a local stub (GA-NFR-02); production keeps the defaults.
 */
@Slf4j
@Component
public class GoogleOAuthSettings {

    static final String GOOGLE_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendUrl;
    private final String authorizationUri;
    private final String tokenUri;
    private final String jwkSetUri;
    private final Duration timeout;

    @Autowired
    public GoogleOAuthSettings(@Value("${app.oauth.google.client-id:}") String clientId,
                               @Value("${app.oauth.google.client-secret:}") String clientSecret,
                               @Value("${app.oauth.google.redirect-uri:}") String redirectUri,
                               @Value("${app.oauth.google.frontend-url:}") String frontendUrl,
                               @Value("${app.oauth.google.authorization-uri:" + GOOGLE_AUTHORIZATION_URI + "}") String authorizationUri,
                               @Value("${app.oauth.google.token-uri:" + GOOGLE_TOKEN_URI + "}") String tokenUri,
                               @Value("${app.oauth.google.jwk-set-uri:" + GOOGLE_JWK_SET_URI + "}") String jwkSetUri,
                               @Value("${app.oauth.google.timeout-seconds:5}") long timeoutSeconds) {
        this.clientId = trimmed(clientId);
        this.clientSecret = trimmed(clientSecret);
        this.redirectUri = trimmed(redirectUri);
        this.frontendUrl = withoutTrailingSlash(trimmed(frontendUrl));
        this.authorizationUri = orDefault(authorizationUri, GOOGLE_AUTHORIZATION_URI);
        this.tokenUri = orDefault(tokenUri, GOOGLE_TOKEN_URI);
        this.jwkSetUri = orDefault(jwkSetUri, GOOGLE_JWK_SET_URI);
        this.timeout = timeoutSeconds > 0 ? Duration.ofSeconds(timeoutSeconds) : DEFAULT_TIMEOUT;
        if (enabled()) {
            log.info("Google sign-in is on. Google returns people to {}", this.redirectUri);
        } else {
            log.info("Google sign-in is off: GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are not both set.");
        }
    }

    /** For tests: Google's real addresses and the 5-second timeout. */
    public GoogleOAuthSettings(String clientId, String clientSecret, String redirectUri, String frontendUrl) {
        this(clientId, clientSecret, redirectUri, frontendUrl, GOOGLE_AUTHORIZATION_URI, GOOGLE_TOKEN_URI,
                GOOGLE_JWK_SET_URI, DEFAULT_TIMEOUT.toSeconds());
    }

    /** GA-FR-02: on only when both the client ID and the client secret are present. */
    public boolean enabled() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    /** Public by design: Google shows it in the sign-in address. */
    public String clientId() {
        return clientId;
    }

    /** GA-FR-04: package-private, for the code exchange only. */
    String clientSecret() {
        return clientSecret;
    }

    /** The backend callback address registered in Google Cloud. */
    public String redirectUri() {
        return redirectUri;
    }

    /** Where the browser goes after the callback, without a trailing slash. */
    public String frontendUrl() {
        return frontendUrl;
    }

    String authorizationUri() {
        return authorizationUri;
    }

    String tokenUri() {
        return tokenUri;
    }

    String jwkSetUri() {
        return jwkSetUri;
    }

    /** GA-SEC-11: how long any one call to Google may take. */
    Duration timeout() {
        return timeout;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static String orDefault(String value, String fallback) {
        String clean = trimmed(value);
        return clean.isEmpty() ? fallback : clean;
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** GA-FR-04: the secret is never part of this. */
    @Override
    public String toString() {
        return "GoogleOAuthSettings[enabled=" + enabled() + ", clientId=" + clientId
                + ", redirectUri=" + redirectUri + ", frontendUrl=" + frontendUrl + "]";
    }
}
