package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.WorkStyleArchetype;

/**
 * FR-49.2: {@code dominant} is null only when the user has never taken the
 * assessment. {@code secondary} is null whenever both axes read decisively —
 * see {@code WorkStyleProfileService} for the closeness rule.
 */
public record WorkStyleProfileResponse(
        WorkStyleArchetype dominant,
        WorkStyleArchetype secondary,
        Integer paceFastScore,
        Integer paceDeliberateScore,
        Integer focusTaskScore,
        Integer focusPeopleScore
) {
}
