package com.visionmapping.controller;

import com.visionmapping.dto.request.GapCoverRequest;
import com.visionmapping.dto.request.SelfAssessmentItemRequest;
import com.visionmapping.dto.response.GapCoverRemovalResponse;
import com.visionmapping.dto.response.SelfAssessmentItemResponse;
import com.visionmapping.service.SelfAssessmentService;
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

/** V8.1 FR-79 to FR-83: strengths and weaknesses, and how each important gap is covered. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/self-assessment-items", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Self-assessment items", description = "Strengths and gaps for a dream")
public class SelfAssessmentItemController {

    private final SelfAssessmentService service;

    @Operation(summary = "List self-assessment items")
    @GetMapping
    public ResponseEntity<List<SelfAssessmentItemResponse>> list(@RequestParam(required = false) Long dreamId,
                                                 @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listItems(dreamId, ArchiveFilter.of(includeArchived)));
    }

    @Operation(summary = "Create a self-assessment item")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SelfAssessmentItemResponse> create(@Valid @RequestBody SelfAssessmentItemRequest request) {
        SelfAssessmentItemResponse created = service.createItem(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Update a self-assessment item")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SelfAssessmentItemResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody SelfAssessmentItemRequest request) {
        return ResponseEntity.ok(service.updateItem(id, request));
    }

    @Operation(summary = "Record how a gap is covered")
    @PutMapping(value = "/{id}/cover", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SelfAssessmentItemResponse> setCover(@PathVariable @Positive Long id, @Valid @RequestBody GapCoverRequest request) {
        return ResponseEntity.ok(service.setCover(id, request));
    }

    /** removeLinked=true also archives the partner or step, or removes the exercise row, the cover created. */
    @Operation(summary = "Remove a gap's cover")
    @DeleteMapping("/{id}/cover")
    public ResponseEntity<GapCoverRemovalResponse> removeCover(@PathVariable @Positive Long id, @RequestParam(defaultValue = "false") boolean removeLinked) {
        return ResponseEntity.ok(service.removeCover(id, removeLinked));
    }

    /** Archives the item. removeLinked works as for removing the cover (AC-10). */
    @Operation(summary = "Archive a self-assessment item")
    @DeleteMapping("/{id}")
    public ResponseEntity<GapCoverRemovalResponse> delete(@PathVariable @Positive Long id, @RequestParam(defaultValue = "false") boolean removeLinked) {
        return ResponseEntity.ok(service.archiveItem(id, removeLinked));
    }

    @Operation(summary = "Restore an archived self-assessment item")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreItem(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a self-assessment item permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
