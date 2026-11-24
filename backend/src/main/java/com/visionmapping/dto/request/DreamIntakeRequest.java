package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V31 v1 (DC-FR-14 to DC-FR-17): a dream not saved yet, in the participant's
 * own words, with the answers to the coach's questions so far (at most 3).
 */
public record DreamIntakeRequest(
        @Size(max = 120) String areaName,
        @NotBlank @Size(max = 1000) String words,
        @Size(max = 3) List<@NotNull @Valid Answer> answers,
        boolean suggestAnyway) {

    public DreamIntakeRequest {
        answers = Copies.list(answers);
    }

    /** One answered question; field names the dream field the answer belongs to, when it does. */
    public record Answer(
            @NotBlank @Size(max = 500) String question,
            @NotBlank @Size(max = 1000) String answer,
            @Pattern(regexp = "^(|WHY_IMPORTANT|SUCCESS_DEFINITION|TARGET_DATE)$") String field) {
    }
}
