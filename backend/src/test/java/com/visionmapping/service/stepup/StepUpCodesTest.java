package com.visionmapping.service.stepup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** V20 SV-FR-10, SV-FR-11, SV-NFR-01: making, hashing and masking codes. */
class StepUpCodesTest {

    @Test
    void aCodeIsAlwaysSixDigitsWithLeadingZerosKept() {
        for (int index = 0; index < 200; index++) {
            assertThat(StepUpCodes.newCode()).matches("\\d{6}");
        }
    }

    @Test
    void typedCodesIgnoreSpacesAndDashesAndAnythingElseIsNotACode() {
        assertThat(StepUpCodes.normalise(" 012 345 ")).isEqualTo("012345");
        assertThat(StepUpCodes.normalise("012-345")).isEqualTo("012345");
        assertThat(StepUpCodes.normalise("12345")).isNull();
        assertThat(StepUpCodes.normalise("12345a")).isNull();
        assertThat(StepUpCodes.normalise(null)).isNull();
    }

    @Test
    void theHashNeedsTheSecretAndIsComparedExactly() {
        String hash = StepUpCodes.hash("123456", "secret-a");
        assertThat(hash).hasSize(64).isEqualTo(StepUpCodes.hash("123456", "secret-a"))
                .isNotEqualTo(StepUpCodes.hash("123456", "secret-b"))
                .isNotEqualTo(StepUpCodes.hash("123457", "secret-a"));
        assertThat(StepUpCodes.sameHash(hash, StepUpCodes.hash("123456", "secret-a"))).isTrue();
        assertThat(StepUpCodes.sameHash(hash, StepUpCodes.hash("654321", "secret-a"))).isFalse();
    }

    @Test
    void anAddressIsTiedIgnoringCaseAndSpaces() {
        assertThat(StepUpCodes.targetHash(" Me@Work.org ")).isEqualTo(StepUpCodes.targetHash("me@work.org")).hasSize(64)
                .isNotEqualTo(StepUpCodes.targetHash("you@work.org"));
    }

    @Test
    void addressesAreMaskedToTheFirstLettersAndTheEnding() {
        assertThat(StepUpCodes.mask("sophea@gmail.com")).isEqualTo("s*****@g****.com");
        assertThat(StepUpCodes.mask("a@b.io")).isEqualTo("a***@b***.io");
        assertThat(StepUpCodes.mask("me@localhost")).isEqualTo("m***@l********");
        assertThat(StepUpCodes.mask("not-an-address")).isEqualTo("***");
        assertThat(StepUpCodes.mask("trailing@")).isEqualTo("***");
    }
}
