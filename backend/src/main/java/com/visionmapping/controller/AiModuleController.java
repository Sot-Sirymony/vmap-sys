package com.visionmapping.controller;

import com.visionmapping.dto.request.AiModuleHelpRequest;
import com.visionmapping.dto.response.AiModuleResponses.AssessmentExplanation;
import com.visionmapping.dto.response.AiModuleResponses.ModuleHelp;
import com.visionmapping.service.ai.AiModuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V9 Phase 7: AI help in the module exercises and the assessment explanation. Drafts only. */
@Validated
@RestController
@RequiredArgsConstructor
public class AiModuleController {

    private final AiModuleService service;
    private final com.visionmapping.service.ModuleJourneyService journey;

    @PostMapping("/api/ai/modules/{number}/help")
    public ModuleHelp help(@PathVariable Integer number, @Valid @RequestBody AiModuleHelpRequest request) {
        return service.help(number, request);
    }

    /** V15 (AH-FR-13): which module next, chosen by Claude from the open modules; falls back to the rule. */
    @PostMapping("/api/ai/modules/next")
    public com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext next(
            @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.next(language);
    }

    /** V15 (AH-FR-14): which module next by the program's rule. No AI. */
    @org.springframework.web.bind.annotation.GetMapping("/api/modules/next")
    public com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext nextByRule(
            @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return journey.next(language);
    }

    /** V15 (AH-FR-15): modules done per stage, chain scores since the first assessment, the next stage. No AI. */
    @org.springframework.web.bind.annotation.GetMapping("/api/modules/journey")
    public com.visionmapping.dto.response.ModuleJourneyResponses.Journey journey(
            @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return journey.journey(language);
    }

    /** V15 (AH-FR-26): one practice for each barrier scored 3 or less. */
    @PostMapping("/api/ai/assessment/practice")
    public com.visionmapping.dto.response.AiModuleResponses.AssessmentPractice practice(
            @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.practice(language);
    }

    /** V15 (AH-FR-29): what changed since the previous take. */
    @PostMapping("/api/ai/assessment/what-changed")
    public com.visionmapping.dto.response.AiModuleResponses.WhatChanged whatChanged(
            @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.whatChanged(language);
    }

    @PostMapping("/api/ai/assessment/explain")
    public AssessmentExplanation explain(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.explainAssessment(language);
    }
}
