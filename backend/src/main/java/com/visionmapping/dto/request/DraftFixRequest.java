package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** V11 (PB-FR-29): ask Claude for a fix to one AI issue in the saved draft. problem is sent as data. */
public record DraftFixRequest(
        @NotBlank @Size(max = 40) String itemKey,
        @NotNull @Size(max = 20) @Pattern(regexp = "VAGUE_STEP|UNREALISTIC_TIMING|GAP") String kind,
        @Size(max = 300) String problem) {
}
