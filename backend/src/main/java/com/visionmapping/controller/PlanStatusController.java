package com.visionmapping.controller;

import com.visionmapping.dto.response.PlanStatusResponse;
import com.visionmapping.service.PlanStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/plan-status", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Plan status", description = "A dream plan's status")
public class PlanStatusController {

    private final PlanStatusService service;

    @Operation(summary = "Get a dream plan's status")
    @GetMapping("/{dreamId}")
    public ResponseEntity<PlanStatusResponse> get(@PathVariable @Positive Long dreamId) {
        return ResponseEntity.ok(service.statusFor(dreamId));
    }
}
