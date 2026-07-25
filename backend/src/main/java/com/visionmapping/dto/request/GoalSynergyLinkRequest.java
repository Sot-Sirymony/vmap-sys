package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * FR-35.1: link the goal in the path to {@code relatedGoalId}, with an optional
 * note on how they reinforce each other.
 */
public record GoalSynergyLinkRequest(
        @NotNull Long relatedGoalId,
        @Size(max = 2000) String note
) {
}
