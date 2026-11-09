package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.visionmapping.util.Copies;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * conversationId left out starts a new conversation. language is en or km
 * (AI-NFR-16). page, dreamId and moduleNumber say what is on screen, so the
 * Dream Coach picks the right context (AI-FR-01).
 *
 * V11 Phase 7 (PB-FR-41 to PB-FR-50): with surface and section the message goes
 * to the plan assistant for that section of the Plan page (PLAN) or of Build my
 * plan (BUILDER), which then needs dreamId. itemRef names one item in the
 * section the participant asked about.
 *
 * V15 Phase 1 (AH-FR-01 to AH-FR-11): with assistantPage the message goes to
 * the page assistant for one area of that page. itemId is the open obstacle,
 * message or module number, and form the unsaved form, sent as data only.
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
        @Size(max = 60) String itemRef,
        @Pattern(regexp = PageAssistantRequest.PAGES) String assistantPage,
        @Size(max = 40) @Pattern(regexp = PageAssistantRequest.AREA) String area,
        Long itemId,
        @Size(max = 60) Map<@Size(max = 60) String, @Size(max = 4000) String> form) {

    public AiChatRequest {
        form = Copies.map(form);
    }

    /** The page assistant request this message is about, or null for the Dream Coach or plan assistant. */
    @Nullable
    public PageAssistantRequest pageRequest() {
        return assistantPage == null ? null : new PageAssistantRequest(assistantPage, area == null ? "page" : area, itemId, form);
    }
}
