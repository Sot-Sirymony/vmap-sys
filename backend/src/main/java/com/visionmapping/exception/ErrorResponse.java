package com.visionmapping.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;

/**
 * The JSON body of every error answer: when it happened, the HTTP status and
 * its name, a message safe to show the user, the request path, and a
 * reference.
 *
 * <p>V10 (SEC-10-02): the reference is the only link between what the user
 * sees and what the server logged. For an unexpected error the server log line
 * carries the same reference with the real cause; the client never sees the
 * cause, a class name, SQL or a stack trace.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String reference,
        /** V20: what happened, for a page that acts on it (StepUpException). Left out when null. */
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        /** V20: tries left after a wrong code. Left out when null. */
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer triesLeft,
        /** V20: on STEP_UP_REQUIRED, which code the page must ask for. Left out when null. */
        @JsonInclude(JsonInclude.Include.NON_NULL) String action
) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFERENCE_BYTES = 6;

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, newReference(), null, null, null);
    }

    /** V20: an answer with a code the page acts on, and the tries left after a wrong code. */
    public static ErrorResponse of(HttpStatus status, String message, String path, String code, Integer triesLeft, String action) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, newReference(), code, triesLeft,
                action);
    }

    /** Twelve hex characters: short enough to read out, random enough not to collide in a log search. */
    static String newReference() {
        byte[] bytes = new byte[REFERENCE_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
