package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.enums.AutomationChoice;
import com.visionmapping.entity.enums.SpeedLever;
import com.visionmapping.entity.enums.StreamlineDecision;
import com.visionmapping.entity.enums.StreamlineStatus;
import com.visionmapping.entity.enums.StreamlineVerdict;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V32 (D-32-1, BR-32-2, BR-32-3): when a saved set still fits, and which saved cards are shown again. */
class StreamlineSuggestionStoreTest {

    @Test
    void theFingerprintFollowsTheStageAndTheReviewsItems() {
        StreamlineResponses.Review base = review(item(1L, "TASK", false), item(2L, "TASK", false), item(3L, "STEP", true));
        String fingerprint = StreamlineSuggestionStore.fingerprint(base, 2);

        assertThat(fingerprint).hasSize(64);
        // Same items in another order: same fingerprint.
        assertThat(StreamlineSuggestionStore.fingerprint(review(item(3L, "STEP", true), item(2L, "TASK", false), item(1L, "TASK", false)), 2))
                .isEqualTo(fingerprint);
        // A title or answer change does not drop the set; each card is checked on its own instead.
        assertThat(StreamlineSuggestionStore.fingerprint(review(withVerdict(item(1L, "TASK", false)), item(2L, "TASK", false),
                item(3L, "STEP", true)), 2)).isEqualTo(fingerprint);
        // Archiving an item (a delete) keeps the set: its own cards drop out one by one instead.
        assertThat(StreamlineSuggestionStore.fingerprint(review(item(1L, "TASK", false), item(2L, "TASK", true), item(3L, "STEP", true)), 2))
                .isEqualTo(fingerprint);
        // Another stage, or another set of items: a new fingerprint.
        assertThat(StreamlineSuggestionStore.fingerprint(base, 3)).isNotEqualTo(fingerprint);
        assertThat(StreamlineSuggestionStore.fingerprint(review(item(1L, "TASK", false), item(2L, "TASK", false), item(4L, "TASK", false)), 2))
                .isNotEqualTo(fingerprint);
    }

    @Test
    void aCardIsShownAgainOnlyWhileItsChangeIsStillToBeMade() {
        StreamlineResponses.Item open = item(1L, "TASK", false);
        StreamlineResponses.Item done = new StreamlineResponses.Item(2L, "TASK", null, 62L, 50L, "T-002", "Title", WorkStatus.NOT_STARTED,
                false, null, "Me", com.visionmapping.entity.enums.RequirementSource.SELF, null, null, StreamlineVerdict.NEEDED,
                StreamlineDecision.KEEP, null, null, true, null, List.of(SpeedLever.BATCH), AutomationChoice.REMIND, null, false,
                null, null, null, null, null, false, null, null, null);
        StreamlineResponses.Review review = review(open, done, item(3L, "TASK", true));

        for (String action : List.of("VERDICT", "ANSWER", "DELETE", "KEEP", "SIMPLIFY", "REPEAT", "DELEGATE", "REMIND", "QUESTION")) {
            assertThat(StreamlineSuggestionStore.stillOpen(review, card(1L, action, null, null))).as(action).isTrue();
        }
        for (String action : List.of("VERDICT", "ANSWER", "DELETE", "KEEP", "SIMPLIFY", "REPEAT", "DELEGATE", "REMIND")) {
            assertThat(StreamlineSuggestionStore.stillOpen(review, card(2L, action, null, null))).as(action).isFalse();
        }
        // A question has nothing to apply, so it stays until dismissed.
        assertThat(StreamlineSuggestionStore.stillOpen(review, card(2L, "QUESTION", null, null))).isTrue();
        // A lever already chosen is done; another lever is not.
        assertThat(StreamlineSuggestionStore.stillOpen(review, card(2L, "LEVER", null, SpeedLever.BATCH))).isFalse();
        assertThat(StreamlineSuggestionStore.stillOpen(review, card(2L, "LEVER", null, SpeedLever.PARALLEL))).isTrue();
        // An archived item, an unknown item, or a merge into an archived item: gone.
        assertThat(StreamlineSuggestionStore.stillOpen(review, card(3L, "QUESTION", null, null))).isFalse();
        assertThat(StreamlineSuggestionStore.stillOpen(review, card(99L, "QUESTION", null, null))).isFalse();
        assertThat(StreamlineSuggestionStore.stillOpen(review, card(1L, "MERGE", 3L, null))).isFalse();
        assertThat(StreamlineSuggestionStore.stillOpen(review, card(1L, "MERGE", 2L, null))).isTrue();
    }

    private static StreamlineAiService.Suggestion card(Long itemId, String action, Long target, SpeedLever lever) {
        return new StreamlineAiService.Suggestion(itemId, "T-00" + itemId, "Title", action, "detail", "reason", target, null, lever,
                null, null, null, null, null, null);
    }

    private static StreamlineResponses.Review review(StreamlineResponses.Item... items) {
        return new StreamlineResponses.Review(9L, "SR-001", "GOAL", 5L, null, "G-001", "Save a deposit", StreamlineStatus.OPEN, 2,
                List.of(), null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), null, List.of(items), null);
    }

    private static StreamlineResponses.Item withVerdict(StreamlineResponses.Item item) {
        return new StreamlineResponses.Item(item.id(), item.kind(), null, 60L, 50L, item.code(), "A new title", WorkStatus.IN_PROGRESS,
                false, null, null, null, null, null, StreamlineVerdict.NEEDED, null, null, null, false, null, List.of(), null, null,
                false, null, null, null, null, null, false, null, null, null);
    }

    private static StreamlineResponses.Item item(Long id, String kind, boolean archived) {
        return new StreamlineResponses.Item(id, kind, null, 60L + id, 50L, "T-00" + id, "Title " + id, WorkStatus.NOT_STARTED, archived,
                null, null, null, null, null, null, null, null, null, false, null, List.of(), null, null, false, null, null, null,
                null, null, false, null, null, null);
    }
}
