package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.GapCoverMethod;
import com.visionmapping.entity.enums.Relevance;
import com.visionmapping.entity.enums.StrengthKind;
import com.visionmapping.util.Copies;
import java.time.LocalDate;
import java.util.List;

/**
 * FR-84: the one-page profile for one dream: what I bring (high-relevance
 * strengths), what I lack (high-relevance weaknesses, each with how it is
 * covered) and the rest recorded only. There is no score (NFR-11).
 *
 * @param complete       BR-63: at least one strength and one weakness, and every high-relevance weakness covered
 * @param uncoveredGaps  high-relevance weaknesses still without a way to cover them
 * @param learnInUse     BR-62: high-relevance weaknesses set to Learn, across every dream
 * @param learnLimit     BR-62: the most that may be set to Learn at one time
 * @param lastSavedOn    when the profile was last saved, or null if never
 * @param comparedWith   the save that changes are measured from: the latest one before today, or null
 * @param retakeSuggested the last save is old enough to look again, as with the six-chain retake
 * @param changes        what differs from the comparedWith save; empty when there is none
 */
public record StrengthsProfileResponse(
        Long dreamId,
        String dreamTitle,
        List<SelfAssessmentItemResponse> whatIBring,
        List<SelfAssessmentItemResponse> whatILack,
        List<SelfAssessmentItemResponse> recordedOnly,
        boolean complete,
        int uncoveredGaps,
        long learnInUse,
        int learnLimit,
        LocalDate lastSavedOn,
        LocalDate comparedWith,
        boolean retakeSuggested,
        List<Change> changes
) {

    public StrengthsProfileResponse {
        whatIBring = Copies.list(whatIBring);
        whatILack = Copies.list(whatILack);
        recordedOnly = Copies.list(recordedOnly);
        changes = Copies.list(changes);
    }

    /**
     * One difference from the earlier save. type is ADDED, REMOVED or CHANGED.
     * The before and after values are null where they do not apply.
     */
    public record Change(String type, StrengthKind kind, String description, Relevance relevanceBefore,
                         Relevance relevanceAfter, GapCoverMethod coverBefore, GapCoverMethod coverAfter) {
    }
}
