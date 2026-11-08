package com.visionmapping.excel;

import java.io.Serial;

/**
 * Signals that a single import row cannot be turned into a valid record.
 * Caught per row so one bad row is skipped and reported, not fatal to the
 * whole import.
 */
class RowParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    RowParseException(String message) {
        super(message);
    }
}
