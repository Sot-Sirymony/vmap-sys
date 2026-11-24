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

    /** SR-FR-14 (D-27-9): AI suggestions for one stage. Suggestions only; nothing changes until the user applies one. */
    @PostMapping("/{id}/stages/{stage}/suggestions")
    public StreamlineAiService.Suggestions suggestions(@PathVariable Long id, @PathVariable @Min(1) @Max(5) int stage,
                                                       @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return aiService.suggest(id, stage, language);
    }
}
