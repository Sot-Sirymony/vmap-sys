package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** BRD 12 COV-FR-29, AI-NFR-09: crisis language always gets the help lines, in English and Khmer. */
class AiSafetyTest {

    private static final String HELP_LINES = "Hotline: 1234";

    @ParameterizedTest
    @ValueSource(strings = {"Sometimes I want to die", "I keep thinking about SUICIDE", "I might hurt myself", "ខ្ញុំចង់ស្លាប់"})
    void recognisesCrisisLanguageInEitherLanguage(String text) {
        assertThat(AiSafety.looksLikeCrisis(text)).isTrue();
    }

    @Test
    void doesNotFlagOrdinaryWorries() {
        assertThat(AiSafety.looksLikeCrisis("I am worried my bakery will fail")).isFalse();
    }

    @Test
    void readsAndRemovesClaudesCrisisMarker() {
        assertThat(AiSafety.markedCrisis("[CRISIS] That sounds hard.")).isTrue();
        assertThat(AiSafety.withoutMarker("[CRISIS] That sounds hard.")).isEqualTo("That sounds hard.");
        assertThat(AiSafety.markedCrisis("That sounds hard.")).isFalse();
        assertThat(AiSafety.withoutMarker("That sounds hard.")).isEqualTo("That sounds hard.");
    }

    @Test
    void givesTheHelpLinesAndSaysWhoWasToldInEnglish() {
        assertThat(AiSafety.crisisReply("en", HELP_LINES, true)).contains(HELP_LINES).contains("let your coach know");
        assertThat(AiSafety.crisisReply("en", HELP_LINES, false)).contains(HELP_LINES).contains("let the program team know");
    }

    @Test
    void givesTheHelpLinesInKhmer() {
        String withCoach = AiSafety.crisisReply("km", HELP_LINES, true);
        String withoutCoach = AiSafety.crisisReply("km", HELP_LINES, false);
        assertThat(withCoach).contains(HELP_LINES).contains("គ្រូណែនាំ").doesNotContain("I'm really sorry");
        assertThat(withoutCoach).contains(HELP_LINES).contains("អ្នកគ្រប់គ្រងកម្មវិធី");
    }
}
