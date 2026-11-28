package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * V34 (D-34-2, D-34-3): the three parts of an inner blocker, an optional link
 * to one of the six chains and an optional dream (none = all dreams).
 */
public record InnerBlockerRequest(
        @NotBlank @Size(max = 120) String blocker,
        @Size(max = 500) String showsUp,
        @NotBlank @Size(max = 300) String practice,
        @Pattern(regexp = CHAIN_KEY_PATTERN) String chainKey,
        @Positive Long dreamId
) {
    /** The six chains of D-17-3; the retired chains can't be chosen (BL-FR-31). Empty means none. */
    public static final String CHAIN_KEY_PATTERN =
            "^(|settlingScore|fearScore|criticismScore|prioritiesScore|persistenceScore|visionScore)$";
}
