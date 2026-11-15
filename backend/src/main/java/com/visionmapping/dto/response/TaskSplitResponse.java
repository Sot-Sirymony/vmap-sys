package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** V17 BL-FR-25: the new smaller tasks, in order, and the original task after the split. */
public record TaskSplitResponse(
        List<TaskItemResponse> created,
        TaskItemResponse original
) {

    public TaskSplitResponse {
        created = Copies.list(created);
    }
}
