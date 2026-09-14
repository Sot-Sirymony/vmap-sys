package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.ExpectationAgreement;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ObstacleRequest(
        Long relatedDreamId,
        Long relatedGoalId,
        Long relatedStepId,
        Long relatedTaskId,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @NotNull ObstacleType obstacleType,
        @NotNull Severity severity,
        @Size(max = 3000) String solution,
        @Size(max = 3000) String rootCause,
        @Size(max = 3000) String creativeAlternatives,
        @Size(max = 2000) String conflictIncident,
        @Size(max = 2000) String conflictCost,
        @Size(max = 2000) String conflictOtherPerspective,
        @Size(max = 2000) String conflictLesson,
        @Size(max = 2000) String conflictPrivateNote,
        @Size(max = 2000) String conflictNextAction,
        // FR-61.1: diagnostic only — see BR-50.
        @Size(max = 2000) String conflictExpectation,
        ExpectationAgreement conflictExpectationAgreed,
        // FR-62.1: completeness (not content) gates Resolved — see BR-51.
        Boolean conflictNoCharacterAttacks,
        Boolean conflictStayedOnIncident,
        Boolean conflictNoThreatsOrSarcasm,
        Boolean conflictDefinedWinWin,
        // FR-60.1: diagnostic only — see BR-49.
        @Size(max = 2000) String criticismOverstated,
        @Size(max = 2000) String criticismDelivery,
        @Size(max = 2000) String criticismSubstance,
        Long requiredPartnerId,
        @NotNull ObstacleStatus status
) {
}
