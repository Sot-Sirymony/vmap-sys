package com.visionmapping.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank @Size(max = 100) String password) {
}
