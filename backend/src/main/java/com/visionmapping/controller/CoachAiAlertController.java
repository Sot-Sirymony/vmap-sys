package com.visionmapping.controller;

import com.visionmapping.dto.response.AiSafetyAlertResponse;
import com.visionmapping.security.Authorities;
import com.visionmapping.service.ai.AiSafetyAlertService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** V9 Phase 3 (AI-FR-30): crisis alerts for coaches. Split from AiController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/coach/ai-alerts", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Coach AI alerts", description = "Crisis alerts the Dream Coach raised for a coach's participants")
public class CoachAiAlertController {

    private final AiSafetyAlertService alertService;

    @Operation(summary = "List my participants' crisis alerts")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Authorities.COACH_COHORTS + "')")
    public ResponseEntity<List<AiSafetyAlertResponse>> alerts() {
        return ResponseEntity.ok(alertService.mine());
    }

    @Operation(summary = "Acknowledge a crisis alert")
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('" + Authorities.COACH_COHORTS + "')")
    public ResponseEntity<AiSafetyAlertResponse> acknowledge(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(alertService.acknowledge(id));
    }
}
