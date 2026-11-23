package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * V23 (UM-FR-27): Edit user. The login email is not editable here. An empty
 * cohortId takes the person out of their cohort.
 */
public record AdminUserUpdateRequest(
        @NotBlank @Size(max = 150) String fullName,
        Long cohortId
) {
}
