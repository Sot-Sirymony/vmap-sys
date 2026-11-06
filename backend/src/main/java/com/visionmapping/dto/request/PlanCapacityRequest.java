package com.visionmapping.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** V11 (PB-FR-16): the hours a week the participant has for plan work; null forgets it. */
public record PlanCapacityRequest(
        @DecimalMin("0.5") @DecimalMax("100.0") BigDecimal hoursPerWeek) {
}
