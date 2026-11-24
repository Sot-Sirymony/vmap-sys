package com.visionmapping.controller;

import com.visionmapping.dto.request.ReviewRhythmRequest;
import com.visionmapping.dto.response.AiPlanResponses.DueReview;
import com.visionmapping.dto.response.AiPlanResponses.ReviewRhythm;
import com.visionmapping.service.ReviewRhythmService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** V31 v1 step 3 (DC-FR-48 to DC-FR-50): a dream's review rhythm, and the reviews it makes due. */
@RestController
@RequiredArgsConstructor
public class ReviewRhythmController {

    private final ReviewRhythmService service;

    /** The dream's rhythm; 204 when it has none. */
    @GetMapping("/api/dreams/{dreamId}/review-rhythm")
    public ResponseEntity<ReviewRhythm> get(@PathVariable Long dreamId) {
        return service.get(dreamId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/api/dreams/{dreamId}/review-rhythm")
    public ReviewRhythm save(@PathVariable Long dreamId, @Valid @RequestBody ReviewRhythmRequest request) {
        return service.save(dreamId, request);
    }

    /** DC-FR-50: the caller's reviews due today. */
    @GetMapping("/api/reviews/due")
    public List<DueReview> due() {
        return service.due();
    }
}
