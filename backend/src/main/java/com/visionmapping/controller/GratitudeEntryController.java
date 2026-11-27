package com.visionmapping.controller;

import com.visionmapping.dto.request.GratitudeEntryRequest;
import com.visionmapping.dto.response.GratitudeEntryResponse;
import com.visionmapping.service.GratitudeEntryService;
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
@RequestMapping(value = ApiPaths.V1 + "/gratitude-entries", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Gratitude entries", description = "The gratitude journal")
public class GratitudeEntryController {

    private final GratitudeEntryService service;

    @Operation(summary = "List gratitude entries, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<GratitudeEntryResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(service.pageGratitudeEntries(ArchiveFilter.of(includeArchived), pageable));
    }

    @Operation(summary = "Create a gratitude entry")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GratitudeEntryResponse> create(@Valid @RequestBody GratitudeEntryRequest request) {
        GratitudeEntryResponse created = service.createGratitudeEntry(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a gratitude entry")
    @GetMapping("/{id}")
    public ResponseEntity<GratitudeEntryResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getGratitudeEntry(id));
    }

    @Operation(summary = "Update a gratitude entry")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GratitudeEntryResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody GratitudeEntryRequest request) {
        return ResponseEntity.ok(service.updateGratitudeEntry(id, request));
    }

    @Operation(summary = "Archive a gratitude entry")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveGratitudeEntry(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived gratitude entry")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreGratitudeEntry(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a gratitude entry permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteGratitudeEntry(id);
        return ResponseEntity.noContent().build();
    }
}
