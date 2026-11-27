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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
 * V15 Phase 1: the page assistant on the Modules, Assessment, Obstacles,
 * Communication and Work Style pages (AH-FR-01 to AH-FR-12). Its chat answers
 * go through /api/ai/chat/stream with an assistantPage. These endpoints give
 * the area's rule checks (no AI), proposed change cards (nothing is saved),
 * card outcomes, and the admin metrics.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/pages", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI page assistant", description = "The AI assistant on each page")
public class PageAssistantController {

    private final PageAssistantService service;

    /** AH-FR-10: the area's rule-based problems; never uses AI. */
    @Operation(summary = "Check a page's items with the AI assistant")
    @PostMapping(value = "/assistant/check", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageCheck> check(@Valid @RequestBody PageAssistantRequest request) {
        return ResponseEntity.ok(service.check(request));
    }

    /** AH-FR-06, AH-FR-07: checked change cards for the area; nothing is saved. */
    @Operation(summary = "Get AI proposals for a page")
    @PostMapping(value = "/assistant/proposals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageProposals> proposals(@Valid @RequestBody PageAssistantRequest request,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.propose(request, language));
    }

    /** AH-FR-09: how many cards of an answer were applied as they were (added) or after editing; counts only. */
    @Operation(summary = "Record what was done with AI proposals")
    @PostMapping(value = "/assistant/requests/{id}/outcome", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> outcome(@PathVariable @Positive Long id, @Valid @RequestBody AiPlanOutcomeRequest request) {
        service.recordOutcome(id, request.added(), request.edited());
        return ResponseEntity.noContent().build();
    }

    /** AH-FR-56: per-feature requests, cost, rating and card outcomes, and the Phase 1 baselines. Admins only. */
    @Operation(summary = "Get the page assistant's usage numbers")
    @GetMapping("/metrics")
    public ResponseEntity<PageMetrics> metrics(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.metrics(days));
    }
}
