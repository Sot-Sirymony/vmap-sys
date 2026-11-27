package com.visionmapping.controller;

import com.visionmapping.dto.request.HabitRequest;
import com.visionmapping.dto.response.HabitResponse;
import com.visionmapping.service.HabitService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping(value = ApiPaths.V1 + "/habits", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Habits", description = "Daily habits and their checks")
public class HabitController {

    private final HabitService service;

    @Operation(summary = "List habits with their state on a day")
    @GetMapping
    public ResponseEntity<List<HabitResponse>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listHabits(date, ArchiveFilter.of(includeArchived)));
    }

    @Operation(summary = "Create a habit")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HabitResponse> create(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody HabitRequest request) {
        HabitResponse created = service.createHabit(request, date);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Rename a habit")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HabitResponse> rename(
            @PathVariable @Positive Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody HabitRequest request) {
        return ResponseEntity.ok(service.renameHabit(id, request, date));
    }

    @Operation(summary = "Tick a habit for a day")
    @PutMapping("/{id}/checks/{date}")
    public ResponseEntity<HabitResponse> check(@PathVariable @Positive Long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.setChecked(id, date, true));
    }

    @Operation(summary = "Untick a habit for a day")
    @DeleteMapping("/{id}/checks/{date}")
    public ResponseEntity<HabitResponse> uncheck(@PathVariable @Positive Long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.setChecked(id, date, false));
    }

    @Operation(summary = "Archive a habit")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveHabit(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived habit")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreHabit(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a habit permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteHabit(id);
        return ResponseEntity.noContent().build();
    }
}
