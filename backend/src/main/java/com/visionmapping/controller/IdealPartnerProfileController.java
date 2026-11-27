package com.visionmapping.controller;

import com.visionmapping.dto.request.IdealPartnerProfileRequest;
import com.visionmapping.dto.response.IdealPartnerProfileResponse;
import com.visionmapping.service.IdealPartnerProfileService;
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

@RestController
@RequestMapping(value = ApiPaths.V1 + "/ideal-partner-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Ideal partner profiles", description = "What the right partner for a step looks like")
public class IdealPartnerProfileController {

    private final IdealPartnerProfileService service;

    @Operation(summary = "List ideal partner profiles")
    @GetMapping
    public ResponseEntity<List<IdealPartnerProfileResponse>> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listProfiles(ArchiveFilter.of(includeArchived)));
    }

    @Operation(summary = "Create an ideal partner profile")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdealPartnerProfileResponse> create(@Valid @RequestBody IdealPartnerProfileRequest request) {
        IdealPartnerProfileResponse created = service.createProfile(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get an ideal partner profile")
    @GetMapping("/{id}")
    public ResponseEntity<IdealPartnerProfileResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getProfile(id));
    }

    @Operation(summary = "Update an ideal partner profile")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdealPartnerProfileResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody IdealPartnerProfileRequest request) {
        return ResponseEntity.ok(service.updateProfile(id, request));
    }

    @Operation(summary = "Archive an ideal partner profile")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveProfile(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived ideal partner profile")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreProfile(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete an ideal partner profile permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteProfile(id);
        return ResponseEntity.noContent().build();
    }
}
