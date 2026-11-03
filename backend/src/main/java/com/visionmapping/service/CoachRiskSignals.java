package com.visionmapping.service;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AttitudeCheckIn;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * V9 (AI-FR-19): the risk signals behind a participant's at-risk flag, each
 * with its reason: falling completion, a falling attitude trend (only when the
 * participant shares journals, D-8), steps that slip again, and a pile of
 * overdue work. Plain rules, so they cost nothing and are always the same for
 * the same data; the AI summary explains them.
 */
@Component
@RequiredArgsConstructor
public class CoachRiskSignals {

    static final int OVERDUE_PILE = 3;

    private final TaskItemRepository taskItemRepository;
    private final VisionStepRepository visionStepRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final Clock clock;

    public List<String> reasons(AppUser participant) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        Instant twoWeeksAgo = now.minus(14, ChronoUnit.DAYS);
        List<TaskItem> tasks = taskItemRepository.findByUser_IdAndArchivedFalse(participant.getId());
        List<VisionStep> steps = visionStepRepository.findByUser_IdAndArchivedFalse(participant.getId());
        List<String> reasons = new ArrayList<>();

        long thisWeek = finished(tasks, steps, weekAgo, now);
        long lastWeek = finished(tasks, steps, twoWeeksAgo, weekAgo);
        if (lastWeek >= 2 && thisWeek * 2 <= lastWeek) {
            reasons.add("Finished %d item(s) this week, down from %d the week before.".formatted(thisWeek, lastWeek));
        }

        long slipping = steps.stream()
                .filter(step -> step.getStatus() != WorkStatus.COMPLETED && step.getOverdueReschedules() >= 1
                        && step.getTargetDate() != null && step.getTargetDate().isBefore(today))
                .count();
        if (slipping > 0) {
            reasons.add("%d step(s) overdue again after being given a new date.".formatted(slipping));
        }

        long overdue = tasks.stream().filter(task -> task.getStatus() != WorkStatus.COMPLETED
                && task.getDueDate() != null && task.getDueDate().isBefore(today)).count()
                + steps.stream().filter(step -> step.getStatus() != WorkStatus.COMPLETED
                && step.getTargetDate() != null && step.getTargetDate().isBefore(today)).count();
        if (overdue >= OVERDUE_PILE) {
            reasons.add("%d items overdue.".formatted(overdue));
        }

        if (participant.isShareJournalsWithCoach()) {
            List<AttitudeCheckIn> checkIns = attitudeCheckInRepository.findTop30ByUser_IdOrderByCheckInDateDesc(participant.getId());
            OptionalDouble recent = average(checkIns, today.minusDays(7), today);
            OptionalDouble before = average(checkIns, today.minusDays(14), today.minusDays(8));
            if (recent.isPresent() && before.isPresent() && before.getAsDouble() - recent.getAsDouble() >= 1.0) {
                reasons.add("Attitude averaged %.1f this week, down from %.1f.".formatted(recent.getAsDouble(), before.getAsDouble()));
            } else if (recent.isPresent() && recent.getAsDouble() <= 2.0) {
                reasons.add("Attitude averaged %.1f of 5 this week.".formatted(recent.getAsDouble()));
            }
        }
        return reasons;
    }

    private static long finished(List<TaskItem> tasks, List<VisionStep> steps, Instant from, Instant to) {
        return tasks.stream().filter(task -> task.getCompletedAt() != null
                && task.getCompletedAt().isAfter(from) && !task.getCompletedAt().isAfter(to)).count()
                + steps.stream().filter(step -> step.getStatus() == WorkStatus.COMPLETED && step.getUpdatedAt() != null
                && step.getUpdatedAt().isAfter(from) && !step.getUpdatedAt().isAfter(to)).count();
    }

    private static OptionalDouble average(List<AttitudeCheckIn> checkIns, LocalDate from, LocalDate to) {
        return checkIns.stream()
                .filter(checkIn -> !checkIn.getCheckInDate().isBefore(from) && !checkIn.getCheckInDate().isAfter(to))
                .mapToInt(AttitudeCheckIn::getScore)
                .average();
    }
}
