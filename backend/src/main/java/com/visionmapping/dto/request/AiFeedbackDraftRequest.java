package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.CoachCommentTarget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** AI-FR-20: what the coach wants to comment on; targetId names the dream, goal or step. */
public record AiFeedbackDraftRequest(
        @NotNull CoachCommentTarget targetType,
        Long targetId,
        @Pattern(regexp = "en|km") String language) {
}
