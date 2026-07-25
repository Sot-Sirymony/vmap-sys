import type { TaskItem } from '../types/vision';

// FR-34.4: a week counts as "heavily draining" once it has this many more
// DRAIN tasks than CHARGE tasks. Coaching threshold only — never a save gate.
export const DRAIN_WEEK_THRESHOLD = 3;

export type OvercommitResult = {
  weekStart: string;
  weekEnd: string;
  /** The week's OTHER draining tasks — candidates to drop or move. */
  candidates: TaskItem[];
};

// The ISO week (Monday–Sunday) containing a yyyy-mm-dd date, matching the
// backend Energy Budget window (BR-28) so the two never disagree.
export function isoWeekRange(dateIso: string): { start: string; end: string } {
  const date = new Date(`${dateIso}T00:00:00`);
  const mondayOffset = (date.getDay() + 6) % 7; // 0 = Monday
  const monday = new Date(date);
  monday.setDate(date.getDate() - mondayOffset);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return { start: toIso(monday), end: toIso(sunday) };
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function inRange(dateIso: string | undefined, start: string, end: string): boolean {
  return Boolean(dateIso) && dateIso! >= start && dateIso! <= end;
}

/**
 * FR-34.4: after a DRAIN task is saved, decide whether its week is now heavily
 * draining and, if so, which other DRAIN tasks are candidates to drop or move.
 * Returns null when no prompt is warranted (below threshold, or the saved task
 * isn't a dated DRAIN task). Pure — the caller decides how to surface it, and
 * it never blocks the save (FR-34.5).
 *
 * `savedTaskId` is the just-saved task's id on edit (so it isn't double-counted
 * against its own stale copy in `tasks`), or null on create. Either way the
 * saved task is counted once by adding one to the week's other-drain total.
 */
export function checkDrainOvercommit(
  savedDueDate: string | undefined,
  savedEnergyDemand: string | undefined,
  savedTaskId: number | null,
  tasks: TaskItem[],
): OvercommitResult | null {
  if (savedEnergyDemand !== 'DRAIN' || !savedDueDate) {
    return null;
  }
  const { start, end } = isoWeekRange(savedDueDate);
  const active = tasks.filter(
    (task) => !task.archived && task.id !== savedTaskId && inRange(task.dueDate, start, end),
  );
  const otherDrains = active.filter((task) => task.energyDemand === 'DRAIN');
  const charges = active.filter((task) => task.energyDemand === 'CHARGE');
  const drainCount = otherDrains.length + 1; // include the task just saved
  if (drainCount - charges.length < DRAIN_WEEK_THRESHOLD) {
    return null;
  }
  return { weekStart: start, weekEnd: end, candidates: otherDrains };
}
