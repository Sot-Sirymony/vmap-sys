package com.visionmapping.controller;

import com.visionmapping.dto.response.ProgressMetricsResponse;
import com.visionmapping.service.ProgressMetricsService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping(value = ApiPaths.V1 + "/progress-metrics", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Progress metrics", description = "Progress numbers for a day")
public class ProgressMetricsController {

    private final ProgressMetricsService service;

    @Operation(summary = "Get the progress metrics for a day")
    @GetMapping
    public ResponseEntity<ProgressMetricsResponse> get(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.metricsFor(date));
    }
}
