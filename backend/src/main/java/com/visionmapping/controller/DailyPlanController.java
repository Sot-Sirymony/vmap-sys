package com.visionmapping.controller;

import com.visionmapping.dto.response.DailyPlanResponse;
import com.visionmapping.service.DailyPlanService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-plan")
@RequiredArgsConstructor
public class DailyPlanController {

    private final DailyPlanService service;

    @GetMapping
    public DailyPlanResponse get(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.planFor(date);
    }
}
