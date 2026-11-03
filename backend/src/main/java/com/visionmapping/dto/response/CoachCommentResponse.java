package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.CoachCommentTarget;
import java.time.Instant;

/** targetTitle names the dream, goal or step, or is null when it is general or gone. */
public record CoachCommentResponse(
        Long id,
        String coachName,
        CoachCommentTarget targetType,
        Long targetId,
        String targetTitle,
        String body,
        Instant createdAt,
        Instant readAt) {
}
