package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** V31 R-07 (DC-FR-61): AI owners are Me or a role, never a person's name. */
class AiOwnerRuleTests {

    @Test
    void blankBecomesMe() {
        assertThat(AiOwnerRule.check(null)).isEqualTo("Me");
        assertThat(AiOwnerRule.check("  ")).isEqualTo("Me");
    }

    @Test
    void meAndRolesAreKept() {
        assertThat(AiOwnerRule.check("Me")).isEqualTo("Me");
        assertThat(AiOwnerRule.check(" Statistics mentor ")).isEqualTo("Statistics mentor");
        assertThat(AiOwnerRule.check("Funding body")).isEqualTo("Funding body");
        assertThat(AiOwnerRule.check("Research assistant")).isEqualTo("Research assistant");
        assertThat(AiOwnerRule.check("ខ្ញុំ")).isEqualTo("ខ្ញុំ");
        assertThat(AiOwnerRule.stripped("Accountant")).isFalse();
    }

    @Test
    void namesAndTitlesAreRemoved() {
        assertThat(AiOwnerRule.check("Dr. Sok")).isNull();
        assertThat(AiOwnerRule.check("Prof Chan, mentor")).isNull();
        assertThat(AiOwnerRule.check("Sokha Chan")).isNull();
        assertThat(AiOwnerRule.check("Mr Lee")).isNull();
        assertThat(AiOwnerRule.stripped("Bopha")).isTrue();
    }

    @Test
    void longRolesAreCut() {
        String owner = "Mentor " + "x".repeat(200);
        assertThat(AiOwnerRule.check(owner)).hasSize(160);
    }

    @Test
    void adminRolesAreParsedAndMatchedAsWholeWords() {
        List<String> roles = AiOwnerRule.parseRoles(" Translator, Program officer\n\nTranslator ");
        assertThat(roles).containsExactly("translator", "program officer");
        assertThat(AiOwnerRule.parseRoles(null)).isEmpty();
        assertThat(AiOwnerRule.check("Khmer translator", roles)).isEqualTo("Khmer translator");
        assertThat(AiOwnerRule.check("Translators", roles)).isNull();
        assertThat(AiOwnerRule.check("Dr. Translator", roles)).isNull();
    }
}
