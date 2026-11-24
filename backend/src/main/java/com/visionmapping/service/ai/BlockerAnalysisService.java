package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.response.AiPlanResponses.BlockerAnalysis;
import com.visionmapping.dto.response.AiPlanResponses.BlockerCandidate;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    /** BR-28-10: possibly-blocked cards shown, rule and AI together. */
    static final int MAX_CANDIDATES = 10;
    /** BA-FR-15: open items AI may add beyond the rule signals. */
    static final int MAX_AI_CANDIDATES = 5;
    /** Open items listed for AI to read, to keep the request small. */
    static final int MAX_OPEN_FOR_AI = 40;
    /** §3.3 signals. */
    static final int OVERDUE_DAYS = 3;
    static final int NO_CHANGE_DAYS = 7;
    static final int STALLED_DAYS = 21;
    static final BigDecimal STALLED_PROGRESS = BigDecimal.valueOf(50);
    /** The strongest signal first. */
    static final List<String> SIGNAL_ORDER = List.of("KEEPS_SLIPPING", "OVERDUE_NO_CHANGE", "STALLED", "SOMETHING_MISSING", "AI");
    /** §3.3: resources that say something is still missing or needed, in English or Khmer. */
    static final java.util.regex.Pattern MISSING_WORDS = java.util.regex.Pattern.compile(
            "(?i)\\b(need|needs|needed|missing|lack|lacking|don't have|do not have|waiting for|no budget|no money|not yet)\\b|ត្រូវការ|ខ្វះ");

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
        List<BlockerCandidate> candidates = snapshot.candidates().stream()
                .sorted(Comparator.comparingInt((BlockerCandidate candidate) -> SIGNAL_ORDER.indexOf(candidate.signal()))
                        .thenComparing(candidate -> candidate.daysOverdue() == null ? 0 : -candidate.daysOverdue())
                        .thenComparing(BlockerCandidate::itemId))
                .limit(MAX_CANDIDATES)
                .toList();
        int blocked = count(snapshot.items(), "BLOCKED");
        int waiting = count(snapshot.items(), "WAITING");
        int gaps = count(snapshot.items(), "GAP");
        int heldUp = snapshot.heldUpStepIds().size();
        if (ranked.isEmpty() && candidates.isEmpty() && snapshot.openItems().isEmpty()) {
            return new BlockerAnalysis(ranked, 0, 0, 0, 0, null, false, null, candidates);
        }

        Optional<String> apiKey = accessService.availableKey(user);
        // The rule half still answers when AI is off or the session's limit is reached.
        if (apiKey.isEmpty() || !sessionGuard.tryUse(user.getId(), dreamId)) {
            return new BlockerAnalysis(ranked, blocked, waiting, gaps, heldUp, null, false, null, candidates);
        }
        List<BlockerItem> forAi = ranked.stream().limit(MAX_AI_BLOCKERS).toList();
        String records = snapshot.dreamText()
                + "\nWhat is holding this dream back, biggest impact first:\n" + (forAi.isEmpty() ? "None marked.\n" : describe(forAi))
                + "\nPossibly blocked (not marked):\n" + (candidates.isEmpty() ? "None found by the rules.\n" : describeCandidates(candidates))
                + "\nOther open items:\n" + describeOpen(snapshot.openItems(), candidates);
        String prompt = "Act as a calm, practical coach. For each blocker listed, use its key in brackets and say what is "
                + "really missing (one obstacle type), the likely root cause in one kind sentence without blame, one concrete "
                + "first move that could start within a day, and the kind of person or resource that could help, as a role. "
                + "Where the participant already named the blocker, build on what they wrote. Then, only when several blockers "
                + "share a cause, say what they have in common. Do not invent blockers that are not listed. "
                + "Under possiblyBlocked, for each possibly blocked item that really looks blocked, and for at most "
                + MAX_AI_CANDIDATES + " other open items that clearly look blocked from what the participant wrote, give its key, "
                + "what is likely missing and a one-sentence reason. Leave out anything that is simply not started yet.";
        AiStructuredRequests.Tracked<AiDrafts.BlockerReview> answer;
        try {
            answer = requests.askTracked(user, apiKey.get(), FEATURE, language, records, prompt, AiDrafts.BlockerReview.class);
        } catch (AiUnavailableException exception) {
            return new BlockerAnalysis(ranked, blocked, waiting, gaps, heldUp, null, false, null, candidates);
        }
        Map<String, AiDrafts.BlockerDraft> drafts = new HashMap<>();
        for (AiDrafts.BlockerDraft draft : Optional.ofNullable(answer.value().blockers()).orElse(List.of())) {
            if (draft.itemKey() != null) {
                drafts.putIfAbsent(draft.itemKey().trim().toUpperCase(), draft);
            }
        }
        // Only keys from this participant's own list; anything else is dropped.
        List<BlockerItem> merged = ranked.stream().map(item -> merge(item, drafts.get(key(item)))).toList();
        List<BlockerCandidate> mergedCandidates = mergeCandidates(candidates, snapshot.openItems(),
                Optional.ofNullable(answer.value().possiblyBlocked()).orElse(List.of()));
        return new BlockerAnalysis(merged, blocked, waiting, gaps, heldUp, limit(answer.value().pattern(), 400), true,
                answer.usage().getId(), mergedCandidates);
    }

    private record Snapshot(String dreamText, List<BlockerItem> items, java.util.Set<Long> heldUpStepIds,
                            List<BlockerCandidate> candidates, Map<String, BlockerCandidate> openItems) {
    }

    private Snapshot collect(AppUser user, Dream dream, LocalDate today) {
        List<BlockerItem> items = new ArrayList<>();
        java.util.Set<Long> heldUp = new java.util.HashSet<>();
        List<BlockerCandidate> candidates = new ArrayList<>();
        // Every open, unmarked item, as a candidate shell AI may pick (BR-28-5: no other keys are accepted).
        Map<String, BlockerCandidate> openItems = new java.util.LinkedHashMap<>();
        Instant now = Instant.now(clock);
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
                } else if (unmarked(step.getStatus())) {
                    BlockerCandidate shell = stepCandidate(step, goal, "AI", today);
                    openItems.put(candidateKey(shell), shell);
                    stepSignal(step, today).map(signal -> stepCandidate(step, goal, signal, today)).ifPresent(candidates::add);
                }

                List<TaskItem> tasks = taskRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), user.getId());
                List<TaskItem> stuck = tasks.stream()
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
                for (TaskItem task : tasks) {
                    if (!unmarked(task.getStatus())) {
                        continue;
                    }
                    BlockerCandidate shell = taskCandidate(task, step, goal, "AI", today, now);
                    openItems.put(candidateKey(shell), shell);
                    taskSignal(task, today, now).map(signal -> taskCandidate(task, step, goal, signal, today, now))
                            .ifPresent(candidates::add);
                }
                if (stepAdded || !stuck.isEmpty()) {
                    later.forEach(other -> heldUp.add(other.getId()));
                }
            }
        }
        return new Snapshot(planService.describeDream(user, dream), items, heldUp, candidates, openItems);
    }

    /** Open work that is not marked Blocked, Waiting or Paused: the only work a possibly-blocked signal applies to. */
    private static boolean unmarked(WorkStatus status) {
        return status == WorkStatus.NOT_STARTED || status == WorkStatus.IN_PROGRESS;
    }

    /** §3.3: the strongest step signal, keeps slipping before something missing. */
    static Optional<String> stepSignal(VisionStep step, LocalDate today) {
        if (step.getOverdueReschedules() >= 1 && step.getTargetDate() != null && step.getTargetDate().isBefore(today)) {
            return Optional.of("KEEPS_SLIPPING");
        }
        if (step.getResources() != null && MISSING_WORDS.matcher(step.getResources()).find()) {
            return Optional.of("SOMETHING_MISSING");
        }
        return Optional.empty();
    }

    /** §3.3: overdue with no change before stalled. */
    static Optional<String> taskSignal(TaskItem task, LocalDate today, Instant now) {
        if (task.getDueDate() != null && !task.getDueDate().isAfter(today.minusDays(OVERDUE_DAYS))
                && task.getUpdatedAt() != null && !task.getUpdatedAt().isAfter(now.minus(Duration.ofDays(NO_CHANGE_DAYS)))) {
            return Optional.of("OVERDUE_NO_CHANGE");
        }
        if (task.getStatus() == WorkStatus.IN_PROGRESS && task.getStartedAt() != null
                && !task.getStartedAt().isAfter(now.minus(Duration.ofDays(STALLED_DAYS)))
                && task.getProgressPercent() != null && task.getProgressPercent().compareTo(STALLED_PROGRESS) < 0) {
            return Optional.of("STALLED");
        }
        return Optional.empty();
    }

    private static BlockerCandidate stepCandidate(VisionStep step, Goal goal, String signal, LocalDate today) {
        Integer overdue = step.getTargetDate() != null && step.getTargetDate().isBefore(today)
                ? (int) ChronoUnit.DAYS.between(step.getTargetDate(), today) : null;
        return new BlockerCandidate("STEP", step.getId(), step.getCode(), step.getTitle(), goal.getId(), goal.getTitle(),
                step.getId(), step.getTitle(), signal, overdue, null, null, step.getProgressPercent(),
                step.getOverdueReschedules(), limit(step.getResources(), 300), null, null, false);
    }

    private static BlockerCandidate taskCandidate(TaskItem task, VisionStep step, Goal goal, String signal, LocalDate today,
                                                  Instant now) {
        Integer overdue = task.getDueDate() != null && task.getDueDate().isBefore(today)
                ? (int) ChronoUnit.DAYS.between(task.getDueDate(), today) : null;
        Integer sinceChange = task.getUpdatedAt() == null ? null : (int) Duration.between(task.getUpdatedAt(), now).toDays();
        Integer inProgress = task.getStatus() == WorkStatus.IN_PROGRESS && task.getStartedAt() != null
                ? (int) Duration.between(task.getStartedAt(), now).toDays() : null;
        return new BlockerCandidate("TASK", task.getId(), task.getCode(), task.getTitle(), goal.getId(), goal.getTitle(),
                step.getId(), step.getTitle(), signal, overdue, sinceChange, inProgress, task.getProgressPercent(), 0, null,
                null, null, false);
    }

    /** AI reasons for the rule candidates, then up to MAX_AI_CANDIDATES more open items, within MAX_CANDIDATES in all. */
    private static List<BlockerCandidate> mergeCandidates(List<BlockerCandidate> candidates, Map<String, BlockerCandidate> openItems,
                                                          List<AiDrafts.HiddenBlockerDraft> drafts) {
        Map<String, AiDrafts.HiddenBlockerDraft> byKey = new java.util.LinkedHashMap<>();
        for (AiDrafts.HiddenBlockerDraft draft : drafts) {
            if (draft.itemKey() != null && openItems.containsKey(draft.itemKey().trim().toUpperCase())) {
                byKey.putIfAbsent(draft.itemKey().trim().toUpperCase(), draft);
            }
        }
        List<BlockerCandidate> merged = new ArrayList<>();
        for (BlockerCandidate candidate : candidates) {
            AiDrafts.HiddenBlockerDraft draft = byKey.remove(candidateKey(candidate));
            merged.add(draft == null ? candidate : withAi(candidate, draft));
        }
        int added = 0;
        for (Map.Entry<String, AiDrafts.HiddenBlockerDraft> entry : byKey.entrySet()) {
            if (added >= MAX_AI_CANDIDATES || merged.size() >= MAX_CANDIDATES || blank(entry.getValue().reason())) {
                continue;
            }
            merged.add(withAi(openItems.get(entry.getKey()), entry.getValue()));
            added++;
        }
        return merged;
    }

    private static BlockerCandidate withAi(BlockerCandidate candidate, AiDrafts.HiddenBlockerDraft draft) {
        return new BlockerCandidate(candidate.itemType(), candidate.itemId(), candidate.code(), candidate.title(),
                candidate.goalId(), candidate.goalTitle(), candidate.stepId(), candidate.stepTitle(), candidate.signal(),
                candidate.daysOverdue(), candidate.daysSinceChange(), candidate.daysInProgress(), candidate.progressPercent(),
                candidate.reschedules(), candidate.resources(), obstacleType(draft.obstacleType()), limit(draft.reason(), 300),
                true);
    }

    private static String describeCandidates(List<BlockerCandidate> candidates) {
        StringBuilder out = new StringBuilder();
        for (BlockerCandidate candidate : candidates) {
            out.append('[').append(candidateKey(candidate)).append("] ").append(candidate.signal()).append(' ')
                    .append(candidate.itemType().toLowerCase()).append(": ").append(clip(candidate.title()))
                    .append(" | goal: ").append(clip(candidate.goalTitle()));
            if (candidate.daysOverdue() != null) {
                out.append(" | overdue ").append(candidate.daysOverdue()).append(" days");
            }
            if (candidate.daysSinceChange() != null) {
                out.append(" | no change for ").append(candidate.daysSinceChange()).append(" days");
            }
            if (candidate.daysInProgress() != null) {
                out.append(" | in progress ").append(candidate.daysInProgress()).append(" days");
            }
            if (candidate.resources() != null) {
                out.append(" | resources: ").append(clip(candidate.resources()));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** The open items AI may also flag, capped so the request stays small. */
    private static String describeOpen(Map<String, BlockerCandidate> openItems, List<BlockerCandidate> candidates) {
        java.util.Set<String> listed = new java.util.HashSet<>();
        candidates.forEach(candidate -> listed.add(candidateKey(candidate)));
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (BlockerCandidate item : openItems.values()) {
            if (listed.contains(candidateKey(item)) || shown >= MAX_OPEN_FOR_AI) {
                continue;
            }
            out.append('[').append(candidateKey(item)).append("] ").append(item.itemType().toLowerCase()).append(": ")
                    .append(clip(item.title())).append(" | goal: ").append(clip(item.goalTitle()));
            if (item.daysOverdue() != null) {
                out.append(" | overdue ").append(item.daysOverdue()).append(" days");
            }
            if (item.resources() != null) {
                out.append(" | resources: ").append(clip(item.resources()));
            }
            out.append('\n');
            shown++;
        }
        return shown == 0 ? "None.\n" : out.toString();
    }

    private static String candidateKey(BlockerCandidate candidate) {
        return candidate.itemType() + "-" + candidate.itemId();
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
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
