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

    @PostMapping("/api/ai/modules/{number}/help")
    public ModuleHelp help(@PathVariable Integer number, @Valid @RequestBody AiModuleHelpRequest request) {
        return service.help(number, request);
    }

    @PostMapping("/api/ai/assessment/explain")
    public AssessmentExplanation explain(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.explainAssessment(language);
    }
}
