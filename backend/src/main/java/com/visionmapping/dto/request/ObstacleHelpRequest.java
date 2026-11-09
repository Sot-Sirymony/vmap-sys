package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * V15 Phase 2 (AH-FR-31 to AH-FR-41): one obstacle helper. obstacleId is the
 * saved obstacle open on screen, if any; form is the unsaved form, as for the
 * page assistant. answers carries the "why" questions already answered in
 * place for ROOT_CAUSE (AH-FR-32). PRIORITISE works on the whole list and
 * needs neither.
 */
public record ObstacleHelpRequest(
        @NotNull @Pattern(regexp = MODES) String mode,
        Long obstacleId,
        @Size(max = 60) Map<@Size(max = 60) String, @Size(max = 4000) String> form,
        @Valid @Size(max = 3) List<WhyAnswer> answers,
        // V15 (AH-FR-40): CONFLICT sends the private "your own reaction" note only when this is true, for this request only.
        Boolean includePrivateNote) {

    public static final String MODES = "CLASSIFY|ROOT_CAUSE|SOLUTIONS|NEXT_TASK|PRIORITISE|CONFLICT";

    public ObstacleHelpRequest {
        form = Copies.map(form);
        answers = Copies.list(answers);
    }

    /** The Phase 2 shape, without the private-note choice. */
    public ObstacleHelpRequest(String mode, Long obstacleId, Map<String, String> form, List<WhyAnswer> answers) {
        this(mode, obstacleId, form, answers, null);
    }

    /** One "why" question and the participant's answer to it. */
    public record WhyAnswer(@NotNull @Size(max = 500) String question, @NotNull @Size(max = 2000) String answer) {
    }

    /** The page assistant request for the same obstacle, so the helper reads exactly what the assistant reads. */
    public PageAssistantRequest pageRequest() {
        return new PageAssistantRequest("OBSTACLES", "item", obstacleId, form);
    }

    /** The answers, or an empty list when none were sent. */
    public List<WhyAnswer> answerList() {
        return answers == null ? List.of() : answers;
    }

    @Nullable
    public String formValue(String key) {
        return form == null ? null : form.get(key);
    }
}
