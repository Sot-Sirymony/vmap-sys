package com.visionmapping.dto.response;

/**
 * V21 (TF-FR-10, TF-FR-17): what a first factor (password or Google) answers:
 * either the session ({@link AuthResponse}), or, when two-factor sign-in is on
 * and the browser is not trusted, the second step ({@link TwoFactorChallengeResponse}).
 */
public sealed interface SignInAnswer permits AuthResponse, TwoFactorChallengeResponse {
}
