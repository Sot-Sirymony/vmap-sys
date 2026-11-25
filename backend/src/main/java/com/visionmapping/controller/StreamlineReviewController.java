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

/** V27 (BRD 27): the Streamline review. Every call is scoped to the signed-in user. */
@RestController
@RequestMapping("/api/streamline-reviews")
@RequiredArgsConstructor
@Validated
public class StreamlineReviewController {

    private final StreamlineReviewService service;
    private final StreamlineAiService aiService;
    private final com.visionmapping.service.StreamlineBatchService batchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StreamlineResponses.Review start(@RequestBody StreamlineRequests.Start request) {
        return service.start(request);
    }

    @GetMapping
    public List<StreamlineResponses.ListItem> list() {
        return service.list();
    }

    /** §6.2: goals and steps worth streamlining. */
    @GetMapping("/candidates")
    public List<StreamlineResponses.Candidate> candidates() {
        return service.candidates();
    }

    /** V32 SF-FR-15 (D-32-12): saves dismissed suggestions, so they stay dismissed on every device. */
    @PostMapping("/candidate-dismissals")
    public List<StreamlineResponses.DismissalView> dismissCandidates(@Valid @RequestBody StreamlineRequests.Dismissals request) {
        return service.dismiss(request);
    }

    /** V32 D-32-12: the dismissals still in force (90 days). */
    @GetMapping("/candidate-dismissals")
    public List<StreamlineResponses.DismissalView> candidateDismissals() {
        return service.dismissals();
    }

    @GetMapping("/{id}")
    public StreamlineResponses.Review get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/summary")
    public StreamlineResponses.Summary summary(@PathVariable Long id) {
        return service.get(id).summary();
    }

