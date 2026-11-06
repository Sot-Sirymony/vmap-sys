package com.visionmapping.controller;

import com.visionmapping.dto.request.AiPlanAddRequest;
import com.visionmapping.dto.request.AiPlanOutcomeRequest;
import com.visionmapping.dto.request.AiPlanUndoRequest;
import com.visionmapping.dto.request.AiRatingRequest;
import com.visionmapping.dto.response.AiPlanResponses.DreamSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.PlanAddResult;
import com.visionmapping.dto.response.AiPlanResponses.GoalSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.PlanCheck;
import com.visionmapping.dto.response.AiPlanResponses.SlippingStep;
import com.visionmapping.dto.response.AiPlanResponses.StepRescue;
import com.visionmapping.dto.response.AiPlanResponses.StepSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.VisionAreaSuggestions;
import com.visionmapping.service.ai.AiPlanFeedbackService;
import com.visionmapping.service.ai.AiPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 * V9 Phase 5: the AI vision area, dream, goal and plan builders. They only
 * suggest; adding goes through the normal create endpoints.
 */
@Validated
@RestController
@RequestMapping("/api/ai/plan")
@RequiredArgsConstructor
public class AiPlanController {

    private final AiPlanService service;
    private final AiPlanFeedbackService feedback;

    @PostMapping("/vision-area-suggestions")
    public VisionAreaSuggestions visionAreas(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.suggestVisionAreas(language);
    }

    @PostMapping("/vision-areas/{visionAreaId}/dream-suggestions")
    public DreamSuggestions dreams(@PathVariable Long visionAreaId,
                                   @RequestParam(defaultValue = "false") boolean stretch,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.suggestDreams(visionAreaId, stretch, language);
    }

    @PostMapping("/dreams/{dreamId}/goal-suggestions")
    public GoalSuggestions goals(@PathVariable Long dreamId,
                                 @RequestParam(defaultValue = "false") boolean stretch,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.suggestGoals(dreamId, stretch, language);
    }

    @PostMapping("/goals/{goalId}/step-suggestions")
    public StepSuggestions steps(@PathVariable Long goalId,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.suggestSteps(goalId, language);
    }

    @PostMapping("/dreams/{dreamId}/check")
    public PlanCheck check(@PathVariable Long dreamId,
                           @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.checkPlan(dreamId, language);
    }

    @GetMapping("/slipping-steps")
    public List<SlippingStep> slippingSteps() {
        return service.slippingSteps();
    }

    @PostMapping("/steps/{stepId}/rescue")
    public StepRescue rescue(@PathVariable Long stepId,
                             @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.rescueStep(stepId, language);
    }

    /** V11 (PB-FR-34): thumbs up or down on one plan builder answer. */
    @PutMapping("/requests/{id}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@PathVariable Long id, @Valid @RequestBody AiRatingRequest request) {
        feedback.rate(id, request);
    }

    /** V11 (PB-FR-35): how many suggestions were kept, for those saved through the normal create endpoints. */
    @PostMapping("/requests/{id}/outcome")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void outcome(@PathVariable Long id, @Valid @RequestBody AiPlanOutcomeRequest request) {
        feedback.recordOutcome(id, request);
    }

    /** V11 (PB-FR-31): kept goals and steps, saved all together or not at all. */
    @PostMapping("/add")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanAddResult add(@Valid @RequestBody AiPlanAddRequest request) {
        return feedback.add(request);
    }

    /** V11 (PB-FR-32): archives what one Add saved. */
    @PostMapping("/undo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@Valid @RequestBody AiPlanUndoRequest request) {
        feedback.undo(request);
    }
}
