package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** V11 (PB-FR-32): the goals and steps one Add saved, to archive again. */
public record AiPlanUndoRequest(
        Long requestId,
        @Size(max = 10) List<@NotNull Long> goalIds,
        @Size(max = 20) List<@NotNull Long> stepIds) {
}
