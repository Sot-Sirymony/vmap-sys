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

@RestController
@RequestMapping("/api/progress-metrics")
@RequiredArgsConstructor
public class ProgressMetricsController {

    private final ProgressMetricsService service;

    @GetMapping
    public ProgressMetricsResponse get(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.metricsFor(date);
    }
}
