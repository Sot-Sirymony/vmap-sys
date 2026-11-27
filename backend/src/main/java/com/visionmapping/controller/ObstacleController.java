package com.visionmapping.controller;

import com.visionmapping.dto.request.GapStepRequest;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.GapStepResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.service.GapStepService;
import com.visionmapping.service.ObstacleService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping(value = ApiPaths.V1 + "/obstacles", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Obstacles", description = "Obstacles on steps and tasks")
public class ObstacleController {

    private final ObstacleService service;
    private final GapStepService gapStepService;

    /** V17 BL-FR-05: {@code ?placement=UNPLACED} lists the active obstacles that still need a step. */
    @Operation(summary = "List obstacles, one page at a time; placement=UNPLACED lists those that still need a step")
    @GetMapping
    public ResponseEntity<PagedResponse<ObstacleResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String placement,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(service.pageObstacles(ArchiveFilter.of(includeArchived), placement, pageable));
    }

    @Operation(summary = "Create an obstacle")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObstacleResponse> create(@Valid @RequestBody ObstacleRequest request) {
        ObstacleResponse created = service.createObstacle(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get an obstacle")
    @GetMapping("/{id}")
    public ResponseEntity<ObstacleResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getObstacle(id));
    }

    @Operation(summary = "List obstacles related to an obstacle")
    @GetMapping("/{id}/related")
    public ResponseEntity<List<ObstacleResponse>> related(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.relatedObstacles(id));
    }

    @Operation(summary = "Update an obstacle")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObstacleResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody ObstacleRequest request) {
        return ResponseEntity.ok(service.updateObstacle(id, request));
    }

    @Operation(summary = "Change an obstacle's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObstacleResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody StatusUpdateRequest<ObstacleStatus> request) {
        return ResponseEntity.ok(service.updateObstacleStatus(id, request.status()));
    }

    /** V17 BL-FR-20: turns this obstacle into a "find help" step and anchors it there. */
    @Operation(summary = "Turn an obstacle into a step")
    @PostMapping(value = "/{id}/convert-to-step", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GapStepResponse> convertToStep(@PathVariable @Positive Long id, @Valid @RequestBody GapStepRequest request) {
        GapStepResponse created = gapStepService.createFromObstacle(id, request);
        return ApiResponses.createdAt("/steps", created.step().id(), created);
    }

    @Operation(summary = "Release the expectation behind an obstacle")
    @PostMapping("/{id}/release-expectation")
    public ResponseEntity<ObstacleResponse> releaseExpectation(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.releaseExpectation(id));
    }

    @Operation(summary = "Archive an obstacle")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveObstacle(id);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Restore an archived obstacle")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreObstacle(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete an obstacle permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteObstacle(id);
        return ResponseEntity.noContent().build();
    }

}
