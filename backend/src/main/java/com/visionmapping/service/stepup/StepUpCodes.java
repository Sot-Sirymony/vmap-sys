package com.visionmapping.service.stepup;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.lang.Nullable;

/**
 * V20 (SV-FR-10, SV-FR-11, SV-NFR-01): making, hashing and comparing codes, and
 * masking addresses. Pure functions, so they are tested on their own.
 */
public final class StepUpCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_SPACE = 1_000_000;
    private static final String HMAC = "HmacSHA256";

    private StepUpCodes() {
    }

    /** Six digits from a secure random source, leading zeros kept. */
    public static String newCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(CODE_SPACE));
    }

    /** What the person typed, with spaces and dashes removed; null when it cannot be a code. */
    @Nullable
    public static String normalise(@Nullable String typed) {
        if (typed == null) {
            return null;
        }
        String digits = typed.replaceAll("[\\s-]", "");
        return digits.matches("\\d{6}") ? digits : null;
    }

    /**
     * HMAC-SHA256 of the code with a server secret, in hex. A plain hash of a
     * six-digit code could be reversed by trying all million codes; the secret
     * prevents that for anyone who sees only the database.
     */
    public static String hash(String code, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(("step-up:" + secret).getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    /** Compares two hashes in constant time. */
    public static boolean sameHash(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 of an address, trimmed and in lower case, so a code is tied to it without keeping it (D-20-2). */
    public static String targetHash(String address) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(address.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** "s*****@g****.com": the first letter of the name and of the domain, and the ending (SV-FR-11). */
    public static String mask(String address) {
        String trimmed = address.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return "***";
        }
        String name = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String host = dot > 0 ? domain.substring(0, dot) : domain;
        String ending = dot > 0 ? domain.substring(dot) : "";
        return name.charAt(0) + "*".repeat(Math.max(3, name.length() - 1)) + "@"
                + host.charAt(0) + "*".repeat(Math.max(3, host.length() - 1)) + ending;
    }
}
