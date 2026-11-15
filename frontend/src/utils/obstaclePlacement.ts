import type { Dream, Goal, Obstacle, ObstaclePlacement, TaskItem, VisionStep } from '../types/vision';
import { tr } from '../i18n/translate';

/**
 * V17 (BRD 17.0.0, D-17-1): a plan blocker sits on a step or a task; its goal
 * and dream come from that step. These helpers read placement the same way the
 * server does, for the Obstacles page and the dashboard.
 */

/** The server sends `placement`; older cached rows fall back to the ids. */
export function placementOf(obstacle: Obstacle): ObstaclePlacement {
  if (obstacle.placement) {
    return obstacle.placement;
  }
  if (obstacle.relatedTaskId) {
    return 'TASK';
  }
  return obstacle.relatedStepId ? 'STEP' : 'NONE';
}

/**
 * BL-FR-05, BL-FR-09: an active obstacle that still needs a step. Exempt
 * partner conflicts (BL-FR-04) never do, and neither do Resolved or Accepted
 * ones, which need no action.
 */
export function needsPlacing(obstacle: Obstacle): boolean {
  if (obstacle.archived || obstacle.status === 'RESOLVED' || obstacle.status === 'ACCEPTED') {
    return false;
  }
  if (obstacle.anchorArchived) {
    return true;
  }
  return placementOf(obstacle) === 'NONE' && !obstacle.placementExempt;
}

/**
 * V17 BL-FR-20: an open, non-exempt obstacle can become a "find help" step.
 * Closed ones need no step, and partner conflicts stay off the steps.
 */
export function canTurnIntoStep(obstacle: Obstacle): boolean {
  return !obstacle.archived && !obstacle.placementExempt
    && obstacle.status !== 'RESOLVED' && obstacle.status !== 'ACCEPTED';
}

/** A short "where it sits" text for tables and cards. */
export function placementText(obstacle: Obstacle, steps: VisionStep[], tasks: TaskItem[]): string {
  const placement = placementOf(obstacle);
  if (placement === 'TASK') {
    const task = tasks.find((item) => item.id === obstacle.relatedTaskId);
    return task ? tr('Task: {title}', { title: task.title }) : tr('On a task');
  }
  if (placement === 'STEP') {
    const step = steps.find((item) => item.id === obstacle.relatedStepId);
    return step ? tr('Step: {title}', { title: step.title }) : tr('On a step');
  }
  return obstacle.placementExempt ? tr('Partner conflict') : tr('Not on a step yet');
}

export type StepGroup = { key: string; label: string; steps: VisionStep[] };

/**
 * BL-FR-03: steps grouped under "Dream · Goal" for the "Where is this
 * blocker?" picker. Groups under `preferDreamId` come first, so moving an
 * older dream-level obstacle starts in its own dream (BL-FR-05).
 */
export function groupStepsForPicker(
  steps: VisionStep[],
  goals: Goal[],
  dreams: Dream[],
  preferDreamId?: number,
): StepGroup[] {
  const goalById = new Map(goals.map((goal) => [goal.id, goal]));
  const dreamById = new Map(dreams.map((dream) => [dream.id, dream]));
  const groups = new Map<string, StepGroup & { dreamId?: number }>();
  for (const step of steps) {
    if (step.archived) {
      continue;
    }
    const goal = goalById.get(step.goalId);
    const dream = goal ? dreamById.get(goal.dreamId) : undefined;
    const key = String(step.goalId);
    if (!groups.has(key)) {
      const label = [dream?.title, goal?.title].filter(Boolean).join(' · ') || tr('Other steps');
      groups.set(key, { key, label, steps: [], dreamId: dream?.id });
    }
    groups.get(key)?.steps.push(step);
  }
  return [...groups.values()]
    .sort((a, b) => {
      const preferA = preferDreamId !== undefined && a.dreamId === preferDreamId ? 0 : 1;
      const preferB = preferDreamId !== undefined && b.dreamId === preferDreamId ? 0 : 1;
      return preferA - preferB || a.label.localeCompare(b.label);
    })
    .map(({ key, label, steps: groupSteps }) => ({
      key,
      label,
      steps: [...groupSteps].sort((a, b) => a.sequenceNumber - b.sequenceNumber),
    }));
}

/** "Goal · Dream" for the step a blocker sits on, shown read-only in the form (BL-FR-03). */
export function stepContext(stepId: number | undefined, steps: VisionStep[], goals: Goal[], dreams: Dream[]): string {
  const step = steps.find((item) => item.id === stepId);
  const goal = step ? goals.find((item) => item.id === step.goalId) : undefined;
  const dream = goal ? dreams.find((item) => item.id === goal.dreamId) : undefined;
  if (!goal) {
    return '';
  }
  return dream
    ? tr('Goal: {goal} · Dream: {dream}', { goal: goal.title, dream: dream.title })
    : tr('Goal: {goal}', { goal: goal.title });
}
