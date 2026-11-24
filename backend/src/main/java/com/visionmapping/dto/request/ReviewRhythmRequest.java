package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** V31 v1 step 3 (DC-FR-48): how often to review one dream. Empty weeklyDay or monthlyDay means none of that kind. */
public record ReviewRhythmRequest(
        boolean daily,
        @Pattern(regexp = "^(|MON|TUE|WED|THU|FRI|SAT|SUN)$") String weeklyDay,
        @Min(1) @Max(28) Integer monthlyDay,
        boolean quarterly) {
}
