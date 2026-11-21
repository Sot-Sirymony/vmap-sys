package com.visionmapping.dto.request;

import com.visionmapping.service.stepup.StepUpAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * V20 (SV-FR-10): ask for a "Verify it's you" code for one action. target is the
 * new reminder address for CONFIRM_REMINDER_EMAIL, and must be left out otherwise.
 */
public record StepUpRequest(@NotNull StepUpAction action, @Size(max = 255) String target) {
}
