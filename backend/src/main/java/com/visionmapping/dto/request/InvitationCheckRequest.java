package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V23: asks whose account an invitation link opens. Sent in a body so the token stays out of URLs and logs. */
public record InvitationCheckRequest(@NotBlank @Size(max = 100) String token) {
}
