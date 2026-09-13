package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.OfferType;
import com.visionmapping.entity.enums.PartnerMotivator;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.WorkStyleArchetype;
import java.time.Instant;

public record PartnerResponse(
        Long id,
        String code,
        String name,
        String role,
        String organization,
        String email,
        String phone,
        String strength,
        PartnerSupportType supportType,
        OfferType offerType,
        Long relatedVisionAreaId,
        Long relatedDreamId,
        Long relatedGoalId,
        Long relatedStepId,
        Long relatedTaskId,
        PartnerStatus status,
        String notes,
        Boolean flagDishonesty,
        Boolean flagAnger,
        Boolean flagPoorJudgment,
        Boolean flagOutsizedReward,
        Boolean flagFlatteryPressure,
        Boolean flagGossip,
        Boolean flagDisregardBoundaries,
        String riskOverrideNote,
        PartnerMotivator primaryMotivator,
        // FR-50.2: non-null once the BR-39 gate has cleared for this
        // partner; the gate never re-fires afterward.
        Instant vettedAt,
        // FR-49.3: the user's own estimate, never a partner self-report.
        WorkStyleArchetype workStyleType,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
