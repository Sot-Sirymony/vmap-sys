package com.visionmapping.service.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets kept in the database, such as the Claude API key, with
 * AES-256-GCM. The key is a SHA-256 of SETTINGS_ENCRYPTION_KEY, or of the JWT
 * secret when that is not set, so a copy of the database alone cannot reveal
 * the secret. Changing the source key makes stored secrets unreadable; they
 * must then be entered again.
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(@Value("${app.security.settings-encryption-key:}") String settingsKey,
                        @Value("${app.security.jwt-secret}") String jwtSecret) {
        String source = settingsKey == null || settingsKey.isBlank() ? jwtSecret : settingsKey;
        if (settingsKey == null || settingsKey.isBlank()) {
            log.info("SETTINGS_ENCRYPTION_KEY is not set; stored secrets are encrypted with a key derived from JWT_SECRET.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
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
        if (sealed == null || !sealed.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
            if (all.length < IV_BYTES + TAG_BITS / 8) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException exception) {
            // Some providers wrap a failed tag check in a runtime exception.
            return null;
        }
    }
}
