import type { TaskItem } from '../types/vision';

/**
 * V17 phase 4 (BL-FR-24 to BL-FR-28): when a task is stuck, the fix is to
 * break it down until the next action is easy to start.
 */

/** Today as yyyy-MM-dd, the same way isOverdue reads it. */
export function localToday(): string {
  return new Date().toISOString().slice(0, 10);
}

export const MIN_SPLIT_ROWS = 2;
export const MAX_SPLIT_ROWS = 5;

/** BL-FR-28: the third time a task is Blocked, suggest breaking it down or recording an obstacle. */
export const REPEATED_BLOCKS = 3;

/** BL-FR-24: Blocked, or In Progress past its due date. Archived and finished tasks never are. */
export function canBreakDown(task: TaskItem, today: string = localToday()): boolean {
  if (task.archived) {
    return false;
  }
  if (task.status === 'BLOCKED') {
    return true;
  }
  return task.status === 'IN_PROGRESS' && !!task.dueDate && task.dueDate < today;
}

export function isRepeatedlyBlocked(task: TaskItem): boolean {
  return !task.archived && task.status === 'BLOCKED' && (task.blockedCount ?? 0) >= REPEATED_BLOCKS;
}

function addDays(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/**
 * BL-FR-25: due dates spread evenly between today and the task's due date,
 * the last on the due date itself. A due date already past (or missing)
 * spreads the rows over the next week instead.
 */
export function spreadDueDates(count: number, dueDate: string | undefined, today: string = localToday()): string[] {
  const end = dueDate && dueDate > today ? dueDate : addDays(today, 7);
  const span = Math.round((Date.parse(`${end}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86_400_000);
  return Array.from({ length: count }, (_, index) => addDays(today, Math.round((span * (index + 1)) / count)));
}

/** BL-FR-27: leaving Blocked asks what changed. */
export function leavesBlocked(before: string | undefined, after: string): boolean {
  return before === 'BLOCKED' && after !== 'BLOCKED';
}
