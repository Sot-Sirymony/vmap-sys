package com.visionmapping.exception;

import java.time.Instant;

/**
 * The JSON body of every error answer: when it happened, the HTTP status and
 * its name, a message safe to show the user, and the request path.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
