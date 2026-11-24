package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.response.AiPlanResponses.BlockerAnalysis;
import com.visionmapping.dto.response.AiPlanResponses.BlockerItem;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.CycleTimeCalculator;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Vision Map plan assistant's blocker analysis for one dream.
 *
 * <p>The rule half needs no AI, so it always answers: every Blocked or Waiting
 * task and step, and every step missing knowledge, a skill or money with no
 * helper yet (the plan check's GAP_NEEDS_HELP rule). For each it works out how
 * long it has been stuck, how many later steps in the goal wait on it, whether
 * the goal's date is at risk, what is missing and whether a helper or an
 * obstacle is already linked. Blockers are ranked by impact.
 *
 * <p>When AI is available, Claude adds a root cause, one first move and the
 * kind of helper for each, plus what the blockers have in common. Nothing is
 * saved: every action goes through the normal pages.
 */
@Service
@RequiredArgsConstructor
public class BlockerAnalysisService {

    public static final String FEATURE = "BLOCKER_ANALYSIS";
    /** Ranked list shown on the page; the counts still cover the whole dream. */
    static final int MAX_BLOCKERS = 15;
    /** Only the top blockers go to Claude, to keep the request small. */
    static final int MAX_AI_BLOCKERS = 8;
    /** A goal due within this many days, with something stuck under it, is at risk. */
    static final int AT_RISK_DAYS = 14;

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiPlanService planService;
    private final GoalRepository goalRepository;
    private final VisionStepRepository stepRepository;
    private final TaskItemRepository taskRepository;
    private final ObstacleRepository obstacleRepository;
    private final PartnerRepository partnerRepository;
    private final CycleTimeCalculator cycleTime;
    private final AiStructuredRequests requests;
    private final PlanSessionGuard sessionGuard;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public BlockerAnalysis analyse(Long dreamId, String language) {
        AppUser user = lookup.currentUser();
        LocalDate today = LocalDate.now(clock);
        Snapshot snapshot = transactions.execute(status -> collect(user, lookup.dream(dreamId), today));
        List<BlockerItem> ranked = snapshot.items().stream()
                .sorted(Comparator.comparingInt(BlockerItem::score).reversed().thenComparing(BlockerItem::itemId))
                .limit(MAX_BLOCKERS)
                .toList();
        int blocked = count(snapshot.items(), "BLOCKED");
        int waiting = count(snapshot.items(), "WAITING");
        int gaps = count(snapshot.items(), "GAP");
        int heldUp = snapshot.heldUpStepIds().size();
        if (ranked.isEmpty()) {
            return new BlockerAnalysis(ranked, 0, 0, 0, 0, null, false, null);
        }

        Optional<String> apiKey = accessService.availableKey(user);
        // The rule half still answers when AI is off or the session's limit is reached.
        if (apiKey.isEmpty() || !sessionGuard.tryUse(user.getId(), dreamId)) {
            return new BlockerAnalysis(ranked, blocked, waiting, gaps, heldUp, null, false, null);
        }
        List<BlockerItem> forAi = ranked.stream().limit(MAX_AI_BLOCKERS).toList();
        String records = snapshot.dreamText() + "\nWhat is holding this dream back, biggest impact first:\n" + describe(forAi);
        String prompt = "Act as a calm, practical coach. For each blocker listed, use its key in brackets and say what is "
                + "really missing (one obstacle type), the likely root cause in one kind sentence without blame, one concrete "
                + "first move that could start within a day, and the kind of person or resource that could help, as a role. "
                + "Where the participant already named the blocker, build on what they wrote. Then, only when several blockers "
                + "share a cause, say what they have in common. Do not invent blockers that are not listed.";
        AiStructuredRequests.Tracked<AiDrafts.BlockerReview> answer;
        try {
            answer = requests.askTracked(user, apiKey.get(), FEATURE, language, records, prompt, AiDrafts.BlockerReview.class);
        } catch (AiUnavailableException exception) {
            return new BlockerAnalysis(ranked, blocked, waiting, gaps, heldUp, null, false, null);
        }
        Map<String, AiDrafts.BlockerDraft> drafts = new HashMap<>();
        for (AiDrafts.BlockerDraft draft : Optional.ofNullable(answer.value().blockers()).orElse(List.of())) {
            if (draft.itemKey() != null) {
                drafts.putIfAbsent(draft.itemKey().trim().toUpperCase(), draft);
            }
        }
        // Only keys from this participant's own list; anything else is dropped.
        List<BlockerItem> merged = ranked.stream().map(item -> merge(item, drafts.get(key(item)))).toList();
        return new BlockerAnalysis(merged, blocked, waiting, gaps, heldUp, limit(answer.value().pattern(), 400), true,
                answer.usage().getId());
    }

    private record Snapshot(String dreamText, List<BlockerItem> items, java.util.Set<Long> heldUpStepIds) {
    }

    private Snapshot collect(AppUser user, Dream dream, LocalDate today) {
        List<BlockerItem> items = new ArrayList<>();
        java.util.Set<Long> heldUp = new java.util.HashSet<>();
        for (Goal goal : goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), user.getId())) {
            if (goal.getStatus() == WorkStatus.COMPLETED) {
                continue;
            }
            List<VisionStep> steps = stepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), user.getId());
            boolean goalSoon = goal.getTargetDate() != null && !goal.getTargetDate().isAfter(today.plusDays(AT_RISK_DAYS));
            for (VisionStep step : steps) {
                if (step.getStatus() == WorkStatus.COMPLETED) {
                    continue;
                }
                List<VisionStep> later = laterOpenSteps(step, steps);
                Obstacle stepObstacle = openObstacle(obstacleRepository.findByRelatedStep_Id(step.getId()));
                boolean stepHelper = partnerRepository.existsByUser_IdAndRelatedStep_IdAndArchivedFalse(user.getId(), step.getId());
                boolean stepAdded = false;

                if (step.getStatus() == WorkStatus.BLOCKED || step.getStatus() == WorkStatus.WAITING) {
                    items.add(stepItem(step, goal, step.getStatus().name(), later.size(), goalSoon, stepObstacle, stepHelper, today));
                    stepAdded = true;
                } else if (planService.gapNeedsHelp(step, steps).isPresent()) {
                    items.add(stepItem(step, goal, "GAP", later.size(), goalSoon, stepObstacle, stepHelper, today));
                    stepAdded = true;
                }

                List<TaskItem> stuck = taskRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), user.getId()).stream()
                        .filter(task -> task.getStatus() == WorkStatus.BLOCKED || task.getStatus() == WorkStatus.WAITING)
                        .toList();
                Map<Long, CycleTimeCalculator.Delays> delays = cycleTime.delays(user.getId(), stuck);
                for (TaskItem task : stuck) {
                    Obstacle taskObstacle = openObstacle(obstacleRepository.findByRelatedTask_Id(task.getId()));
                    boolean taskHelper = stepHelper
                            || partnerRepository.existsByUser_IdAndRelatedTask_IdAndArchivedFalse(user.getId(), task.getId());
                    items.add(taskItem(task, step, goal, later.size(), goalSoon,
                            taskObstacle != null ? taskObstacle : stepObstacle, taskHelper,
                            delays.getOrDefault(task.getId(), CycleTimeCalculator.Delays.NONE), today));
                }
                if (stepAdded || !stuck.isEmpty()) {
                    later.forEach(other -> heldUp.add(other.getId()));
                }
            }
        }
        return new Snapshot(planService.describeDream(user, dream), items, heldUp);
    }

    /** Unfinished steps later in the goal's order: the ones that wait on this step. */
    private static List<VisionStep> laterOpenSteps(VisionStep step, List<VisionStep> steps) {
        int position = step.getSequenceNumber() == null ? Integer.MAX_VALUE : step.getSequenceNumber();
        return steps.stream()
                .filter(other -> !other.getId().equals(step.getId()) && other.getStatus() != WorkStatus.COMPLETED
                        && other.getSequenceNumber() != null && other.getSequenceNumber() > position)
                .toList();
    }

    @Nullable
    private static Obstacle openObstacle(List<Obstacle> obstacles) {
        return obstacles.stream()
                .filter(obstacle -> !obstacle.isArchived()
                        && (obstacle.getStatus() == ObstacleStatus.OPEN || obstacle.getStatus() == ObstacleStatus.IN_PROGRESS))
                .findFirst()
                .orElse(null);
    }

    private static BlockerItem stepItem(VisionStep step, Goal goal, String kind, int heldUp, boolean goalSoon,
                                        @Nullable Obstacle obstacle, boolean hasHelper, LocalDate today) {
        ObstacleType type = obstacle != null ? obstacle.getObstacleType() : step.getGapKind();
        boolean overdue = step.getTargetDate() != null && step.getTargetDate().isBefore(today);
        String reason = obstacle != null ? firstNonBlank(obstacle.getTitle(), obstacle.getDescription()) : null;
        int score = score(kind, step.getPriority(), heldUp, goalSoon, overdue, null, 0, obstacle != null || hasHelper);
        return new BlockerItem("STEP", step.getId(), step.getCode(), step.getTitle(), kind, goal.getId(), goal.getTitle(),
                step.getId(), step.getTitle(), reason, step.getPriority(), step.getTargetDate(), overdue, null, 0, heldUp,
                goalSoon, type == null ? null : type.name(), false, obstacle == null ? null : obstacle.getId(), hasHelper,
                score, null, null, null);
    }

    private static BlockerItem taskItem(TaskItem task, VisionStep step, Goal goal, int heldUp, boolean goalSoon,
                                        @Nullable Obstacle obstacle, boolean hasHelper, CycleTimeCalculator.Delays delay,
                                        LocalDate today) {
        String kind = task.getStatus().name();
        BigDecimal days = task.getStatus() == WorkStatus.BLOCKED ? delay.blockedDays() : delay.waitingDays();
        boolean overdue = task.getDueDate() != null && task.getDueDate().isBefore(today);
        ObstacleType type = obstacle != null ? obstacle.getObstacleType() : step.getGapKind();
        int score = score(kind, task.getPriority(), heldUp, goalSoon, overdue, days, task.getBlockedCount(),
                obstacle != null || hasHelper);
        return new BlockerItem("TASK", task.getId(), task.getCode(), task.getTitle(), kind, goal.getId(), goal.getTitle(),
                step.getId(), step.getTitle(), firstNonBlank(task.getBlockerReason(), obstacle == null ? null : obstacle.getTitle()),
                task.getPriority(), task.getDueDate(), overdue, days, task.getBlockedCount(), heldUp, goalSoon,
                type == null ? null : type.name(), false, obstacle == null ? null : obstacle.getId(), hasHelper, score,
                null, null, null);
    }

    /**
     * Impact, highest first: how bad the hold-up is (Blocked over Waiting over a
     * gap), how important the work is, how much later work waits on it, a goal
     * date close by, lateness, days stuck, repeat blocks, and no help yet.
     */
    static int score(String kind, @Nullable Priority priority, int heldUp, boolean goalSoon, boolean overdue,
                     @Nullable BigDecimal daysStuck, int timesBlocked, boolean helpInPlace) {
        int score = switch (kind) {
            case "BLOCKED" -> 6;
            case "WAITING" -> 3;
            default -> 2;
        };
        score += 2 * switch (priority == null ? Priority.MEDIUM : priority) {
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
        score += 2 * Math.min(heldUp, 5);
        score += goalSoon ? 4 : 0;
        score += overdue ? 3 : 0;
        score += daysStuck == null ? 0 : Math.min(daysStuck.intValue(), 30) / 5;
        score += Math.min(Math.max(timesBlocked - 1, 0), 3);
        score += helpInPlace ? 0 : 2;
        return score;
    }

    private static String describe(List<BlockerItem> items) {
        StringBuilder out = new StringBuilder();
        for (BlockerItem item : items) {
            out.append('[').append(key(item)).append("] ").append(item.kind()).append(' ')
                    .append(item.itemType().toLowerCase()).append(": ").append(clip(item.title()))
                    .append(" | goal: ").append(clip(item.goalTitle()));
            if ("TASK".equals(item.itemType())) {
                out.append(" | step: ").append(clip(item.stepTitle()));
            }
            if (item.reason() != null) {
                out.append(" | what blocks it: ").append(clip(item.reason()));
            }
            if (item.obstacleType() != null) {
                out.append(" | missing: ").append(item.obstacleType());
            }
            if (item.daysStuck() != null && item.daysStuck().signum() > 0) {
                out.append(" | stuck ").append(item.daysStuck()).append(" days");
            }
            if (item.timesBlocked() > 1) {
                out.append(" | blocked ").append(item.timesBlocked()).append(" times");
            }
            out.append(" | holds up ").append(item.heldUpSteps()).append(" later step(s)");
            if (item.goalAtRisk()) {
                out.append(" | goal date is close");
            }
            out.append(item.hasHelper() ? " | a helper is linked" : " | no helper linked").append('\n');
        }
        return out.toString();
    }

    private static BlockerItem merge(BlockerItem item, @Nullable AiDrafts.BlockerDraft draft) {
        if (draft == null) {
            return item;
        }
        String type = item.obstacleType();
        boolean guessed = false;
        if (type == null) {
            type = obstacleType(draft.obstacleType());
            guessed = type != null;
        }
        return new BlockerItem(item.itemType(), item.itemId(), item.code(), item.title(), item.kind(), item.goalId(),
                item.goalTitle(), item.stepId(), item.stepTitle(), item.reason(), item.priority(), item.dueDate(), item.overdue(),
                item.daysStuck(), item.timesBlocked(), item.heldUpSteps(), item.goalAtRisk(), type, guessed, item.obstacleId(),
                item.hasHelper(), item.score(), limit(draft.rootCause(), 300), limit(draft.firstMove(), 300),
                limit(draft.helper(), 200));
    }

    @Nullable
    private static String obstacleType(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return ObstacleType.valueOf(value.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String key(BlockerItem item) {
        return item.itemType() + "-" + item.itemId();
    }

    private static int count(List<BlockerItem> items, String kind) {
        return (int) items.stream().filter(item -> kind.equals(item.kind())).count();
    }

    @Nullable
    private static String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    @Nullable
    private static String limit(@Nullable String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
