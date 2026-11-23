package com.visionmapping.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

/**
 * V21 (BRD 21.0.0): a two-factor answer the page acts on. {@code code} says what
 * happened: TWO_FACTOR_WRONG_CODE (400, with {@code triesLeft}),
 * TWO_FACTOR_ENDED (410: start again from the password), TWO_FACTOR_INVALID
 * (400, malformed request) or TWO_FACTOR_SETUP_REQUIRED (403, phase 4).
 */
public class TwoFactorException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String WRONG_CODE = "TWO_FACTOR_WRONG_CODE";
    public static final String ENDED = "TWO_FACTOR_ENDED";
    public static final String INVALID = "TWO_FACTOR_INVALID";
    public static final String SETUP_REQUIRED = "TWO_FACTOR_SETUP_REQUIRED";

    /** TF-FR-34: an admin or coach past their set-up-by date may only set up two-step sign-in. */
    /** V24 (D-24-4): for a participant while two-step sign-in is required for everyone. */
    public static TwoFactorException setupRequiredForEveryone() {
        return new TwoFactorException(HttpStatus.FORBIDDEN, SETUP_REQUIRED,
                "Set up two-step sign-in to continue. Every account needs it.", null);
    }

    public static TwoFactorException setupRequired() {
        return new TwoFactorException(HttpStatus.FORBIDDEN, SETUP_REQUIRED,
                "Set up two-step sign-in to continue. Coach and admin accounts need it.", null);
    }

    private final transient HttpStatus status;
    private final String code;
    private final Integer triesLeft;

    public TwoFactorException(HttpStatus status, String code, String message, @Nullable Integer triesLeft) {
        super(message);
        this.status = status;
        this.code = code;
        this.triesLeft = triesLeft;
    }

    /** TF-FR-11, TF-FR-16: the challenge is unknown, used, expired or ended by wrong codes. */
    public static TwoFactorException ended() {
        return new TwoFactorException(HttpStatus.GONE, ENDED, "This sign-in step has ended. Please sign in again.", null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    @Nullable
    public Integer getTriesLeft() {
        return triesLeft;
    }
}
