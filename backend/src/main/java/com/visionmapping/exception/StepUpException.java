package com.visionmapping.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

/**
 * V20 (BRD 20.0.0): a "Verify it's you" answer the page acts on. code tells the
 * page what happened (STEP_UP_REQUIRED, STEP_UP_WRONG_CODE, STEP_UP_CODE_ENDED,
 * STEP_UP_MAIL_FAILED); triesLeft is set for a wrong code.
 */
public class StepUpException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String REQUIRED = "STEP_UP_REQUIRED";
    public static final String WRONG_CODE = "STEP_UP_WRONG_CODE";
    public static final String CODE_ENDED = "STEP_UP_CODE_ENDED";
    public static final String MAIL_FAILED = "STEP_UP_MAIL_FAILED";

    private final transient HttpStatus status;
    private final String code;
    private final Integer triesLeft;
    private final String action;

    public StepUpException(HttpStatus status, String code, String message, @Nullable Integer triesLeft) {
        this(status, code, message, triesLeft, null);
    }

    /** action names the code the page must ask for, on STEP_UP_REQUIRED. */
    public StepUpException(HttpStatus status, String code, String message, @Nullable Integer triesLeft, @Nullable String action) {
        super(message);
        this.status = status;
        this.code = code;
        this.triesLeft = triesLeft;
        this.action = action;
    }

    @Nullable
    public String getAction() {
        return action;
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
