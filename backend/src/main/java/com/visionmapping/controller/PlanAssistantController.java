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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V11 Phase 7: the plan assistant on the Plan page and Build my plan
 * (PB-FR-41 to PB-FR-50). Its chat answers go through /api/ai/chat/stream with
 * a surface and section. These endpoints give the section check, which needs
 * no AI, proposed change cards, and saving one card on the Plan page.
 */
@Validated
@RestController
@RequestMapping("/api/ai/plan/dreams/{dreamId}/assistant")
@RequiredArgsConstructor
public class PlanAssistantController {

    private final PlanAssistantService service;

    /** PB-FR-49: the section's rule-based problems; never uses AI. */
    @PostMapping("/section-check")
    public SectionCheck sectionCheck(@PathVariable Long dreamId, @Valid @RequestBody PlanAssistantRequest request) {
        return service.sectionCheck(dreamId, request);
    }

    /** PB-FR-46, PB-FR-47: change cards for the section; nothing is saved. */
    @PostMapping("/proposals")
    public AssistantProposals proposals(@PathVariable Long dreamId, @Valid @RequestBody PlanAssistantRequest request,
                                        @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.propose(dreamId, request, language);
    }

    /** PB-FR-46: saves one card the participant chose, on the Plan page, through the normal services. */
    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantApplied apply(@PathVariable Long dreamId, @Valid @RequestBody PlanAssistantApplyRequest request) {
        return service.apply(dreamId, request);
    }
}
