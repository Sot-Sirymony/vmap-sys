package com.visionmapping.dto.response;

import java.time.Instant;

/**
 * FR-35.2: a synergy link seen from one goal's side — {@code goalId} is the goal
 * being viewed, and the {@code relatedGoal*} fields describe the goal on the
 * other end. {@code crossVisionArea} is true when the two goals sit in different
 * Vision Areas, which is what makes the link a cross-pollination candidate.
 */
public record GoalSynergyLinkResponse(
        Long id,
        Long goalId,
        Long relatedGoalId,
        String relatedGoalCode,
        String relatedGoalTitle,
        String relatedGoalVisionAreaName,
        boolean crossVisionArea,
        String note,
        Instant createdAt
) {
}
