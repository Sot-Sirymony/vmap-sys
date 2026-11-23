package com.visionmapping.service.oauth;

/**
 * V14 (GA-FR-09 to GA-FR-12, GA-FR-17, GA-SEC-11): why a Google sign-in did not
 * finish. {@link #reason()} is the value in the frontend address
 * ({@code #result=error&reason=…}); it never carries a token or an address.
 */
public enum GoogleSignInFailure {
    /** GA-FR-10: the person pressed Cancel at Google. */
    CANCELLED("cancelled"),
    /** GA-FR-09: the state is unknown, expired or already used. */
    EXPIRED("expired"),
    /** GA-FR-12: the ID token failed a check, or Google's answer was malformed. */
    VERIFICATION("verification"),
    /** GA-SEC-11: Google was slow, unreachable or refused the code. */
    GOOGLE("google"),
    /** GA-FR-02, GA-FR-17: the feature is off, or the account cannot sign in. */
    UNAVAILABLE("unavailable"),
    /**
     * V23 (UM-FR-32): the account is blocked. Said plainly, as for a right password:
     * Google has already proved the person owns the address.
     */
    BLOCKED("blocked"),
    /** V24 (SA-FR-12): an Admin turned Google sign-in off for everyone but Admins. */
    METHOD_OFF("google-off"),
    /** GA-SEC-09: too many Google sign-in steps from this network. */
    TOO_MANY("too-many");

    private final String reason;

    GoogleSignInFailure(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
