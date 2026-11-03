package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AI-FR-17: what the message is for. partnerId, when given, lets the draft use
 * the partner's name and role (AI-NFR-04 allows it for a named outreach); their
 * email and phone are never sent.
 */
public record AiOutreachRequest(
        Long partnerId,
        Long relatedDreamId,
        @Size(max = 500) String purpose,
        @NotBlank @Size(max = 2000) String request,
        @Pattern(regexp = "en|km") String language) {
}
