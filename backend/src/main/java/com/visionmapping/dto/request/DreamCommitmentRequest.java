package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DreamCommitmentRequest(
        @NotBlank @Size(max = 160) String signedName,
        @NotNull LocalDate startDate,
        @NotBlank @Size(max = 1000) String firstAction
) {
}
