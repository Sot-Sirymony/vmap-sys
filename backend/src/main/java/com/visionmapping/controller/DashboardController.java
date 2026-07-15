package com.visionmapping.controller;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    /** @param visionAreaId scope every number to one area; omit for the whole portfolio. */
    @GetMapping
    public DashboardSummaryResponse summary(@RequestParam(required = false) Long visionAreaId) {
        return service.buildDashboardSummary(visionAreaId);
    }
}
