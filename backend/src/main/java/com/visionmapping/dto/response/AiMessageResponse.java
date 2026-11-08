package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.AiMessageRole;
import com.visionmapping.entity.enums.AiSafetyFlag;
import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

/** rating is null for the participant's own messages and unrated replies. */
public record AiMessageResponse(
        Long id,
        AiMessageRole role,
        String content,
        AiSafetyFlag safetyFlag,
        Integer rating,
        Instant createdAt,
        // AI-FR-05: what a reply was based on, such as plan or fears.
        List<String> contextSources) {
    public AiMessageResponse {
        contextSources = Copies.list(contextSources);
    }
}
