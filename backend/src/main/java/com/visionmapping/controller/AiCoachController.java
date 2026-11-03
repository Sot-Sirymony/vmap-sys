package com.visionmapping.controller;

import com.visionmapping.dto.request.AiFeedbackDraftRequest;
import com.visionmapping.dto.request.AiSummaryRatingRequest;
import com.visionmapping.dto.response.AiCoachResponses.CohortSummary;
import com.visionmapping.dto.response.AiCoachResponses.FeedbackDraft;
import com.visionmapping.dto.response.AiCoachResponses.ParticipantSummary;
import com.visionmapping.service.ai.AiCoachService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V9 Phase 8: the coach co-pilot. Every request goes through the coach's
 * access check (D-6). GET answers 204 when this week's summary is not made yet.
 */
@Validated
@RestController
@RequestMapping("/api/coach")
@RequiredArgsConstructor
public class AiCoachController {

    private final AiCoachService service;

    @GetMapping("/participants/{id}/ai-summary")
    public ResponseEntity<ParticipantSummary> participantSummary(@PathVariable Long id) {
        ParticipantSummary summary = service.participantSummary(id);
        return summary == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(summary);
    }

    @PostMapping("/participants/{id}/ai-summary")
    public ParticipantSummary generateParticipantSummary(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.generateParticipantSummary(id, language);
    }

    @PostMapping("/participants/{id}/feedback-draft")
    public FeedbackDraft feedbackDraft(@PathVariable Long id, @Valid @RequestBody AiFeedbackDraftRequest request) {
        return service.feedbackDraft(id, request);
    }

    @GetMapping("/cohorts/{cohortId}/ai-summary")
    public ResponseEntity<CohortSummary> cohortSummary(@PathVariable Long cohortId) {
        CohortSummary summary = service.cohortSummary(cohortId);
        return summary == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(summary);
    }

    @PostMapping("/cohorts/{cohortId}/ai-summary")
    public CohortSummary generateCohortSummary(@PathVariable Long cohortId,
                                               @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.generateCohortSummary(cohortId, language);
    }

    @PutMapping("/ai-summaries/{id}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@PathVariable Long id, @Valid @RequestBody AiSummaryRatingRequest request) {
        service.rate(id, request);
    }
}
