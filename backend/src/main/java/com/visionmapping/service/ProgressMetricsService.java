package com.visionmapping.service;

import com.visionmapping.dto.response.ChainAssessmentSummaryResponse;
import com.visionmapping.dto.response.ProgressMetricsResponse;
import com.visionmapping.dto.response.ProgressMetricsResponse.Metric;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.MilestoneRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-75: BO-01 to BO-06 for one person rather than a cohort, so each is "have I
 * done this" and the group percentages and week deadlines do not apply.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProgressMetricsService {

    private static final int WEEK_DAYS = 7;
    private static final List<PartnerStatus> RECRUITED = List.of(PartnerStatus.AGREED, PartnerStatus.ACTIVE, PartnerStatus.COMPLETED);

    private final EntityLookup lookup;
    private final DreamRepository dreamRepository;
    private final VisionStepRepository visionStepRepository;
    private final PartnerRepository partnerRepository;
    private final MilestoneRepository milestoneRepository;
    private final PlanStatusService planStatusService;
    private final ChainAssessmentService chainAssessmentService;

    @Value("${app.progress.target-improvement-percent:25}")
    private int targetImprovementPercent;

    @Value("${app.progress.target-weekly-completion-percent:60}")
    private int targetWeeklyCompletionPercent;

    public ProgressMetricsResponse metricsFor(LocalDate date) {
        Long userId = lookup.userId();
        long rankedDreams = dreamRepository.countByUser_IdAndArchivedFalseAndLetterRankIsNotNull(userId);
        long completePlans = planStatusService.countCompletePlans();
        long recruited = partnerRepository.countByUser_IdAndArchivedFalseAndStatusIn(userId, RECRUITED);
        long milestones = milestoneRepository.countByUser_IdAndArchivedFalse(userId);
        return new ProgressMetricsResponse(List.of(
                new Metric("BO-01", "Dreams written and ranked", String.valueOf(rankedDreams), "At least 1", rankedDreams > 0),
                new Metric("BO-02", "Complete plans", String.valueOf(completePlans), "At least 1 with goals, steps, dates and resources", completePlans > 0),
                improvementMetric(),
                weeklyStepsMetric(userId, date),
                new Metric("BO-05", "Partners recruited", String.valueOf(recruited), "At least 1 agreed or active", recruited > 0),
                new Metric("BO-06", "Milestones reached", String.valueOf(milestones), "At least 1", milestones > 0)));
    }

    private Metric improvementMetric() {
        ChainAssessmentSummaryResponse summary = chainAssessmentService.summary();
        BigDecimal improvement = summary.improvementPercent();
        String target = "+" + targetImprovementPercent + "% from your baseline";
        if (improvement == null) {
            return new Metric("BO-03", "Assessment improvement",
                    summary.assessments().isEmpty() ? "Not taken yet" : "Baseline only", target, false);
        }
        return new Metric("BO-03", "Assessment improvement", improvement + "%", target,
                improvement.compareTo(BigDecimal.valueOf(targetImprovementPercent)) >= 0);
    }

    /** Steps planned for the last seven days, ending on the given date. */
    private Metric weeklyStepsMetric(Long userId, LocalDate date) {
        List<VisionStep> planned = visionStepRepository.findByUser_IdAndArchivedFalseAndTargetDateBetween(
                userId, date.minusDays(WEEK_DAYS - 1L), date);
        String target = targetWeeklyCompletionPercent + "% or more of the last 7 days' steps";
        if (planned.isEmpty()) {
            return new Metric("BO-04", "Weekly steps completed", "No steps planned", target, false);
        }
        long completed = planned.stream().filter(step -> step.getStatus() == WorkStatus.COMPLETED).count();
        long percent = Math.round(completed * 100.0 / planned.size());
        return new Metric("BO-04", "Weekly steps completed", "%d of %d (%d%%)".formatted(completed, planned.size(), percent), target,
                percent >= targetWeeklyCompletionPercent);
    }
}
