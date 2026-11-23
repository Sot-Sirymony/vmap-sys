package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V23 (UM-FR-09 to UM-FR-12): create or change a role.
 *
 * @param permissions   permission keys; "Use own plan" and the permissions others need are added (UM-FR-07)
 * @param reason        why, kept in the security log
 * @param cohortChanges when "Coach cohorts" is removed and holders coach active cohorts: who coaches each instead
 */
public record RoleRequest(
        @NotBlank @Size(min = 3, max = 40) String name,
        @Size(max = 200) String description,
        @NotNull @Size(max = 40) List<@NotBlank @Size(max = 60) String> permissions,
        @NotBlank @Size(min = 10, max = 300) String reason,
        @Valid @Size(max = 100) List<AdminRoleChangeRequest.CohortCoachChange> cohortChanges
) {

    public RoleRequest {
        permissions = Copies.list(permissions);
        cohortChanges = Copies.list(cohortChanges);
    }
}
