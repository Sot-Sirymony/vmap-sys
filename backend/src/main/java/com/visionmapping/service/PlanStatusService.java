package com.visionmapping.service;

import com.visionmapping.dto.response.PlanStatusResponse;
import com.visionmapping.dto.response.PlanStatusResponse.Stage;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-73: reads how far one dream has come through the guided plan. It stores
 * nothing, because the plan's real content is the dream's own goals and steps.
 * Resources and people are shown but do not hold back planReady: a first plan
 * is allowed to say what it still needs.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanStatusService {

    private final EntityLookup lookup;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final ObstacleRepository obstacleRepository;
    private final PartnerRepository partnerRepository;

    public PlanStatusResponse statusFor(Long dreamId) {
        return evaluate(lookup.dream(dreamId), lookup.userId());
    }

    public boolean anyPlanReady() {
        Long userId = lookup.userId();
        return dreamRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .anyMatch(dream -> evaluate(dream, userId).planReady());
    }

    /** FR-75: dreams with goals, steps, dates and resources all in place. */
    public long countCompletePlans() {
        return countCompletePlans(lookup.userId());
    }

    /** The same count for any user; callers check they may see that user (V9 coach view). */
    public long countCompletePlans(Long userId) {
        return dreamRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .map(dream -> evaluate(dream, userId))
                .filter(status -> status.planReady() && status.stages().stream()
                        .anyMatch(stage -> stage.key().equals("RESOURCES") && stage.done()))
                .count();
    }

    private PlanStatusResponse evaluate(Dream dream, Long userId) {
        List<Goal> goals = goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), userId);
        List<VisionStep> steps = new ArrayList<>();
        long goalsWithoutSteps = 0;
        for (Goal goal : goals) {
            List<VisionStep> goalSteps = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), userId);
            steps.addAll(goalSteps);
            if (goalSteps.isEmpty()) {
                goalsWithoutSteps++;
            }
        }
        long undatedSteps = steps.stream().filter(step -> step.getTargetDate() == null).count();

        boolean statementDone = hasText(dream.getWhyImportant()) && hasText(dream.getSuccessDefinition());
        boolean goalsDone = !goals.isEmpty();
        boolean stepsDone = goalsDone && goalsWithoutSteps == 0;
        boolean datesDone = stepsDone && undatedSteps == 0;
        boolean resourcesDone = obstacleRepository.existsForDreamTree(userId, dream.getId());
        boolean peopleDone = partnerRepository.existsAnyPartnerForDream(userId, dream.getId());

        List<Stage> stages = List.of(
                new Stage("STATEMENT", "Dream statement", statementDone,
                        statementDone ? "Why it matters and what success looks like are written." : "Write why it matters and what success looks like."),
                new Stage("GOALS", "Goals", goalsDone,
                        goalsDone ? count(goals.size(), "goal") + " in place." : "Add the major results this dream needs."),
                new Stage("STEPS", "Steps", stepsDone,
                        stepsDone ? count(steps.size(), "step") + " in place."
                                : goalsDone ? count(goalsWithoutSteps, "goal") + " still without steps." : "Add goals first."),
                new Stage("DATES", "Owners and dates", datesDone,
                        datesDone ? "Every step has an owner and a date."
                                : undatedSteps > 0 ? count(undatedSteps, "step") + " still without a date." : "Add steps first."),
                new Stage("RESOURCES", "Resources needed", resourcesDone,
                        resourcesDone ? "What is missing has been recorded." : "Record what is missing as an obstacle on this dream."),
                new Stage("PEOPLE", "People", peopleDone,
                        peopleDone ? "A partner is linked to this dream or one of its goals." : "Link a partner or mentor to this dream or one of its goals."));
        return new PlanStatusResponse(dream.getId(), stages, statementDone && goalsDone && stepsDone && datesDone);
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static String count(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
