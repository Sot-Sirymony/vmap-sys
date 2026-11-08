package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** V11 (PB-FR-32): the goals, steps and tasks one Add saved, to archive again. */
public record AiPlanUndoRequest(
        Long requestId,
        @Size(max = 15) List<@NotNull Long> goalIds,
        @Size(max = 200) List<@NotNull Long> stepIds,
        @Size(max = 500) List<@NotNull Long> taskIds,
        // V11 Phase 5: partners and obstacles the add created.
        @Size(max = 10) List<@NotNull Long> partnerIds,
        @Size(max = 10) List<@NotNull Long> obstacleIds) {
    public AiPlanUndoRequest {
        goalIds = Copies.list(goalIds);
        stepIds = Copies.list(stepIds);
        taskIds = Copies.list(taskIds);
        partnerIds = Copies.list(partnerIds);
        obstacleIds = Copies.list(obstacleIds);
    }
}
