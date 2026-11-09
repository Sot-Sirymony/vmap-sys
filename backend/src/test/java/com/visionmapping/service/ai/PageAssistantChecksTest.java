package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.dto.response.PageAssistantResponses.PageChange;
import com.visionmapping.entity.enums.Severity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** V15 (AH-FR-07, AH-NFR-04): the server check on every change card before it is shown. */
class PageAssistantChecksTest {

    private static final Map<String, PageAssistantService.FieldRule> RULES = Map.of(
            "solution", PageAssistantService.FieldRule.text(20),
            "severity", PageAssistantService.FieldRule.oneOf(Severity.values()));

    @Test
    void keepsOnlyAllowedFieldsAndValuesTrimmedAndClipped() {
        List<AiDrafts.PageChangeDraft> drafts = new ArrayList<>(Arrays.asList(
                null,
                new AiDrafts.PageChangeDraft(null, "x", "no field"),
                new AiDrafts.PageChangeDraft("solution", null, "no value"),
                new AiDrafts.PageChangeDraft(" severity ", "critical", null),
                new AiDrafts.PageChangeDraft("severity", "very high", "not a value"),
                new AiDrafts.PageChangeDraft("solution", "  Ask the librarian for a search plan this week  ", "too long"),
                new AiDrafts.PageChangeDraft("unknown", "x", "not a field")));

        List<PageChange> kept = PageAssistantService.checked(drafts, RULES);

        assertThat(kept).extracting(PageChange::field).containsExactly("severity", "solution");
        assertThat(kept.get(0).value()).isEqualTo("CRITICAL");
        assertThat(kept.get(0).reason()).isEmpty();
        assertThat(kept.get(1).value()).hasSize(20).endsWith("...");
    }

    @Test
    void keepsAtMostTwoVersionsOfAFieldAndFiveCards() {
        List<AiDrafts.PageChangeDraft> drafts = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            drafts.add(new AiDrafts.PageChangeDraft("solution", "Idea " + index, ""));
        }
        assertThat(PageAssistantService.checked(drafts, RULES)).hasSize(2);

        Map<String, PageAssistantService.FieldRule> many = Map.of("a", PageAssistantService.FieldRule.text(50),
                "b", PageAssistantService.FieldRule.text(50), "c", PageAssistantService.FieldRule.text(50),
                "d", PageAssistantService.FieldRule.text(50));
        List<AiDrafts.PageChangeDraft> eight = new ArrayList<>();
        for (String field : List.of("a", "a", "b", "b", "c", "c", "d", "d")) {
            eight.add(new AiDrafts.PageChangeDraft(field, "value", ""));
        }
        assertThat(PageAssistantService.checked(eight, many)).hasSize(PageAssistantService.MAX_CHANGES);
        assertThat(PageAssistantService.checked(null, RULES)).isEmpty();
    }
}
