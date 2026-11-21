package com.visionmapping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.ReportShareRequest;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ReportShareResponse;
import com.visionmapping.dto.response.SharedReportResponse;
import com.visionmapping.entity.ReportShareLink;
import com.visionmapping.entity.SavedReport;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.mapper.ReportRowsMapper;
import com.visionmapping.repository.ReportShareLinkRepository;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.ManagementReportService.Built;
import com.visionmapping.service.ManagementReportService.ReportFile;
import com.visionmapping.service.report.ReportExtras;
import com.visionmapping.service.report.ReportSection;
import com.visionmapping.service.report.ReportText;
import com.visionmapping.service.support.EntityLookup;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V19 (BRD 19.0.0, RM-FR-46 to RM-FR-48, RM-FR-51, RM-FR-52): read-only links
 * to a participant's final reports, following the V9 plan share pattern. The
 * token is 256 random bits shown once; only its SHA-256 is kept. A link
 * expires after 7, 30 or 90 days and can be stopped at any time. Only a final
 * report whose snapshot is verified can be shared (RM-NFR-04). An unknown,
 * expired or stopped link, or one whose report is archived or fails its
 * check, all answer the same 404 and reveal nothing (RM-NFR-01).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReportShareService {

    static final int DEFAULT_EXPIRY_DAYS = 30;
    static final Set<Integer> EXPIRY_CHOICES = Set.of(7, 30, 90);
    static final Set<ReportSection> DEFAULT_SECTIONS = EnumSet.of(ReportSection.COMMENTARY, ReportSection.GOAL_HEALTH, ReportSection.CHANGES);
    static final String NO_LONGER_SHARED = "This report is no longer shared.";
    static final String ONLY_FINAL = "Only a final report can be shared. Finalise it first.";
    static final String ARCHIVED = "Restore this report before you share it.";
    static final String UNVERIFIED = "This report could not be verified, so it cannot be shared.";
    static final String BAD_EXPIRY = "A share link lasts 7, 30 or 90 days.";
    private static final int TOKEN_BYTES = 32;
    private static final String TARGET = "report_share";

    private final SavedReportService savedReports;
    private final ManagementReportService reports;
    private final ReportShareLinkRepository repository;
    private final SecurityEventService securityEvents;
    private final EntityLookup lookup;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    // ---------- the participant ----------

    /** RM-FR-46: the report's links, newest first, with their views. */
    @Transactional(readOnly = true)
    public List<ReportShareResponse> list(Long reportId) {
        SavedReport report = savedReports.owned(reportId);
        return repository.findByReport_IdAndUser_IdOrderByCreatedAtDescIdDesc(report.getId(), lookup.userId()).stream()
                .map(link -> toResponse(link, null))
                .toList();
    }

    /** RM-FR-46, RM-FR-48: a new link; the answer holds its token, the only time it is shown. */
    @SecurityAudit(value = SecurityEventType.SHARE_LINK_CREATED, target = TARGET)
    public ReportShareResponse create(Long reportId, ReportShareRequest request) {
        SavedReport report = savedReports.owned(reportId);
        requireShareable(report);
        int days = request.expiresInDays() == null ? DEFAULT_EXPIRY_DAYS : request.expiresInDays();
        if (!EXPIRY_CHOICES.contains(days)) {
            throw new BusinessRuleException(BAD_EXPIRY);
        }
        Set<ReportSection> sections = chosen(request.sections());
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        ReportShareLink link = repository.save(ReportShareLink.builder()
                .user(report.getUser())
                .report(report)
                .tokenHash(hash(token))
                .sections(writeSections(sections))
                .expiresAt(Instant.now(clock).plus(Duration.ofDays(days)))
                .build());
        return toResponse(link, token);
    }

    /** RM-FR-46: stop a link at once; stopping one already stopped changes nothing. */
    @SecurityAudit(value = SecurityEventType.SHARE_LINK_REVOKED, target = TARGET)
    public ReportShareResponse revoke(Long reportId, Long shareId) {
        savedReports.owned(reportId);
        ReportShareLink link = repository.findByIdAndReport_IdAndUser_Id(shareId, reportId, lookup.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found."));
        if (link.getRevokedAt() == null) {
            link.setRevokedAt(Instant.now(clock));
        }
        return toResponse(link, null);
    }

    // ---------- the reviewer (public, no sign-in) ----------

    /** RM-FR-47: the shared view. Counts the visit; the log keeps only the date and the link (RM-FR-52). */
    public SharedReportResponse view(String token) {
        ReportShareLink link = open(token);
        Instant now = Instant.now(clock);
        link.setViewCount(link.getViewCount() + 1);
        link.setLastViewedAt(now);
        securityEvents.recordAnonymous(SecurityEventType.SHARE_LINK_OPENED, SecurityOutcome.SUCCESS, TARGET, link.getId(), "view");
        return sharedView(link.getReport(), sectionsOf(link), link.getExpiresAt());
    }

    /**
     * RM-FR-47, and RM-FR-50 for a coach: a final report with only the chosen
     * sections, from its snapshot. expiresAt is null for a coach share.
     */
    SharedReportResponse sharedView(SavedReport report, Set<ReportSection> sections, @Nullable Instant expiresAt) {
        Built content = savedReports.content(report);
        ManagementReportResponse summary = shared(content.summary(), sections);
        return new SharedReportResponse(report.getName(), report.getCode(), firstName(report.getUser().getFullName()), report.getLanguage(),
                report.getFinalizedAt(), expiresAt, sections.stream().map(Enum::name).toList(), summary,
                sections.contains(ReportSection.COMMENTARY) ? savedReports.commentaryOf(report) : null,
                sections.contains(ReportSection.DETAILS)
                        ? ReportRowsMapper.toResponse(reports.rows(content.report(), report.getLanguage())).rows() : null);
    }

    /** RM-FR-48, RM-FR-50: only a final, unarchived, verified report can be shared, by link or with a coach. */
    void requireShareable(SavedReport report) {
        if (!report.isFinal()) {
            throw new StateConflictException(ONLY_FINAL);
        }
        if (report.isArchived()) {
            throw new StateConflictException(ARCHIVED);
        }
        if (!savedReports.decode(report).verified()) {
            throw new StateConflictException(UNVERIFIED);
        }
    }

    /** The sections chosen, or Commentary, Goal health and What changed when none were. */
    static Set<ReportSection> chosen(@Nullable List<ReportSection> requested) {
        return requested == null || requested.isEmpty() ? DEFAULT_SECTIONS : EnumSet.copyOf(requested);
    }

    Set<ReportSection> readSections(String json) {
        try {
            List<ReportSection> stored = mapper.readValue(json, new TypeReference<>() { });
            return stored.isEmpty() ? DEFAULT_SECTIONS : EnumSet.copyOf(stored);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read the shared sections.", exception);
        }
    }

    String writeSections(Set<ReportSection> sections) {
        return writeJson(new TreeSet<>(sections));
    }

    /** RM-FR-47: the shared report as a PDF, with only the shared sections. */
    @Transactional(readOnly = true)
    public ReportFile pdf(String token, @Nullable String language) {
        ReportShareLink link = open(token);
        SavedReport report = link.getReport();
        Built content = savedReports.content(report);
        String fileLanguage = language == null || language.isBlank() ? report.getLanguage() : language;
        Set<ReportSection> sections = sectionsOf(link);
        return reports.pdf(content.report(), content.summary(), fileLanguage,
                SavedReportService.fileName(report, content.report(), "pdf"),
                new ReportExtras(sections.contains(ReportSection.COMMENTARY) ? savedReports.commentaryOf(report) : null, sections, false),
                SavedReportService.cover(report, content.report(), ReportText.of(fileLanguage)));
    }

    /** The open link for a token; anything else is the same 404, logged without the visitor (RM-NFR-01, RM-FR-52). */
    private ReportShareLink open(String token) {
        Instant now = Instant.now(clock);
        ReportShareLink link = repository.findByTokenHash(hash(token))
                .filter(found -> found.isOpenAt(now))
                .filter(found -> found.getReport().isFinal() && !found.getReport().isArchived())
                .filter(found -> savedReports.decode(found.getReport()).verified())
                .orElse(null);
        if (link == null) {
            securityEvents.recordAnonymous(SecurityEventType.SHARE_LINK_OPENED, SecurityOutcome.DENIED, TARGET, null,
                    "unknown, expired or stopped report link");
            throw new ResourceNotFoundException(NO_LONGER_SHARED);
        }
        return link;
    }

    /** RM-FR-47: no "needs attention" items or owners, and only the shared parts. */
    static ManagementReportResponse shared(ManagementReportResponse summary, Set<ReportSection> sections) {
        return new ManagementReportResponse(summary.scope(), summary.asOf(), summary.period(), summary.periodFigures(), summary.headline(),
                summary.tasksByStatus(), summary.openTasksByPriority(), summary.blockersByKind(), summary.unplacedObstacles(),
                sections.contains(ReportSection.GOAL_HEALTH) ? summary.goals() : List.of(), List.of(), summary.trend(), summary.filters(),
                List.of(), sections.contains(ReportSection.CHANGES) ? summary.comparison() : null);
    }

    // ---------- helpers ----------

    private ReportShareResponse toResponse(ReportShareLink link, @Nullable String token) {
        return new ReportShareResponse(link.getId(), sectionsOf(link).stream().map(Enum::name).toList(), link.getCreatedAt(),
                link.getExpiresAt(), link.getRevokedAt(), link.isOpenAt(Instant.now(clock)), link.getViewCount(), link.getLastViewedAt(),
                token);
    }

    private Set<ReportSection> sectionsOf(ReportShareLink link) {
        return readSections(link.getSections());
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to save the share link's sections.", exception);
        }
    }

    private static String firstName(@Nullable String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    /** SHA-256 of the token, in hex; also the rate limiter's key, so the raw token is never kept. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
