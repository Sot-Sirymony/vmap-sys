package com.visionmapping.controller;

import com.visionmapping.dto.request.PlanAssistantApplyRequest;
import com.visionmapping.dto.request.PlanAssistantRequest;
import com.visionmapping.dto.response.AiPlanResponses.AssistantApplied;
import com.visionmapping.dto.response.AiPlanResponses.AssistantProposals;
import com.visionmapping.dto.response.AiPlanResponses.SectionCheck;
import com.visionmapping.service.ai.PlanAssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * V11 Phase 7: the plan assistant on the Plan page and Build my plan
 * (PB-FR-41 to PB-FR-50). Its chat answers go through /api/ai/chat/stream with
 * a surface and section. These endpoints give the section check, which needs
 * no AI, proposed change cards, and saving one card on the Plan page.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/plan/dreams/{dreamId}/assistant", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI plan assistant", description = "The AI assistant inside a dream plan")
public class PlanAssistantController {

    private final PlanAssistantService service;

    /** PB-FR-49: the section's rule-based problems; never uses AI. */
    @Operation(summary = "Check a plan section with the AI assistant")
    @PostMapping(value = "/section-check", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SectionCheck> sectionCheck(@PathVariable @Positive Long dreamId, @Valid @RequestBody PlanAssistantRequest request) {
        return ResponseEntity.ok(service.sectionCheck(dreamId, request));
    }

    /** PB-FR-46, PB-FR-47: change cards for the section; nothing is saved. */
    @Operation(summary = "Get AI proposals for a plan section")
    @PostMapping(value = "/proposals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantProposals> proposals(@PathVariable @Positive Long dreamId, @Valid @RequestBody PlanAssistantRequest request,
                                        @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.propose(dreamId, request, language));
    }

    /** PB-FR-46: saves one card the participant chose, on the Plan page, through the normal services. */
    @Operation(summary = "Apply an AI proposal to the plan")
    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantApplied> apply(@PathVariable @Positive Long dreamId, @Valid @RequestBody PlanAssistantApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(dreamId, request));
    }
}
