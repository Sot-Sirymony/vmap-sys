package com.visionmapping.controller;

import com.visionmapping.dto.request.ReviewRhythmRequest;
import com.visionmapping.dto.response.AiPlanResponses.ReviewRhythm;
import com.visionmapping.service.ReviewRhythmService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** V31 v1 step 3 (DC-FR-48, DC-FR-49): a dream's review rhythm. The reviews it makes due are in {@link DueReviewController}. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/dreams", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Review rhythm", description = "How often a dream is reviewed")
public class ReviewRhythmController {

    private final ReviewRhythmService service;

    /** The dream's rhythm; 204 when it has none. */
    @Operation(summary = "Get a dream's review rhythm (204 when it has none)")
    @GetMapping("/{dreamId}/review-rhythm")
    public ResponseEntity<ReviewRhythm> get(@PathVariable @Positive Long dreamId) {
        return service.get(dreamId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Save a dream's review rhythm")
    @PutMapping(value = "/{dreamId}/review-rhythm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewRhythm> save(@PathVariable @Positive Long dreamId, @Valid @RequestBody ReviewRhythmRequest request) {
        return ResponseEntity.ok(service.save(dreamId, request));
    }
}
