package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.ExpectationAgreement;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import java.time.Instant;

public record ObstacleResponse(
        Long id,
        Long relatedDreamId,
        Long relatedGoalId,
        Long relatedStepId,
        Long relatedTaskId,
        String title,
        String description,
        ObstacleType obstacleType,
        Severity severity,
        String solution,
        String rootCause,
        String creativeAlternatives,
        String conflictIncident,
        String conflictCost,
        String conflictOtherPerspective,
        String conflictLesson,
        // FR-54.2 / BR-43: never included in any Excel export sheet.
        String conflictPrivateNote,
        String conflictNextAction,
        // FR-61: diagnostic only — see BR-50.
        String conflictExpectation,
        ExpectationAgreement conflictExpectationAgreed,
        // FR-61.2: non-null once released; never re-fires afterward.
        Instant expectationReleasedAt,
        // FR-62.1: completeness (not content) gates Resolved — see BR-51.
        Boolean conflictNoCharacterAttacks,
        Boolean conflictStayedOnIncident,
        Boolean conflictNoThreatsOrSarcasm,
        Boolean conflictDefinedWinWin,
        Long requiredPartnerId,
        ObstacleStatus status,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
