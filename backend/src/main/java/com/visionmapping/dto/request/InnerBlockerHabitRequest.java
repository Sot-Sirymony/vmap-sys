package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V34 (D-34-6): the name of the daily habit made from a practice, edited by the participant. */
public record InnerBlockerHabitRequest(@NotBlank @Size(max = 120) String name) {
}
