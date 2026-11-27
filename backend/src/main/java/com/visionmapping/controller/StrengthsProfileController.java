package com.visionmapping.controller;

import com.visionmapping.dto.response.StrengthsProfileResponse;
import com.visionmapping.service.StrengthsProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** V8.1 FR-84: one dream's Strengths and Gaps profile, and saving it so a retake can show what changed. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/strengths-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Strengths profiles", description = "Strengths profile of a dream")
public class StrengthsProfileController {

    private final StrengthsProfileService service;

    @Operation(summary = "Get a dream's strengths profile")
    @GetMapping("/{dreamId}")
    public ResponseEntity<StrengthsProfileResponse> get(@PathVariable @Positive Long dreamId) {
        return ResponseEntity.ok(service.profile(dreamId));
    }

    @Operation(summary = "Save a snapshot of a strengths profile")
    @PostMapping("/{dreamId}/snapshots")
    public ResponseEntity<StrengthsProfileResponse> save(@PathVariable @Positive Long dreamId) {
        return ResponseEntity.ok(service.saveSnapshot(dreamId));
    }
}
