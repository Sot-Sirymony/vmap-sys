package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

/** V10 (SEC-07-06): common, breached and self-describing passwords are refused. */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void refusesCommonPasswordsWhateverTheirCase() {
        assertThatThrownBy(() -> policy.check("Password123", "pat@example.com"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("too common");
        assertThatThrownBy(() -> policy.check("QWERTY123", null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> policy.check("12345678", null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refusesTheAccountsOwnEmailAndTheAppsName() {
        assertThatThrownBy(() -> policy.check("sokha.chan-2026", "sokha.chan@example.com"))
                .hasMessageContaining("email");
        assertThatThrownBy(() -> policy.check("MyVisionMapping!", null)).hasMessageContaining("app's name");
    }

    @Test
    void acceptsAnUncommonPasswordWithNoOtherCompositionRules() {
        assertThatCode(() -> policy.check("steady harbor lamp", "pat@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("Steady-Harbor-Lamp-42", "pat@example.com")).doesNotThrowAnyException();
    }

    @Test
    void theListIsLoaded() {
        assertThat(policy.isCommon("iloveyou")).isTrue();
        assertThat(policy.isCommon("not-on-any-list-7f3")).isFalse();
    }
}
