package com.visionmapping.controller;

import com.visionmapping.dto.response.InsightResponse;
import com.visionmapping.service.InsightService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * FR-36.1: the Insight Library search endpoint. Read-only aggregation over the
 * user's own reviews and obstacles (BR-30); it never creates or edits a record.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/insights", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Insights", description = "Saved insights")
public class InsightController {

    private final InsightService service;

    @Operation(summary = "Search insights")
    @GetMapping
    public ResponseEntity<List<InsightResponse>> search(@RequestParam(required = false, defaultValue = "") String query) {
        return ResponseEntity.ok(service.searchInsights(query));
    }
}
