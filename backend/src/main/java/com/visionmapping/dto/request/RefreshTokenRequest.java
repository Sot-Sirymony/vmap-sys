package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V10 (SEC-07-04): trading a refresh token for a new access token, or signing out with it. */
public record RefreshTokenRequest(@NotBlank @Size(max = 100) String refreshToken) {
}
