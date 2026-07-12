package com.visionmapping.excel;

/**
 * Signals that a single import row cannot be turned into a valid record.
 * Caught per row so one bad row is skipped and reported, not fatal to the
 * whole import.
 */
class RowParseException extends RuntimeException {

    RowParseException(String message) {
        super(message);
    }
}
