package com.visionmapping.dto.response;

import java.util.List;

/** FR-73: where one dream stands in the guided plan, in the order to work through it. */
public record PlanStatusResponse(Long dreamId, List<Stage> stages, boolean planReady) {

    public record Stage(String key, String label, boolean done, String detail) {
    }
}
