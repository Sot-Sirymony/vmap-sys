package com.visionmapping.controller;

import com.visionmapping.dto.request.CohortMemberRequest;
import com.visionmapping.dto.request.CohortRequest;
import com.visionmapping.dto.response.CohortMemberResponse;
import com.visionmapping.dto.response.CohortResponse;
import com.visionmapping.service.CohortAdminService;
import jakarta.validation.Valid;
import java.util.List;
import com.visionmapping.web.RequiresStepUp;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** V9: cohort management. The service requires the ADMIN role. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/cohorts", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Authorities.MANAGE_COHORTS + "')")
@Tag(name = "Admin: cohorts", description = "Cohorts, their coaches, join codes and members")
public class CohortAdminController {

    private final CohortAdminService service;

    @Operation(summary = "List cohorts")
    @GetMapping
    public ResponseEntity<List<CohortResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @Operation(summary = "Create a cohort")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresStepUp
    public ResponseEntity<CohortResponse> create(@Valid @RequestBody CohortRequest request) {
        CohortResponse created = service.create(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Update a cohort")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresStepUp
    public ResponseEntity<CohortResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody CohortRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @Operation(summary = "Make a new join code")
    @PostMapping("/{id}/join-code")
    public ResponseEntity<CohortResponse> newJoinCode(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.newJoinCode(id));
    }

    @Operation(summary = "Archive a cohort")
    @PostMapping("/{id}/archive")
    public ResponseEntity<CohortResponse> archive(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.setArchived(id, true));
    }

    @Operation(summary = "Restore an archived cohort")
    @PostMapping("/{id}/restore")
    public ResponseEntity<CohortResponse> restore(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.setArchived(id, false));
    }

    @Operation(summary = "List a cohort's members")
    @GetMapping("/{id}/members")
    public ResponseEntity<List<CohortMemberResponse>> members(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.members(id));
    }

    @Operation(summary = "Add a member to a cohort")
    @PostMapping(value = "/{id}/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CohortMemberResponse> addMember(@PathVariable @Positive Long id, @Valid @RequestBody CohortMemberRequest request) {
        return ResponseEntity.ok(service.addMember(id, request));
    }

    @Operation(summary = "Remove a member from a cohort")
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable @Positive Long id, @PathVariable @Positive Long userId) {
        service.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
