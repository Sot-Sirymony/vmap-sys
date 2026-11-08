package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** FR-73: where one dream stands in the guided plan, in the order to work through it. */
public record PlanStatusResponse(Long dreamId, List<Stage> stages, boolean planReady) {

    public PlanStatusResponse {
        stages = Copies.list(stages);
    }


    public record Stage(String key, String label, boolean done, String detail) {
    }
}
