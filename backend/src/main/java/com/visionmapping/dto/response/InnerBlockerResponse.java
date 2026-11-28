package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.InnerBlockerAnswer;
import com.visionmapping.entity.enums.InnerBlockerStatus;
import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

/**
 * V34: one inner blocker. habitStreak is filled only when the request gave the
 * user's local day. lastAnswers holds the latest four weekly answers, newest
 * first; practiceNotHelping is true after two No answers in a row (D-34-10).
 */
public record InnerBlockerResponse(
        Long id,
        String blocker,
        String showsUp,
        String practice,
        String chainKey,
        Long dreamId,
        String dreamTitle,
        Long habitId,
        String habitName,
        boolean habitArchived,
        Integer habitStreak,
        InnerBlockerStatus status,
        Instant resolvedAt,
        boolean archived,
        List<InnerBlockerAnswer> lastAnswers,
        boolean practiceNotHelping,
        Instant createdAt,
        Instant updatedAt
) {
    public InnerBlockerResponse {
        lastAnswers = Copies.list(lastAnswers);
    }
}
