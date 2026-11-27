package com.visionmapping.controller;

import com.visionmapping.dto.request.PlanShareRequest;
import com.visionmapping.dto.response.PlanShareResponse;
import com.visionmapping.service.PlanShareService;
import com.visionmapping.web.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V9 (v0.1 FR-10): the owner manages share links. A partner opens one through
 * {@link SharedPlanController} without signing in.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/plan-shares", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Plan shares", description = "Read-only links to a dream plan, for partners")
public class PlanShareController {

    private final PlanShareService service;

    @Operation(summary = "List my plan share links")
    @GetMapping
    public ResponseEntity<List<PlanShareResponse>> list(@RequestParam(required = false) @Positive Long dreamId) {
        return ResponseEntity.ok(service.list(dreamId));
    }

    @Operation(summary = "Make a share link for a dream plan")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlanShareResponse> create(@Valid @RequestBody PlanShareRequest request) {
        PlanShareResponse created = service.create(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Revoke a share link")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<PlanShareResponse> revoke(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.revoke(id));
    }
}
