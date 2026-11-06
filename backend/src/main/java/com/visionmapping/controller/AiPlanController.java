package com.visionmapping.controller;

import com.visionmapping.dto.request.AiPlanAddRequest;
import com.visionmapping.dto.request.DraftGoalStepsRequest;
import com.visionmapping.dto.request.DraftFixRequest;
import com.visionmapping.dto.request.DraftStepTasksRequest;
import com.visionmapping.dto.request.PlanCapacityRequest;
import com.visionmapping.dto.response.AiPlanResponses.DraftCheck;
import com.visionmapping.dto.response.AiPlanResponses.DraftFix;
import com.visionmapping.dto.response.AiPlanResponses.PlanCapacity;
import com.visionmapping.service.ai.PlanDraftCheckService;
import com.visionmapping.dto.request.PlanHelpersRequest;
import com.visionmapping.dto.response.AiPlanResponses.HelpersAndRisks;
import com.visionmapping.service.ai.AiHelpersService;
import com.visionmapping.dto.response.AiPlanResponses.TaskSuggestions;
import com.visionmapping.dto.request.PlanAskRequest;
import com.visionmapping.dto.request.PlanClarifiedFieldRequest;
import com.visionmapping.dto.request.PlanClarifyRequest;
import com.visionmapping.dto.response.AiPlanResponses.ClarifyQuestion;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.service.DreamService;
import com.visionmapping.dto.request.PlanDraftRequest;
import com.visionmapping.dto.response.PlanDraftResponse;
import com.visionmapping.service.ai.PlanBuilderDraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final PlanBuilderDraftService drafts;
    private final DreamService dreamService;
    private final AiHelpersService helpers;
    private final PlanDraftCheckService draftCheck;

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
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                 @Valid @RequestBody(required = false) PlanAskRequest ask) {
        return service.suggestGoals(dreamId, stretch, language, ask);
    }

    @PostMapping("/goals/{goalId}/step-suggestions")
    public StepSuggestions steps(@PathVariable Long goalId,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                 @Valid @RequestBody(required = false) PlanAskRequest ask) {
        return service.suggestSteps(goalId, language, ask);
    }

    /** V11 (PB-FR-14): tasks for a complex step already in the plan. */
    @PostMapping("/steps/{stepId}/task-suggestions")
    public TaskSuggestions tasks(@PathVariable Long stepId,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                 @Valid @RequestBody(required = false) PlanAskRequest ask) {
        return service.suggestTasks(stepId, language, ask);
    }

    /** V11 (PB-FR-14): tasks for a complex step that exists only in the Build my plan draft. */
    @PostMapping("/dreams/{dreamId}/draft-task-suggestions")
    public TaskSuggestions draftStepTasks(@PathVariable Long dreamId, @Valid @RequestBody DraftStepTasksRequest request,
                                          @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.suggestDraftStepTasks(dreamId, request, language);
    }

    /** V11 (PB-FR-23 to PB-FR-26, PB-FR-40): helpers, risks and resources for the plan being built. */
    @PostMapping("/dreams/{dreamId}/helpers-and-risks")
    public HelpersAndRisks helpersAndRisks(@PathVariable Long dreamId, @Valid @RequestBody PlanHelpersRequest request,
                                           @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return helpers.suggest(dreamId, request, language);
    }

    /** V11 (PB-FR-28): the plan check on the saved Build my plan draft; rule checks work without AI. */
    @PostMapping("/dreams/{dreamId}/draft-check")
    public DraftCheck draftCheck(@PathVariable Long dreamId,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return draftCheck.check(dreamId, language);
    }

    /** V11 (PB-FR-29): a proposed change for one AI issue in the draft; nothing is saved. */
    @PostMapping("/dreams/{dreamId}/draft-fix")
    public DraftFix draftFix(@PathVariable Long dreamId, @Valid @RequestBody DraftFixRequest request,
                             @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return draftCheck.fix(dreamId, request, language);
    }

    /** V11 (PB-FR-16): hours a week for plan work, and the hours the saved plan already asks of each week. */
    @GetMapping("/capacity")
    public PlanCapacity capacity() {
        return draftCheck.capacity();
    }

    @PutMapping("/capacity")
    public PlanCapacity saveCapacity(@Valid @RequestBody PlanCapacityRequest request) {
        return draftCheck.saveCapacity(request);
    }

    /** V11 (PB-FR-08): the next clarifying question about the dream, one at a time, at most three. */
    @PostMapping("/dreams/{dreamId}/clarify")
    public ClarifyQuestion clarify(@PathVariable Long dreamId, @Valid @RequestBody PlanClarifyRequest request,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.clarifyDream(dreamId, request, language);
    }

    /** V11 (PB-FR-10): save one clarifying answer into the dream, when the participant chooses to. */
    @PutMapping("/dreams/{dreamId}/clarified-field")
    public DreamResponse saveClarifiedField(@PathVariable Long dreamId, @Valid @RequestBody PlanClarifiedFieldRequest request) {
        return dreamService.saveClarifiedField(dreamId, request.field(), request.value());
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

    /** V11 Phase 2: steps for a goal that exists only in the Build my plan draft. */
    @PostMapping("/dreams/{dreamId}/draft-step-suggestions")
    public StepSuggestions draftGoalSteps(@PathVariable Long dreamId, @Valid @RequestBody DraftGoalStepsRequest request,
                                          @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.suggestDraftGoalSteps(dreamId, request, language);
    }

    /** V11 (PB-FR-30): the participant's unfinished Build my plan draft; 204 when there is none. */
    @GetMapping("/drafts/{dreamId}")
    public ResponseEntity<PlanDraftResponse> draft(@PathVariable Long dreamId) {
        return drafts.get(dreamId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/drafts/{dreamId}")
    public PlanDraftResponse saveDraft(@PathVariable Long dreamId, @Valid @RequestBody PlanDraftRequest request) {
        return drafts.save(dreamId, request);
    }

    @DeleteMapping("/drafts/{dreamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardDraft(@PathVariable Long dreamId) {
        drafts.discard(dreamId);
    }

    /** V11 (PB-FR-31): every new item in the draft, saved all together or not at all; the draft is then removed. */
    @PostMapping("/drafts/{dreamId}/add")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanAddResult addDraft(@PathVariable Long dreamId) {
        return drafts.add(dreamId);
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
