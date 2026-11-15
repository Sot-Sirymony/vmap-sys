import type { Goal, Obstacle, TaskItem, VisionStep } from '../types/vision';
import { placementOf } from './obstaclePlacement';

export type BlockerCount = { blockers: number; blockedTasks: number };

export type BlockerRollup = {
  byGoal: Map<number, BlockerCount>;
  byDream: Map<number, BlockerCount>;
};

const EMPTY: BlockerCount = { blockers: 0, blockedTasks: 0 };

function bump(map: Map<number, BlockerCount>, id: number | undefined, field: keyof BlockerCount) {
  if (id === undefined) {
    return;
  }
  const current = map.get(id) ?? { ...EMPTY };
  map.set(id, { ...current, [field]: current[field] + 1 });
}

/**
 * V17 BL-FR-15: dreams and goals carry no blockers of their own; they show a
 * read-only count of what is blocking the steps and tasks under them. Counts
 * open obstacles that sit on a step or task (their goal and dream are derived
 * from it, BL-FR-02) and Blocked tasks. Archived records are left out.
 */
export function computeBlockerRollup({ obstacles, tasks, steps, goals }: {
  obstacles: Obstacle[];
  tasks: TaskItem[];
  steps: VisionStep[];
  goals: Goal[];
}): BlockerRollup {
  const byGoal = new Map<number, BlockerCount>();
  const byDream = new Map<number, BlockerCount>();
  for (const obstacle of obstacles) {
    if (obstacle.archived || (obstacle.status !== 'OPEN' && obstacle.status !== 'IN_PROGRESS')) {
      continue;
    }
    if (placementOf(obstacle) === 'NONE' || obstacle.anchorArchived) {
      continue;
    }
    bump(byGoal, obstacle.relatedGoalId, 'blockers');
    bump(byDream, obstacle.relatedDreamId, 'blockers');
  }
  const goalOfStep = new Map(steps.map((step) => [step.id, step.goalId]));
  const dreamOfGoal = new Map(goals.map((goal) => [goal.id, goal.dreamId]));
  for (const task of tasks) {
    if (task.archived || task.status !== 'BLOCKED') {
      continue;
    }
    const goalId = goalOfStep.get(task.stepId);
    bump(byGoal, goalId, 'blockedTasks');
    bump(byDream, goalId === undefined ? undefined : dreamOfGoal.get(goalId), 'blockedTasks');
  }
  return { byGoal, byDream };
}

export function rollupFor(map: Map<number, BlockerCount>, id: number): BlockerCount {
  return map.get(id) ?? EMPTY;
}
