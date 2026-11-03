package com.visionmapping.dto.request;

import jakarta.validation.constraints.Size;

/** V9 (AI-FR-22): what the admin wants changed, such as "simpler words" or "add a farming example". */
public record AiModuleDraftRequest(@Size(max = 1000) String instruction) {
}
