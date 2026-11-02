package com.visionmapping.dto.response;

import java.time.Instant;

public record UiTextOverrideResponse(Long id, String language, String sourceText, String text, Instant updatedAt) {
}
