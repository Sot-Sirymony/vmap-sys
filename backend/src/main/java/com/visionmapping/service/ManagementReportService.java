package com.visionmapping.service;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.AttentionItem;
import com.visionmapping.dto.response.ManagementReportResponse.GoalHealthRow;
import com.visionmapping.dto.response.ManagementReportResponse.HealthReason;
import com.visionmapping.dto.response.ManagementReportResponse.Headline;
import com.visionmapping.dto.response.ManagementReportResponse.PriorityCount;
import com.visionmapping.dto.response.ManagementReportResponse.Scope;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.report.ManagementReport;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.DreamInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.PeriodFigures;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ManagementReportDeckWriter;
import com.visionmapping.service.report.ManagementReportExcelWriter;
import com.visionmapping.service.report.ReportComparison;
import com.visionmapping.service.report.ReportExtras;
import com.visionmapping.service.report.ReportFilters;
import com.visionmapping.service.report.ReportPeriod;
import com.visionmapping.service.report.ReportRows;
import com.visionmapping.service.report.ReportRules;
import com.visionmapping.service.report.ReportRules.Assessment;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.service.report.ReportText;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V18 (BRD 18.0.0): the Management Report. One model of the participant's own
 * plan in scope (all dreams, one vision area or one dream) feeds the Summary
 * tab, the Excel report and the deck (MR-NFR-03). Read-only and rule-based: no
 * AI (MR-NFR-10). Loaded with a fixed number of queries, never one per row
 * (MR-NFR-04).
 *
 * <p>V19 (BRD 19.0.0): a reporting period with its own figures (RM-FR-06 to
 * RM-FR-09), health reasons (RM-FR-01) and "Done – open work" (RM-FR-02). The
 * model now holds plain values, never entities (RM-NFR-02).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ManagementReportService {

    static final String ONE_SCOPE = "Choose either a vision area or a dream, not both.";
    private static final int MAX_ATTENTION = 10;
    private static final Set<ObstacleType> HELP_GAP_KINDS = EnumSet.of(ObstacleType.KNOWLEDGE, ObstacleType.SKILL, ObstacleType.MONEY);

    private final EntityLookup lookup;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final ObstacleRepository obstacleRepository;
    private final PartnerRepository partnerRepository;
    private final ProgressLogRepository progressLogRepository;
    private final Clock clock;

    /**
     * What a report covers: at most one of areaId and dreamId (neither means
     * all dreams), and a period preset or custom from and to dates (RM-FR-06).
     */
    public record ReportScope(@Nullable Long areaId, @Nullable Long dreamId, @Nullable String period, @Nullable LocalDate from,
                              @Nullable LocalDate to, ReportFilters filters) {

        public ReportScope {
            filters = filters == null ? ReportFilters.NONE : filters;
        }

        public ReportScope(@Nullable Long areaId, @Nullable Long dreamId, @Nullable String period, @Nullable LocalDate from,
                           @Nullable LocalDate to) {
            this(areaId, dreamId, period, from, to, ReportFilters.NONE);
        }
    }

    public ManagementReportResponse summary(ReportScope scope) {
        return live(scope).summary();
    }

    /** V19: a report model and its Summary, built together so they always agree (MR-NFR-03). */
    public record Built(ManagementReport report, ManagementReportResponse summary) {
    }

    /** The live model and Summary (with the trend) for a scope; what a draft shows and what Finalise freezes. */
    public Built live(ReportScope scope) {
        ManagementReport report = build(scope);
        return new Built(report, toResponse(report, trend(report)));
    }

    /**
     * V19 RM-FR-13 to RM-FR-15: the Details tab's rows, made by the same code
     * as the Excel Report sheet, so both always list the same rows.
     */
    public List<ReportRows.Row> rows(ManagementReport report, @Nullable String language) {
        List<ReportRows.Row> rows = ReportRows.build(report, ReportText.of(language));
        if (rows.size() > ManagementReportExcelWriter.MAX_ROWS) {
            throw new BusinessRuleException(ManagementReportExcelWriter.TOO_MANY_ROWS);
        }
        return rows;
    }

    /**
     * MR-FR-14: the dashboard's twelve-week trend (DashboardTrend), fed only
     * with the progress logs of the tasks in scope, so both mean "average task
     * progress". Only the Summary needs it; the files skip this query.
     */
    private List<DashboardSummaryResponse.TrendPoint> trend(ManagementReport report) {
        Set<Long> taskIds = report.tasks().map(task -> task.task().id()).collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = report.asOf();
        List<ProgressLog> logs = progressLogRepository.findTrendWindow(lookup.userId(),
                        DashboardTrend.start(today).atStartOfDay(clock.getZone()).toInstant()).stream()
                .filter(log -> log.getRelatedTask() != null && taskIds.contains(log.getRelatedTask().getId()))
                .toList();
        return DashboardTrend.build(logs, today, clock.getZone());
    }

    /**
     * A generated file and the name to download it as. A record's own equals,
     * hashCode and toString would compare and print the array's identity, so
     * they use its contents (and toString only its size, never the bytes).
     */
    public record ReportFile(String fileName, byte[] content) {

        @Override
        public boolean equals(Object other) {
            return other instanceof ReportFile file && fileName.equals(file.fileName) && Arrays.equals(content, file.content);
        }

        @Override
        public int hashCode() {
            return 31 * fileName.hashCode() + Arrays.hashCode(content);
        }

        @Override
        public String toString() {
            return "ReportFile[fileName=" + fileName + ", size=" + content.length + "]";
        }
    }

    /** V18 MR-FR-15 to MR-FR-22: the Excel report, built from the same model as the Summary. */
    public ReportFile excel(ReportScope scope, @Nullable String language) {
        ManagementReport report = build(scope);
        return excel(report, toResponse(report), language, fileName(report, "xlsx"));
    }

    /** The Excel file for a given model and Summary: live, or frozen in a final report (RM-FR-20). */
    public ReportFile excel(ManagementReport report, ManagementReportResponse summary, @Nullable String language, String fileName) {
        return excel(report, summary, language, fileName, ReportExtras.NONE);
    }

    /** V19 phase 3: with the commentary, the chosen sections and the draft mark (RM-FR-39, RM-FR-40, RM-FR-43). */
    public ReportFile excel(ManagementReport report, ManagementReportResponse summary, @Nullable String language, String fileName,
                            ReportExtras extras) {
        return new ReportFile(fileName, ManagementReportExcelWriter.write(report, summary, ReportText.of(language), extras));
    }

    /**
     * V18 MR-FR-23 to MR-FR-31: the PowerPoint deck. The title defaults to
     * "Progress report"; the participant's name is on the title slide unless
     * they leave it out (Q-05).
     */
    public ReportFile deck(ReportScope scope, @Nullable String language, @Nullable String title, boolean includeName) {
        ManagementReport report = build(scope);
        return deck(report, toResponse(report), language, title, includeName, fileName(report, "pptx"));
    }

    /** The deck for a given model and Summary: live, or frozen in a final report (RM-FR-20). */
    public ReportFile deck(ManagementReport report, ManagementReportResponse summary, @Nullable String language, @Nullable String title,
                           boolean includeName, String fileName) {
        return deck(report, summary, language, title, includeName, fileName, ReportExtras.NONE);
    }

    /** V19 phase 3: with the commentary, the chosen sections and the draft mark (RM-FR-39, RM-FR-41, RM-FR-43). */
    public ReportFile deck(ManagementReport report, ManagementReportResponse summary, @Nullable String language, @Nullable String title,
                           boolean includeName, String fileName, ReportExtras extras) {
        String presenter = includeName ? lookup.currentUser().getFullName() : null;
        return new ReportFile(fileName, ManagementReportDeckWriter.write(report, summary, ReportText.of(language), title, presenter, extras));
    }

    /**
     * V19 RM-FR-32 to RM-FR-37: the Summary compared with an earlier final
     * report. The comparison itself never queries the plan; one query here
     * says why goals of the earlier report left the scope (RM-FR-34).
     */
    public ManagementReportResponse compared(Built now, ReportComparison.Earlier earlier) {
        Set<Long> current = now.summary().goals().stream().map(GoalHealthRow::goalId).collect(Collectors.toSet());
        List<Long> gone = earlier.summary().goals().stream().map(GoalHealthRow::goalId).filter(id -> !current.contains(id)).toList();
        return now.summary().withComparison(ReportComparison.compare(earlier, now.report(), now.summary(), removedReasons(gone, now.report())));
    }

    /**
     * Archived (or its dream archived): COMPLETED_ARCHIVED when it was
     * Completed, else ARCHIVED. Still active but under a dream outside the
     * scope: MOVED. Otherwise not known (for example a filter left it out).
     */
    private Map<Long, String> removedReasons(List<Long> goalIds, ManagementReport report) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        Long userId = lookup.userId();
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (Goal goal : goalRepository.findAllById(goalIds)) {
            if (!goal.getUser().getId().equals(userId)) {
                continue;
            }
            Dream dream = goal.getDream();
            if (goal.isArchived() || dream.isArchived()) {
                reasons.put(goal.getId(), goal.getStatus() == WorkStatus.COMPLETED ? "COMPLETED_ARCHIVED" : "ARCHIVED");
            } else if ("DREAM".equals(report.scopeKind()) && !dream.getId().equals(report.dreamId())
                    || "AREA".equals(report.scopeKind()) && !Objects.equals(dream.visionAreaId(), report.areaId())) {
                reasons.put(goal.getId(), "MOVED");
            }
        }
        return reasons;
    }

    /**
     * "Management_Report_Dream_12_2026-11-16.xlsx": built from the scope's kind
     * and id, never from free text, so it is always a safe file name.
     */
    public static String fileName(ManagementReport report, String extension) {
        String scope = switch (report.scopeKind()) {
            case "DREAM" -> "Dream_" + report.dreamId();
            case "AREA" -> "Area_" + report.areaId();
            default -> "All_dreams";
        };
        return "Management_Report_" + scope + "_" + report.asOf() + "." + extension;
    }

    /** The plan's entities in scope, before they are copied into the report model. */
    private record Loaded(List<Goal> goals, Map<Long, Dream> dreamById, Map<Long, List<VisionStep>> stepsByGoal,
                          Map<Long, List<TaskItem>> tasksByStep, List<Obstacle> obstacles, Set<Long> stepIds) {

        Loaded {
            goals = Copies.list(goals);
            dreamById = Copies.map(dreamById);
            stepsByGoal = Copies.map(stepsByGoal);
            tasksByStep = Copies.map(tasksByStep);
            obstacles = Copies.list(obstacles);
            stepIds = Copies.set(stepIds);
        }
    }

    /**
     * MR-FR-03, MR-NFR-01: the participant's unarchived plan under the scope.
     * Another user's area or dream answers 404 through the lookup.
     */
    public ManagementReport build(ReportScope scope) {
        if (scope.areaId() != null && scope.dreamId() != null) {
            throw new BusinessRuleException(ONE_SCOPE);
        }
        LocalDate today = LocalDate.now(clock);
        ReportPeriod period = ReportPeriod.resolve(scope.period(), scope.from(), scope.to(), today);
        Long userId = lookup.userId();
        Dreams dreams = dreams(scope, userId);
        String kind = dreams.kind();
        Loaded loaded = load(userId, dreams.dreams());
        Blockers sorted = sortBlockers(loaded, kind);
        Map<Long, List<BlockerInfo>> openByTask = sorted.openByTask();
        Map<Long, List<BlockerInfo>> openByStep = sorted.openByStep();
        Set<Long> stepsWithPartner = partnerRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .map(Partner::getRelatedStep)
                .filter(Objects::nonNull)
                .map(VisionStep::getId)
                .collect(Collectors.toSet());

        List<GoalNode> all = new ArrayList<>();
        for (Goal goal : loaded.goals()) {
            List<VisionStep> goalSteps = loaded.stepsByGoal().getOrDefault(goal.getId(), List.of());
            List<StepNode> stepNodes = goalSteps.stream()
                    .map(step -> new StepNode(step(step),
                            loaded.tasksByStep().getOrDefault(step.getId(), List.of()).stream()
                                    .map(task -> new TaskNode(task(task), openByTask.getOrDefault(task.getId(), List.of())))
                                    .toList(),
                            openByStep.getOrDefault(step.getId(), List.of()),
                            gapNeedsHelp(step, goalSteps, stepsWithPartner, openByStep, openByTask, loaded.tasksByStep())))
                    .toList();
            Dream dream = loaded.dreamById().get(goal.getDream().getId());
            all.add(GoalNode.assessed(goal(goal), new DreamInfo(dream.getId(), dream.getTitle()), stepNodes, today));
        }
        List<TaskItem> allTasks = loaded.tasksByStep().values().stream().flatMap(List::stream).toList();
        List<String> owners = allTasks.stream().map(TaskItem::getOwner).filter(owner -> owner != null && !owner.isBlank())
                .map(String::strip).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        // RM-FR-11: the period figures count only what the filters keep, like the headline.
        List<GoalNode> nodes = scope.filters().apply(all);
        Set<Long> keptTasks = nodes.stream().flatMap(GoalNode::tasks).map(task -> task.task().id()).collect(Collectors.toSet());
        Set<Long> keptSteps = nodes.stream().flatMap(node -> node.steps().stream()).map(step -> step.step().id()).collect(Collectors.toSet());
        List<TaskItem> tasks = allTasks.stream().filter(task -> keptTasks.contains(task.getId())).toList();
        List<Obstacle> placed = sorted.placed().stream()
                .filter(obstacle -> obstacle.getRelatedTask() != null ? keptTasks.contains(obstacle.getRelatedTask().getId())
                        : keptSteps.contains(obstacle.getRelatedStep().getId()))
                .toList();
        PeriodFigures figures = periodFigures(userId, period, today, tasks, placed);
        return new ManagementReport(kind, scope.areaId(), scope.dreamId(), dreams.label(), today, period, nodes, sorted.unplaced(),
                figures, scope.filters(), owners);
    }

    /** The scope's kind (ALL, AREA or DREAM), its label, and its unarchived dreams. */
    private record Dreams(String kind, @Nullable String label, List<Dream> dreams) {

        Dreams {
            dreams = Copies.list(dreams);
        }
    }

    private Dreams dreams(ReportScope scope, Long userId) {
        if (scope.dreamId() != null) {
            Dream dream = lookup.dream(scope.dreamId());
            return new Dreams("DREAM", dream.getTitle(), dream.isArchived() ? List.of() : List.of(dream));
        }
        if (scope.areaId() != null) {
            return new Dreams("AREA", lookup.visionArea(scope.areaId()).getName(),
                    dreamRepository.findByVisionArea_IdAndUser_IdAndArchivedFalse(scope.areaId(), userId));
        }
        return new Dreams("ALL", null, dreamRepository.findByUser_IdAndArchivedFalse(userId));
    }

    /**
     * Obstacles sorted by where they sit: open ones on tasks and steps in
     * scope, every placed one (for the period figures), and the active ones
     * in scope that are not on a step yet.
     */
    private record Blockers(Map<Long, List<BlockerInfo>> openByTask, Map<Long, List<BlockerInfo>> openByStep,
                            List<Obstacle> placed, List<BlockerInfo> unplaced) {

        Blockers {
            openByTask = Copies.map(openByTask);
            openByStep = Copies.map(openByStep);
            placed = Copies.list(placed);
            unplaced = Copies.list(unplaced);
        }
    }

    private Blockers sortBlockers(Loaded loaded, String kind) {
        Map<Long, List<BlockerInfo>> openByTask = new LinkedHashMap<>();
        Map<Long, List<BlockerInfo>> openByStep = new LinkedHashMap<>();
        List<Obstacle> placed = new ArrayList<>();
        List<BlockerInfo> unplaced = new ArrayList<>();
        for (Obstacle obstacle : loaded.obstacles()) {
            Long stepId = placedStepId(obstacle, loaded.stepIds());
            if (stepId == null) {
                if (ObstacleService.needsPlacing(obstacle) && inScope(obstacle, kind, loaded.dreamById())) {
                    unplaced.add(blocker(obstacle));
                }
                continue;
            }
            placed.add(obstacle);
            if (!ReportRules.isOpen(obstacle)) {
                continue;
            }
            if (obstacle.getRelatedTask() != null) {
                openByTask.computeIfAbsent(obstacle.getRelatedTask().getId(), id -> new ArrayList<>()).add(blocker(obstacle));
            } else {
                openByStep.computeIfAbsent(stepId, id -> new ArrayList<>()).add(blocker(obstacle));
            }
        }
        return new Blockers(openByTask, openByStep, placed, unplaced);
    }

    /** Goals, steps, tasks and obstacles for the dreams in scope: four queries, sorted into report order. */
    private Loaded load(Long userId, List<Dream> dreams) {
        Map<Long, Dream> dreamById = dreams.stream().collect(Collectors.toMap(Dream::getId, Function.identity()));
        List<Goal> goals = goalRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(goal -> dreamById.containsKey(goal.getDream().getId()))
                .sorted(Comparator.comparing((Goal goal) -> dreamById.get(goal.getDream().getId()).getTitle(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Goal::getTargetDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Goal::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Set<Long> goalIds = goals.stream().map(Goal::getId).collect(Collectors.toSet());
        Map<Long, List<VisionStep>> stepsByGoal = visionStepRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(step -> goalIds.contains(step.getGoal().getId()))
                .sorted(Comparator.comparing(VisionStep::getSequenceNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(VisionStep::getId))
                .collect(Collectors.groupingBy(step -> step.getGoal().getId(), LinkedHashMap::new, Collectors.toList()));
        Set<Long> stepIds = stepsByGoal.values().stream().flatMap(List::stream).map(VisionStep::getId).collect(Collectors.toSet());
        Map<Long, List<TaskItem>> tasksByStep = taskItemRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(task -> stepIds.contains(task.getStep().getId()))
                .sorted(Comparator.comparing(TaskItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskItem::getId))
                .collect(Collectors.groupingBy(task -> task.getStep().getId(), LinkedHashMap::new, Collectors.toList()));
        return new Loaded(goals, dreamById, stepsByGoal, tasksByStep, obstacleRepository.findByUser_IdAndArchivedFalse(userId), stepIds);
    }

    /** The step an obstacle sits on (its task's step for a task), when that step is in scope; else null. */
    @Nullable
    private static Long placedStepId(Obstacle obstacle, Set<Long> stepIds) {
        if (obstacle.getRelatedTask() != null) {
            Long stepId = obstacle.getRelatedTask().getStep().getId();
            return stepIds.contains(stepId) ? stepId : null;
        }
        if (obstacle.getRelatedStep() != null && stepIds.contains(obstacle.getRelatedStep().getId())) {
            return obstacle.getRelatedStep().getId();
        }
        return null;
    }

    /**
     * RM-FR-08, RM-FR-09: only figures history can support. Tasks completed
     * (completedAt in the period), progress gained (positive progress-log
     * changes in the period, averaged over the tasks in scope), blockers on
     * steps or tasks in scope opened (created) and closed (resolvedAt) in the
     * period, and tasks due in the period, before today, not Completed by its
     * end.
     */
    private PeriodFigures periodFigures(Long userId, ReportPeriod period, LocalDate today, Collection<TaskItem> tasks,
                                        List<Obstacle> placed) {
        if (tasks.isEmpty() && placed.isEmpty()) {
            return PeriodFigures.NONE;
        }
        long completed = tasks.stream().filter(task -> period.contains(day(task.getCompletedAt()))).count();
        long becameOverdue = tasks.stream()
                .filter(task -> period.contains(task.getDueDate()) && task.getDueDate().isBefore(today))
                .filter(task -> task.getCompletedAt() == null || day(task.getCompletedAt()).isAfter(period.to()))
                .count();
        long opened = placed.stream().filter(obstacle -> period.contains(day(obstacle.getCreatedAt()))).count();
        long closed = placed.stream()
                .filter(obstacle -> obstacle.getStatus() == ObstacleStatus.RESOLVED || obstacle.getStatus() == ObstacleStatus.ACCEPTED)
                .filter(obstacle -> period.contains(day(obstacle.getResolvedAt())))
                .count();
        return new PeriodFigures(completed, progressGained(userId, period, tasks), opened, closed, becameOverdue);
    }

    private BigDecimal progressGained(Long userId, ReportPeriod period, Collection<TaskItem> tasks) {
        if (tasks.isEmpty()) {
            return PeriodFigures.NONE.progressGained();
        }
        Set<Long> taskIds = tasks.stream().map(TaskItem::getId).collect(Collectors.toSet());
        Instant start = period.from().atStartOfDay(clock.getZone()).toInstant();
        BigDecimal gained = progressLogRepository.findTrendWindow(userId, start).stream()
                .filter(log -> log.getRelatedTask() != null && taskIds.contains(log.getRelatedTask().getId()))
                .filter(log -> period.contains(day(log.getLoggedAt())))
                .map(log -> log.getProgressPercentAfter().subtract(log.getProgressPercentBefore()))
                .filter(change -> change.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return gained.divide(BigDecimal.valueOf(tasks.size()), 1, RoundingMode.HALF_UP);
    }

    @Nullable
    private LocalDate day(@Nullable Instant instant) {
        return instant == null ? null : instant.atZone(clock.getZone()).toLocalDate();
    }

    // ---------- entity to report value (RM-NFR-02: only the fields the report may show) ----------

    private GoalInfo goal(Goal goal) {
        return new GoalInfo(goal.getId(), goal.getCode(), goal.getTitle(), goal.getStatus(), goal.getPriority(),
                orZero(goal.getProgressPercent()), goal.getTargetDate(), day(goal.getCreatedAt()));
    }

    private static StepInfo step(VisionStep step) {
        return new StepInfo(step.getId(), step.getTitle(), step.getStatus(), step.getPriority(), orZero(step.getProgressPercent()),
                step.getTargetDate(), step.getGapKind());
    }

    private TaskInfo task(TaskItem task) {
        return new TaskInfo(task.getId(), task.getTitle(), task.getStatus(), task.getPriority(), orZero(task.getProgressPercent()),
                task.getDueDate(), task.getOwner(), task.getNextAction(), task.getBlockerReason(), day(task.getUpdatedAt()),
                day(task.getCompletedAt()));
    }

    /** Title, kind, severity, status and solution only (MR-NFR-02); never conflict or criticism notes. */
    private BlockerInfo blocker(Obstacle obstacle) {
        String context = "";
        if (obstacle.getRelatedGoal() != null) {
            context = obstacle.getRelatedGoal().getTitle();
        } else if (obstacle.getRelatedDream() != null) {
            context = obstacle.getRelatedDream().getTitle();
        }
        return new BlockerInfo(obstacle.getId(), obstacle.getTitle(), obstacle.getObstacleType(), obstacle.getSeverity(),
                obstacle.getStatus(), obstacle.getSolution(), day(obstacle.getCreatedAt()), context);
    }

    private static BigDecimal orZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** An unplaced obstacle belongs to the scope through its dream; with all dreams, one with no dream counts too. */
    private static boolean inScope(Obstacle obstacle, String kind, Map<Long, Dream> dreamById) {
        if (obstacle.getRelatedDream() == null) {
            return "ALL".equals(kind);
        }
        return dreamById.containsKey(obstacle.getRelatedDream().getId());
    }

    /**
     * V17 BL-FR-22, the same rule as the plan check: a step missing knowledge,
     * a skill or money, not itself a "find help" step, with no linked partner,
     * no step made to cover it and no open blocker on it or its tasks.
     */
    private static boolean gapNeedsHelp(VisionStep step, List<VisionStep> goalSteps, Set<Long> stepsWithPartner,
                                        Map<Long, List<BlockerInfo>> openByStep, Map<Long, List<BlockerInfo>> openByTask,
                                        Map<Long, List<TaskItem>> tasksByStep) {
        if (step.getStatus() == WorkStatus.COMPLETED || step.getGapKind() == null || !HELP_GAP_KINDS.contains(step.getGapKind())
                || step.getCoversStep() != null || step.getCoversObstacle() != null) {
            return false;
        }
        boolean covered = goalSteps.stream()
                .anyMatch(other -> other.getCoversStep() != null && other.getCoversStep().getId().equals(step.getId()));
        boolean blocker = openByStep.containsKey(step.getId()) || tasksByStep.getOrDefault(step.getId(), List.of()).stream()
                .anyMatch(task -> openByTask.containsKey(task.getId()));
        return !covered && !stepsWithPartner.contains(step.getId()) && !blocker;
    }

    ManagementReportResponse toResponse(ManagementReport report) {
        return toResponse(report, List.of());
    }

    ManagementReportResponse toResponse(ManagementReport report, List<DashboardSummaryResponse.TrendPoint> trend) {
        LocalDate today = report.asOf();
        List<TaskInfo> tasks = report.tasks().map(TaskNode::task).toList();
        List<BlockerInfo> blockers = report.blockers().toList();

        Map<String, Long> tasksByStatus = new LinkedHashMap<>();
        for (WorkStatus status : List.of(WorkStatus.NOT_STARTED, WorkStatus.IN_PROGRESS, WorkStatus.WAITING, WorkStatus.BLOCKED,
                WorkStatus.COMPLETED, WorkStatus.PAUSED)) {
            tasksByStatus.put(status.name(), tasks.stream().filter(task -> task.status() == status).count());
        }
        List<PriorityCount> byPriority = new ArrayList<>();
        for (Priority priority : Priority.values()) {
            List<TaskInfo> open = tasks.stream()
                    .filter(task -> task.priority() == priority && task.status() != WorkStatus.COMPLETED)
                    .toList();
            long overdue = open.stream().filter(task -> ReportRules.isOverdue(task, today)).count();
            byPriority.add(new PriorityCount(priority.name(), open.size() - overdue, overdue));
        }
        Map<String, Long> blockersByKind = new LinkedHashMap<>();
        for (ObstacleType type : ObstacleType.values()) {
            long count = blockers.stream().filter(obstacle -> obstacle.type() == type).count();
            if (count > 0) {
                blockersByKind.put(type.name(), count);
            }
        }

        Map<Health, Long> health = new EnumMap<>(Health.class);
        for (Health value : Health.values()) {
            health.put(value, 0L);
        }
        List<GoalHealthRow> rows = new ArrayList<>();
        for (GoalNode node : report.goals()) {
            Assessment assessment = ReportRules.assessmentOf(node, today);
            health.merge(assessment.health(), 1L, Long::sum);
            GoalInfo goal = node.goal();
            long overdueTasks = node.tasks().filter(task -> ReportRules.isOverdue(task.task(), today)).count();
            long blockedItems = node.tasks().filter(task -> task.task().status() == WorkStatus.BLOCKED).count()
                    + node.blockers().count();
            List<HealthReason> reasons = assessment.reasons().stream()
                    .map(reason -> new HealthReason(reason.code(), reason.value(), reason.elapsed(), reason.title()))
                    .toList();
            rows.add(new GoalHealthRow(goal.id(), goal.code(), goal.title(), node.dream().id(), node.dream().title(),
                    assessment.health().name(), percent(goal.progress()), goal.targetDate(), overdueTasks, blockedItems,
                    ReportRules.nextAction(node), reasons));
        }
        rows.sort(Comparator.comparing((GoalHealthRow row) -> Health.valueOf(row.health()))
                .thenComparing(GoalHealthRow::progress));

        BigDecimal overall = report.goals().isEmpty() ? BigDecimal.ZERO.setScale(1)
                : report.goals().stream().map(node -> percent(node.goal().progress()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(report.goals().size()), 1, RoundingMode.HALF_UP);
        Map<String, Long> healthCounts = new LinkedHashMap<>();
        health.forEach((key, value) -> healthCounts.put(key.name(), value));
        Headline headline = new Headline(overall, healthCounts, tasks.size(),
                tasksByStatus.get(WorkStatus.COMPLETED.name()),
                tasks.stream().filter(task -> ReportRules.isOverdue(task, today)).count(),
                tasksByStatus.get(WorkStatus.BLOCKED.name()),
                blockers.size(),
                tasks.stream().filter(task -> ReportRules.isDueSoon(task, today)).count());

        ReportPeriod period = report.period();
        PeriodFigures figures = report.periodFigures();
        return new ManagementReportResponse(new Scope(report.scopeKind(), report.areaId(), report.dreamId(), report.scopeLabel()),
                today,
                new ManagementReportResponse.Period(period.kind().name(), period.from(), period.to(), period.endsBeforeToday(today),
                        period.from().isBefore(ReportPeriod.RESOLVED_AT_RECORDED_FROM)),
                new ManagementReportResponse.PeriodFigures(figures.tasksCompleted(), figures.progressGained(), figures.blockersOpened(),
                        figures.blockersClosed(), figures.tasksBecameOverdue()),
                headline, tasksByStatus, byPriority, blockersByKind, report.unplaced().size(), rows, attention(report), trend,
                filters(report.filters()), report.owners(), null);
    }

    private static ManagementReportResponse.Filters filters(ReportFilters filters) {
        return new ManagementReportResponse.Filters(
                filters.priorities().stream().sorted().map(Enum::name).toList(), filters.owner(),
                filters.healths().stream().sorted().map(Enum::name).toList(), filters.includeCompleted());
    }

    /**
     * MR-FR-12: up to 10 items, most urgent first: Critical blockers, Blocked
     * tasks, overdue High or Critical tasks, other overdue tasks, Completed
     * goals with open work (V19 RM-FR-02), then steps whose gap still needs
     * help. Context is "Goal › Step".
     */
    private static List<AttentionItem> attention(ManagementReport report) {
        LocalDate today = report.asOf();
        List<AttentionItem> items = new ArrayList<>();
        addCriticalBlockers(report, items);
        addTasks(report, items, "BLOCKED_TASK", task -> task.status() == WorkStatus.BLOCKED);
        addTasks(report, items, "OVERDUE_URGENT", task -> task.status() != WorkStatus.BLOCKED && ReportRules.isOverdue(task, today)
                && (task.priority() == Priority.HIGH || task.priority() == Priority.CRITICAL));
        addTasks(report, items, "OVERDUE", task -> task.status() != WorkStatus.BLOCKED && ReportRules.isOverdue(task, today)
                && task.priority() != Priority.HIGH && task.priority() != Priority.CRITICAL);
        for (GoalNode goal : report.goals()) {
            if (ReportRules.health(goal, today) == Health.DONE_OPEN_WORK) {
                items.add(new AttentionItem("REOPEN_OR_FINISH", "GOAL", goal.goal().id(), goal.goal().title(), goal.dream().title(),
                        null, goal.goal().id()));
            }
        }
        for (GoalNode goal : report.goals()) {
            goal.steps().stream().filter(StepNode::gapNeedsHelp).forEach(step -> items.add(item("GAP_NEEDS_HELP", "STEP",
                    step.step().id(), step.step().title(), goal.goal().title(), step, goal)));
        }
        return items.stream().limit(MAX_ATTENTION).toList();
    }

    private static void addCriticalBlockers(ManagementReport report, List<AttentionItem> items) {
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().title() + " › " + step.step().title();
                step.blockers().stream().filter(obstacle -> obstacle.severity() == Severity.CRITICAL)
                        .forEach(obstacle -> items.add(item("CRITICAL_BLOCKER", "OBSTACLE", obstacle.id(), obstacle.title(), where, step, goal)));
                for (TaskNode task : step.tasks()) {
                    task.blockers().stream().filter(obstacle -> obstacle.severity() == Severity.CRITICAL)
                            .forEach(obstacle -> items.add(item("CRITICAL_BLOCKER", "OBSTACLE", obstacle.id(),
                                    obstacle.title(), where + " › " + task.task().title(), step, goal)));
                }
            }
        }
    }

    private static void addTasks(ManagementReport report, List<AttentionItem> items, String kind, Predicate<TaskInfo> rule) {
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                for (TaskNode task : step.tasks()) {
                    if (rule.test(task.task())) {
                        items.add(item(kind, "TASK", task.task().id(), task.task().title(),
                                goal.goal().title() + " › " + step.step().title(), step, goal));
                    }
                }
            }
        }
    }

    private static AttentionItem item(String kind, String itemType, Long itemId, String title, String context, StepNode step,
                                      GoalNode goal) {
        return new AttentionItem(kind, itemType, itemId, title, context, step.step().id(), goal.goal().id());
    }

    private static BigDecimal percent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
