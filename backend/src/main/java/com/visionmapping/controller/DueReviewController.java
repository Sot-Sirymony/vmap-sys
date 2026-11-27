package com.visionmapping.controller;

import com.visionmapping.dto.response.AiPlanResponses.DueReview;
import com.visionmapping.service.ReviewRhythmService;
import java.util.List;
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

/** V31 DC-FR-50: the caller's reviews due today. Split from ReviewRhythmController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Due reviews", description = "Reviews a dream's rhythm makes due")
public class DueReviewController {

    private final ReviewRhythmService service;

    @Operation(summary = "List my reviews due today")
    @GetMapping("/due")
    public ResponseEntity<List<DueReview>> due() {
        return ResponseEntity.ok(service.due());
    }
}
