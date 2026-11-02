package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A blank or missing reminderEmail sends reminders to the login email; the service trims and checks it. */
public record ReminderSettingsRequest(
        boolean enabled,
        @NotBlank @Size(max = 60) String timeZone,
        @Size(max = 255) String reminderEmail) {
}
