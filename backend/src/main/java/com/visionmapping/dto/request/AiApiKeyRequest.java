package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V9: a new Claude API key. It is encrypted on save and never sent back. */
public record AiApiKeyRequest(@NotBlank @Size(min = 20, max = 300) String apiKey) {
}
