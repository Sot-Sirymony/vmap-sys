package com.visionmapping.controller;

import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.request.ReorderRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.GoalService;
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
import com.visionmapping.dto.response.LinkedPartnerResponse;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/goals", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Goals", description = "Goals under a dream")
public class GoalController {

    private final GoalService service;

    @Operation(summary = "List goals")
    @GetMapping
    public ResponseEntity<List<GoalResponse>> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listGoals(ArchiveFilter.of(includeArchived)));
    }

    @Operation(summary = "Create a goal")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        GoalResponse created = service.createGoal(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a goal")
    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getGoal(id));
    }

    @Operation(summary = "Update a goal")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(service.updateGoal(id, request));
    }

    @Operation(summary = "Change a goal's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody StatusUpdateRequest<WorkStatus> request) {
        return ResponseEntity.ok(service.updateGoalStatus(id, request.status(), ProgressOverride.of(request.manualOverride())));
    }

    @Operation(summary = "Reorder goals")
    @PatchMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        service.reorderGoals(request.parentId(), request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive a goal")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveGoal(id);
        return ResponseEntity.noContent().build();
    }

    /** V29 AR-FR-04: archive with an optional reason (BR-29-1); the DELETE above keeps working without one. */
    @Operation(summary = "Archive a goal, with an optional reason")
    @PostMapping(value = "/{id}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> archive(@PathVariable @Positive Long id, @Valid @RequestBody(required = false) ArchiveRequest request) {
        service.archiveGoal(id, ArchiveRequest.reasonOf(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived goal")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreGoal(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a goal permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteGoal(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Count what archiving a goal would also archive")
    @GetMapping("/{id}/archive-impact")
    public ResponseEntity<ArchiveImpactResponse> archiveImpact(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.goalArchiveImpact(id));
    }

    @Operation(summary = "Whether a goal has a linked partner")
    @GetMapping("/{id}/has-linked-partner")
    public ResponseEntity<LinkedPartnerResponse> hasLinkedPartner(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(new LinkedPartnerResponse(service.hasLinkedPartner(id)));
    }

}
