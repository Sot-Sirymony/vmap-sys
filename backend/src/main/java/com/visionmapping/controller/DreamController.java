package com.visionmapping.controller;

import com.visionmapping.dto.request.DreamCommitmentRequest;
import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.request.ReorderRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.service.DreamService;
import com.visionmapping.service.PlanPdfService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.validation.annotation.Validated;
import com.visionmapping.dto.response.LinkedPartnerResponse;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/dreams", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Dreams", description = "Dreams under a vision area")
public class DreamController {

    private final DreamService service;
    private final PlanPdfService planPdfService;

    @Operation(summary = "List dreams")
    @GetMapping
    public ResponseEntity<List<DreamResponse>> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listDreams(ArchiveFilter.of(includeArchived)));
    }

    @Operation(summary = "Create a dream")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DreamResponse> create(@Valid @RequestBody DreamRequest request) {
        DreamResponse created = service.createDream(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a dream")
    @GetMapping("/{id}")
    public ResponseEntity<DreamResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getDream(id));
    }

    @Operation(summary = "Update a dream")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DreamResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody DreamRequest request) {
        return ResponseEntity.ok(service.updateDream(id, request));
    }

    @Operation(summary = "Change a dream's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DreamResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody StatusUpdateRequest<DreamStatus> request) {
        return ResponseEntity.ok(service.updateDreamStatus(id, request.status()));
    }

    @Operation(summary = "Record the commitment to a dream")
    @PostMapping(value = "/{id}/commitment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DreamResponse> commit(@PathVariable @Positive Long id, @Valid @RequestBody DreamCommitmentRequest request) {
        return ResponseEntity.ok(service.commitToDream(id, request));
    }

    @Operation(summary = "Download a dream plan as a PDF")
    @GetMapping(value = "/{id}/plan.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> planPdf(@PathVariable @Positive Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dream-plan-" + id + ".pdf\"")
                .body(planPdfService.exportPlan(id));
    }

    @Operation(summary = "Reorder dreams")
    @PatchMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        service.reorderDreams(request.parentId(), request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive a dream")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveDream(id);
        return ResponseEntity.noContent().build();
    }

    /** V29 AR-FR-04: archive with an optional reason (BR-29-1); the DELETE above keeps working without one. */
    @Operation(summary = "Archive a dream, with an optional reason")
    @PostMapping(value = "/{id}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> archive(@PathVariable @Positive Long id, @Valid @RequestBody(required = false) ArchiveRequest request) {
        service.archiveDream(id, ArchiveRequest.reasonOf(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived dream")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreDream(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a dream permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteDream(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Count what archiving a dream would also archive")
    @GetMapping("/{id}/archive-impact")
    public ResponseEntity<ArchiveImpactResponse> archiveImpact(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.dreamArchiveImpact(id));
    }

    @Operation(summary = "Whether a dream has a linked partner")
    @GetMapping("/{id}/has-linked-partner")
    public ResponseEntity<LinkedPartnerResponse> hasLinkedPartner(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(new LinkedPartnerResponse(service.hasLinkedPartner(id)));
    }

}
