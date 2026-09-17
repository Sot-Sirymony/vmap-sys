package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Vision Map drag-and-drop reorder: parentId scopes the check (the dream for
 * goals, the goal for steps, the step for tasks) and orderedIds is the full
 * sibling set in its new order — reused across GoalController,
 * VisionStepController, and TaskItemController.
 */
public record ReorderRequest(
        @NotNull Long parentId,
        @NotEmpty List<Long> orderedIds
) {
}
