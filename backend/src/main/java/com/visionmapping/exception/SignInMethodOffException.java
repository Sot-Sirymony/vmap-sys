package com.visionmapping.exception;

import java.io.Serial;

/**
 * V24 (SA-FR-10 to SA-FR-12): an Admin has turned this sign-in method off for
 * everyone but Admins. Answered 403 with a message saying which method to use.
 * For password sign-in it is raised only after the password matched, so it
 * tells a guesser nothing about the account.
 */
public class SignInMethodOffException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String PASSWORD_OFF = "Password sign-in is turned off. Sign in with Google.";
    public static final String PASSWORD_SIGN_UP_OFF = "Sign-up with a password is turned off. Sign up with Google.";
    public static final String GOOGLE_OFF = "Google sign-in is turned off. Sign in with your password.";

    public SignInMethodOffException(String message) {
        super(message);
    }
}
