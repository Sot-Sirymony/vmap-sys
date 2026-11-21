package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V14 (GA-FR-23): the link code from the Google callback and the account's password. */
public record GoogleLinkRequest(
        @NotBlank @Size(max = 100) String linkToken,
        @NotBlank @Size(max = 100) String password
) {
}
