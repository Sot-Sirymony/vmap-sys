package com.visionmapping.dto.response;

import java.time.Instant;

/** V23: shown on the invitation page before the person chooses a password. */
public record InvitationInfoResponse(String fullName, String email, Instant expiresAt) {
}
