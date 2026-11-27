package com.visionmapping.controller;

import com.visionmapping.dto.request.StreamlineRequests;
import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.service.StreamlineReviewService;
import com.visionmapping.service.ai.StreamlineAiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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
import com.visionmapping.web.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;

/** V27 (BRD 27): the Streamline review. Every call is scoped to the signed-in user. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/streamline-reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Streamline reviews", description = "The five-stage Streamline review and its AI help")
public class StreamlineReviewController {

    private final StreamlineReviewService service;
    private final StreamlineAiService aiService;
    private final com.visionmapping.service.StreamlineBatchService batchService;

    @Operation(summary = "Start a Streamline review")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> start(@Valid @RequestBody StreamlineRequests.Start request) {
        StreamlineResponses.Review created = service.start(request);
        return ApiResponses.createdAt("/streamline-reviews", created.id(), created);
    }

    @Operation(summary = "List reviews")
    @GetMapping
    public ResponseEntity<List<StreamlineResponses.ListItem>> list() {
        return ResponseEntity.ok(service.list());
    }

    /** §6.2: goals and steps worth streamlining. */
    @Operation(summary = "List goals and steps worth streamlining")
    @GetMapping("/candidates")
    public ResponseEntity<List<StreamlineResponses.Candidate>> candidates() {
        return ResponseEntity.ok(service.candidates());
    }

    /** V32 SF-FR-15 (D-32-12): saves dismissed suggestions, so they stay dismissed on every device. */
    @Operation(summary = "Dismiss streamlining candidates")
    @PostMapping(value = "/candidate-dismissals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StreamlineResponses.DismissalView>> dismissCandidates(@Valid @RequestBody StreamlineRequests.Dismissals request) {
        return ResponseEntity.ok(service.dismiss(request));
    }

    /** V32 D-32-12: the dismissals still in force (90 days). */
    @Operation(summary = "List dismissed candidates")
    @GetMapping("/candidate-dismissals")
    public ResponseEntity<List<StreamlineResponses.DismissalView>> candidateDismissals() {
        return ResponseEntity.ok(service.dismissals());
    }

    @Operation(summary = "Get a review")
    @GetMapping("/{id}")
    public ResponseEntity<StreamlineResponses.Review> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Operation(summary = "Get a review's summary")
    @GetMapping("/{id}/summary")
    public ResponseEntity<StreamlineResponses.Summary> summary(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.get(id).summary());
    }

    @Operation(summary = "Update a review item's answers")
    @PutMapping(value = "/{id}/items/{itemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> updateItem(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId,
                                                 @Valid @RequestBody StreamlineRequests.UpdateItem request) {
        return ResponseEntity.ok(service.updateItem(id, itemId, request));
    }

    @Operation(summary = "Answer one question for many items")
    @PutMapping(value = "/{id}/items/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> bulk(@PathVariable @Positive Long id, @Valid @RequestBody StreamlineRequests.BulkQuestion request) {
        return ResponseEntity.ok(service.bulkQuestion(id, request));
    }

    @Operation(summary = "Cut an item in the review (archived, not deleted)")
    @PostMapping(value = "/{id}/items/{itemId}/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> delete(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId,
                                             @Valid @RequestBody(required = false) StreamlineRequests.Delete request) {
        return ResponseEntity.ok(service.deleteItem(id, itemId, request));
    }

    @Operation(summary = "Bring back an item cut in the review")
    @PostMapping("/{id}/items/{itemId}/restore")
    public ResponseEntity<StreamlineResponses.Review> restore(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId) {
        return ResponseEntity.ok(service.restoreItem(id, itemId));
    }

    @Operation(summary = "Merge an item into another")
    @PostMapping(value = "/{id}/items/{itemId}/merge", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> merge(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId,
                                            @Valid @RequestBody StreamlineRequests.Merge request) {
        return ResponseEntity.ok(service.mergeItem(id, itemId, request));
    }

    @Operation(summary = "Simplify an item")
    @PutMapping(value = "/{id}/items/{itemId}/simplify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> simplify(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId,
                                               @Valid @RequestBody StreamlineRequests.Simplify request) {
        return ResponseEntity.ok(service.simplify(id, itemId, request));
    }

    @Operation(summary = "Make a task repeat from the review")
    @PostMapping(value = "/{id}/items/{itemId}/repeat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> repeat(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId,
                                             @Valid @RequestBody StreamlineRequests.Repeat request) {
        return ResponseEntity.ok(service.repeat(id, itemId, request));
    }

    @Operation(summary = "Set a review's target")
    @PutMapping(value = "/{id}/target", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> target(@PathVariable @Positive Long id, @Valid @RequestBody StreamlineRequests.Target request) {
        return ResponseEntity.ok(service.setTarget(id, request));
    }

    @Operation(summary = "Complete a review stage")
    @PostMapping("/{id}/stages/{stage}/complete")
    public ResponseEntity<StreamlineResponses.Review> complete(@PathVariable @Positive Long id, @PathVariable @Min(1) @Max(5) int stage) {
        return ResponseEntity.ok(service.completeStage(id, stage));
    }

    @Operation(summary = "Mark a review stage as not needed")
    @PostMapping(value = "/{id}/stages/{stage}/not-needed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.Review> notNeeded(@PathVariable @Positive Long id, @PathVariable @Min(1) @Max(5) int stage,
                                                @Valid @RequestBody StreamlineRequests.NotNeeded request) {
        return ResponseEntity.ok(service.notNeeded(id, stage, request));
    }

    @Operation(summary = "Abandon a review")
    @PostMapping("/{id}/abandon")
    public ResponseEntity<StreamlineResponses.Review> abandon(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.abandon(id));
    }

    /** V32 SF-FR-08 (D-32-7): drafts the current stage and every later one in one AI request. Suggestions only. */
    @Operation(summary = "Draft AI suggestions for every remaining stage")
    @PostMapping("/{id}/draft-all")
    public ResponseEntity<StreamlineAiService.DraftAll> draftAll(@PathVariable @Positive Long id,
                                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(aiService.draftAll(id, language));
    }

    /** V32 (D-32-7, D-32-8): the saved drafts for the current and later stages, and whether Draft all stages can run. */
    @Operation(summary = "Get a review's saved drafts")
    @GetMapping("/{id}/drafts")
    public ResponseEntity<StreamlineAiService.Drafts> drafts(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(aiService.drafts(id));
    }

    /** V32 SF-FR-05 (D-32-4): Apply selected or Apply all, one action after another; stops at the first failure. */
    @Operation(summary = "Apply a batch of suggestions")
    @PostMapping(value = "/{id}/apply-batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamlineResponses.BatchResult> applyBatch(@PathVariable @Positive Long id, @Valid @RequestBody StreamlineRequests.ApplyBatch request) {
        return ResponseEntity.ok(batchService.apply(id, request));
    }

    /** V32 SF-FR-06 (D-32-5): undoes a whole batch while its stage is current, for up to 24 hours. */
    @Operation(summary = "Undo a batch of suggestions")
    @PostMapping("/{id}/batches/{batchId}/undo")
    public ResponseEntity<StreamlineResponses.UndoResult> undoBatch(@PathVariable @Positive Long id, @PathVariable @Positive Long batchId) {
        return ResponseEntity.ok(batchService.undo(id, batchId));
    }

    /** V32 SF-FR-07: the latest batches on one stage (the current one when left out), newest first. */
    @Operation(summary = "List a stage's recent batches")
    @GetMapping("/{id}/batches")
    public ResponseEntity<List<StreamlineResponses.BatchView>> batches(@PathVariable @Positive Long id,
                                                       @RequestParam(required = false) @Min(1) @Max(5) Integer stage) {
        return ResponseEntity.ok(batchService.recent(id, stage));
    }

    /** SR-FR-14 (D-27-9): AI suggestions for one stage. Suggestions only; nothing changes until the user applies one. */
    @Operation(summary = "Get AI suggestions for a stage")
    @PostMapping("/{id}/stages/{stage}/suggestions")
    public ResponseEntity<StreamlineAiService.Suggestions> suggestions(@PathVariable @Positive Long id, @PathVariable @Min(1) @Max(5) int stage,
                                                       @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(aiService.suggest(id, stage, language));
    }

    /**
     * V32 SF-FR-01 (D-32-1): the saved suggestions for a stage, when they still
     * fit the review; 204 when there are none. Uses no AI.
     */
    @Operation(summary = "Get a stage's saved suggestions (204 when there are none)")
    @GetMapping("/{id}/stages/{stage}/suggestions")
    public ResponseEntity<StreamlineAiService.Suggestions> savedSuggestions(
            @PathVariable @Positive Long id, @PathVariable @Min(1) @Max(5) int stage) {
        return aiService.saved(id, stage).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** V32 D-32-1: removes a dismissed card from the saved set. */
    @Operation(summary = "Dismiss a suggestion card")
    @PostMapping(value = "/{id}/stages/{stage}/suggestions/dismiss", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> dismissSuggestion(@PathVariable @Positive Long id, @PathVariable @Min(1) @Max(5) int stage,
                                  @Valid @RequestBody StreamlineRequests.DismissSuggestion request) {
        aiService.dismiss(id, stage, request.itemId(), request.action(), request.targetItemId());
        return ResponseEntity.noContent().build();
    }

    /** V30 SA-FR-01 (D-30-4): AI-ranked goals and steps to streamline. Suggestions only. */
    @Operation(summary = "Ask the AI which goals and steps to streamline")
    @PostMapping("/ai-candidates")
    public ResponseEntity<StreamlineAiService.Picks> aiCandidates(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(aiService.pick(language));
    }

    /** V30 SA-FR-07 (D-30-9): advice on a completed review. Advice only. */
    @Operation(summary = "Get AI advice on a completed review")
    @PostMapping("/{id}/summary-advice")
    public ResponseEntity<StreamlineAiService.Advice> summaryAdvice(@PathVariable @Positive Long id,
                                                    @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(aiService.advise(id, language));
    }
}
