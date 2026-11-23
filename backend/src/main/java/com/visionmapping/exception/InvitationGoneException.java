package com.visionmapping.exception;

import java.io.Serial;

/** V23 (UM-FR-24): an invitation link that was used, replaced or has expired. Answered 410. */
public class InvitationGoneException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvitationGoneException(String message) {
        super(message);
    }
}
