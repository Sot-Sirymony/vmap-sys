package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * V23 (UM-FR-14): delete a custom role.
 *
 * @param moveTo the role its holders move to; needed only while someone holds it
 */
public record RoleDeleteRequest(@Size(max = 40) String moveTo, @NotBlank @Size(min = 10, max = 300) String reason) {
}
