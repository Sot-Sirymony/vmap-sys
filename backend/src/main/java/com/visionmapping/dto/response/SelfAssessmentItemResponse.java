package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.GapCoverMethod;
import com.visionmapping.entity.enums.Relevance;
import com.visionmapping.entity.enums.SelfAssessmentSource;
import com.visionmapping.entity.enums.StrengthKind;
import java.time.Instant;

/** One strength or weakness. outsideMismatch is true when an outside view was recorded and the user does not agree with it. */
public record SelfAssessmentItemResponse(
        Long id,
        Long dreamId,
        StrengthKind kind,
        String description,
        String evidence,
        Relevance relevance,
        SelfAssessmentSource source,
        String outsideBy,
        String outsideView,
        Boolean outsideAgrees,
        boolean outsideMismatch,
        GapCoverMethod coverMethod,
        String coverNote,
        Long linkedPartnerId,
        Long linkedStepId,
        Integer linkedModuleNumber,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
