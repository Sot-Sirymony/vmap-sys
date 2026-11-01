package com.visionmapping.dto.response;

/** emailConfigured lets the page say plainly when turning this on would send nothing. */
public record ReminderSettingsResponse(boolean enabled, String timeZone, boolean emailConfigured, int sendHour) {
}
