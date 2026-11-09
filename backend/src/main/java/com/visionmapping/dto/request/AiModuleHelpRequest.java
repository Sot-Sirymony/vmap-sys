package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * V9 Phase 7: what the participant has typed so far, unsaved, for a draft or feedback.
 *
 * <p>V15 Phase 4 adds: EXPLAIN (the module in plain words, AH-FR-18), REFINE
 * (a new draft that is shorter, more specific, more in their words or another
 * version, with an optional note; rows listed in keepRows are the
 * participant's own edits and come back unchanged, AH-FR-17) and CHECK
 * (fields left empty or too short, AH-FR-20). answers are the participant's
 * answers to the Help me start questions, used by DRAFT and REFINE (AH-FR-16).
 */
public record AiModuleHelpRequest(
        @NotNull @Pattern(regexp = "START|DRAFT|FEEDBACK|EXPLAIN|REFINE|CHECK") String mode,
        @Size(max = 20) List<Map<String, String>> rows,
        @Pattern(regexp = "en|km") String language,
        @Valid @Size(max = 3) List<QuestionAnswer> answers,
        @Pattern(regexp = "SHORTER|SPECIFIC|MY_WORDS|ANOTHER") String refine,
        @Size(max = 300) String note,
        @Size(max = 3) List<@NotNull @Min(0) @Max(2) Integer> keepRows) {

    public AiModuleHelpRequest {
        rows = Copies.list(rows);
        answers = Copies.list(answers);
        keepRows = Copies.list(keepRows);
    }

    /** A V9 request: mode, rows and language only. */
    public AiModuleHelpRequest(String mode, List<Map<String, String>> rows, String language) {
        this(mode, rows, language, null, null, null, null);
    }

    /** One Help me start question and the participant's answer, kept for the session only. */
    public record QuestionAnswer(@NotNull @Size(max = 500) String question, @NotNull @Size(max = 2000) String answer) {
    }
}
