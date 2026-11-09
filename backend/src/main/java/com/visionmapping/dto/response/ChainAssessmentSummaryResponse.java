package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.util.List;

/** Oldest first. improvementPercent compares latest to baseline; null until there are two. */
public record ChainAssessmentSummaryResponse(
        List<ChainAssessmentResponse> assessments,
        BigDecimal improvementPercent,
        boolean retakeSuggested,
        // V15 (AH-FR-28): the latest take is 4 weeks old or more and a module was completed since.
        boolean retakeAfterModule
) {
    public ChainAssessmentSummaryResponse {
        assessments = Copies.list(assessments);
    }
}
