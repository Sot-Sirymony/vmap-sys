package com.visionmapping.exception;

import java.io.Serial;

/**
 * The record does not exist, or it belongs to someone else (Core Business Rules
 * 13 and 14). Both cases get the same HTTP 404, so a caller cannot tell whether
 * another user's record exists.
 */
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
