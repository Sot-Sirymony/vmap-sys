package com.visionmapping.exception;

import java.io.Serial;

/**
 * The record is in a state that does not allow the change, for example
 * editing a final report (V19 D-19-1, RMC-09). Answered with HTTP 409 and the
 * exception's message, which is written for the participant to read.
 */
public class StateConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StateConflictException(String message) {
        super(message);
    }
}
