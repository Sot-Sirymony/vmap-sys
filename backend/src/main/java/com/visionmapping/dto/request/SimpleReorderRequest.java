package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Drag-and-drop reorder with no parent to scope by: VisionAreaController
 * (vision areas, one set per user) and TaskItemController's overall
 * Tasks-page order (/api/tasks/list-order). Every other reorder uses
 * ReorderRequest instead.
 */
public record SimpleReorderRequest(
        @NotEmpty @Size(max = 1000) List<Long> orderedIds
) {
    public SimpleReorderRequest {
        orderedIds = Copies.list(orderedIds);
    }
}
