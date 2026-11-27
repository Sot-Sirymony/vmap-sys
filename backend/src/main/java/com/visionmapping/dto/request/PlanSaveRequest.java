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

    /** V33 (D-33-11): the accepted SHOULD rules of an optional body; none when there is no body. */
    public static java.util.Set<String> acceptedShouldOf(@org.springframework.lang.Nullable PlanSaveRequest request) {
        return request == null || request.acceptedShould() == null ? java.util.Set.of() : java.util.Set.copyOf(request.acceptedShould());
    }

    public PlanSaveRequest {
        acceptedShould = Copies.list(acceptedShould);
    }
}
