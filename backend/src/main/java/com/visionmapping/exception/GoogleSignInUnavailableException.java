package com.visionmapping.exception;

import java.io.Serial;

/**
 * V14 (GA-FR-02): Google sign-in is not set up on this server. Maps to 503;
 * the frontend error screen shows the message and offers password sign-in.
 */
public class GoogleSignInUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Google sign-in is not set up yet. Please use your email and password.";

    public GoogleSignInUnavailableException() {
        super(MESSAGE);
    }
}
