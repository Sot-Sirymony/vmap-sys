package com.visionmapping.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChainAssessmentResponse(
        Long id,
        LocalDate takenOn,
        Integer settlingScore,
        Integer fearScore,
        Integer criticismScore,
        Integer visionScore,
        // Retired in V17 (BL-FR-31): set on past takes only.
        Integer knowledgeScore,
        Integer resourcesScore,
        // The average of the chains this take measured.
        BigDecimal averageScore,
        // V17 BL-FR-30: set on takes from V17 on.
        Integer prioritiesScore,
        Integer persistenceScore
) {
}
