package com.visionmapping.controller;

import com.visionmapping.dto.request.IssueReportRequest;
import com.visionmapping.dto.request.IssueReportStatusUpdateRequest;
import com.visionmapping.dto.response.IssueReportResponse;
import com.visionmapping.service.IssueReportService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-38: in-app issue & improvement reporting. A user raises reports and lists
 * their own; an admin lists/filters the whole queue and changes status. Role
 * enforcement lives in the service (admin paths throw 403).
 */
@RestController
@RequestMapping("/api/issue-reports")
@RequiredArgsConstructor
public class IssueReportController {

    private final IssueReportService service;

    /** FR-38.3: the caller's own reports. */
    @GetMapping
    public List<IssueReportResponse> listMine(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listMyReports(includeArchived);
    }

    /** FR-38.4: admin triage queue, optionally filtered. */
    @GetMapping("/all")
    public List<IssueReportResponse> listAll(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        return service.listAllReports(reportType, status, severity);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueReportResponse create(@Valid @RequestBody IssueReportRequest request) {
        return service.createReport(request);
    }

    @GetMapping("/{id}")
    public IssueReportResponse get(@PathVariable Long id) {
        return service.getReport(id);
    }

    @PatchMapping("/{id}/status")
    public IssueReportResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody IssueReportStatusUpdateRequest request) {
        return service.updateStatus(id, request.status(), request.resolutionNote());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveReport(id);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreReport(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteReport(id);
    }
}
