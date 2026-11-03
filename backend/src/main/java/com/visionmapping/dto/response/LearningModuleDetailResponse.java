package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.ModuleStage;
import com.visionmapping.entity.enums.ModuleStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * FR-72. An empty fields list means existing data proves the exercise:
 * requirementMessage then says what is still missing, or is null once it is met.
 */
public record LearningModuleDetailResponse(
        Integer number,
        Integer position,
        ModuleStage stage,
        String title,
        String summary,
        String guidance,
        String exerciseInstructions,
        String linkPath,
        List<Field> fields,
        List<Map<String, String>> rows,
        String requirementMessage,
        ModuleStatus status,
        Instant completedAt,
        // V9 (v0.1 FR-11): the coach's rating of the saved exercise, or null.
        Integer coachRating,
        String coachFeedback,
        String coachName,
        Instant coachRatedAt
) {

    public record Field(String key, String label, String type) {
    }
}
