package com.visionmapping.exception;

/**
 * A caller has exceeded a rate limit. Distinct from BusinessRuleException
 * because it maps to 429 rather than 400: the request was well formed and may
 * well succeed later, which is what tells a client to back off and retry rather
 * than to change what it sent.
 *
 * <p>When the wait is known, it is sent as a Retry-After header (V10, SEC-06-02).
 */
public class TooManyRequestsException extends RuntimeException {

    private final Long retryAfterSeconds;

    public TooManyRequestsException(String message) {
        this(message, null);
    }

    public TooManyRequestsException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Null when the wait is not known. */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
