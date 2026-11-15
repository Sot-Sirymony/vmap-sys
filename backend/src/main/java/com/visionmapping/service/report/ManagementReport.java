package com.visionmapping.service.report;

import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.util.Copies;
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
 * @param scopeKind ALL, AREA or DREAM
 * @param scopeLabel the area's or dream's name, or null for ALL
 * @param unplaced active, non-exempt obstacles in scope that are not on a step yet (V17 BL-FR-05)
 */
public record ManagementReport(String scopeKind, @Nullable Long areaId, @Nullable Long dreamId, @Nullable String scopeLabel,
                               LocalDate asOf, List<GoalNode> goals, List<Obstacle> unplaced) {

    public ManagementReport {
        goals = Copies.list(goals);
        unplaced = Copies.list(unplaced);
    }

    /** A goal, its dream and its steps in sequence order. */
    public record GoalNode(Goal goal, Dream dream, List<StepNode> steps) {

        public GoalNode {
            steps = Copies.list(steps);
        }

        public Stream<TaskNode> tasks() {
            return steps.stream().flatMap(step -> step.tasks().stream());
        }

        /** Open blockers on the goal's steps and tasks. */
        public Stream<Obstacle> blockers() {
            return steps.stream().flatMap(step -> Stream.concat(step.blockers().stream(),
                    step.tasks().stream().flatMap(task -> task.blockers().stream())));
        }
    }

    /**
     * A step, its tasks in their order, the open blockers placed on the step
     * itself, and whether its gap still needs help planned (V17 BL-FR-22).
     */
    public record StepNode(VisionStep step, List<TaskNode> tasks, List<Obstacle> blockers, boolean gapNeedsHelp) {

        public StepNode {
            tasks = Copies.list(tasks);
            blockers = Copies.list(blockers);
        }
    }

    /** A task and the open blockers placed on it. */
    public record TaskNode(TaskItem task, List<Obstacle> blockers) {

        public TaskNode {
            blockers = Copies.list(blockers);
        }
    }

    public Stream<TaskNode> tasks() {
        return goals.stream().flatMap(GoalNode::tasks);
    }

    public Stream<Obstacle> blockers() {
        return goals.stream().flatMap(GoalNode::blockers);
    }

    public Stream<StepNode> steps() {
        return goals.stream().flatMap(goal -> goal.steps().stream());
    }
}
