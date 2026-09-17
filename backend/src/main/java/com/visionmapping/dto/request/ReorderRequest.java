package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Drag-and-drop reorder: parentId scopes the check (the vision area for
 * dreams, the dream for goals, the goal for steps, the step for tasks) and
 * orderedIds is the full sibling set in its new order — reused across
 * DreamController, GoalController, VisionStepController, and
 * TaskItemController. VisionAreaController uses SimpleReorderRequest instead,
 * since a vision area has no parent to scope by.
 */
public record ReorderRequest(
        @NotNull Long parentId,
        @NotEmpty List<Long> orderedIds
) {
}
