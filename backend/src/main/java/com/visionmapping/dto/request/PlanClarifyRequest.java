package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** V11 (PB-FR-08): the dream's clarifying questions answered so far, at most three. */
public record PlanClarifyRequest(
        @Size(max = 3) List<@NotNull @Valid PlanClarification> answers) {
    public PlanClarifyRequest {
        answers = Copies.list(answers);
    }
}
