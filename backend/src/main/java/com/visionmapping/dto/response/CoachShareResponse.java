package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

/**
 * V19 RM-FR-50: whether this report is shared with the participant's coach.
 * available is false outside an active cohort with a coach; coachName is then
 * null. sections and sharedAt are empty and null when it is not shared.
 */
public record CoachShareResponse(boolean available, String coachName, boolean shared, List<String> sections, Instant sharedAt) {

    public CoachShareResponse {
        sections = Copies.list(sections);
    }
}
