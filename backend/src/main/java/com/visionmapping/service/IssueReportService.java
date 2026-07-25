package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.IssueReportRequest;
import com.visionmapping.dto.response.IssueReportResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.IssueReport;
import com.visionmapping.entity.enums.IssueReportStatus;
import com.visionmapping.entity.enums.ReportType;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.IssueReportRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-38: in-app issue & improvement reporting. A user raises a report and
 * tracks their own (FR-38.1/38.3); an ADMIN triages the whole queue and changes
 * status/resolution (FR-38.4). Everything here is in-app — no notification is
 * sent on resolution (FR-38.5). BR-32: reports are user-scoped for authoring
 * and self-viewing, only ADMIN triages; a bug needs a severity, and a bug moved
 * to RESOLVED needs a resolution note.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class IssueReportService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final IssueReportRepository issueReportRepository;

    /** FR-38.3: the caller's own reports, newest first. */
    @Transactional(readOnly = true)
    public List<IssueReportResponse> listMyReports(boolean includeArchived) {
        return findAllForUser(issueReportRepository, lookup.userId(), includeArchived).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(mapper::toResponse)
                .toList();
    }

    /** FR-38.4: the full queue for admins, optionally filtered. */
    @Transactional(readOnly = true)
    public List<IssueReportResponse> listAllReports(String reportType, String status, String severity) {
        requireAdmin();
        ReportType typeFilter = reportType == null ? null : parseEnum(ReportType.class, reportType);
        IssueReportStatus statusFilter = status == null ? null : parseEnum(IssueReportStatus.class, status);
        Severity severityFilter = severity == null ? null : parseEnum(Severity.class, severity);
        return issueReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(report -> typeFilter == null || report.getReportType() == typeFilter)
                .filter(report -> statusFilter == null || report.getStatus() == statusFilter)
                .filter(report -> severityFilter == null || report.getSeverity() == severityFilter)
                .map(mapper::toResponse)
                .toList();
    }

    public IssueReportResponse createReport(IssueReportRequest request) {
        AppUser user = lookup.currentUser();
        requireSeverityForBug(request.reportType(), request.severity());
        IssueReport entity = IssueReport.builder()
                .code(nextCode("IR", issueReportRepository.findByUser_Id(user.getId()), IssueReport::getCode))
                .user(user)
                .reportType(request.reportType())
                .title(request.title())
                .description(request.description())
                .severity(request.severity())
                .contextRoute(request.contextRoute())
                .appVersion(request.appVersion())
                .status(IssueReportStatus.OPEN)
                .build();
        return mapper.toResponse(issueReportRepository.save(entity));
    }

    /** The owner sees their own report; an admin sees any report. */
    @Transactional(readOnly = true)
    public IssueReportResponse getReport(Long id) {
        return mapper.toResponse(accessibleReport(id));
    }

    /** FR-38.4: admin-only status change, with the resolution rule (BR-32). */
    public IssueReportResponse updateStatus(Long id, String status, String resolutionNote) {
        requireAdmin();
        IssueReport report = issueReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue report not found: " + id));
        IssueReportStatus newStatus = parseEnum(IssueReportStatus.class, status);
        if (resolutionNote != null) {
            report.setResolutionNote(resolutionNote);
        }
        if (report.getReportType() == ReportType.BUG
                && newStatus == IssueReportStatus.RESOLVED
                && isBlank(report.getResolutionNote())) {
            throw new BusinessRuleException("A resolved bug must include a resolution note.");
        }
        report.setStatus(newStatus);
        return mapper.toResponse(report);
    }

    public void archiveReport(Long id) {
        ownedReport(id).setArchived(true);
    }

    public void restoreReport(Long id) {
        ownedReport(id).setArchived(false);
    }

    public void permanentlyDeleteReport(Long id) {
        IssueReport report = ownedReport(id);
        requireArchived(report.isArchived(), "Issue report");
        issueReportRepository.delete(report);
    }

    private static void requireSeverityForBug(ReportType type, Severity severity) {
        if (type == ReportType.BUG && severity == null) {
            throw new BusinessRuleException("A bug report must include a severity.");
        }
    }

    /** Owner-only access, used for the reporter's own archive/restore/delete. */
    private IssueReport ownedReport(Long id) {
        IssueReport report = issueReportRepository.findById(id)
                .filter(r -> r.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Issue report not found: " + id));
        return report;
    }

    /** Owner or admin — used for reads that both roles may perform. */
    private IssueReport accessibleReport(Long id) {
        IssueReport report = issueReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue report not found: " + id));
        if (!report.getUser().getId().equals(lookup.userId()) && !isAdmin()) {
            throw new ResourceNotFoundException("Issue report not found: " + id);
        }
        return report;
    }

    private void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Only an administrator can triage issue reports.");
        }
    }

    private boolean isAdmin() {
        return lookup.currentUser().getRole() == UserRole.ADMIN;
    }
}
