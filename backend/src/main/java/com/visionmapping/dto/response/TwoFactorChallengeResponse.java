package com.visionmapping.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * V21 (TF-FR-10, TF-FR-38): the second step of signing in. No session exists
 * yet. {@code status} is always "TWO_FACTOR_REQUIRED" so the page can tell this
 * answer from a session. {@code sentTo} is the masked email for the email
 * method; {@code emailUnavailable} is true when the code could not be emailed,
 * so only a recovery code can finish.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TwoFactorChallengeResponse(
        String status,
        String challengeToken,
        String method,
        String sentTo,
        boolean emailUnavailable,
        Instant expiresAt
) implements SignInAnswer {

    public static final String STATUS = "TWO_FACTOR_REQUIRED";
}
