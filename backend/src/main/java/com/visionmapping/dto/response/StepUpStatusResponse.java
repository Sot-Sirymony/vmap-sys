package com.visionmapping.dto.response;

/**
 * V20 (SV-FR-34, SV-NFR-09): whether "Verify it's you" is on, how codes reach
 * people (EMAIL, LOG on a local or test run, NONE without a mail server), and
 * so whether protected actions ask for a code at all.
 */
public record StepUpStatusResponse(boolean enabled, String delivery, boolean active) {
}
