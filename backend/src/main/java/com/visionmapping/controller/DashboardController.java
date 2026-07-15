package com.visionmapping.controller;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.service.DashboardService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    /**
     * @param visionAreaId scope every number to one area; omit for the whole portfolio.
     * @param from,to      window (inclusive, yyyy-MM-dd) for the two time-based tiles —
     *                     tasks due in the period and completed in the period. Omit for
     *                     the current month.
     */
    @GetMapping
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) Long visionAreaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.buildDashboardSummary(visionAreaId, from, to);
    }
}
