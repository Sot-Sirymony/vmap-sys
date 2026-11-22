package com.visionmapping.service.support;

import com.visionmapping.entity.AiSettings;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.repository.AiSettingsRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * V10 (SEC-04-04): after a rotation of SETTINGS_ENCRYPTION_KEY, re-seals every
 * stored secret that still opens only with an older key (the previous key, or
 * the pre-V10 JWT-derived key) with the current one. Runs once at start-up and
 * logs how many values it changed, so the operator knows when
 * SETTINGS_ENCRYPTION_KEY_PREVIOUS can be removed.
 *
 * <p>Covers the Claude API key and authenticator secrets (current and pending).
 * Google sign-in attempts are left out: they last 10 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretResealJob {

    private final SecretCipher cipher;
    private final AiSettingsRepository aiSettingsRepository;
    private final UserTwoFactorRepository userTwoFactorRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            int changed = resealAll();
            if (changed > 0) {
                log.info("Re-sealed {} stored secrets with the current SETTINGS_ENCRYPTION_KEY", changed);
            }
        } catch (RuntimeException exception) {
            // Old values still open with the previous key; the next start tries again.
            log.error("Could not re-seal stored secrets with the current key", exception);
        }
    }

    /** @return how many values were sealed again */
    @Transactional
    public int resealAll() {
        int changed = 0;
        for (AiSettings settings : aiSettingsRepository.findAll()) {
            changed += reseal(settings.getApiKeyEncrypted(), settings::setApiKeyEncrypted);
        }
        for (UserTwoFactor settings : userTwoFactorRepository.findAll()) {
            changed += reseal(settings.getTotpSecret(), settings::setTotpSecret);
            changed += reseal(settings.getPendingTotpSecret(), settings::setPendingTotpSecret);
        }
        return changed;
    }

    private int reseal(String sealed, Consumer<String> store) {
        return cipher.reseal(sealed).map(fresh -> {
            store.accept(fresh);
            return 1;
        }).orElse(0);
    }
}
