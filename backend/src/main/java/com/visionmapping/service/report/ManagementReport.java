package com.visionmapping.service.report;

import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.lang.Nullable;

/**
 * V18 (BRD 18.0.0, MR-NFR-03): the one model behind the Management Report's
 * Summary tab, Excel report and deck. It holds the participant's unarchived
 * plan in scope, in report order, with the open blockers on each step and
 * task. Writers only format it; they never query.
 *
 * <p>V19 (BRD 19.0.0, RM-NFR-02): it holds plain values copied from the
 * entities, never the entities themselves, and only the fields the report may
 * show. That keeps private fields (conflict notes, partner contact details)
 * out of every file by construction, and lets a later phase store the model
 * as a frozen snapshot.
 *
 * @param scopeKind ALL, AREA or DREAM
 * @param scopeLabel the area's or dream's name, or null for ALL
 * @param unplaced active, non-exempt obstacles in scope that are not on a step yet (V17 BL-FR-05)
 * @param periodFigures what happened in the period (RM-FR-08)
 * @param filters the filters below the scope (RM-FR-10); goals are already narrowed by them
 * @param owners the task owners in scope before filtering, for the owner filter's choices
 */
public record ManagementReport(String scopeKind, @Nullable Long areaId, @Nullable Long dreamId, @Nullable String scopeLabel,
                               LocalDate asOf, ReportPeriod period, List<GoalNode> goals, List<BlockerInfo> unplaced,
                               PeriodFigures periodFigures, ReportFilters filters, List<String> owners) {

    public ManagementReport {
        goals = Copies.list(goals);
        unplaced = Copies.list(unplaced);
        owners = Copies.list(owners);
        filters = filters == null ? ReportFilters.NONE : filters;
    }

    /** The dream a goal belongs to. */
    public record DreamInfo(Long id, String title) {
    }

    /** A goal's reportable fields. Progress is never null; createdOn is the day it was created in the app's zone. */
    public record GoalInfo(Long id, String code, String title, WorkStatus status, @Nullable Priority priority, BigDecimal progress,
                           @Nullable LocalDate targetDate, @Nullable LocalDate createdOn) {
    }

    /** A step's reportable fields. */
    public record StepInfo(Long id, String title, WorkStatus status, @Nullable Priority priority, BigDecimal progress,
                           @Nullable LocalDate targetDate, @Nullable ObstacleType gapKind) {
    }

    /**
     * A task's reportable fields. lastUpdatedOn counts a Blocked task's days
     * open (V18 phase 2); completedOn feeds the commentary draft (RM-FR-28).
     */
    public record TaskInfo(Long id, String title, WorkStatus status, @Nullable Priority priority, BigDecimal progress,
                           @Nullable LocalDate dueDate, @Nullable String owner, @Nullable String nextAction,
                           @Nullable String blockerReason, @Nullable LocalDate lastUpdatedOn, @Nullable LocalDate completedOn) {
    }

    /**
     * An obstacle: title, kind, severity, status and solution only (MR-NFR-02).
     * context names the goal or dream an unplaced obstacle belongs to, else empty.
     */
    public record BlockerInfo(Long id, String title, @Nullable ObstacleType type, @Nullable Severity severity,
                              ObstacleStatus status, @Nullable String solution, @Nullable LocalDate createdOn, String context) {
    }

    /**
     * RM-FR-08: what happened in the period, on the tasks and placed blockers
     * in scope. progressGained is in points of average task progress.
     */
    public record PeriodFigures(long tasksCompleted, BigDecimal progressGained, long blockersOpened, long blockersClosed,
                                long tasksBecameOverdue) {

        public static final PeriodFigures NONE = new PeriodFigures(0, BigDecimal.ZERO.setScale(1), 0, 0, 0);
    }

    /**
     * A goal, its dream and its steps in sequence order. V19 RM-FR-11: the
     * assessment is judged on all the goal's work before any filter narrows
     * its steps and tasks, so filtering never changes a goal's health. It is
     * null only on a node built by hand; ReportRules.assessmentOf fills it in.
     */
    public record GoalNode(GoalInfo goal, DreamInfo dream, List<StepNode> steps, @Nullable ReportRules.Assessment assessment) {

        public GoalNode {
            steps = Copies.list(steps);
        }

        /** A goal assessed on its own steps, as of {@code today}. */
        public static GoalNode assessed(GoalInfo goal, DreamInfo dream, List<StepNode> steps, LocalDate today) {
            GoalNode node = new GoalNode(goal, dream, steps, null);
            return new GoalNode(goal, dream, steps, ReportRules.assess(node, today));
        }

        public Stream<TaskNode> tasks() {
            return steps.stream().flatMap(step -> step.tasks().stream());
        }

        /** Open blockers on the goal's steps and tasks. */
        public Stream<BlockerInfo> blockers() {
            return steps.stream().flatMap(step -> Stream.concat(step.blockers().stream(),
                    step.tasks().stream().flatMap(task -> task.blockers().stream())));
        }
    }

    /**
     * A step, its tasks in their order, the open blockers placed on the step
     * itself, and whether its gap still needs help planned (V17 BL-FR-22).
     */
    public record StepNode(StepInfo step, List<TaskNode> tasks, List<BlockerInfo> blockers, boolean gapNeedsHelp) {

        public StepNode {
            tasks = Copies.list(tasks);
            blockers = Copies.list(blockers);
        }
    }

    /** A task and the open blockers placed on it. */
    public record TaskNode(TaskInfo task, List<BlockerInfo> blockers) {

        public TaskNode {
            blockers = Copies.list(blockers);
        }
    }

    public Stream<TaskNode> tasks() {
        return goals.stream().flatMap(GoalNode::tasks);
    }

    public Stream<BlockerInfo> blockers() {
        return goals.stream().flatMap(GoalNode::blockers);
    }

    public Stream<StepNode> steps() {
        return goals.stream().flatMap(goal -> goal.steps().stream());
    }
}
