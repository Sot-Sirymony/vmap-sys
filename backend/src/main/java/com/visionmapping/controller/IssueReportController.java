package com.visionmapping.controller;

import com.visionmapping.dto.request.IssueReportRequest;
import com.visionmapping.dto.request.IssueReportStatusUpdateRequest;
import com.visionmapping.dto.response.IssueReportResponse;
import com.visionmapping.service.IssueReportService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

/**
 * FR-38: in-app issue & improvement reporting. A user raises reports and lists
 * their own; an admin lists/filters the whole queue and changes status. Role
 * enforcement lives in the service (admin paths throw 403).
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/issue-reports", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Issue reports", description = "Problems and ideas reported by users")
public class IssueReportController {

    private final IssueReportService service;

    /** FR-38.3: the caller's own reports. */
    @Operation(summary = "List my issue reports")
    @GetMapping
    public ResponseEntity<List<IssueReportResponse>> listMine(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(service.listMyReports(ArchiveFilter.of(includeArchived)));
    }

    /** FR-38.4: admin triage queue, optionally filtered. */
    @Operation(summary = "List everyone's issue reports (support only)")
    @GetMapping("/all")
    public ResponseEntity<List<IssueReportResponse>> listAll(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        return ResponseEntity.ok(service.listAllReports(reportType, status, severity));
    }

    @Operation(summary = "Create an issue report")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IssueReportResponse> create(@Valid @RequestBody IssueReportRequest request) {
        IssueReportResponse created = service.createReport(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get an issue report")
    @GetMapping("/{id}")
    public ResponseEntity<IssueReportResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getReport(id));
    }

    @Operation(summary = "Change an issue report's status (support only)")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IssueReportResponse> updateStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody IssueReportStatusUpdateRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request.status(), request.resolutionNote()));
    }

    @Operation(summary = "Archive an issue report")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveReport(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived issue report")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreReport(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete an issue report permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteReport(id);
        return ResponseEntity.noContent().build();
    }
}
