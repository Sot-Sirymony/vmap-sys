package com.visionmapping.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * V15 (AH-NFR-12): keeps the quality test set the shape the BRD asks for, so it
 * cannot drift by accident. Runs with every build; calls no AI.
 */
class PageAssistantEvalSetTests {

    @Test
    void theSetHasTheCountsTheBrdAsksFor() throws Exception {
        List<PageAssistantEvalSet.Case> cases = PageAssistantEvalSet.load().cases();
        Map<String, Long> perPage = cases.stream().collect(Collectors.groupingBy(PageAssistantEvalSet.Case::page, Collectors.counting()));
        assertThat(perPage).containsEntry("MODULE", 10L).containsEntry("ASSESSMENT", 6L).containsEntry("OBSTACLES", 10L)
                .containsEntry("COMMUNICATION", 10L).containsEntry("WORK_STYLE", 8L);
        assertThat(cases.stream().map(PageAssistantEvalSet.Case::id).distinct().count()).isEqualTo(cases.size());
        // English and Khmer on every page, and vague or sensitive cases.
        for (String page : perPage.keySet()) {
            Set<String> languages = cases.stream().filter(item -> item.page().equals(page)).map(PageAssistantEvalSet.Case::language)
                    .collect(Collectors.toSet());
            assertThat(languages).as(page).containsExactlyInAnyOrder("en", "km");
        }
        assertThat(cases).anyMatch(item -> "vague".equals(item.kind())).anyMatch(item -> "sensitive".equals(item.kind()));
    }

    @Test
    void messagesCoverEveryStyleAndMotivatorAndWorkStyleCoversDecisiveAndClose() throws Exception {
        List<PageAssistantEvalSet.Case> cases = PageAssistantEvalSet.load().cases();
        Set<String> styles = cases.stream().filter(item -> "COMMUNICATION".equals(item.page()) && item.partner() != null)
                .map(item -> item.partner().get("workStyleType")).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<String> motivators = cases.stream().filter(item -> "COMMUNICATION".equals(item.page()) && item.partner() != null)
                .map(item -> item.partner().get("primaryMotivator")).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        assertThat(styles).containsExactlyInAnyOrder("DRIVER", "CONNECTOR", "STEADIER", "PLANNER");
        assertThat(motivators).containsExactlyInAnyOrder("FINANCIAL_GAIN", "AVOIDING_LOSS", "SHARED_VISION", "RECOGNITION");
        Set<String> kinds = cases.stream().filter(item -> "WORK_STYLE".equals(item.page())).map(PageAssistantEvalSet.Case::kind)
                .collect(Collectors.toSet());
        assertThat(kinds).containsExactlyInAnyOrder("decisive", "close");
        // Every pattern compiles, so the live run checks what the set says.
        cases.forEach(item -> item.mustNot().forEach(Pattern::compile));
    }
}
