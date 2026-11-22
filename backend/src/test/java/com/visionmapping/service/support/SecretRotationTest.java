package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AiSettings;
import com.visionmapping.entity.UserTwoFactor;
import com.visionmapping.repository.AiSettingsRepository;
import com.visionmapping.repository.UserTwoFactorRepository;
import com.visionmapping.service.twofactor.TwoFactorProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V10 (SEC-04-04): rotating the encryption and two-factor secrets loses nothing. */
class SecretRotationTest {

    private static final String JWT = "jwt-secret-for-rotation-tests";
    private final SecretCipher before = new SecretCipher("old-settings-key", JWT);
    private final SecretCipher during = new SecretCipher("new-settings-key", "old-settings-key", JWT);
    private final SecretCipher after = new SecretCipher("new-settings-key", JWT);

    @Test
    void duringARotationValuesSealedWithThePreviousKeyStillOpen() {
        String sealed = before.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(during.decrypt(sealed)).contains("JBSWY3DPEHPK3PXP");
        assertThat(after.decrypt(sealed)).isEmpty();
    }

    @Test
    void resealMovesAValueToTheCurrentKeyAndLeavesCurrentValuesAlone() {
        String old = before.encrypt("sk-ant-value");

        String fresh = during.reseal(old).orElseThrow();

        assertThat(after.decrypt(fresh)).contains("sk-ant-value");
        assertThat(during.reseal(fresh)).isEmpty();
        assertThat(during.reseal(null)).isEmpty();
        assertThat(during.reseal("v1:damaged")).isEmpty();
    }

    @Test
    void thePreviousKeyMustDifferFromTheCurrentOne() {
        assertThatThrownBy(() -> new SecretCipher("same-key", "same-key", JWT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theStartUpJobResealsTheClaudeKeyAndAuthenticatorSecrets() {
        AiSettings ai = new AiSettings();
        ai.setApiKeyEncrypted(before.encrypt("sk-ant-value"));
        UserTwoFactor twoFactor = new UserTwoFactor();
        twoFactor.setTotpSecret(before.encrypt("JBSWY3DPEHPK3PXP"));
        twoFactor.setPendingTotpSecret(during.encrypt("KRSXG5CTMVRXEZLU"));
        AiSettingsRepository aiRepository = mock(AiSettingsRepository.class);
        UserTwoFactorRepository twoFactorRepository = mock(UserTwoFactorRepository.class);
        when(aiRepository.findAll()).thenReturn(List.of(ai));
        when(twoFactorRepository.findAll()).thenReturn(List.of(twoFactor));

        int changed = new SecretResealJob(during, aiRepository, twoFactorRepository).resealAll();

        assertThat(changed).isEqualTo(2);
        assertThat(after.decrypt(ai.getApiKeyEncrypted())).contains("sk-ant-value");
        assertThat(after.decrypt(twoFactor.getTotpSecret())).contains("JBSWY3DPEHPK3PXP");
        assertThat(after.decrypt(twoFactor.getPendingTotpSecret())).contains("KRSXG5CTMVRXEZLU");
    }

    @Test
    void recoveryCodesMadeBeforeATwoFactorSecretRotationStillMatch() {
        TwoFactorProperties old = new TwoFactorProperties(true, "old-two-factor-secret", 5, 30, 14);
        String stored = old.hash("ABCD-EFGH");
        TwoFactorProperties rotating = new TwoFactorProperties(true, "new-two-factor-secret", "old-two-factor-secret", 5, 30, 14);
        TwoFactorProperties rotated = new TwoFactorProperties(true, "new-two-factor-secret", 5, 30, 14);

        assertThat(rotating.matches(stored, "ABCD-EFGH")).isTrue();
        assertThat(rotating.matches(stored, "WRONG-CODE")).isFalse();
        assertThat(rotating.matches(rotating.hash("ABCD-EFGH"), "ABCD-EFGH")).isTrue();
        assertThat(rotated.matches(stored, "ABCD-EFGH")).isFalse();
    }
}
