package com.visionmapping.dto.response;

/**
 * The session handed back by register/login.
 *
 * <p>{@code appearance} rides along by design (FR-39.6): fetching the theme in a
 * separate request after sign-in would paint the default theme first and swap it
 * a moment later — the most visible possible bug in a theming feature. Returning
 * it with the session makes the first paint correct.
 *
 * <p>V10 (SEC-07-04): {@code token} is the short-lived access token, kept in
 * memory by the client; {@code refreshToken} renews it and is kept in browser
 * storage; {@code expiresIn} is the access token's lifetime in seconds.
 */
public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        String role,
        AppearancePreferencesResponse appearance,
        String refreshToken,
        long expiresIn
) {
}
