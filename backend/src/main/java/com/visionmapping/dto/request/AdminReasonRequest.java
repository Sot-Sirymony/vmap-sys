package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V23 (UM-FR-31, UM-FR-33): blocking or unblocking needs a reason, kept in the security log. */
public record AdminReasonRequest(@NotBlank @Size(min = 10, max = 300) String reason) {
}
