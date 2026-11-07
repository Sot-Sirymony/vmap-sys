package com.visionmapping.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * messages is null in the list and filled when one conversation is opened.
 * dreamId and section (V11, PB-FR-50) are set for a plan assistant conversation.
 */
public record AiConversationResponse(
        Long id,
        String title,
        Instant updatedAt,
        List<AiMessageResponse> messages,
        Long dreamId,
        String section) {
}
