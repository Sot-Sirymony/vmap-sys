package com.visionmapping.dto.response;

/**
 * V17 BL-FR-19, BL-FR-20: the new "find help" step, the partner created with
 * it (or null) and, when made from an obstacle, that obstacle now anchored to
 * the new step (or null).
 */
public record GapStepResponse(
        VisionStepResponse step,
        PartnerResponse partner,
        ObstacleResponse obstacle
) {
}
