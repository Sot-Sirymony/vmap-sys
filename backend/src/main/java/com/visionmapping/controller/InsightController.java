package com.visionmapping.controller;

import com.visionmapping.dto.response.InsightResponse;
import com.visionmapping.service.InsightService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-36.1: the Insight Library search endpoint. Read-only aggregation over the
 * user's own reviews and obstacles (BR-30); it never creates or edits a record.
 */
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService service;

    @GetMapping
    public List<InsightResponse> search(@RequestParam(required = false, defaultValue = "") String query) {
        return service.searchInsights(query);
    }
}
