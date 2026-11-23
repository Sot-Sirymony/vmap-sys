package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V23 (BRD 23.0.0, UM-FR-29, UM-FR-30): change a person's role.
 *
 * @param role          the new role's key, for example {@code COACH}
 * @param reason        why, kept in the security log (10 to 300 characters)
 * @param cohortChanges when the person coaches active cohorts and the new role cannot coach: who coaches each instead
 */
public record AdminRoleChangeRequest(
        @NotBlank @Size(max = 40) String role,
        @NotBlank @Size(min = 10, max = 300) String reason,
        @Valid @Size(max = 100) List<CohortCoachChange> cohortChanges
) {

    public AdminRoleChangeRequest {
        cohortChanges = Copies.list(cohortChanges);
    }

    /** @param newCoachId the new coach's account id, or null to leave the cohort without a coach */
    public record CohortCoachChange(@jakarta.validation.constraints.NotNull Long cohortId, Long newCoachId) {
    }
}
