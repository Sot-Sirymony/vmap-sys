package com.visionmapping.controller;

import com.visionmapping.dto.response.AiModuleResponses.AssessmentExplanation;
import com.visionmapping.dto.response.AiModuleResponses.AssessmentPractice;
import com.visionmapping.dto.response.AiModuleResponses.WhatChanged;
import com.visionmapping.service.ai.AiModuleService;
import jakarta.validation.constraints.Pattern;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** V8: AI help with the self-assessment. Split from AiModuleController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/assessment", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI assessment help", description = "AI help with the self-assessment")
public class AiAssessmentController {

    private final AiModuleService service;

    @Operation(summary = "Get a practice question for the assessment")
    @PostMapping("/practice")
    public ResponseEntity<AssessmentPractice> practice(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.practice(language));
    }

    @Operation(summary = "Explain what changed since the last assessment")
    @PostMapping("/what-changed")
    public ResponseEntity<WhatChanged> whatChanged(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.whatChanged(language));
    }

    @Operation(summary = "Explain my assessment")
    @PostMapping("/explain")
    public ResponseEntity<AssessmentExplanation> explain(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.explainAssessment(language));
    }
}
