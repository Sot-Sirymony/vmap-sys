package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V23 (UM-FR-36): the person's email typed exactly, and a reason kept in the security log. */
public record AdminUserDeleteRequest(
        @NotBlank @Size(max = 180) String confirmEmail,
        @NotBlank @Size(min = 10, max = 300) String reason
) {
}
