package com.visionmapping.controller;

import com.visionmapping.dto.request.ProgressLogRequest;
import com.visionmapping.dto.response.ProgressLogResponse;
import com.visionmapping.service.ProgressLogService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.visionmapping.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/progress-logs", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Progress logs", description = "Records of task progress")
public class ProgressLogController {

    private final ProgressLogService service;

    @Operation(summary = "List progress logs, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<ProgressLogResponse>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(service.pageProgressLogs(pageable));
    }

    @Operation(summary = "Create a progress log")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProgressLogResponse> create(@Valid @RequestBody ProgressLogRequest request) {
        ProgressLogResponse created = service.createProgressLog(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a progress log")
    @GetMapping("/{id}")
    public ResponseEntity<ProgressLogResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getProgressLog(id));
    }

    @Operation(summary = "Archive a progress log")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveProgressLog(id);
        return ResponseEntity.noContent().build();
    }
}
