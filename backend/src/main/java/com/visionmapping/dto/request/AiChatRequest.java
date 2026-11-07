package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * conversationId left out starts a new conversation. language is en or km
 * (AI-NFR-16). page, dreamId and moduleNumber say what is on screen, so the
 * Dream Coach picks the right context (AI-FR-01).
 *
 * V11 Phase 7 (PB-FR-41 to PB-FR-50): with surface and section the message goes
 * to the plan assistant for that section of the Plan page (PLAN) or of Build my
 * plan (BUILDER), which then needs dreamId. itemRef names one item in the
 * section the participant asked about.
 */
public record AiChatRequest(
        Long conversationId,
        @NotBlank @Size(max = 4000) String message,
        @Pattern(regexp = "en|km") String language,
        @Size(max = 200) String page,
        Long dreamId,
        Integer moduleNumber,
        @Pattern(regexp = PlanAssistantRequest.SURFACES) String surface,
        @Pattern(regexp = PlanAssistantRequest.SECTIONS) String section,
        @Size(max = 60) String itemRef) {
}
