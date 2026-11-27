package com.visionmapping.controller;

import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.service.PartnerService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping(value = ApiPaths.V1 + "/partners", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Partners", description = "People and resources that help")
public class PartnerController {

    private final PartnerService service;

    @Operation(summary = "List partners, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<PartnerResponse>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PartnerSupportType supportType,
            @RequestParam(required = false) PartnerStatus status,
            @RequestParam(required = false) Long dreamId) {
        return ResponseEntity.ok(service.pagePartners(pageable, ArchiveFilter.of(includeArchived), search, supportType, status, dreamId));
    }

    @Operation(summary = "Create a partner")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PartnerResponse> create(@Valid @RequestBody PartnerRequest request) {
        PartnerResponse created = service.createPartner(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a partner")
    @GetMapping("/{id}")
    public ResponseEntity<PartnerResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getPartner(id));
    }

    @Operation(summary = "Update a partner")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PartnerResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody PartnerRequest request) {
        return ResponseEntity.ok(service.updatePartner(id, request));
    }

    @Operation(summary = "Change a partner's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PartnerResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody StatusUpdateRequest<PartnerStatus> request) {
        return ResponseEntity.ok(service.updatePartnerStatus(id, request.status()));
    }

    @Operation(summary = "Archive a partner")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archivePartner(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived partner")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restorePartner(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a partner permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeletePartner(id);
        return ResponseEntity.noContent().build();
    }
}
