package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V31 (D-31-4, DC-FR-53): the rules whose SHOULD fails the participant
 * accepted with "I understand" before Save my plan. Counted only.
 */
public record PlanSaveRequest(@Size(max = 8) List<@NotNull @Pattern(regexp = "^R-0[1-8]$") String> acceptedShould) {

    public PlanSaveRequest {
        acceptedShould = Copies.list(acceptedShould);
    }
}
