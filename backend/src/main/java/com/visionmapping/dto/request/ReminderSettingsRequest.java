package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReminderSettingsRequest(boolean enabled, @NotBlank @Size(max = 60) String timeZone) {
}
