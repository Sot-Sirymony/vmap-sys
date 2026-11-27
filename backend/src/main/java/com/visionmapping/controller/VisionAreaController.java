package com.visionmapping.controller;

import com.visionmapping.dto.request.SimpleReorderRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.service.VisionAreaService;
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

@RestController
@RequestMapping(value = ApiPaths.V1 + "/vision-areas", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Vision areas", description = "Major life or work areas at the top of the plan")
public class VisionAreaController {

    private final VisionAreaService service;

    @Operation(summary = "List vision areas")
    @GetMapping
    public ResponseEntity<List<VisionAreaResponse>> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listVisionAreas(ArchiveFilter.of(includeArchived)));
    }

    @Operation(summary = "Create a vision area")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisionAreaResponse> create(@Valid @RequestBody VisionAreaRequest request) {
        VisionAreaResponse created = service.createVisionArea(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a vision area")
    @GetMapping("/{id}")
    public ResponseEntity<VisionAreaResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getVisionArea(id));
    }

    @Operation(summary = "Update a vision area")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisionAreaResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody VisionAreaRequest request) {
        return ResponseEntity.ok(service.updateVisionArea(id, request));
    }

    @Operation(summary = "Change a vision area's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisionAreaResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody StatusUpdateRequest<LifecycleStatus> request) {
        return ResponseEntity.ok(service.updateVisionAreaStatus(id, request.status()));
    }

    // No parent scope — a vision area is the top of the hierarchy.
    @Operation(summary = "Reorder vision areas")
    @PatchMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorder(@Valid @RequestBody SimpleReorderRequest request) {
        service.reorderVisionAreas(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive a vision area")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveVisionArea(id);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Restore an archived vision area")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreVisionArea(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a vision area permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteVisionArea(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Count what archiving a vision area would also archive")
    @GetMapping("/{id}/archive-impact")
    public ResponseEntity<ArchiveImpactResponse> archiveImpact(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.visionAreaArchiveImpact(id));
    }

}
