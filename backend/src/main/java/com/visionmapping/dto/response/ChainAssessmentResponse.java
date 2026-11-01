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
        Integer knowledgeScore,
        Integer resourcesScore,
        BigDecimal averageScore
) {
}
