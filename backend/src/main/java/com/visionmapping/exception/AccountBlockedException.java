package com.visionmapping.exception;

import java.io.Serial;

/**
 * V23 (UM-FR-32): the password (or Google) was right, but the account is blocked.
 * Answered 403 with a message the person can act on. Raised only after the
 * password matched, so it tells a guesser nothing.
 */
public class AccountBlockedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "This account is blocked. Contact your program admin.";

    public AccountBlockedException() {
        super(MESSAGE);
    }
}
