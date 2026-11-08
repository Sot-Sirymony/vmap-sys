package com.visionmapping.exception;

import java.io.Serial;

/**
 * AI-NFR-02: AI help cannot run right now (switched off, budget reached or
 * Claude unreachable). Maps to 503 so the page shows the fallback message and
 * everything else keeps working.
 */
public class AiUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "AI help is temporarily unavailable.";

    public AiUnavailableException() {
        super(MESSAGE);
    }
}
