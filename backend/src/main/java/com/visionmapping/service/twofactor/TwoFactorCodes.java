package com.visionmapping.service.twofactor;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.lang.Nullable;

/**
 * V21 (TF-FR-04, TF-FR-15, TF-FR-21, TF-NFR-01): recovery codes, challenge and
 * device tokens, and how they are hashed. Pure functions, tested on their own.
 */
public final class TwoFactorCodes {

    static final int RECOVERY_CODE_COUNT = 10;
    static final int RECOVERY_CODE_LENGTH = 10;
    /** No 0/O, 1/I/L: easy to read back from paper. 31 letters, about 49 bits per code. */
    static final String RECOVERY_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int TOKEN_BYTES = 32;

    private static final String HMAC = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TwoFactorCodes() {
    }

    /** TF-FR-04: ten new recovery codes, shown as "ABCDE-FGHJK". */
    public static List<String> newRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder(RECOVERY_CODE_LENGTH + 1);
            for (int c = 0; c < RECOVERY_CODE_LENGTH; c++) {
                if (c == RECOVERY_CODE_LENGTH / 2) {
                    code.append('-');
                }
                code.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return List.copyOf(codes);
    }

    /**
     * What the person typed as a recovery code, in upper case without spaces or
     * dashes; null when it cannot be one.
     */
    @Nullable
    public static String normaliseRecoveryCode(@Nullable String typed) {
        if (typed == null) {
            return null;
        }
        String clean = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (clean.length() != RECOVERY_CODE_LENGTH) {
            return null;
        }
        for (char c : clean.toCharArray()) {
            if (RECOVERY_ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return clean;
    }

    /** A URL-safe random token for a sign-in challenge or a trusted device (256 bits). */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 in hex, for long random tokens. */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * HMAC-SHA256 with a server secret, for short codes (recovery and email
     * codes) that a plain hash would not protect. The "two-factor:" prefix keeps
     * these hashes apart from V20's step-up hashes even with the same secret.
     */
    public static String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(("two-factor:" + secret).getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    /** Compares two hashes in constant time. */
    public static boolean sameHash(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
}
