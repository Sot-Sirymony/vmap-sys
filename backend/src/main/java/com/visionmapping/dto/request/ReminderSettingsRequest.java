package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A blank or missing reminderEmail sends reminders to the login email; the service trims and checks it.
 * V19 (RM-FR-49): reportReminder (NONE, WEEKLY or MONTHLY) and reportReminderDay (1 = Monday to 7 =
 * Sunday, weekly only) are optional; left out, the report reminder stays as it was.
 */
public record ReminderSettingsRequest(
        boolean enabled,
        @NotBlank @Size(max = 60) String timeZone,
        @Size(max = 255) String reminderEmail,
        @Pattern(regexp = "NONE|WEEKLY|MONTHLY") String reportReminder,
        @Min(1) @Max(7) Integer reportReminderDay) {

    /** The settings before V19, with the report reminder left as it was. */
    public ReminderSettingsRequest(boolean enabled, String timeZone, String reminderEmail) {
        this(enabled, timeZone, reminderEmail, null, null);
    }
}
