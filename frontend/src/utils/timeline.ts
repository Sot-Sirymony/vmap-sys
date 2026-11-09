import type { Dream, Goal, TaskItem, VisionStep } from '../types/vision';

/**
 * V16 (BRD 16.0.0, TL-FR-07 to TL-FR-20): one dream's goals, steps and tasks
 * as timeline rows. Pure functions, so the date rules are tested on their own.
 *
 * Dates are calendar dates ("YYYY-MM-DD") handled as whole days since the
 * epoch in UTC, so no time zone can shift a bar by a day (TL-NFR-02).
 */

export type TimelineKind = 'dream' | 'goal' | 'step' | 'task';

/** Why a start or end was worked out instead of read from the record (TL-FR-10). */
export type WorkedOutReason =
  | 'task-no-start'
  | 'from-first-task'
  | 'after-previous-step'
  | 'from-goal'
  | 'from-first-step'
  | 'from-dream'
  | 'from-first-goal'
  | 'from-created'
  | 'from-last-task'
  | 'from-last-step'
  | 'from-last-goal'
  | 'clamped-to-end';

export type TimelineRow = {
  key: string;
  kind: TimelineKind;
  id: number;
  depth: number;
  parentKey: string | null;
  code: string;
  title: string;
  /** Day number (days since 1970-01-01, UTC). */
  start: number;
  end: number;
  startWorkedOut: WorkedOutReason | null;
  endWorkedOut: WorkedOutReason | null;
  status: string;
  priority: string;
  progress: number;
  owner: string | null;
  /** Days past its own stored end date while not completed (business rule 9); 0 when not overdue. */
  overdueDays: number;
  blocked: boolean;
  blockerReason: string | null;
  /** Day after the parent's end, when this row runs past it (BR-40 overrun); otherwise null. */
  overrunFrom: number | null;
  /** The parent's own overrun text, shown on dream and goal rows (TL-FR-20). */
  overrunDetail: string | null;
  archived: boolean;
  hasChildren: boolean;
};

/** A record with no usable date: listed, never guessed onto the calendar (TL-FR-14). */
export type UndatedRow = { key: string; kind: TimelineKind; id: number; code: string; title: string };

export type DreamTimeline = { rows: TimelineRow[]; undated: UndatedRow[] };

export type TimelineFilters = {
  priority?: string;
  status?: string;
  /** TL-FR-26: when false, completed rows are hidden (their parents stay if they lead to other rows). */
  showCompleted?: boolean;
};

const DAY_MS = 86_400_000;

/** "YYYY-MM-DD" (or an ISO instant, whose date part is used) to a day number. */
export function toDay(date: string): number {
  const [year, month, day] = date.slice(0, 10).split('-').map(Number);
  return Math.floor(Date.UTC(year, month - 1, day) / DAY_MS);
}

/** A day number back to "YYYY-MM-DD". */
export function fromDay(day: number): string {
  return new Date(day * DAY_MS).toISOString().slice(0, 10);
}

/** Today in the participant's own time zone, as a day number. */
export function todayDay(now: Date = new Date()): number {
  return Math.floor(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / DAY_MS);
}

/** An instant (e.g. createdAt) to the participant's local calendar day. */
function localDayOf(instant: string): number {
  return todayDay(new Date(instant));
}

function minOf(values: (number | null)[]): number | null {
  const present = values.filter((value): value is number => value !== null);
  return present.length ? Math.min(...present) : null;
}

function maxOf(values: (number | null)[]): number | null {
  const present = values.filter((value): value is number => value !== null);
  return present.length ? Math.max(...present) : null;
}

function byOrder<T extends { sortOrder?: number | null; id: number }>(a: T, b: T) {
  return (a.sortOrder ?? Number.MAX_SAFE_INTEGER) - (b.sortOrder ?? Number.MAX_SAFE_INTEGER) || a.id - b.id;
}

