package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * V9 (v0.1 FR-11): the participant's saved pitch (module 11 rows: pitch,
 * practice, self-rating) and the coach's rating, null until rated.
 */
public record PitchReviewResponse(
        List<Map<String, String>> rows,
        Integer coachRating,
        String coachFeedback,
        String coachName,
        Instant coachRatedAt) {
    public PitchReviewResponse {
        rows = Copies.list(rows);
    }
}
