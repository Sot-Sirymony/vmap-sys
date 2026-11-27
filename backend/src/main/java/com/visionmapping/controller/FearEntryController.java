package com.visionmapping.controller;

import com.visionmapping.dto.request.FearEntryRequest;
import com.visionmapping.dto.response.FearEntryResponse;
import com.visionmapping.service.FearEntryService;
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
@RequestMapping(value = ApiPaths.V1 + "/fear-entries", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Fear entries", description = "The fear journal")
public class FearEntryController {

    private final FearEntryService service;

    @Operation(summary = "List fear entries, newest first, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<FearEntryResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.pageFearEntries(ArchiveFilter.of(includeArchived), pageable));
    }

    @Operation(summary = "Create a fear entry")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FearEntryResponse> create(@Valid @RequestBody FearEntryRequest request) {
        FearEntryResponse created = service.createFearEntry(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Update a fear entry")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FearEntryResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody FearEntryRequest request) {
        return ResponseEntity.ok(service.updateFearEntry(id, request));
    }

    @Operation(summary = "Archive a fear entry")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveFearEntry(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived fear entry")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreFearEntry(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a fear entry permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteFearEntry(id);
        return ResponseEntity.noContent().build();
    }
}
