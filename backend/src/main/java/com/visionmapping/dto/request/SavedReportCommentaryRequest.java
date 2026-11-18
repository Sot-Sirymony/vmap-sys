package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** V19 RM-FR-26, RM-FR-30: a draft's commentary, saved as the participant types (RM-FR-27). */
public record SavedReportCommentaryRequest(
        @Size(max = 2000) String highlights,
        @Size(max = 2000) String risks,
        @Size(max = 2000) String decisionsNeeded,
        boolean nothingNeeded,
        @Size(max = 2000) String nextFocus,
        @Size(max = 200) Map<Long, @Size(max = 500) String> goalNotes
) {

    public SavedReportCommentaryRequest {
        goalNotes = Copies.map(goalNotes);
    }
}
