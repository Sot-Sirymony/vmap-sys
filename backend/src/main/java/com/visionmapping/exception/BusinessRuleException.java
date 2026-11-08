package com.visionmapping.exception;

import java.io.Serial;

/**
 * A request broke one of the method's rules, for example a Blocked task without
 * a reason. Answered with HTTP 400 and the exception's message, which is written
 * for the participant to read.
 */
public class BusinessRuleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
