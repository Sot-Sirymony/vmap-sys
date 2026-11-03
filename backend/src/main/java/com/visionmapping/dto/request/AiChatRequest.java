package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * conversationId left out starts a new conversation. language is en or km
 * (AI-NFR-16). page, dreamId and moduleNumber say what is on screen, so the
 * Dream Coach picks the right context (AI-FR-01).
 */
public record AiChatRequest(
        Long conversationId,
        @NotBlank @Size(max = 4000) String message,
        @Pattern(regexp = "en|km") String language,
        @Size(max = 200) String page,
        Long dreamId,
        Integer moduleNumber) {
}
