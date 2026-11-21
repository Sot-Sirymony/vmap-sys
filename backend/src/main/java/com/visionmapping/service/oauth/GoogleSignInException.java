package com.visionmapping.service.oauth;

import java.io.Serial;

/**
 * V14: a Google sign-in step failed. Never reaches an HTTP answer as JSON: the
 * callback turns it into a redirect with {@link GoogleSignInFailure#reason()}
 * (GA-FR-09). The message is for the server log and never holds a token.
 */
public class GoogleSignInException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final GoogleSignInFailure failure;

    public GoogleSignInException(GoogleSignInFailure failure, String logMessage) {
        super(logMessage);
        this.failure = failure;
    }

    public GoogleSignInException(GoogleSignInFailure failure, String logMessage, Throwable cause) {
        super(logMessage, cause);
        this.failure = failure;
    }

    public GoogleSignInFailure failure() {
        return failure;
    }
}
