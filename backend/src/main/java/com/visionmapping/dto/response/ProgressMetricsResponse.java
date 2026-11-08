package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** FR-75: the six objectives, measured for one person. */
public record ProgressMetricsResponse(List<Metric> metrics) {

    public ProgressMetricsResponse {
        metrics = Copies.list(metrics);
    }


    public record Metric(String id, String label, String value, String target, boolean met) {
    }
}
