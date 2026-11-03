package com.visionmapping.controller;

import com.visionmapping.dto.response.AiPlanResponses.DreamSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.GoalSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.PlanCheck;
import com.visionmapping.dto.response.AiPlanResponses.SlippingStep;
import com.visionmapping.dto.response.AiPlanResponses.StepRescue;
import com.visionmapping.dto.response.AiPlanResponses.StepSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.VisionAreaSuggestions;
import com.visionmapping.service.ai.AiPlanService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
