package com.visionmapping.controller;

import com.visionmapping.dto.request.AiPlanOutcomeRequest;
import com.visionmapping.dto.request.PageAssistantRequest;
import com.visionmapping.dto.response.PageAssistantResponses.PageCheck;
import com.visionmapping.dto.response.PageAssistantResponses.PageMetrics;
import com.visionmapping.dto.response.PageAssistantResponses.PageProposals;
import com.visionmapping.service.ai.PageAssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V15 Phase 1: the page assistant on the Modules, Assessment, Obstacles,
 * Communication and Work Style pages (AH-FR-01 to AH-FR-12). Its chat answers
 * go through /api/ai/chat/stream with an assistantPage. These endpoints give
 * the area's rule checks (no AI), proposed change cards (nothing is saved),
 * card outcomes, and the admin metrics.
 */
@Validated
@RestController
@RequestMapping("/api/ai/pages")
@RequiredArgsConstructor
public class PageAssistantController {

    private final PageAssistantService service;

    /** AH-FR-10: the area's rule-based problems; never uses AI. */
    @PostMapping("/assistant/check")
    public PageCheck check(@Valid @RequestBody PageAssistantRequest request) {
        return service.check(request);
    }

    /** AH-FR-06, AH-FR-07: checked change cards for the area; nothing is saved. */
    @PostMapping("/assistant/proposals")
    public PageProposals proposals(@Valid @RequestBody PageAssistantRequest request,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.propose(request, language);
    }

    /** AH-FR-09: how many cards of an answer were applied as they were (added) or after editing; counts only. */
    @PostMapping("/assistant/requests/{id}/outcome")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void outcome(@PathVariable Long id, @Valid @RequestBody AiPlanOutcomeRequest request) {
        service.recordOutcome(id, request.added(), request.edited());
    }

    /** AH-FR-56: per-feature requests, cost, rating and card outcomes, and the Phase 1 baselines. Admins only. */
    @GetMapping("/metrics")
    public PageMetrics metrics(@RequestParam(defaultValue = "30") int days) {
        return service.metrics(days);
    }
}
