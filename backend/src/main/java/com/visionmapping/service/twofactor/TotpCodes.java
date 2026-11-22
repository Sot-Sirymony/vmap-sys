package com.visionmapping.service.twofactor;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.lang.Nullable;

/**
 * V21 (BRD 21.0.0, TF-FR-02, TF-FR-13, D-21-6): authenticator-app codes,
 * RFC 6238 (TOTP) over RFC 4226 (HOTP) with HMAC-SHA1, 6 digits and 30-second
 * steps, which every common authenticator app uses. Built on the JDK; pure
 * functions, so they are tested against the RFC's own vectors.
 */
public final class TotpCodes {

    static final int STEP_SECONDS = 30;
    static final int DIGITS = 6;
    /** One step either side, for clocks a little apart (TF-FR-13). */
    static final int DRIFT_STEPS = 1;
    /** 160 bits, the size RFC 4226 recommends. */
    static final int SECRET_BYTES = 20;

    private static final String HMAC = "HmacSHA1";
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpCodes() {
    }

    /** A new secret, Base32 without padding, as authenticator apps expect. */
    public static String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return base32(secret);
    }

    /** The 30-second step a moment falls in. */
    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    }

    /** The 6-digit code for a Base32 secret at a step. */
    public static String code(String base32Secret, long step) {
        return code(fromBase32(base32Secret), step, DIGITS);
    }

    /**
     * TF-FR-13: the step a typed code matches, within one step either side of
     * {@code nowStep}, and only if it is later than {@code lastUsedStep}, so a
     * code that was already accepted cannot be used again. Empty otherwise.
     */
    public static OptionalLong matchingStep(String base32Secret, @Nullable String typed, long nowStep, @Nullable Long lastUsedStep) {
        if (typed == null) {
            return OptionalLong.empty();
        }
        String digits = typed.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = fromBase32(base32Secret);
        for (long step = nowStep - DRIFT_STEPS; step <= nowStep + DRIFT_STEPS; step++) {
            boolean unused = lastUsedStep == null || step > lastUsedStep;
            if (unused && constantTimeEquals(code(key, step, DIGITS), digits)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * TF-FR-02: the address an authenticator app reads from the QR code,
     * {@code otpauth://totp/Issuer:account?secret=…&issuer=…&algorithm=SHA1&digits=6&period=30}.
     */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** RFC 4226 section 5.3: HMAC-SHA1 of the step, dynamic truncation, then the last digits. */
    static String code(byte[] key, long step, int digits) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % POWERS[digits]);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA1 is not available", exception);
        }
    }

    /** RFC 4648 Base32, upper case, without padding. */
    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    /** Reads Base32, ignoring case, spaces, dashes and padding (people type keys by hand). */
    static byte[] fromBase32(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == ' ' || c == '-' || c == '=') {
                continue;
            }
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a Base32 character");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String expected, String typed) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), typed.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
