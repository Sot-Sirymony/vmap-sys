package com.visionmapping.controller;

import com.visionmapping.dto.request.AiOutreachRequest;
import com.visionmapping.dto.request.AiPitchRequest;
import com.visionmapping.dto.response.AiDailyResponses.NextStep;
import com.visionmapping.dto.response.AiDailyResponses.OutreachDraft;
import com.visionmapping.dto.response.AiDailyResponses.PitchFeedback;
import com.visionmapping.dto.response.AiDailyResponses.ResourceIdeas;
import com.visionmapping.dto.response.AiDailyResponses.WeeklyReviewDraft;
import com.visionmapping.service.ai.AiDailyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
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

/** V9 Phase 6: AI for daily use. Suggestions only; saving goes through the normal endpoints. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/daily", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI daily help", description = "AI help with the next step, the weekly review, pitches, outreach and resources")
public class AiDailyController {

    private final AiDailyService service;

    @Operation(summary = "Suggest the next step for the minutes I have")
    @PostMapping("/next-step")
    public ResponseEntity<NextStep> nextStep(@RequestParam @Min(5) @Max(480) int minutes,
                             @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.nextStep(minutes, language));
    }

    @Operation(summary = "Draft my weekly review with AI")
    @PostMapping("/weekly-review-draft")
    public ResponseEntity<WeeklyReviewDraft> weeklyReview(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.weeklyReview(language));
    }

    @Operation(summary = "Get AI feedback on a pitch")
    @PostMapping(value = "/pitch-feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PitchFeedback> pitch(@Valid @RequestBody AiPitchRequest request) {
        return ResponseEntity.ok(service.pitch(request));
    }

    @Operation(summary = "Draft an outreach message with AI")
    @PostMapping(value = "/outreach-draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OutreachDraft> outreach(@Valid @RequestBody AiOutreachRequest request) {
        return ResponseEntity.ok(service.outreach(request));
    }

    @Operation(summary = "Suggest resources for a goal")
    @PostMapping("/goals/{goalId}/resource-ideas")
    public ResponseEntity<ResourceIdeas> resources(@PathVariable @Positive Long goalId,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.resources(goalId, language));
    }

    @Operation(summary = "Suggest resources for a step")
    @PostMapping("/steps/{stepId}/resource-ideas")
    public ResponseEntity<ResourceIdeas> stepResources(@PathVariable @Positive Long stepId,
                                       @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.stepResources(stepId, language));
    }
}
