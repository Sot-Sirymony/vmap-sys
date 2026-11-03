package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.CoachCommentTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** targetId names the dream, goal or step; it is left out for GENERAL. */
public record CoachCommentRequest(
        @NotNull CoachCommentTarget targetType,
        Long targetId,
        @NotBlank @Size(max = 2000) String body) {
}
