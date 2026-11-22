package com.visionmapping.service.twofactor;

import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.entity.enums.TwoFactorMethod;
import com.visionmapping.repository.UserTwoFactorRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 (TF-FR-40): "Verify it's you" may take a current authenticator code
 * instead of the emailed one. The same rules as signing in apply: one step of
 * drift, and a code that was accepted once is never accepted again.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatorCodeCheck {

    private final UserTwoFactorRepository settingsRepository;
    private final TotpSecretVault vault;
    private final Clock clock;

    /** True when the person uses an authenticator app and the code is current and unused. */
    @Transactional
    public boolean accepts(Long userId, @Nullable String code) {
        Optional<UserTwoFactor> settings = settingsRepository.findById(userId)
                .filter(found -> found.isEnabled() && found.getMethod() == TwoFactorMethod.TOTP);
        Optional<String> secret = settings.flatMap(found -> vault.open(found.getTotpSecret()));
        if (secret.isEmpty()) {
            return false;
        }
        OptionalLong step = TotpCodes.matchingStep(secret.get(), code, TotpCodes.step(clock.instant()),
                settings.get().getLastTotpStep());
        return step.isPresent() && settingsRepository.acceptStep(userId, step.getAsLong()) == 1;
    }
}
