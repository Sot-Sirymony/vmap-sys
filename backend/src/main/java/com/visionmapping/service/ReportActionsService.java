package com.visionmapping.service;

import com.visionmapping.dto.request.ReportQuery;
import com.visionmapping.dto.request.ReportShareRequest;
import com.visionmapping.dto.response.CommentaryDraftResponse;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ReportShareResponse;
import com.visionmapping.dto.response.SharedReportResponse;
import com.visionmapping.service.ManagementReportService.ReportFile;
import com.visionmapping.service.ai.ReportCommentaryAiService;
import com.visionmapping.service.report.ReportExtras;
import com.visionmapping.service.report.ReportSection;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.util.UserScope;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * V33 (D-33-11): the report actions that check a rate limit or choose between
 * two ways of building a report. They were in the report controllers; moving
 * them here keeps each controller to one service call per request.
 *
 * <p>The limits are unchanged: downloads (RM-NFR-07, V10 SEC-06-02) and share
 * links per account, and public links per share token.
 */
@Service
@RequiredArgsConstructor
public class ReportActionsService {

    private final ManagementReportService reports;
    private final SavedReportService savedReports;
    private final ReportShareService shares;
    private final ReportCommentaryAiService aiDrafts;
    private final RequestRateLimiter rateLimiter;
    private final UserScope userScope;

    /** The live summary, or (RM-FR-32) compared with one of the participant's final reports on the same scope. */
    public ManagementReportResponse summary(ReportQuery query) {
        if (query.compareTo() == null) {
            return reports.summary(query.toScope());
        }
        return savedReports.live(query.toScope(), query.compareTo()).summary();
    }

    /** The live report as Excel; the chosen sections and a Changes sheet when asked (V19 RM-FR-39, RM-FR-40). */
    public ReportFile excel(ReportQuery query, @Nullable String language, @Nullable List<ReportSection> sections) {
        limitExport();
        if (query.compareTo() == null && sections == null) {
            return reports.excel(query.toScope(), language);
        }
        ManagementReportService.Built built = savedReports.live(query.toScope(), query.compareTo());
        return reports.excel(built.report(), built.summary(), language,
                ManagementReportService.fileName(built.report(), "xlsx"), extras(sections));
    }

    /** The live report as slides; the chosen sections and a What changed slide when asked (V19 RM-FR-39, RM-FR-41). */
    public ReportFile deck(ReportQuery query, @Nullable String language, @Nullable String title, boolean includeName,
                           @Nullable List<ReportSection> sections) {
        limitExport();
        if (query.compareTo() == null && sections == null) {
            return reports.deck(query.toScope(), language, title, includeName);
        }
        ManagementReportService.Built built = savedReports.live(query.toScope(), query.compareTo());
        return reports.deck(built.report(), built.summary(), language, title, includeName,
                ManagementReportService.fileName(built.report(), "pptx"), extras(sections));
    }

    /** The live report as a PDF. */
    public ReportFile pdf(ReportQuery query, @Nullable String language, @Nullable List<ReportSection> sections) {
        limitExport();
        return reports.pdf(savedReports.live(query.toScope(), query.compareTo()), language, extras(sections));
    }

    /** A saved report as Excel. */
    public ReportFile savedExcel(Long id, @Nullable String language, @Nullable List<ReportSection> sections) {
        limitExport();
        return savedReports.excel(id, language, sections);
    }

    /** RM-FR-42: a saved report as a PDF, from the snapshot once final. */
    public ReportFile savedPdf(Long id, @Nullable String language, @Nullable List<ReportSection> sections) {
        limitExport();
        return savedReports.pdf(id, language, sections);
    }

    /** A saved report as slides. */
    public ReportFile savedDeck(Long id, @Nullable String language, @Nullable String title, boolean includeName,
                                @Nullable List<ReportSection> sections) {
        limitExport();
        return savedReports.deck(id, language, title, includeName, sections);
    }

    /** RM-FR-46, RM-FR-48: a read-only link to a final report; 20 a day per account (RM-NFR-07). */
    public ReportShareResponse share(Long id, ReportShareRequest request) {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_SHARE, currentUserKey());
        return shares.create(id, request);
    }

    /** RM-FR-28 fixed rules, or RM-FR-29 Draft with AI. */
    public CommentaryDraftResponse commentaryDraft(Long id, @Nullable String language, String mode) {
        return "AI".equals(mode) ? aiDrafts.draft(id, language) : savedReports.commentaryDraft(id, language);
    }

    /** RM-FR-47: a shared report opened through its link, limited per link. */
    public SharedReportResponse publicView(String token) {
        rateLimiter.check(RequestRateLimiter.Action.SHARED_REPORT, ReportShareService.hash(token));
        return shares.view(token);
    }

    /** RM-FR-47: a shared report's PDF opened through its link, limited per link. */
    public ReportFile publicPdf(String token, @Nullable String language) {
        rateLimiter.check(RequestRateLimiter.Action.SHARED_REPORT, ReportShareService.hash(token));
        return shares.pdf(token, language);
    }

    private void limitExport() {
        rateLimiter.check(RequestRateLimiter.Action.REPORT_EXPORT, currentUserKey());
    }

    private String currentUserKey() {
        return "user:" + userScope.currentUser().getId();
    }

    private static ReportExtras extras(@Nullable List<ReportSection> sections) {
        return new ReportExtras(null, sections == null ? null : Set.copyOf(sections), false);
    }
}
