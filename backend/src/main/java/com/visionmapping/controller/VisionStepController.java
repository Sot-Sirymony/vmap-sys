package com.visionmapping.controller;

import com.visionmapping.dto.request.GapStepRequest;
import com.visionmapping.dto.request.ReorderRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.GapStepResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.GapStepService;
import com.visionmapping.service.VisionStepService;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.ProgressOverride;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.visionmapping.dto.request.ArchiveRequest;
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
@RequestMapping(value = ApiPaths.V1 + "/steps", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Steps", description = "Steps under a goal")
public class VisionStepController {

    private final VisionStepService service;
    private final GapStepService gapStepService;

    @Operation(summary = "List steps")
    @GetMapping
    public ResponseEntity<List<VisionStepResponse>> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listSteps(ArchiveFilter.of(includeArchived)));
    }

    @Operation(summary = "Create a step")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisionStepResponse> create(@Valid @RequestBody VisionStepRequest request) {
        VisionStepResponse created = service.createStep(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a step")
    @GetMapping("/{id}")
    public ResponseEntity<VisionStepResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getStep(id));
    }

    @Operation(summary = "Update a step")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisionStepResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody VisionStepRequest request) {
        return ResponseEntity.ok(service.updateStep(id, request));
    }

    @Operation(summary = "Change a step's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisionStepResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody StatusUpdateRequest<WorkStatus> request) {
        return ResponseEntity.ok(service.updateStepStatus(id, request.status(), ProgressOverride.of(request.manualOverride())));
    }

    @Operation(summary = "Reorder steps")
    @PatchMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        service.reorderSteps(request.parentId(), request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive a step")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id, @RequestParam(defaultValue = "false") boolean removePartner) {
        // V17 BL-FR-21: removePartner also archives the partner a "find help" step created.
        service.archiveStep(id, removePartner);
        return ResponseEntity.noContent().build();
    }

    /** V29 AR-FR-04: archive with an optional reason (BR-29-1); the DELETE above keeps working without one. */
    @Operation(summary = "Archive a step, with an optional reason")
    @PostMapping(value = "/{id}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> archive(@PathVariable @Positive Long id, @Valid @RequestBody(required = false) ArchiveRequest request) {
        service.archiveStep(id, ArchiveRequest.removesPartner(request), ArchiveRequest.reasonOf(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived step")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreStep(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a step permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteStep(id);
        return ResponseEntity.noContent().build();
    }

    /** V17 BL-FR-19: turns this step's gap into a "find help" step placed just before it. */
    @Operation(summary = "Make a step that closes a gap (find help, learn, or decide)")
    @PostMapping(value = "/{id}/gap-step", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GapStepResponse> gapStep(@PathVariable @Positive Long id, @Valid @RequestBody GapStepRequest request) {
        GapStepResponse created = gapStepService.createFromStep(id, request);
        return ApiResponses.createdAt("/steps", created.step().id(), created);
    }

    @Operation(summary = "Count what archiving a step would also archive")
    @GetMapping("/{id}/archive-impact")
    public ResponseEntity<ArchiveImpactResponse> archiveImpact(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.stepArchiveImpact(id));
    }

}
