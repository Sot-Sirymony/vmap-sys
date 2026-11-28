package com.visionmapping.controller;

import com.visionmapping.dto.request.InnerBlockerCheckinsRequest;
import com.visionmapping.dto.response.InnerBlockerCheckinsResponse;
import com.visionmapping.service.InnerBlockerCheckinService;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** V34 (D-34-10): the weekly "Did the practice help?" answers saved with a review. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/reviews/{reviewId}/inner-blocker-checkins", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Inner blocker check-ins", description = "Weekly answers on whether each inner blocker's practice helped")
public class InnerBlockerCheckinController {

    private final InnerBlockerCheckinService service;

    @Operation(summary = "Read the answers saved with a review")
    @GetMapping
    public ResponseEntity<InnerBlockerCheckinsResponse> list(@PathVariable @Positive Long reviewId) {
        return ResponseEntity.ok(service.list(reviewId));
    }

    @Operation(summary = "Save the answers for a weekly review, replacing earlier ones")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InnerBlockerCheckinsResponse> save(@PathVariable @Positive Long reviewId,
                                                             @Valid @RequestBody InnerBlockerCheckinsRequest request) {
        return ResponseEntity.ok(service.save(reviewId, request));
    }
}
