import { describe, expect, it } from 'vitest';
import { checkDrainOvercommit, isoWeekRange, DRAIN_WEEK_THRESHOLD } from './energyBudgetNudge';
import type { EnergyDemand, TaskItem } from '../types/vision';

// A Thursday; its ISO week runs Mon 2026-07-20 … Sun 2026-07-26.
const THURSDAY = '2026-07-23';

function task(id: number, dueDate: string, energyDemand?: EnergyDemand, archived = false): TaskItem {
  return {
    id,
    code: `T-${id}`,
    stepId: 1,
    title: `Task ${id}`,
    owner: 'Owner',
    priority: 'MEDIUM',
    dueDate,
    status: 'NOT_STARTED',
    progressPercent: 0,
    energyDemand,
    archived,
  };
}

describe('isoWeekRange', () => {
  it('returns the Monday–Sunday week containing the date', () => {
    expect(isoWeekRange(THURSDAY)).toEqual({ start: '2026-07-20', end: '2026-07-26' });
  });

  it('keeps a Sunday inside its own week', () => {
    expect(isoWeekRange('2026-07-26')).toEqual({ start: '2026-07-20', end: '2026-07-26' });
  });
});

describe('checkDrainOvercommit', () => {
  it('returns null for a non-DRAIN task', () => {
    expect(checkDrainOvercommit(THURSDAY, 'CHARGE', null, [])).toBeNull();
  });

  it('returns null when the task has no due date', () => {
    expect(checkDrainOvercommit(undefined, 'DRAIN', null, [])).toBeNull();
  });

  it('does not prompt below the threshold', () => {
    // Two existing drains + the one just saved = 3 drains, but one charge nets
    // it to 2, below the threshold of 3.
    const tasks = [task(1, THURSDAY, 'DRAIN'), task(2, '2026-07-21', 'DRAIN'), task(3, '2026-07-22', 'CHARGE')];
    expect(checkDrainOvercommit(THURSDAY, 'DRAIN', null, tasks)).toBeNull();
  });

  it('prompts and lists the other drain tasks once the week tips over', () => {
    // Two existing drains + the saved one = 3, no charges → net 3 ≥ threshold.
    const tasks = [task(1, '2026-07-20', 'DRAIN'), task(2, '2026-07-21', 'DRAIN')];
    const result = checkDrainOvercommit(THURSDAY, 'DRAIN', null, tasks);
    expect(result).not.toBeNull();
    expect(result?.weekStart).toBe('2026-07-20');
    expect(result?.candidates.map((candidate) => candidate.id)).toEqual([1, 2]);
    expect(DRAIN_WEEK_THRESHOLD).toBe(3);
  });

  it('excludes the edited task itself (by id) so it is not double-counted', () => {
    // Editing task 1 (already a DRAIN in the list) to DRAIN: it is excluded by
    // id then re-counted once, so with one other drain the net is 2, no prompt.
    const tasks = [task(1, THURSDAY, 'DRAIN'), task(2, '2026-07-21', 'DRAIN')];
    expect(checkDrainOvercommit(THURSDAY, 'DRAIN', 1, tasks)).toBeNull();
  });

  it('ignores tasks in other weeks and archived tasks', () => {
    const tasks = [
      task(1, '2026-07-13', 'DRAIN'), // previous week
      task(2, '2026-07-27', 'DRAIN'), // next week
      task(3, THURSDAY, 'DRAIN', true), // archived
    ];
    // Only the saved task counts this week → net 1, no prompt.
    expect(checkDrainOvercommit(THURSDAY, 'DRAIN', null, tasks)).toBeNull();
  });
});