    @PutMapping("/{id}/items/{itemId}")
    public StreamlineResponses.Review updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                                 @Valid @RequestBody StreamlineRequests.UpdateItem request) {
        return service.updateItem(id, itemId, request);
    }

    @PutMapping("/{id}/items/bulk")
    public StreamlineResponses.Review bulk(@PathVariable Long id, @Valid @RequestBody StreamlineRequests.BulkQuestion request) {
        return service.bulkQuestion(id, request);
    }

    @PostMapping("/{id}/items/{itemId}/delete")
    public StreamlineResponses.Review delete(@PathVariable Long id, @PathVariable Long itemId,
                                             @RequestBody(required = false) StreamlineRequests.Delete request) {
        return service.deleteItem(id, itemId, request);
    }

    @PostMapping("/{id}/items/{itemId}/restore")
    public StreamlineResponses.Review restore(@PathVariable Long id, @PathVariable Long itemId) {
        return service.restoreItem(id, itemId);
    }

    @PostMapping("/{id}/items/{itemId}/merge")
    public StreamlineResponses.Review merge(@PathVariable Long id, @PathVariable Long itemId,
                                            @Valid @RequestBody StreamlineRequests.Merge request) {
        return service.mergeItem(id, itemId, request);
    }

    @PutMapping("/{id}/items/{itemId}/simplify")
    public StreamlineResponses.Review simplify(@PathVariable Long id, @PathVariable Long itemId,
                                               @Valid @RequestBody StreamlineRequests.Simplify request) {
        return service.simplify(id, itemId, request);
    }

    @PostMapping("/{id}/items/{itemId}/repeat")
    public StreamlineResponses.Review repeat(@PathVariable Long id, @PathVariable Long itemId,
                                             @Valid @RequestBody StreamlineRequests.Repeat request) {
        return service.repeat(id, itemId, request);
    }

    @PutMapping("/{id}/target")
    public StreamlineResponses.Review target(@PathVariable Long id, @Valid @RequestBody StreamlineRequests.Target request) {
        return service.setTarget(id, request);
    }

    @PostMapping("/{id}/stages/{stage}/complete")
    public StreamlineResponses.Review complete(@PathVariable Long id, @PathVariable @Min(1) @Max(5) int stage) {
        return service.completeStage(id, stage);
    }

    @PostMapping("/{id}/stages/{stage}/not-needed")
    public StreamlineResponses.Review notNeeded(@PathVariable Long id, @PathVariable @Min(1) @Max(5) int stage,
                                                @Valid @RequestBody StreamlineRequests.NotNeeded request) {
        return service.notNeeded(id, stage, request);
    }

    @PostMapping("/{id}/abandon")
    public StreamlineResponses.Review abandon(@PathVariable Long id) {
        return service.abandon(id);
    }

    /** V32 SF-FR-08 (D-32-7): drafts the current stage and every later one in one AI request. Suggestions only. */
    @PostMapping("/{id}/draft-all")
    public StreamlineAiService.DraftAll draftAll(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return aiService.draftAll(id, language);
    }

    /** V32 (D-32-7, D-32-8): the saved drafts for the current and later stages, and whether Draft all stages can run. */
    @GetMapping("/{id}/drafts")
    public StreamlineAiService.Drafts drafts(@PathVariable Long id) {
        return aiService.drafts(id);
    }

    /** V32 SF-FR-05 (D-32-4): Apply selected or Apply all, one action after another; stops at the first failure. */
    @PostMapping("/{id}/apply-batch")
    public StreamlineResponses.BatchResult applyBatch(@PathVariable Long id, @Valid @RequestBody StreamlineRequests.ApplyBatch request) {
        return batchService.apply(id, request);
    }

    /** V32 SF-FR-06 (D-32-5): undoes a whole batch while its stage is current, for up to 24 hours. */
    @PostMapping("/{id}/batches/{batchId}/undo")
    public StreamlineResponses.UndoResult undoBatch(@PathVariable Long id, @PathVariable Long batchId) {
        return batchService.undo(id, batchId);
    }

    /** V32 SF-FR-07: the latest batches on one stage (the current one when left out), newest first. */
    @GetMapping("/{id}/batches")
    public List<StreamlineResponses.BatchView> batches(@PathVariable Long id,
                                                       @RequestParam(required = false) @Min(1) @Max(5) Integer stage) {
        return batchService.recent(id, stage);
    }

    /** SR-FR-14 (D-27-9): AI suggestions for one stage. Suggestions only; nothing changes until the user applies one. */
    @PostMapping("/{id}/stages/{stage}/suggestions")
    public StreamlineAiService.Suggestions suggestions(@PathVariable Long id, @PathVariable @Min(1) @Max(5) int stage,
                                                       @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return aiService.suggest(id, stage, language);
    }

    /**
     * V32 SF-FR-01 (D-32-1): the saved suggestions for a stage, when they still
     * fit the review; 204 when there are none. Uses no AI.
     */
    @GetMapping("/{id}/stages/{stage}/suggestions")
    public org.springframework.http.ResponseEntity<StreamlineAiService.Suggestions> savedSuggestions(
            @PathVariable Long id, @PathVariable @Min(1) @Max(5) int stage) {
        return aiService.saved(id, stage).map(org.springframework.http.ResponseEntity::ok)
                .orElseGet(() -> org.springframework.http.ResponseEntity.noContent().build());
    }

    /** V32 D-32-1: removes a dismissed card from the saved set. */
    @PostMapping("/{id}/stages/{stage}/suggestions/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissSuggestion(@PathVariable Long id, @PathVariable @Min(1) @Max(5) int stage,
                                  @Valid @RequestBody StreamlineRequests.DismissSuggestion request) {
        aiService.dismiss(id, stage, request.itemId(), request.action(), request.targetItemId());
    }

    /** V30 SA-FR-01 (D-30-4): AI-ranked goals and steps to streamline. Suggestions only. */
    @PostMapping("/ai-candidates")
    public StreamlineAiService.Picks aiCandidates(@RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return aiService.pick(language);
    }

    /** V30 SA-FR-07 (D-30-9): advice on a completed review. Advice only. */
    @PostMapping("/{id}/summary-advice")
    public StreamlineAiService.Advice summaryAdvice(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return aiService.advise(id, language);
    }
}
