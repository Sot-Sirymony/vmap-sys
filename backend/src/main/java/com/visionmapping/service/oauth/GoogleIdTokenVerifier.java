package com.visionmapping.service.oauth;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * V14 (GA-FR-12, GA-FR-13, GA-SEC-03 to GA-SEC-05): checks Google's ID token
 * before anything in it is trusted:
 *
 * <ul>
 *   <li>the RS256 signature against Google's published keys. The key set is
 *       cached and fetched again when a token names a key ID it does not know,
 *       so Google rotating its keys does not break sign-in;</li>
 *   <li>{@code iss} is {@code https://accounts.google.com} or {@code accounts.google.com};</li>
 *   <li>{@code aud} holds our client ID;</li>
 *   <li>{@code exp} is present and not passed, with 2 minutes of clock leeway;</li>
 *   <li>{@code nonce} equals the sign-in attempt's nonce;</li>
 *   <li>{@code email_verified} is true, and {@code sub} and {@code email} are present.</li>
 * </ul>
 *
 * Any failure is {@link GoogleSignInFailure#VERIFICATION}. The token itself is never logged.
 */
@Component
public class GoogleIdTokenVerifier {

    static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    static final Duration CLOCK_LEEWAY = Duration.ofMinutes(2);

    private final NimbusJwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleOAuthSettings settings, Clock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(settings.timeout());
        factory.setReadTimeout(settings.timeout());
        this.decoder = NimbusJwtDecoder.withJwkSetUri(settings.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory))
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_LEEWAY);
        timestamps.setClock(clock);
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps, GoogleIdTokenVerifier::expiryPresent, GoogleIdTokenVerifier::issuer,
                audience(settings.clientId()))));
    }

    /** The verified identity, or VERIFICATION when any check fails. */
    GoogleIdentity verify(String idToken, String expectedNonce) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException exception) {
            throw new GoogleSignInException(GoogleSignInFailure.VERIFICATION,
                    "Google ID token refused: " + exception.getClass().getSimpleName(), exception);
        }
        if (!expectedNonce.equals(jwt.getClaimAsString("nonce"))) {
            throw new GoogleSignInException(GoogleSignInFailure.VERIFICATION, "Google ID token nonce does not match");
        }
        if (!emailVerified(jwt.getClaims().get("email_verified"))) {
            throw new GoogleSignInException(GoogleSignInFailure.VERIFICATION, "Google email is not verified");
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new GoogleSignInException(GoogleSignInFailure.VERIFICATION, "Google ID token has no sub or email");
        }
        return new GoogleIdentity(subject, email.trim(), jwt.getClaimAsString("name"));
    }

    /** Google sends a boolean; a "true" string is accepted for safety. */
    private static boolean emailVerified(Object claim) {
        return Boolean.TRUE.equals(claim) || "true".equals(claim);
    }

    private static OAuth2TokenValidatorResult expiryPresent(Jwt jwt) {
        return jwt.getExpiresAt() != null ? OAuth2TokenValidatorResult.success() : failure("The token has no expiry");
    }

    /** Read as a string: "accounts.google.com" is not a URL. */
    private static OAuth2TokenValidatorResult issuer(Jwt jwt) {
        return GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success() : failure("The token is not from Google");
    }

    private static OAuth2TokenValidator<Jwt> audience(String clientId) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success() : failure("The token is for another app");
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