function isCompleted(status: string) {
  return status === 'COMPLETED';
}

type Span = { start: number | null; end: number | null; startWhy: WorkedOutReason | null; endWhy: WorkedOutReason | null };

/**
 * Builds the rows for one dream, in the tree's order (TL-FR-07): goals by
 * sort order, steps by sequence number, tasks by sort order.
 */
export function buildDreamTimeline(
  dream: Dream,
  goals: Goal[],
  steps: VisionStep[],
  tasks: TaskItem[],
  today: number = todayDay(),
): DreamTimeline {
  const dreamGoals = goals.filter((goal) => goal.dreamId === dream.id).sort(byOrder);
  const stepsByGoal = new Map<number, VisionStep[]>();
  for (const goal of dreamGoals) {
    stepsByGoal.set(goal.id, steps
      .filter((step) => step.goalId === goal.id)
      .sort((a, b) => a.sequenceNumber - b.sequenceNumber || a.id - b.id));
  }
  const tasksByStep = new Map<number, TaskItem[]>();
  for (const goalSteps of stepsByGoal.values()) {
    for (const step of goalSteps) {
      tasksByStep.set(step.id, tasks.filter((task) => task.stepId === step.id).sort(byOrder));
    }
  }

  // --- 1. Tasks: start is startDate, else a one-day bar on the due date (TL-FR-08).
  const taskSpan = new Map<number, Span>();
  for (const stepTasks of tasksByStep.values()) {
    for (const task of stepTasks) {
      const end = toDay(task.dueDate);
      taskSpan.set(task.id, task.startDate
        ? { start: toDay(task.startDate), end, startWhy: null, endWhy: null }
        : { start: end, end, startWhy: 'task-no-start', endWhy: null });
    }
  }

  // --- 2. Steps' own start: first task, else the day after the previous step's target date.
  const stepOwnStart = new Map<number, { day: number; why: WorkedOutReason } | null>();
  const stepEnd = new Map<number, { day: number | null; why: WorkedOutReason | null }>();
  for (const goalSteps of stepsByGoal.values()) {
    goalSteps.forEach((step, index) => {
      const childStarts = (tasksByStep.get(step.id) ?? []).map((task) => taskSpan.get(task.id)?.start ?? null);
      const firstTask = minOf(childStarts);
      const previous = index > 0 ? goalSteps[index - 1] : undefined;
      if (firstTask !== null) {
        stepOwnStart.set(step.id, { day: firstTask, why: 'from-first-task' });
      } else if (previous?.targetDate) {
        stepOwnStart.set(step.id, { day: toDay(previous.targetDate) + 1, why: 'after-previous-step' });
      } else {
        stepOwnStart.set(step.id, null);
      }
      const lastTask = maxOf((tasksByStep.get(step.id) ?? []).map((task) => taskSpan.get(task.id)?.end ?? null));
      stepEnd.set(step.id, step.targetDate
        ? { day: toDay(step.targetDate), why: null }
        : { day: lastTask, why: lastTask === null ? null : 'from-last-task' });
    });
  }

  // --- 3. Goals' own start: first step's own start. End: target date, else last step end.
  const goalOwnStart = new Map<number, number | null>();
  const goalEnd = new Map<number, { day: number | null; why: WorkedOutReason | null }>();
  for (const goal of dreamGoals) {
    const goalSteps = stepsByGoal.get(goal.id) ?? [];
    goalOwnStart.set(goal.id, minOf(goalSteps.map((step) => stepOwnStart.get(step.id)?.day ?? null)));
    const lastStep = maxOf(goalSteps.map((step) => stepEnd.get(step.id)?.day ?? null));
    goalEnd.set(goal.id, goal.targetDate
      ? { day: toDay(goal.targetDate), why: null }
      : { day: lastStep, why: lastStep === null ? null : 'from-last-step' });
  }

  // --- 4. Dream: commitment start, else first goal, else the day it was created.
  const firstGoal = minOf(dreamGoals.map((goal) => goalOwnStart.get(goal.id) ?? null));
  let dreamStart: number | null;
  let dreamStartWhy: WorkedOutReason | null;
  if (dream.commitmentStartDate) {
    dreamStart = toDay(dream.commitmentStartDate);
    dreamStartWhy = null;
  } else if (firstGoal !== null) {
    dreamStart = firstGoal;
    dreamStartWhy = 'from-first-goal';
  } else if (dream.createdAt) {
    dreamStart = localDayOf(dream.createdAt);
    dreamStartWhy = 'from-created';
  } else {
    dreamStart = null;
    dreamStartWhy = null;
  }
  const lastGoal = maxOf(dreamGoals.map((goal) => goalEnd.get(goal.id)?.day ?? null));
  const dreamEnd = dream.targetDate ? toDay(dream.targetDate) : lastGoal;
  const dreamEndWhy: WorkedOutReason | null = dream.targetDate ? null : (lastGoal === null ? null : 'from-last-goal');

  // --- 5. Assemble rows; fall back down the chain for starts, and list anything still undated.
  const rows: TimelineRow[] = [];
  const undated: UndatedRow[] = [];

  function push(row: Omit<TimelineRow, 'start' | 'end' | 'startWorkedOut' | 'endWorkedOut' | 'overdueDays' | 'overrunFrom'>,
                span: Span, storedEnd: boolean, parentEnd: number | null) {
    if (span.start === null || span.end === null) {
      undated.push({ key: row.key, kind: row.kind, id: row.id, code: row.code, title: row.title });
      return false;
    }
    let { start, startWhy } = span;
    // A worked-out start never runs past the end; it becomes a one-day bar.
    if (start > span.end) {
      start = span.end;
      startWhy = startWhy ?? 'clamped-to-end';
    }
    const overdueDays = storedEnd && span.end < today && !isCompleted(row.status) ? today - span.end : 0;
    const overrunFrom = parentEnd !== null && span.end > parentEnd ? parentEnd + 1 : null;
    rows.push({ ...row, start, end: span.end, startWorkedOut: startWhy, endWorkedOut: span.endWhy, overdueDays, overrunFrom });
    return true;
  }

  const dreamKey = `dream-${dream.id}`;
  const dreamProgress = dreamGoals.length
    ? Math.round(dreamGoals.reduce((sum, goal) => sum + goal.progressPercent, 0) / dreamGoals.length)
    : 0;
  push({
    key: dreamKey, kind: 'dream', id: dream.id, depth: 0, parentKey: null, code: dream.code, title: dream.title,
    status: dream.status, priority: dream.priority, progress: dreamProgress, owner: null, blocked: false, blockerReason: null,
    overrunDetail: dream.scheduleOverrun ? dream.scheduleOverrunDetail ?? null : null, archived: dream.archived,
    hasChildren: dreamGoals.length > 0,
  }, { start: dreamStart, end: dreamEnd, startWhy: dreamStartWhy, endWhy: dreamEndWhy }, Boolean(dream.targetDate), null);

  for (const goal of dreamGoals) {
    const goalKey = `goal-${goal.id}`;
    const own = goalOwnStart.get(goal.id) ?? null;
    const end = goalEnd.get(goal.id) ?? { day: null, why: null };
    const goalStart = own ?? dreamStart;
    const goalSteps = stepsByGoal.get(goal.id) ?? [];
    push({
      key: goalKey, kind: 'goal', id: goal.id, depth: 1, parentKey: dreamKey, code: goal.code, title: goal.title,
      status: goal.status, priority: goal.priority, progress: goal.progressPercent, owner: null,
      blocked: goal.status === 'BLOCKED', blockerReason: null,
      overrunDetail: goal.scheduleOverrun ? goal.scheduleOverrunDetail ?? null : null, archived: goal.archived,
      hasChildren: goalSteps.length > 0,
    }, {
      start: goalStart, end: end.day,
      startWhy: own !== null ? 'from-first-step' : (goalStart !== null ? 'from-dream' : null), endWhy: end.why,
    }, Boolean(goal.targetDate), dreamEnd);

    for (const step of goalSteps) {
      const stepKey = `step-${step.id}`;
      const ownStart = stepOwnStart.get(step.id) ?? null;
      const stepStart = ownStart?.day ?? goalStart;
      const stepEndInfo = stepEnd.get(step.id) ?? { day: null, why: null };
      const stepTasks = tasksByStep.get(step.id) ?? [];
      push({
        key: stepKey, kind: 'step', id: step.id, depth: 2, parentKey: goalKey, code: step.code, title: step.title,
        status: step.status, priority: step.priority, progress: step.progressPercent, owner: step.owner,
        blocked: step.status === 'BLOCKED', blockerReason: null, overrunDetail: null, archived: step.archived,
        hasChildren: stepTasks.length > 0,
      }, {
        start: stepStart, end: stepEndInfo.day,
        startWhy: ownStart?.why ?? (stepStart !== null ? 'from-goal' : null), endWhy: stepEndInfo.why,
      }, Boolean(step.targetDate), end.day);

      for (const task of stepTasks) {
        const span = taskSpan.get(task.id);
        if (!span) continue;
        push({
          key: `task-${task.id}`, kind: 'task', id: task.id, depth: 3, parentKey: stepKey, code: task.code, title: task.title,
          status: task.status, priority: task.priority, progress: task.progressPercent, owner: task.owner,
          blocked: task.status === 'BLOCKED', blockerReason: task.blockerReason ?? null, overrunDetail: null,
          archived: task.archived, hasChildren: false,
        }, span, true, stepEndInfo.day);
      }
    }
  }

  return { rows, undated };
}

