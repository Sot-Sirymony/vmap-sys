package com.visionmapping.controller;

import com.visionmapping.dto.request.AiPlanAddRequest;
import com.visionmapping.dto.request.DreamIntakeRequest;
import com.visionmapping.dto.request.VisionStatementRequest;
import com.visionmapping.dto.response.AiPlanResponses.DreamIntake;
import com.visionmapping.dto.response.AiPlanResponses.VisionStatementDraft;
import com.visionmapping.service.ai.DreamCoachIntakeService;
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
import com.visionmapping.service.ai.SuggestionScope;
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
import com.visionmapping.service.ai.BlockerAnalysisService;
import com.visionmapping.dto.response.AiPlanResponses.BlockerAnalysis;
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
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;

/**
 * V9 Phase 5: the AI vision area, dream, goal and plan builders. They only
 * suggest; adding goes through the normal create endpoints.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/plan", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI plan building", description = "AI suggestions and checks while building a dream plan")
public class AiPlanController {

    private final AiPlanService service;
    private final AiPlanFeedbackService feedback;
    private final PlanBuilderDraftService drafts;
    private final DreamService dreamService;
    private final AiHelpersService helpers;
    private final PlanDraftCheckService draftCheck;
    private final BlockerAnalysisService blockers;
    private final DreamCoachIntakeService intake;

    @Operation(summary = "Suggest vision areas")
    @PostMapping("/vision-area-suggestions")
    public ResponseEntity<VisionAreaSuggestions> visionAreas(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.suggestVisionAreas(language));
    }

    @Operation(summary = "Suggest dreams for a vision area")
    @PostMapping("/vision-areas/{visionAreaId}/dream-suggestions")
    public ResponseEntity<DreamSuggestions> dreams(@PathVariable @Positive Long visionAreaId,
                                   @RequestParam(defaultValue = "false") boolean stretch,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.suggestDreams(visionAreaId, SuggestionScope.of(stretch), language));
    }

    @Operation(summary = "Suggest goals for a dream")
    @PostMapping(value = "/dreams/{dreamId}/goal-suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalSuggestions> goals(@PathVariable @Positive Long dreamId,
                                 @RequestParam(defaultValue = "false") boolean stretch,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                 @Valid @RequestBody(required = false) PlanAskRequest ask) {
        return ResponseEntity.ok(service.suggestGoals(dreamId, SuggestionScope.of(stretch), language, ask));
    }

    @Operation(summary = "Suggest steps for a goal")
    @PostMapping(value = "/goals/{goalId}/step-suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StepSuggestions> steps(@PathVariable @Positive Long goalId,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                 @Valid @RequestBody(required = false) PlanAskRequest ask) {
        return ResponseEntity.ok(service.suggestSteps(goalId, language, ask));
    }

    /** V11 (PB-FR-14): tasks for a complex step already in the plan. */
    @Operation(summary = "Suggest tasks for a step")
    @PostMapping(value = "/steps/{stepId}/task-suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskSuggestions> tasks(@PathVariable @Positive Long stepId,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                 @Valid @RequestBody(required = false) PlanAskRequest ask) {
        return ResponseEntity.ok(service.suggestTasks(stepId, language, ask));
    }

    /** V17 BL-FR-29: smaller tasks for one Blocked or overdue task. Suggestions only. */
    @Operation(summary = "Suggest smaller tasks for a task")
    @PostMapping("/tasks/{taskId}/smaller-tasks")
    public ResponseEntity<TaskSuggestions> smallerTasks(@PathVariable @Positive Long taskId,
                                        @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.suggestSmallerTasks(taskId, language));
    }

    /** V11 (PB-FR-14): tasks for a complex step that exists only in the Build my plan draft. */
    @Operation(summary = "Suggest tasks for a draft plan's step")
    @PostMapping(value = "/dreams/{dreamId}/draft-task-suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskSuggestions> draftStepTasks(@PathVariable @Positive Long dreamId, @Valid @RequestBody DraftStepTasksRequest request,
                                          @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.suggestDraftStepTasks(dreamId, request, language));
    }

    /** V11 (PB-FR-23 to PB-FR-26, PB-FR-40): helpers, risks and resources for the plan being built. */
    @Operation(summary = "Suggest helpers and risks for a draft plan")
    @PostMapping(value = "/dreams/{dreamId}/helpers-and-risks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HelpersAndRisks> helpersAndRisks(@PathVariable @Positive Long dreamId, @Valid @RequestBody PlanHelpersRequest request,
                                           @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(helpers.suggest(dreamId, request, language));
    }

    /** V11 (PB-FR-28): the plan check on the saved Build my plan draft; rule checks work without AI. */
    @Operation(summary = "Check a draft plan")
    @PostMapping("/dreams/{dreamId}/draft-check")
    public ResponseEntity<DraftCheck> draftCheck(@PathVariable @Positive Long dreamId,
                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language,
                                 // V31 DC-FR-04: the live pass/fail list re-runs the rules alone, never AI (DC-NFR-03).
                                 @RequestParam(defaultValue = "false") boolean rulesOnly) {
        return ResponseEntity.ok(draftCheck.check(dreamId, language, rulesOnly));
    }

    /** V11 (PB-FR-29): a proposed change for one AI issue in the draft; nothing is saved. */
    @Operation(summary = "Suggest a fix for a draft plan problem")
    @PostMapping(value = "/dreams/{dreamId}/draft-fix", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftFix> draftFix(@PathVariable @Positive Long dreamId, @Valid @RequestBody DraftFixRequest request,
                             @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(draftCheck.fix(dreamId, request, language));
    }

    /** V11 (PB-FR-16): hours a week for plan work, and the hours the saved plan already asks of each week. */
    @Operation(summary = "Get my weekly planning capacity")
    @GetMapping("/capacity")
    public ResponseEntity<PlanCapacity> capacity() {
        return ResponseEntity.ok(draftCheck.capacity());
    }

    @Operation(summary = "Save my weekly planning capacity")
    @PutMapping(value = "/capacity", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanCapacity> saveCapacity(@Valid @RequestBody PlanCapacityRequest request) {
        return ResponseEntity.ok(draftCheck.saveCapacity(request));
    }

    /** V11 (PB-FR-08): the next clarifying question about the dream, one at a time, at most three. */
    /** V31 v1 (DC-FR-12): a vision statement worded from the participant's sentence; nothing is saved. */
    @Operation(summary = "Draft a vision statement")
    @PostMapping(value = "/vision-statement", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisionStatementDraft> visionStatement(@Valid @RequestBody VisionStatementRequest request,
                                                @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(intake.visionStatement(request, language));
    }

    /** V31 v1 (DC-FR-15, DC-FR-16): the next question about a dream not saved yet, or its restatement. */
    @Operation(summary = "Turn a first idea into a dream draft")
    @PostMapping(value = "/dream-intake", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DreamIntake> dreamIntake(@Valid @RequestBody DreamIntakeRequest request,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(intake.dreamIntake(request, language));
    }

    @Operation(summary = "Ask a clarifying question about a dream")
    @PostMapping(value = "/dreams/{dreamId}/clarify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClarifyQuestion> clarify(@PathVariable @Positive Long dreamId, @Valid @RequestBody PlanClarifyRequest request,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.clarifyDream(dreamId, request, language));
    }

    /** V11 (PB-FR-10): save one clarifying answer into the dream, when the participant chooses to. */
    @Operation(summary = "Save a dream field the AI helped clarify")
    @PutMapping(value = "/dreams/{dreamId}/clarified-field", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DreamResponse> saveClarifiedField(@PathVariable @Positive Long dreamId, @Valid @RequestBody PlanClarifiedFieldRequest request) {
        return ResponseEntity.ok(dreamService.saveClarifiedField(dreamId, request.field(), request.value()));
    }

    @Operation(summary = "Check a dream plan")
    @PostMapping("/dreams/{dreamId}/check")
    public ResponseEntity<PlanCheck> check(@PathVariable @Positive Long dreamId,
                           @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.checkPlan(dreamId, language));
    }

    /** Vision Map: what is holding the dream back, ranked; the rule half works without AI. Nothing is saved. */
    @Operation(summary = "Analyse what blocks a dream plan")
    @PostMapping("/dreams/{dreamId}/blocker-analysis")
    public ResponseEntity<BlockerAnalysis> blockerAnalysis(@PathVariable @Positive Long dreamId,
                                           @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(blockers.analyse(dreamId, language));
    }

    @Operation(summary = "List steps that are slipping")
    @GetMapping("/slipping-steps")
    public ResponseEntity<List<SlippingStep>> slippingSteps() {
        return ResponseEntity.ok(service.slippingSteps());
    }

    @Operation(summary = "Suggest how to rescue a slipping step")
    @PostMapping("/steps/{stepId}/rescue")
    public ResponseEntity<StepRescue> rescue(@PathVariable @Positive Long stepId,
                             @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.rescueStep(stepId, language));
    }

    /** V11 Phase 2: steps for a goal that exists only in the Build my plan draft. */
    @Operation(summary = "Suggest steps for a draft plan's goal")
    @PostMapping(value = "/dreams/{dreamId}/draft-step-suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StepSuggestions> draftGoalSteps(@PathVariable @Positive Long dreamId, @Valid @RequestBody DraftGoalStepsRequest request,
                                          @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.suggestDraftGoalSteps(dreamId, request, language));
    }

    /** V11 (PB-FR-30): the participant's unfinished Build my plan draft; 204 when there is none. */
    @Operation(summary = "Get a dream's saved plan draft (204 when there is none)")
    @GetMapping("/drafts/{dreamId}")
    public ResponseEntity<PlanDraftResponse> draft(@PathVariable @Positive Long dreamId) {
        return drafts.get(dreamId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Save a dream's plan draft")
    @PutMapping(value = "/drafts/{dreamId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanDraftResponse> saveDraft(@PathVariable @Positive Long dreamId, @Valid @RequestBody PlanDraftRequest request) {
        return ResponseEntity.ok(drafts.save(dreamId, request));
    }

    @Operation(summary = "Discard a dream's plan draft")
    @DeleteMapping("/drafts/{dreamId}")
    public ResponseEntity<Void> discardDraft(@PathVariable @Positive Long dreamId) {
        drafts.discard(dreamId);
        return ResponseEntity.noContent().build();
    }

    /** V11 (PB-FR-31): every new item in the draft, saved all together or not at all; the draft is then removed. */
    @Operation(summary = "Add every new item in a plan draft to the plan")
    @PostMapping(value = "/drafts/{dreamId}/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanAddResult> addDraft(@PathVariable @Positive Long dreamId,
                                  @Valid @RequestBody(required = false) com.visionmapping.dto.request.PlanSaveRequest request) {
        // V31 DC-FR-56: the server re-runs the rules and refuses any MUST fail.
        return ResponseEntity.status(HttpStatus.CREATED).body(drafts.add(dreamId, com.visionmapping.dto.request.PlanSaveRequest.acceptedShouldOf(request)));
    }

    /** V11 (PB-FR-34): thumbs up or down on one plan builder answer. */
    @Operation(summary = "Rate an AI plan request")
    @PutMapping(value = "/requests/{id}/rating", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> rate(@PathVariable @Positive Long id, @Valid @RequestBody AiRatingRequest request) {
        feedback.rate(id, request);
        return ResponseEntity.noContent().build();
    }

    /** V11 (PB-FR-35): how many suggestions were kept, for those saved through the normal create endpoints. */
    @Operation(summary = "Record what was done with AI suggestions")
    @PostMapping(value = "/requests/{id}/outcome", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> outcome(@PathVariable @Positive Long id, @Valid @RequestBody AiPlanOutcomeRequest request) {
        feedback.recordOutcome(id, request);
        return ResponseEntity.noContent().build();
    }

    /** V11 (PB-FR-31): kept goals and steps, saved all together or not at all. */
    @Operation(summary = "Add AI suggestions to the plan")
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanAddResult> add(@Valid @RequestBody AiPlanAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback.add(request));
    }

    /** V11 (PB-FR-32): archives what one Add saved. */
    @Operation(summary = "Undo AI suggestions added to the plan")
    @PostMapping(value = "/undo", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> undo(@Valid @RequestBody AiPlanUndoRequest request) {
        feedback.undo(request);
        return ResponseEntity.noContent().build();
    }
}
