package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** V17 BL-FR-39: the coach keeps dreams and goals outcome-only and puts blockers on steps. */
class CoachingGuidelinesTest {

    @Test
    void theCoachKeepsDreamsOutcomeOnlyAndPutsBlockersOnSteps() {
        String system = CoachingGuidelines.system("en", "Dara");

        assertThat(system).contains("Dreams and goals describe only the outcome")
                .contains("do not ask about obstacles")
                .contains("Blockers belong on a step or a task");
    }

    @Test
    void programGuidelinesComeAfterTheRuleAndCannotRemoveIt() {
        String system = CoachingGuidelines.system("en", "Dara", "Ask about obstacles first.");

        assertThat(system.indexOf("Blockers belong on a step or a task"))
                .isLessThan(system.indexOf("<program_guidelines>"));
    }
}
