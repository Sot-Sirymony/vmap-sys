package com.visionmapping.dto.response;

/**
 * The session handed back by register/login.
 *
 * <p>{@code appearance} rides along by design (FR-39.6): fetching the theme in a
 * separate request after sign-in would paint the default theme first and swap it
 * a moment later — the most visible possible bug in a theming feature. Returning
 * it with the session makes the first paint correct.
 */
public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        String role,
        AppearancePreferencesResponse appearance
) {
}
