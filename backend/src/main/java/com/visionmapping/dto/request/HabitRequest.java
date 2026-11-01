package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HabitRequest(@NotBlank @Size(max = 120) String name) {
}
