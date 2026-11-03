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

/** V9 Phase 6: AI for daily use. Suggestions only; saving goes through the normal endpoints. */
@Validated
@RestController
@RequestMapping("/api/ai/daily")
@RequiredArgsConstructor
public class AiDailyController {

    private final AiDailyService service;

    @PostMapping("/next-step")
    public NextStep nextStep(@RequestParam @Min(5) @Max(480) int minutes,
                             @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.nextStep(minutes, language);
    }

    @PostMapping("/weekly-review-draft")
    public WeeklyReviewDraft weeklyReview(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.weeklyReview(language);
    }

    @PostMapping("/pitch-feedback")
    public PitchFeedback pitch(@Valid @RequestBody AiPitchRequest request) {
        return service.pitch(request);
    }

    @PostMapping("/outreach-draft")
    public OutreachDraft outreach(@Valid @RequestBody AiOutreachRequest request) {
        return service.outreach(request);
    }

    @PostMapping("/goals/{goalId}/resource-ideas")
    public ResourceIdeas resources(@PathVariable Long goalId,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.resources(goalId, language);
    }
}
