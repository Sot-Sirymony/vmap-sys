package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V21 (TF-FR-03): the first code from the authenticator app, to finish setup. */
public record TotpConfirmRequest(@NotBlank @Size(max = 20) String code) {
}
