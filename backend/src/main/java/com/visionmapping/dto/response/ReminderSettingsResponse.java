package com.visionmapping.dto.response;

/**
 * emailConfigured lets the page say plainly when turning this on would send nothing.
 * reminderEmail is the chosen address or null; sendsTo is where mail actually goes.
 */
public record ReminderSettingsResponse(
        boolean enabled,
        String timeZone,
        boolean emailConfigured,
        int sendHour,
        String reminderEmail,
        String sendsTo) {
}
