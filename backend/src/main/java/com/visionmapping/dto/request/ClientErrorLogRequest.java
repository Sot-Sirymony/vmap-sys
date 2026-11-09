package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A request that failed in the browser. status 0 means the server could not be
 * reached at all. The path is the API path without its query string.
 */
public record ClientErrorLogRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,40}") String page,
        @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE") String method,
        @NotBlank @Size(max = 300) String path,
        @Min(0) @Max(599) int status,
        @Size(max = 2000) String message,
        @Size(max = 40) String reference,
        @Size(max = 40) String appVersion
) {
}
