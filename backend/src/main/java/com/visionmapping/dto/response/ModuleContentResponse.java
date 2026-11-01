package com.visionmapping.dto.response;

import java.util.List;

/** FR-78: an admin's view of one module in one language. translated is false when it is showing English. */
public record ModuleContentResponse(
        Integer number,
        Integer position,
        String language,
        boolean translated,
        String title,
        String summary,
        String guidance,
        String exerciseInstructions,
        List<LearningModuleDetailResponse.Field> fields
) {
}
