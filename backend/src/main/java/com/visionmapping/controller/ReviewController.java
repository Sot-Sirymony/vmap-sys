package com.visionmapping.controller;

import com.visionmapping.dto.request.ReviewRequest;
import com.visionmapping.dto.response.ReviewResponse;
import com.visionmapping.service.ReviewService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import com.visionmapping.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Daily, weekly, monthly and quarterly reviews")
public class ReviewController {

    private final ReviewService service;

    @Operation(summary = "List reviews, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<ReviewResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(service.pageReviews(ArchiveFilter.of(includeArchived), pageable));
    }

    @Operation(summary = "Create a review")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody ReviewRequest request) {
        ReviewResponse created = service.createReview(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a review")
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getReview(id));
    }

    @Operation(summary = "Update a review")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(service.updateReview(id, request));
    }

    @Operation(summary = "Archive a review")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveReview(id);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Restore an archived review")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreReview(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a review permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteReview(id);
        return ResponseEntity.noContent().build();
    }

}
