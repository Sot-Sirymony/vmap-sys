package com.visionmapping.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V11 Phase 3: what the participant adds to a goal or step suggestion request.
 * answers (PB-FR-08, PB-FR-09) are clarifying questions already answered;
 * suggestAnyway (PB-FR-11) skips further questions. refine (PB-FR-19) asks
 * for a changed set, keeping the items in keep unchanged (PB-FR-20) and
 * replacing those in replace; ANOTHER replaces one item (PB-FR-21).
 * Every text here is the participant's and is sent to Claude as data.
 */
public record PlanAskRequest(
        @Size(max = 6) List<@NotNull @Valid PlanClarification> answers,
        boolean suggestAnyway,
        @Size(max = 10) @Pattern(regexp = "FEWER|MORE|SMALLER|BOLDER|EARLIER|LATER|ANOTHER") String refine,
        @Size(max = 300) String note,
        @Size(max = 25) List<@NotNull @Size(max = 220) String> keep,
        @Size(max = 25) List<@NotNull @Size(max = 220) String> replace) {

    public static final int MAX_QUESTIONS = 3;
}
