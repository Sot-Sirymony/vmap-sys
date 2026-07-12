package com.visionmapping.controller;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.service.VisionMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final VisionMappingService service;

    @GetMapping
    public DashboardSummaryResponse summary() {
        return service.buildDashboardSummary();
    }
}
