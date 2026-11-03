package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CohortMemberRequest(@NotBlank @Size(max = 180) String email) {
}
