package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V11 (PB-FR-31, PB-FR-35): the plan builder suggestions a participant kept,
 * saved in one request that succeeds or fails as a whole. Each item is the
 * same request the normal create endpoint takes, so the same rules apply.
 * requestId, when given, is the AI answer the items came from; edited says
 * whether the participant changed the item before adding it.
 */
public record AiPlanAddRequest(
        Long requestId,
        @Valid @Size(max = 10) List<@NotNull @Valid GoalItem> goals,
        @Valid @Size(max = 20) List<@NotNull @Valid StepItem> steps,
        // Suggested tasks for one step, from the Vision Map plan assistant.
        @Valid @Size(max = 20) List<@NotNull @Valid TaskItem> tasks) {

    public AiPlanAddRequest {
        goals = Copies.list(goals);
        steps = Copies.list(steps);
        tasks = Copies.list(tasks);
    }


    /** Goals and steps only, as before tasks could be added here. */
    public AiPlanAddRequest(Long requestId, List<GoalItem> goals, List<StepItem> steps) {
        this(requestId, goals, steps, null);
    }

    public record GoalItem(@NotNull @Valid GoalRequest goal, boolean edited) {
    }

    public record StepItem(@NotNull @Valid VisionStepRequest step, boolean edited) {
    }

    public record TaskItem(@NotNull @Valid TaskItemRequest task, boolean edited) {
    }
}
