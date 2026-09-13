package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.CommunicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CommunicationMessageRequest(
        Long partnerId,
        Long relatedDreamId,
        Long relatedGoalId,
        Long relatedTaskId,
        @Size(max = 180) String audience,
        @Size(max = 500) String purpose,
        @Size(max = 220) String subject,
        @Size(max = 1000) String hook,
        @Size(max = 2000) String problem,
        @Size(max = 2000) String request,
        @Size(max = 2000) String benefitToPartner,
        @Size(max = 2000) String wordPicture,
        @Size(max = 2000) String expectedOutcome,
        @Size(max = 2000) String objectionsAndAnswers,
        @Size(max = 2000) String socialProof,
        @Size(max = 2000) String valueComparison,
        @Size(max = 2000) String callToAction,
        @Size(max = 6000) String messageBody,
        @NotNull CommunicationStatus status,
        LocalDate followUpDate
) {
}
