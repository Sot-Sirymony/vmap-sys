package com.visionmapping.service;

import com.visionmapping.dto.request.CoachShareRequest;
import com.visionmapping.dto.response.CoachShareResponse;
import com.visionmapping.dto.response.CoachSharedReportResponse;
import com.visionmapping.dto.response.SharedReportResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.ReportCoachShare;
import com.visionmapping.entity.SavedReport;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.ReportCoachShareRepository;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.service.report.ReportSection;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V19 (BRD 19.0.0, RM-FR-50, decision D-19-3): a participant in an active
 * cohort shares a final report with their coach, read-only, with the sections
 * they choose. The coach sees it in the coach workspace, under the V9 D-6
 * rules: only while the participant is in an active cohort they coach. The
 * coach never sees drafts and cannot change anything. Unsharing, archiving the
 * report or leaving the cohort removes access at once. Anything the coach may
 * not see answers 404, the same as a report that does not exist.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReportCoachShareService {

    static final String NO_COACH = "Join a cohort with a coach to share a report with them.";
    static final String NOT_SHARED = "Report not found.";
    private static final String TARGET = "report_coach_share";

    private final SavedReportService savedReports;
    private final ReportShareService shares;
    private final CoachService coaches;
    private final ReportCoachShareRepository repository;
    private final EntityLookup lookup;
    private final Clock clock;

    // ---------- the participant ----------

    @Transactional(readOnly = true)
    public CoachShareResponse status(Long reportId) {
        SavedReport report = savedReports.owned(reportId);
        return toResponse(coachOf(lookup.currentUser()), repository.findByReport_IdAndUser_Id(report.getId(), lookup.userId()).orElse(null));
    }

    /** Shares the report with the participant's current coach, or changes the sections already shared. */
    @SecurityAudit(value = SecurityEventType.SHARE_LINK_CREATED, target = TARGET)
    public CoachShareResponse share(Long reportId, CoachShareRequest request) {
        SavedReport report = savedReports.owned(reportId);
        shares.requireShareable(report);
        AppUser coach = coachOf(lookup.currentUser());
        if (coach == null) {
            throw new BusinessRuleException(NO_COACH);
        }
        Set<ReportSection> sections = ReportShareService.chosen(request.sections());
        ReportCoachShare share = repository.findByReport_IdAndUser_Id(report.getId(), lookup.userId())
                .orElseGet(() -> ReportCoachShare.builder().user(report.getUser()).report(report).createdAt(Instant.now(clock)).build());
        share.setCoach(coach);
        share.setSections(shares.writeSections(sections));
        return toResponse(coach, repository.save(share));
    }

    /** RM-FR-50: unsharing removes the coach's access at once; unsharing twice changes nothing. */
    @SecurityAudit(value = SecurityEventType.SHARE_LINK_REVOKED, target = TARGET)
    public CoachShareResponse unshare(Long reportId) {
        SavedReport report = savedReports.owned(reportId);
        repository.deleteByReportId(report.getId());
        return toResponse(coachOf(lookup.currentUser()), null);
    }

    // ---------- the coach ----------

    /** The final reports the participant shared with the current coach, newest share first. */
    @Transactional(readOnly = true)
    public List<CoachSharedReportResponse> sharedWithMe(Long participantId) {
        AppUser participant = coaches.requireVisibleParticipant(participantId);
        return repository.findByUser_IdAndCoach_IdOrderByCreatedAtDescIdDesc(participant.getId(), lookup.userId()).stream()
                .filter(share -> visible(share.getReport()))
                .map(share -> {
                    SavedReport report = share.getReport();
                    return new CoachSharedReportResponse(report.getId(), report.getCode(), report.getName(), report.getScopeLabel(),
                            report.getPeriodKind(), report.getPeriodFrom(), report.getPeriodTo(), report.getFinalizedAt(), share.getCreatedAt());
                })
                .toList();
    }

    /** One shared report, read-only, with only the shared sections. */
    @SecurityAudit(value = SecurityEventType.COACH_VIEWED_PARTICIPANT, target = "user")
    @Transactional(readOnly = true)
    public SharedReportResponse view(Long participantId, Long reportId) {
        AppUser participant = coaches.requireVisibleParticipant(participantId);
        ReportCoachShare share = repository.findByReport_IdAndUser_IdAndCoach_Id(reportId, participant.getId(), lookup.userId())
                .filter(found -> visible(found.getReport()) && savedReports.decode(found.getReport()).verified())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_SHARED));
        return shares.sharedView(share.getReport(), shares.readSections(share.getSections()), null);
    }

    // ---------- helpers ----------

    private static boolean visible(SavedReport report) {
        return report.isFinal() && !report.isArchived();
    }

    /** The coach of the participant's active cohort, or null (V9 D-6). */
    @Nullable
    static AppUser coachOf(AppUser participant) {
        Cohort cohort = participant.getCohort();
        if (cohort == null || cohort.isArchived() || cohort.getCoach() == null || cohort.getCoach().getId().equals(participant.getId())) {
            return null;
        }
        return cohort.getCoach();
    }

    private CoachShareResponse toResponse(@Nullable AppUser coach, @Nullable ReportCoachShare share) {
        // A share made with an earlier coach does not count: that coach can no longer see it.
        boolean shared = share != null && coach != null && share.getCoach().getId().equals(coach.getId());
        return new CoachShareResponse(coach != null, coach == null ? null : coach.getFullName(), shared,
                shared ? shares.readSections(share.getSections()).stream().map(Enum::name).sorted().toList() : List.of(),
                shared ? share.getCreatedAt() : null);
    }
}
