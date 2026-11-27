package com.visionmapping.controller;

import com.visionmapping.dto.request.AiFeedbackDraftRequest;
import com.visionmapping.dto.request.AiSummaryRatingRequest;
import com.visionmapping.dto.response.AiCoachResponses.CohortSummary;
import com.visionmapping.dto.response.AiCoachResponses.FeedbackDraft;
import com.visionmapping.dto.response.AiCoachResponses.ParticipantSummary;
import com.visionmapping.service.ai.AiCoachService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;

/**
 * V9 Phase 8: the coach co-pilot. Every request goes through the coach's
 * access check (D-6). GET answers 204 when this week's summary is not made yet.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/coach", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Authorities.COACH_COHORTS + "')")
@Tag(name = "Coach AI summaries", description = "AI summaries and feedback drafts for a coach's participants and cohorts")
public class AiCoachController {

    private final AiCoachService service;

    @Operation(summary = "Get a participant's latest AI summary (204 when there is none)")
    @GetMapping("/participants/{id}/ai-summary")
    public ResponseEntity<ParticipantSummary> participantSummary(@PathVariable @Positive Long id) {
        return Optional.ofNullable(service.participantSummary(id)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Make a new AI summary of a participant")
    @PostMapping("/participants/{id}/ai-summary")
    public ResponseEntity<ParticipantSummary> generateParticipantSummary(@PathVariable @Positive Long id,
                                                         @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.generateParticipantSummary(id, language));
    }

    @Operation(summary = "Draft feedback for a participant with AI")
    @PostMapping(value = "/participants/{id}/feedback-draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeedbackDraft> feedbackDraft(@PathVariable @Positive Long id, @Valid @RequestBody AiFeedbackDraftRequest request) {
        return ResponseEntity.ok(service.feedbackDraft(id, request));
    }

    @Operation(summary = "Get a cohort's latest AI summary (204 when there is none)")
    @GetMapping("/cohorts/{cohortId}/ai-summary")
    public ResponseEntity<CohortSummary> cohortSummary(@PathVariable @Positive Long cohortId) {
        return Optional.ofNullable(service.cohortSummary(cohortId)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Make a new AI summary of a cohort")
    @PostMapping("/cohorts/{cohortId}/ai-summary")
    public ResponseEntity<CohortSummary> generateCohortSummary(@PathVariable @Positive Long cohortId,
                                               @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.generateCohortSummary(cohortId, language));
    }

    @Operation(summary = "Rate an AI summary")
    @PutMapping(value = "/ai-summaries/{id}/rating", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> rate(@PathVariable @Positive Long id, @Valid @RequestBody AiSummaryRatingRequest request) {
        service.rate(id, request);
        return ResponseEntity.noContent().build();
    }
}
