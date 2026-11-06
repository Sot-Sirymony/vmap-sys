package com.visionmapping.exception;

/**
 * V10 (SEC-07-04): a refresh token that was replaced a moment ago, almost
 * always because two tabs refreshed together. Answered with 409, not 401: the
 * client reads the newer token another tab saved and tries again, and the
 * session stays open.
 */
public class SessionSupersededException extends RuntimeException {

    public SessionSupersededException() {
        super("This sign-in was just renewed in another tab. Try again.");
    }
}
