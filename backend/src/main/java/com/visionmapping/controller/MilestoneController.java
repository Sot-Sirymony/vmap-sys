package com.visionmapping.controller;

import com.visionmapping.dto.request.MilestoneRequest;
import com.visionmapping.dto.response.MilestoneResponse;
import com.visionmapping.service.MilestoneService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping(value = ApiPaths.V1 + "/milestones", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Milestones", description = "Milestones reached on the way to a dream")
public class MilestoneController {

    private final MilestoneService service;

    @Operation(summary = "List a dream's milestones")
    @GetMapping
    public ResponseEntity<List<MilestoneResponse>> list(@RequestParam Long dreamId) {
        return ResponseEntity.ok(service.listForDream(dreamId));
    }

    @Operation(summary = "Create a milestone")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MilestoneResponse> create(@Valid @RequestBody MilestoneRequest request) {
        MilestoneResponse created = service.create(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Archive a milestone")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archive(id);
        return ResponseEntity.noContent().build();
    }
}
