package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.CommunicationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record CommunicationMessageResponse(
        Long id,
        Long partnerId,
        Long relatedDreamId,
        Long relatedGoalId,
        Long relatedTaskId,
        String audience,
        String purpose,
        String subject,
        String hook,
        String problem,
        String request,
        String benefitToPartner,
        String wordPicture,
        String expectedOutcome,
        String objectionsAndAnswers,
        String socialProof,
        String valueComparison,
        String callToAction,
        String messageBody,
        CommunicationStatus status,
        LocalDate followUpDate,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
