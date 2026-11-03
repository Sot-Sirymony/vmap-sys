package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;

/** AI-NFR-03: true gives the one AI consent (D-2); false withdraws it. */
public record AiConsentRequest(@NotNull Boolean consent) {
}
