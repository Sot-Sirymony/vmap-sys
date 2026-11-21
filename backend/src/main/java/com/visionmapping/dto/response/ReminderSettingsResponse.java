package com.visionmapping.dto.response;

/**
 * emailConfigured lets the page say plainly when turning this on would send nothing.
 * reminderEmail is the chosen address or null; sendsTo is where mail actually goes.
 * V19 (RM-FR-49): reportReminder is NONE, WEEKLY or MONTHLY; reportReminderDay is the weekday
 * of a weekly one (1 = Monday to 7 = Sunday), else null.
 */
public record ReminderSettingsResponse(
        boolean enabled,
        String timeZone,
        boolean emailConfigured,
        int sendHour,
        String reminderEmail,
        String sendsTo,
        String reportReminder,
        Integer reportReminderDay) {
}
