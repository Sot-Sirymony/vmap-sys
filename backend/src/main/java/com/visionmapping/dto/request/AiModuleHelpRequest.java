package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** V9 Phase 7: what the participant has typed so far, unsaved, for a draft or feedback. */
public record AiModuleHelpRequest(
        @NotNull @Pattern(regexp = "START|DRAFT|FEEDBACK") String mode,
        @Size(max = 20) List<Map<String, String>> rows,
        @Pattern(regexp = "en|km") String language) {
}
