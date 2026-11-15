import { describe, expect, it } from 'vitest';
import { aGoal, anObstacle, aStep, aTask } from '../test/builders';
import { computeBlockerRollup, rollupFor } from './blockerRollup';

describe('blocker roll-up (V17 BL-FR-15)', () => {
  const goals = [aGoal({ id: 1, dreamId: 10 }), aGoal({ id: 2, dreamId: 10 })];
  const steps = [aStep({ id: 5, goalId: 1 }), aStep({ id: 6, goalId: 2 })];

  it('counts open blockers on steps and tasks, and Blocked tasks, per goal and dream', () => {
    const rollup = computeBlockerRollup({
      goals,
      steps,
      obstacles: [
        anObstacle({ id: 1, status: 'OPEN', placement: 'STEP', relatedStepId: 5, relatedGoalId: 1, relatedDreamId: 10 }),
        anObstacle({ id: 2, status: 'IN_PROGRESS', placement: 'TASK', relatedTaskId: 9, relatedStepId: 6, relatedGoalId: 2, relatedDreamId: 10 }),
        // Not counted: resolved, unplaced (older dream-level), archived, or on an archived step.
        anObstacle({ id: 3, status: 'RESOLVED', placement: 'STEP', relatedStepId: 5, relatedGoalId: 1, relatedDreamId: 10 }),
        anObstacle({ id: 4, status: 'OPEN', placement: 'NONE', relatedDreamId: 10 }),
        anObstacle({ id: 5, status: 'OPEN', placement: 'STEP', relatedStepId: 5, relatedGoalId: 1, relatedDreamId: 10, archived: true }),
        anObstacle({ id: 6, status: 'OPEN', placement: 'STEP', relatedStepId: 5, relatedGoalId: 1, relatedDreamId: 10, anchorArchived: true }),
      ],
      tasks: [
        aTask({ id: 20, stepId: 5, status: 'BLOCKED', blockerReason: 'No data' }),
        aTask({ id: 21, stepId: 5, status: 'IN_PROGRESS' }),
        aTask({ id: 22, stepId: 6, status: 'BLOCKED', blockerReason: 'Waiting', archived: true }),
      ],
    });

    expect(rollupFor(rollup.byGoal, 1)).toEqual({ blockers: 1, blockedTasks: 1 });
    expect(rollupFor(rollup.byGoal, 2)).toEqual({ blockers: 1, blockedTasks: 0 });
    expect(rollupFor(rollup.byDream, 10)).toEqual({ blockers: 2, blockedTasks: 1 });
    expect(rollupFor(rollup.byGoal, 99)).toEqual({ blockers: 0, blockedTasks: 0 });
  });
});
