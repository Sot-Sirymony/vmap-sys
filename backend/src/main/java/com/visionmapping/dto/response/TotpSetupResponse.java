package com.visionmapping.dto.response;

import java.time.Instant;

/**
 * V21 (TF-FR-02): what the authenticator app needs: the Base32 key (shown as
 * text) and the otpauth:// address (shown as a QR code). Shown once; the setup
 * is forgotten after {@code expiresAt} unless confirmed.
 */
public record TotpSetupResponse(String secret, String otpauthUri, Instant expiresAt) {
}
