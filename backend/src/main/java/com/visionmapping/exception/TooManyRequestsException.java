package com.visionmapping.exception;

/**
 * A caller has exceeded a rate limit. Distinct from BusinessRuleException
 * because it maps to 429 rather than 400: the request was well formed and may
 * well succeed later, which is what tells a client to back off and retry rather
 * than to change what it sent.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
