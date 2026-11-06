package com.visionmapping.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's IP address, used only as a rate-limiting key.
 *
 * Render terminates TLS in front of the app, so the socket address is the proxy
 * for every caller and would rate-limit the whole internet as one client. The
 * first entry of X-Forwarded-For is the original caller.
 *
 * A client can forge that header, so this is not an identity — it is a
 * throttling key that makes casual abuse cost something. The per-email limits
 * are the ones that protect a person, and they cannot be sidestepped this way.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
