package com.visionmapping.exception;

import java.io.Serial;

/**
 * V10 (SEC-07-03): a refresh token that is unknown, expired, revoked or reused.
 * Answered with 401 so the client signs the user out.
 */
public class SessionEndedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SessionEndedException() {
        super("Your session has ended. Please sign in again.");
    }
}
