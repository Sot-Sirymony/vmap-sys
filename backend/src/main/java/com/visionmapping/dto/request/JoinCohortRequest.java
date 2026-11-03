package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinCohortRequest(@NotBlank @Size(max = 12) String joinCode) {
}
