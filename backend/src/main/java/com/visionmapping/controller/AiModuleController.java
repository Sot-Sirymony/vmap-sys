package com.visionmapping.controller;

import com.visionmapping.dto.request.AiModuleHelpRequest;
import com.visionmapping.dto.response.AiModuleResponses.ModuleHelp;
import com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext;
import com.visionmapping.service.ai.AiModuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V8: AI help with the learning modules. The rule-based journey is in
 * {@link ModuleJourneyController} and the assessment help in
 * {@link AiAssessmentController} (V33 D-33-1: one root per controller).
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/modules", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI module help", description = "AI help with the learning modules")
public class AiModuleController {

    private final AiModuleService service;

    @Operation(summary = "Ask the AI for help with a module")
    @PostMapping(value = "/{number}/help", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ModuleHelp> help(@PathVariable @Positive Integer number, @Valid @RequestBody AiModuleHelpRequest request) {
        return ResponseEntity.ok(service.help(number, request));
    }

    @Operation(summary = "Ask the AI which module to take next")
    @PostMapping("/next")
    public ResponseEntity<ModuleNext> next(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.next(language));
    }
}
