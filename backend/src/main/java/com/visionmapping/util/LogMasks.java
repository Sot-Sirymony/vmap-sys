package com.visionmapping.util;

import org.springframework.lang.Nullable;
/**
 * V10 (SEC-09-07): personal data is masked before it reaches a log line. An
 * operator can still tell addresses apart; a leaked log does not hand out a
 * list of users.
 */
public final class LogMasks {

    private LogMasks() {
    }

    /** "monysout69@gmail.com" becomes "mo***@gmail.com". */
    public static String email(@Nullable String email) {
        if (email == null || email.isBlank()) {
            return "(none)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String shown = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return shown + "***" + email.substring(at);
    }
}
