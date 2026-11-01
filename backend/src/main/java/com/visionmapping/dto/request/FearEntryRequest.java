package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.FearLikelihood;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FearEntryRequest(
        @NotBlank @Size(max = 500) String fear,
        @NotBlank @Size(max = 2000) String worstCase,
        @NotNull FearLikelihood likelihood,
        @NotBlank @Size(max = 2000) String firstTest,
        Long relatedDreamId
) {
}
