package com.visionmapping.service;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.AttentionItem;
import com.visionmapping.dto.response.ManagementReportResponse.GoalHealthRow;
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
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.report.ManagementReport;
import com.visionmapping.service.report.ManagementReportDeckWriter;
import com.visionmapping.service.report.ManagementReportExcelWriter;
import com.visionmapping.service.report.ReportText;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportRules;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V18 (BRD 18.0.0): the Management Report. One model of the participant's own
 * plan in scope (all dreams, one vision area or one dream) feeds the Summary
 * tab here and, in later phases, the Excel report and the deck (MR-NFR-03).
 * Read-only and rule-based: no AI (MR-NFR-10). Loaded with a fixed number of
 * queries, never one per row (MR-NFR-04).
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
    private final com.visionmapping.repository.ProgressLogRepository progressLogRepository;
    private final Clock clock;

    public ManagementReportResponse summary(@Nullable Long areaId, @Nullable Long dreamId) {
        ManagementReport report = build(areaId, dreamId);
        return toResponse(report, trend(report));
    }

    /**
     * MR-FR-14: the dashboard's twelve-week trend (DashboardTrend), fed only
     * with the progress logs of the tasks in scope, so both mean "average task
     * progress". Only the Summary needs it; the files skip this query.
     */
    private List<DashboardSummaryResponse.TrendPoint> trend(ManagementReport report) {
        Set<Long> taskIds = report.tasks().map(task -> task.task().getId()).collect(Collectors.toSet());
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
    public ReportFile excel(@Nullable Long areaId, @Nullable Long dreamId, @Nullable String language) {
        ManagementReport report = build(areaId, dreamId);
        byte[] content = ManagementReportExcelWriter.write(report, toResponse(report), ReportText.of(language), clock.getZone());
        return new ReportFile(fileName(report, "xlsx"), content);
    }

    /**
     * V18 MR-FR-23 to MR-FR-31: the PowerPoint deck. The title defaults to
     * "Progress report"; the participant's name is on the title slide unless
     * they leave it out (Q-05).
     */
    public ReportFile deck(@Nullable Long areaId, @Nullable Long dreamId, @Nullable String language, @Nullable String title,
                           boolean includeName) {
        ManagementReport report = build(areaId, dreamId);
        String presenter = includeName ? lookup.currentUser().getFullName() : null;
        byte[] content = ManagementReportDeckWriter.write(report, toResponse(report), ReportText.of(language), title, presenter);
        return new ReportFile(fileName(report, "pptx"), content);
    }

    /**
     * "Management_Report_Dream_12_2026-11-16.xlsx": built from the scope's kind
     * and id, never from free text, so it is always a safe file name.
     */
    static String fileName(ManagementReport report, String extension) {
        String scope = switch (report.scopeKind()) {
            case "DREAM" -> "Dream_" + report.dreamId();
            case "AREA" -> "Area_" + report.areaId();
            default -> "All_dreams";
        };
        return "Management_Report_" + scope + "_" + report.asOf() + "." + extension;
    }

    /**
     * MR-FR-03, MR-NFR-01: the participant's unarchived plan under the scope.
     * Another user's area or dream answers 404 through the lookup.
     */
    public ManagementReport build(@Nullable Long areaId, @Nullable Long dreamId) {
        if (areaId != null && dreamId != null) {
            throw new BusinessRuleException(ONE_SCOPE);
        }
        Long userId = lookup.userId();
        String kind;
        String label = null;
        List<Dream> dreams;
        if (dreamId != null) {
            Dream dream = lookup.dream(dreamId);
            kind = "DREAM";
            label = dream.getTitle();
            dreams = dream.isArchived() ? List.of() : List.of(dream);
        } else if (areaId != null) {
            kind = "AREA";
            label = lookup.visionArea(areaId).getName();
            dreams = dreamRepository.findByVisionArea_IdAndUser_IdAndArchivedFalse(areaId, userId);
        } else {
            kind = "ALL";
            dreams = dreamRepository.findByUser_IdAndArchivedFalse(userId);
        }
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

        List<Obstacle> obstacles = obstacleRepository.findByUser_IdAndArchivedFalse(userId);
        Map<Long, List<Obstacle>> openByTask = new LinkedHashMap<>();
        Map<Long, List<Obstacle>> openByStep = new LinkedHashMap<>();
        List<Obstacle> unplaced = new ArrayList<>();
        for (Obstacle obstacle : obstacles) {
            if (obstacle.getRelatedTask() != null && stepIds.contains(obstacle.getRelatedTask().getStep().getId())) {
                if (ReportRules.isOpen(obstacle)) {
                    openByTask.computeIfAbsent(obstacle.getRelatedTask().getId(), id -> new ArrayList<>()).add(obstacle);
                }
            } else if (obstacle.getRelatedStep() != null && stepIds.contains(obstacle.getRelatedStep().getId())) {
                if (ReportRules.isOpen(obstacle)) {
                    openByStep.computeIfAbsent(obstacle.getRelatedStep().getId(), id -> new ArrayList<>()).add(obstacle);
                }
            } else if (ObstacleService.needsPlacing(obstacle) && inScope(obstacle, kind, dreamById)) {
                unplaced.add(obstacle);
            }
        }
        Set<Long> stepsWithPartner = partnerRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .map(Partner::getRelatedStep)
                .filter(Objects::nonNull)
                .map(VisionStep::getId)
                .collect(Collectors.toSet());

        List<GoalNode> nodes = new ArrayList<>();
        for (Goal goal : goals) {
            List<VisionStep> goalSteps = stepsByGoal.getOrDefault(goal.getId(), List.of());
            List<StepNode> stepNodes = goalSteps.stream()
                    .map(step -> new StepNode(step,
                            tasksByStep.getOrDefault(step.getId(), List.of()).stream()
                                    .map(task -> new TaskNode(task, openByTask.getOrDefault(task.getId(), List.of())))
                                    .toList(),
                            openByStep.getOrDefault(step.getId(), List.of()),
                            gapNeedsHelp(step, goalSteps, stepsWithPartner, openByStep, openByTask, tasksByStep)))
                    .toList();
            nodes.add(new GoalNode(goal, dreamById.get(goal.getDream().getId()), stepNodes));
        }
        return new ManagementReport(kind, areaId, dreamId, label, LocalDate.now(clock), nodes, unplaced);
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
                                        Map<Long, List<Obstacle>> openByStep, Map<Long, List<Obstacle>> openByTask,
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
        List<TaskItem> tasks = report.tasks().map(TaskNode::task).toList();
        List<Obstacle> blockers = report.blockers().toList();

        Map<String, Long> tasksByStatus = new LinkedHashMap<>();
        for (WorkStatus status : List.of(WorkStatus.NOT_STARTED, WorkStatus.IN_PROGRESS, WorkStatus.WAITING, WorkStatus.BLOCKED,
                WorkStatus.COMPLETED, WorkStatus.PAUSED)) {
            tasksByStatus.put(status.name(), tasks.stream().filter(task -> task.getStatus() == status).count());
        }
        List<PriorityCount> byPriority = new ArrayList<>();
        for (Priority priority : Priority.values()) {
            List<TaskItem> open = tasks.stream()
                    .filter(task -> task.getPriority() == priority && task.getStatus() != WorkStatus.COMPLETED)
                    .toList();
            long overdue = open.stream().filter(task -> ReportRules.isOverdue(task, today)).count();
            byPriority.add(new PriorityCount(priority.name(), open.size() - overdue, overdue));
        }
        Map<String, Long> blockersByKind = new LinkedHashMap<>();
        for (ObstacleType type : ObstacleType.values()) {
            long count = blockers.stream().filter(obstacle -> obstacle.getObstacleType() == type).count();
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
            Health value = ReportRules.health(node, today, clock.getZone());
            health.merge(value, 1L, Long::sum);
            Goal goal = node.goal();
            long overdueTasks = node.tasks().filter(task -> ReportRules.isOverdue(task.task(), today)).count();
            long blockedItems = node.tasks().filter(task -> task.task().getStatus() == WorkStatus.BLOCKED).count()
                    + node.blockers().count();
            rows.add(new GoalHealthRow(goal.getId(), goal.getCode(), goal.getTitle(), node.dream().getId(), node.dream().getTitle(),
                    value.name(), percent(goal.getProgressPercent()), goal.getTargetDate(), overdueTasks, blockedItems,
                    ReportRules.nextAction(node)));
        }
        rows.sort(Comparator.comparing((GoalHealthRow row) -> Health.valueOf(row.health()))
                .thenComparing(GoalHealthRow::progress));

        BigDecimal overall = report.goals().isEmpty() ? BigDecimal.ZERO.setScale(1)
                : report.goals().stream().map(node -> percent(node.goal().getProgressPercent()))
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

        return new ManagementReportResponse(new Scope(report.scopeKind(), report.areaId(), report.dreamId(), report.scopeLabel()),
                today, headline, tasksByStatus, byPriority, blockersByKind, report.unplaced().size(), rows, attention(report), trend);
    }

    /**
     * MR-FR-12: up to 10 items, most urgent first: Critical blockers, Blocked
     * tasks, overdue High or Critical tasks, other overdue tasks, then steps
     * whose gap still needs help. Context is "Goal › Step".
     */
    private static List<AttentionItem> attention(ManagementReport report) {
        LocalDate today = report.asOf();
        List<AttentionItem> items = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().getTitle() + " › " + step.step().getTitle();
                for (Obstacle obstacle : step.blockers()) {
                    if (obstacle.getSeverity() == Severity.CRITICAL) {
                        items.add(item("CRITICAL_BLOCKER", "OBSTACLE", obstacle.getId(), obstacle.getTitle(), where, step, goal));
                    }
                }
                for (TaskNode task : step.tasks()) {
                    task.blockers().stream().filter(obstacle -> obstacle.getSeverity() == Severity.CRITICAL)
                            .forEach(obstacle -> items.add(item("CRITICAL_BLOCKER", "OBSTACLE", obstacle.getId(),
                                    obstacle.getTitle(), where + " › " + task.task().getTitle(), step, goal)));
                }
            }
        }
        addTasks(report, items, "BLOCKED_TASK", task -> task.getStatus() == WorkStatus.BLOCKED);
        addTasks(report, items, "OVERDUE_URGENT", task -> task.getStatus() != WorkStatus.BLOCKED && ReportRules.isOverdue(task, today)
                && (task.getPriority() == Priority.HIGH || task.getPriority() == Priority.CRITICAL));
        addTasks(report, items, "OVERDUE", task -> task.getStatus() != WorkStatus.BLOCKED && ReportRules.isOverdue(task, today)
                && task.getPriority() != Priority.HIGH && task.getPriority() != Priority.CRITICAL);
        for (GoalNode goal : report.goals()) {
            goal.steps().stream().filter(StepNode::gapNeedsHelp).forEach(step -> items.add(item("GAP_NEEDS_HELP", "STEP",
                    step.step().getId(), step.step().getTitle(), goal.goal().getTitle(), step, goal)));
        }
        return items.stream().limit(MAX_ATTENTION).toList();
    }

    private static void addTasks(ManagementReport report, List<AttentionItem> items, String kind,
                                 java.util.function.Predicate<TaskItem> rule) {
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                for (TaskNode task : step.tasks()) {
                    if (rule.test(task.task())) {
                        items.add(item(kind, "TASK", task.task().getId(), task.task().getTitle(),
                                goal.goal().getTitle() + " › " + step.step().getTitle(), step, goal));
                    }
                }
            }
        }
    }

    private static AttentionItem item(String kind, String itemType, Long itemId, String title, String context, StepNode step,
                                      GoalNode goal) {
        return new AttentionItem(kind, itemType, itemId, title, context, step.step().getId(), goal.goal().getId());
    }

    private static BigDecimal percent(@Nullable BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(1, RoundingMode.HALF_UP);
    }
}
