package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.enums.RepeatCadence;
import com.visionmapping.entity.enums.RequirementSource;
import com.visionmapping.entity.enums.StreamlineStatus;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V27 SR-FR-14: only valid suggestions reach the page, and Claude never sees people's names. */
class StreamlineAiServiceTest {

    @Test
    void keepsOnlyActionsAllowedForTheStageOnActiveItemsInThisReview() {
        StreamlineResponses.Review review = review();
        StreamlineAiService.SuggestionDrafts drafts = new StreamlineAiService.SuggestionDrafts(List.of(
                draft("T-001", "DELETE", "", "", "", ""),
                draft("T-002", "MERGE", "T-001", "", "", ""),
                draft("T-002", "MERGE", "S-001", "", "", ""),   // a task into a step: refused
                draft("T-099", "DELETE", "", "", "", ""),       // not in the review
                draft("T-003", "DELETE", "", "", "", ""),       // archived
                draft("T-001", "REPEAT", "", "", "", "WEEKLY"))); // not a stage 2 action
        List<StreamlineAiService.Suggestion> stage2 = StreamlineAiService.check(review, 2, drafts);
        assertThat(stage2).extracting(StreamlineAiService.Suggestion::code).containsExactly("T-001", "T-002");
        assertThat(stage2.get(1).targetItemId()).isEqualTo(1L);

        List<StreamlineAiService.Suggestion> stage5 = StreamlineAiService.check(review, 5, new StreamlineAiService.SuggestionDrafts(List.of(
                draft("T-001", "REPEAT", "", "", "", "WEEKLY"),
                draft("S-001", "REPEAT", "", "", "", "WEEKLY"),  // a step cannot repeat
                draft("T-002", "REPEAT", "", "", "", "HOURLY")))); // not a cadence
        assertThat(stage5).hasSize(1);
        assertThat(stage5.get(0).cadence()).isEqualTo(RepeatCadence.WEEKLY);
    }

    @Test
    void theRecordsCarryTheSourceTypeButNeverTheRequirementOwnersName() {
        String records = StreamlineAiService.records(review());
        assertThat(records).contains("T-001", "asked for by PARTNER").doesNotContain("Dara Sok").doesNotContain("T-003");
    }

    private static StreamlineAiService.SuggestionDraft draft(String code, String action, String target, String verdict, String lever,
                                                             String cadence) {
        return new StreamlineAiService.SuggestionDraft(code, action, "detail", "reason", target, verdict, lever, cadence);
    }

    private static StreamlineResponses.Review review() {
        List<StreamlineResponses.Item> items = List.of(
                item(1L, "TASK", "T-001", false, "Dara Sok", RequirementSource.PARTNER),
                item(2L, "TASK", "T-002", false, null, null),
                item(3L, "TASK", "T-003", true, null, null),
                item(4L, "STEP", "S-001", false, null, null));
        return new StreamlineResponses.Review(9L, "SR-001", "GOAL", 5L, null, "G-001", "Save a deposit", StreamlineStatus.OPEN, 2,
                List.of(), null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), null, items, null);
    }

    private static StreamlineResponses.Item item(Long id, String kind, String code, boolean archived, String owner, RequirementSource source) {
        return new StreamlineResponses.Item(id, kind, kind.equals("STEP") ? 50L : null, kind.equals("TASK") ? 60L + id : null, 50L, code,
                "Title " + code, WorkStatus.NOT_STARTED, archived, null, owner, source, null, null, null, null, null, null, false, null,
                List.of(), null, null, false, null, null, null, null, null, false, null);
    }
}
