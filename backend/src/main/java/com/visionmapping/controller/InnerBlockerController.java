package com.visionmapping.controller;

import com.visionmapping.dto.request.InnerBlockerHabitRequest;
import com.visionmapping.dto.request.InnerBlockerRequest;
import com.visionmapping.dto.request.InnerBlockerStatusRequest;
import com.visionmapping.dto.response.InnerBlockerResponse;
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.entity.enums.InnerBlockerStatus;
import com.visionmapping.service.InnerBlockerService;
import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V34 (BRD 34): the participant's own inner blockers. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/inner-blockers", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Inner blockers", description = "The participant's own inner blockers: the blocker, how it shows up and a short practice")
public class InnerBlockerController {

    private final InnerBlockerService service;

    @Operation(summary = "List inner blockers, newest first, one page at a time. "
            + "date (the user's local day) adds the habit streak; dreamId with includeAllDreams adds the blockers for all dreams")
    @GetMapping
    public ResponseEntity<PagedResponse<InnerBlockerResponse>> list(
            @RequestParam(required = false) InnerBlockerStatus status,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) @Positive Long dreamId,
            @RequestParam(defaultValue = "false") boolean includeAllDreams,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.page(status, archived, dreamId, includeAllDreams, date, pageable));
    }

    @Operation(summary = "Read one inner blocker with its latest weekly answers")
    @GetMapping("/{id}")
    public ResponseEntity<InnerBlockerResponse> get(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.get(id, date));
    }

    @Operation(summary = "Add an inner blocker (at most 10 active)")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InnerBlockerResponse> create(@Valid @RequestBody InnerBlockerRequest request) {
        InnerBlockerResponse created = service.create(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Edit an inner blocker")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InnerBlockerResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody InnerBlockerRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @Operation(summary = "Mark an inner blocker as no longer holding you back, or active again")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InnerBlockerResponse> changeStatus(@PathVariable @Positive Long id,
                                                             @Valid @RequestBody InnerBlockerStatusRequest request) {
        return ResponseEntity.ok(service.changeStatus(id, request));
    }

    @Operation(summary = "Archive an inner blocker; stopHabit also archives its daily habit")
    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable @Positive Long id, @RequestParam(defaultValue = "false") boolean stopHabit) {
        service.archive(id, stopHabit);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived inner blocker")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restore(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Make the practice a daily habit and link it")
    @PostMapping(value = "/{id}/habit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InnerBlockerResponse> makeHabit(
            @PathVariable @Positive Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody InnerBlockerHabitRequest request) {
        InnerBlockerResponse updated = service.makeHabit(id, request, date);
        return ApiResponses.createdAt("/habits", updated.habitId(), updated);
    }
}