/**
 * TL-FR-26: the map's priority and status filters, plus "show completed". A
 * row stays when it matches or leads to a row that does, so a match is never
 * shown without its ancestry. The dream row always stays.
 */
export function filterTimelineRows(rows: TimelineRow[], filters: TimelineFilters): TimelineRow[] {
  const { priority = '', status = '', showCompleted = true } = filters;
  if (!priority && !status && showCompleted) {
    return rows;
  }
  const matches = (row: TimelineRow) =>
    (!priority || row.priority === priority)
    && (!status || row.status === status)
    && (showCompleted || !isCompleted(row.status));
  const byKey = new Map(rows.map((row) => [row.key, row]));
  const keep = new Set<string>();
  for (const row of rows) {
    if (row.kind === 'dream') {
      keep.add(row.key);
      continue;
    }
    if (!matches(row)) continue;
    let current: TimelineRow | undefined = row;
    while (current && !keep.has(current.key)) {
      keep.add(current.key);
      current = current.parentKey ? byKey.get(current.parentKey) : undefined;
    }
  }
  return rows.filter((row) => keep.has(row.key));
}

/** Rows whose ancestors are all expanded (TL-FR-25). */
export function visibleRows(rows: TimelineRow[], expanded: ReadonlySet<string>): TimelineRow[] {
  const byKey = new Map(rows.map((row) => [row.key, row]));
  return rows.filter((row) => {
    let parentKey = row.parentKey;
    while (parentKey) {
      // A parent that could not be dated is not drawn; its children answer to the next one up.
      if (byKey.has(parentKey) && !expanded.has(parentKey)) return false;
      parentKey = byKey.get(parentKey)?.parentKey ?? null;
    }
    return true;
  });
}

/** TL-FR-25: the single-dream view opens with goals and steps showing, tasks folded away. */
export function defaultExpanded(rows: TimelineRow[]): Set<string> {
  return new Set(rows.filter((row) => row.kind === 'dream' || row.kind === 'goal').map((row) => row.key));
}
