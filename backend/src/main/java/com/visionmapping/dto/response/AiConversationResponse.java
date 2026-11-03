package com.visionmapping.dto.response;

import java.time.Instant;
import java.util.List;

/** messages is null in the list and filled when one conversation is opened. */
public record AiConversationResponse(
        Long id,
        String title,
        Instant updatedAt,
        List<AiMessageResponse> messages) {
}
