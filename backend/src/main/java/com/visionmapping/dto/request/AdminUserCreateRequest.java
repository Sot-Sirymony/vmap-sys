package com.visionmapping.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * V23 (UM-FR-22): Add user. No password: the person chooses one from the
 * invitation (D-23-5). role is a role key; empty means Participant. The server
 * checks the viewer may give that role (D-23-9).
 */
public record AdminUserCreateRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 40) String role,
        Long cohortId
) {
}
