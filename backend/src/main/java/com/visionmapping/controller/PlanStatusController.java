package com.visionmapping.controller;

import com.visionmapping.dto.response.PlanStatusResponse;
import com.visionmapping.service.PlanStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan-status")
@RequiredArgsConstructor
public class PlanStatusController {

    private final PlanStatusService service;

    @GetMapping("/{dreamId}")
    public PlanStatusResponse get(@PathVariable Long dreamId) {
        return service.statusFor(dreamId);
    }
}
