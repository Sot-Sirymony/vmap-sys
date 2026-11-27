package com.visionmapping.controller;

import com.visionmapping.dto.request.ChainAssessmentRequest;
import com.visionmapping.dto.response.ChainAssessmentResponse;
import com.visionmapping.dto.response.ChainAssessmentSummaryResponse;
import com.visionmapping.service.ChainAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/chain-assessments", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Chain assessments", description = "The chain assessment")
public class ChainAssessmentController {

    private final ChainAssessmentService service;

    @Operation(summary = "Get the chain assessment summary")
    @GetMapping
    public ResponseEntity<ChainAssessmentSummaryResponse> summary() {
        return ResponseEntity.ok(service.summary());
    }

    @Operation(summary = "Save a chain assessment")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChainAssessmentResponse> save(@Valid @RequestBody ChainAssessmentRequest request) {
        return ResponseEntity.ok(service.save(request));
    }
}
