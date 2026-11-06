package com.visionmapping.service.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets kept in the database, such as the Claude API key, with
 * AES-256-GCM. The key is a SHA-256 of SETTINGS_ENCRYPTION_KEY, so a copy of
 * the database alone cannot reveal the secret.
 *
 * <p>V10 (SEC-04-03): outside the local and test profiles SETTINGS_ENCRYPTION_KEY
 * is required, at least 32 characters, and different from JWT_SECRET, so the
 * two secrets can be rotated independently. Locally a blank key still falls
 * back to the JWT secret, as before.
 *
 * <p>Values saved before V10 were sealed with a key derived from JWT_SECRET.
 * Decryption tries that legacy key when the current one fails, so the stored
 * Claude key keeps working after the new variable is set; it is sealed with the
 * new key the next time an admin saves it. New values always use the new key.
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_KEY_LENGTH = 32;
    private static final Set<String> PROFILES_ALLOWING_FALLBACK = Set.of("local", "test");

    private final SecretKeySpec key;
    /** Null when the current key already is the JWT-derived one. */
    private final SecretKeySpec legacyKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public SecretCipher(@Value("${app.security.settings-encryption-key:}") String settingsKey,
                        @Value("${app.security.jwt-secret}") String jwtSecret,
                        Environment environment) {
        this(settingsKey, jwtSecret, allowsFallback(environment));
    }

    /** For tests: behaves like the local profile. */
    public SecretCipher(String settingsKey, String jwtSecret) {
        this(settingsKey, jwtSecret, true);
    }

    private SecretCipher(String settingsKey, String jwtSecret, boolean allowFallback) {
        boolean hasSettingsKey = settingsKey != null && !settingsKey.isBlank();
        if (!allowFallback) {
            validateForProduction(settingsKey, jwtSecret);
        }
        if (!hasSettingsKey) {
            log.info("SETTINGS_ENCRYPTION_KEY is not set; stored secrets are encrypted with a key derived from JWT_SECRET.");
        }
        this.key = derive(hasSettingsKey ? settingsKey : jwtSecret);
        this.legacyKey = hasSettingsKey && jwtSecret != null && !jwtSecret.isBlank() ? derive(jwtSecret) : null;
    }

    private static boolean allowsFallback(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(PROFILES_ALLOWING_FALLBACK::contains);
    }

    private static void validateForProduction(String settingsKey, String jwtSecret) {
        if (settingsKey == null || settingsKey.isBlank()) {
            throw new IllegalStateException(
                    "SETTINGS_ENCRYPTION_KEY must be set outside the local/test profiles (SEC-04-03).");
        }
        if (settingsKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "SETTINGS_ENCRYPTION_KEY must be at least " + MIN_KEY_LENGTH + " characters long.");
        }
        if (settingsKey.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "SETTINGS_ENCRYPTION_KEY must be different from JWT_SECRET, so each can be rotated on its own.");
        }
    }

    private static SecretKeySpec derive(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt a secret", exception);
        }
    }

    /** Null when the value was sealed with another key or is damaged. */
    public String decrypt(String sealed) {
        String plain = decryptWith(key, sealed);
        if (plain == null && legacyKey != null) {
            plain = decryptWith(legacyKey, sealed);
        }
        return plain;
    }

    private static String decryptWith(SecretKeySpec candidate, String sealed) {
        if (sealed == null || !sealed.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
            if (all.length < IV_BYTES + TAG_BITS / 8) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, candidate, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException exception) {
            // Some providers wrap a failed tag check in a runtime exception.
            return null;
        }
    }
}
