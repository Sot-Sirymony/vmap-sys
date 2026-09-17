package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Drag-and-drop reorder for an entity with no parent to scope by — today
 * only VisionAreaController, whose vision areas are reordered as one set
 * per user. Every other entity uses ReorderRequest instead.
 */
public record SimpleReorderRequest(
        @NotEmpty List<Long> orderedIds
) {
}
