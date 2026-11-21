package com.visionmapping.service.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.lang.Nullable;

/**
 * V14 (GA-FR-06, GA-FR-07, GA-FR-08, GA-SEC-01, GA-SEC-02, GA-SEC-08): the
 * random values, hashes and checks the Google sign-in flow is built from.
 */
public final class OAuthValues {

    /** 256 bits: state, nonce and the one-time codes. */
    static final int RANDOM_BYTES = 32;
    /** 64 bytes gives an 86-character PKCE verifier, inside RFC 7636's 43 to 128. */
    static final int VERIFIER_BYTES = 64;
    static final int MAX_RETURN_PATH = 512;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OAuthValues() {
    }

    /** A URL-safe random value of the given number of bytes. */
    static String random(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** SHA-256 in hex: how state and codes are stored (GA-SEC-02). */
    static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /** RFC 7636 S256: BASE64URL(SHA-256(ASCII(verifier))), without padding. */
    static String pkceChallenge(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
    }

    /**
     * GA-FR-08: only a path inside the app. It must start with one "/", not
     * "//" or "/\" (both lead to another site in a browser), and hold no
     * control characters or spaces. Anything else becomes "/".
     */
    public static String safeReturnPath(@Nullable String from) {
        if (from == null || from.isEmpty() || from.length() > MAX_RETURN_PATH || from.charAt(0) != '/') {
            return "/";
        }
        if (from.length() > 1 && (from.charAt(1) == '/' || from.charAt(1) == '\\')) {
            return "/";
        }
        for (int i = 0; i < from.length(); i++) {
            char c = from.charAt(i);
            if (c <= ' ' || c == 0x7F || c == '\\') {
                return "/";
            }
        }
        return from;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
