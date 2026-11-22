package com.visionmapping.service.twofactor;

import com.visionmapping.service.support.SecretCipher;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * V21 (TF-NFR-01): authenticator secrets are kept sealed with SecretCipher
 * (AES-256-GCM, SETTINGS_ENCRYPTION_KEY), so a copy of the database cannot
 * make anyone's codes.
 */
@Component
@RequiredArgsConstructor
public class TotpSecretVault {

    private final SecretCipher secretCipher;

    public String seal(String base32Secret) {
        return secretCipher.encrypt(base32Secret);
    }

    /** Empty when the value is missing or can no longer be opened (for example, the key changed). */
    public Optional<String> open(@Nullable String sealed) {
        return secretCipher.decrypt(sealed);
    }
}
