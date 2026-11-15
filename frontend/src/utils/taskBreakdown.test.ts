import { describe, expect, it } from 'vitest';
import type { TaskItem } from '../types/vision';
import { canBreakDown, isRepeatedlyBlocked, leavesBlocked, spreadDueDates } from './taskBreakdown';

const base = { id: 1, stepId: 2, title: 'Write the report', owner: 'Me', priority: 'HIGH', dueDate: '2026-11-30',
  status: 'NOT_STARTED', progressPercent: 0, archived: false } as TaskItem;

describe('break it down (V17 BL-FR-24 to BL-FR-28)', () => {
  it('offers a breakdown for a Blocked task or one In Progress past its due date', () => {
    expect(canBreakDown({ ...base, status: 'BLOCKED' }, '2026-11-15')).toBe(true);
    expect(canBreakDown({ ...base, status: 'IN_PROGRESS', dueDate: '2026-11-10' }, '2026-11-15')).toBe(true);
    expect(canBreakDown({ ...base, status: 'IN_PROGRESS' }, '2026-11-15')).toBe(false);
    expect(canBreakDown({ ...base, status: 'NOT_STARTED', dueDate: '2026-11-10' }, '2026-11-15')).toBe(false);
    expect(canBreakDown({ ...base, status: 'BLOCKED', archived: true }, '2026-11-15')).toBe(false);
  });

  it('spreads due dates evenly up to the task due date', () => {
    expect(spreadDueDates(3, '2026-11-30', '2026-11-15')).toEqual(['2026-11-20', '2026-11-25', '2026-11-30']);
    // A past due date spreads over the next week instead.
    expect(spreadDueDates(2, '2026-11-01', '2026-11-15')).toEqual(['2026-11-19', '2026-11-22']);
  });

  it('flags a task blocked a third time', () => {
    expect(isRepeatedlyBlocked({ ...base, status: 'BLOCKED', blockedCount: 3 })).toBe(true);
    expect(isRepeatedlyBlocked({ ...base, status: 'BLOCKED', blockedCount: 2 })).toBe(false);
    expect(isRepeatedlyBlocked({ ...base, status: 'IN_PROGRESS', blockedCount: 5 })).toBe(false);
  });

  it('knows when a task leaves Blocked', () => {
    expect(leavesBlocked('BLOCKED', 'IN_PROGRESS')).toBe(true);
    expect(leavesBlocked('BLOCKED', 'BLOCKED')).toBe(false);
    expect(leavesBlocked('WAITING', 'IN_PROGRESS')).toBe(false);
  });
});
