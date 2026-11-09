import type { CommunicationMessage, Dream, Goal, Milestone, Priority, Review, TaskItem, VisionArea, VisionStep } from '../types/vision';

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
  | 'from-today'
  | 'from-last-task'
  | 'from-last-step'
  | 'from-last-goal'
  | 'clamped-to-end';

export type TimelineRow = {
  key: string;
  kind: TimelineKind;
  id: number;
  /** The dream this row belongs to (its own id for a dream row). */
  dreamId: number;
  /** Set on every row of a multi-dream timeline: the vision area it is grouped under (TL-FR-33). */
  groupKey?: string;
  groupLabel?: string;
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
  /** Tasks only: the local day it was marked Completed (TL-FR-15). */
  completedOn: number | null;
};

/** A record with no usable date: listed, never guessed onto the calendar (TL-FR-14). */
export type UndatedRow = { key: string; kind: TimelineKind; id: number; dreamId: number; code: string; title: string };

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
    // Nothing says when it began (for example a shared plan, which carries
    // only target dates): it is drawn from today, and says so.
    dreamStart = today;
    dreamStartWhy = 'from-today';
  }
  const lastGoal = maxOf(dreamGoals.map((goal) => goalEnd.get(goal.id)?.day ?? null));
  const dreamEnd = dream.targetDate ? toDay(dream.targetDate) : lastGoal;
  const dreamEndWhy: WorkedOutReason | null = dream.targetDate ? null : (lastGoal === null ? null : 'from-last-goal');

  // --- 5. Assemble rows; fall back down the chain for starts, and list anything still undated.
  const rows: TimelineRow[] = [];
  const undated: UndatedRow[] = [];

  function push(fields: Omit<TimelineRow, 'start' | 'end' | 'startWorkedOut' | 'endWorkedOut' | 'overdueDays' | 'overrunFrom' | 'dreamId' | 'completedOn'>
                  & { completedOn?: number | null },
                span: Span, storedEnd: boolean, parentEnd: number | null) {
    const row = { ...fields, completedOn: fields.completedOn ?? null, dreamId: dream.id };
    if (span.start === null || span.end === null) {
      undated.push({ key: row.key, kind: row.kind, id: row.id, dreamId: dream.id, code: row.code, title: row.title });
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
    // Rolled-up progress arrives with decimals (e.g. 4.17); a bar shows whole percents.
    rows.push({ ...row, progress: Math.round(row.progress), start, end: span.end, startWorkedOut: startWhy, endWorkedOut: span.endWhy, overdueDays, overrunFrom });
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
          completedOn: task.status === 'COMPLETED' && task.completedAt ? localDayOf(task.completedAt) : null,
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

const PRIORITY_RANK: Record<Priority, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

/** The top dream first: lettered dreams by letter, then the rest in their usual order. */
function byLetterRank(dreams: Dream[]) {
  return dreams
    .map((dream, index) => ({ dream, index }))
    .sort((a, b) => {
      const left = a.dream.letterRank;
      const right = b.dream.letterRank;
      if (left && right) return left.localeCompare(right) || a.index - b.index;
      if (left) return -1;
      if (right) return 1;
      return a.index - b.index;
    })
    .map(({ dream }) => dream);
}

/**
 * TL-FR-32 to TL-FR-34: every dream on one timeline, grouped under its vision
 * area. Areas go by priority, then name, with dreams that have no known area
 * last; inside an area, dreams go by letter rank and then their usual order.
 */
export function buildPlanTimeline(
  areas: VisionArea[],
  dreams: Dream[],
  goals: Goal[],
  steps: VisionStep[],
  tasks: TaskItem[],
  today: number = todayDay(),
  unassignedLabel = 'Unassigned',
): DreamTimeline {
  const orderedAreas = [...areas].sort((a, b) =>
    (PRIORITY_RANK[a.priority as Priority] ?? 9) - (PRIORITY_RANK[b.priority as Priority] ?? 9) || a.name.localeCompare(b.name));
  const groups: { key: string; label: string; dreams: Dream[] }[] = orderedAreas.map((area) => ({
    key: `area-${area.id}`,
    label: area.name,
    dreams: byLetterRank(dreams.filter((dream) => dream.visionAreaId === area.id)),
  }));
  const known = new Set(areas.map((area) => area.id));
  groups.push({ key: 'area-none', label: unassignedLabel, dreams: byLetterRank(dreams.filter((dream) => !known.has(dream.visionAreaId))) });

  const rows: TimelineRow[] = [];
  const undated: UndatedRow[] = [];
  for (const group of groups) {
    for (const dream of group.dreams) {
      const one = buildDreamTimeline(dream, goals, steps, tasks, today);
      rows.push(...one.rows.map((row) => ({ ...row, groupKey: group.key, groupLabel: group.label })));
      undated.push(...one.undated);
    }
  }
  return { rows, undated };
}

/** TL-FR-34: open on one dream with its goals and steps showing, the rest folded. */
export function expandedForDream(rows: TimelineRow[], dreamId: number): Set<string> {
  return new Set(rows
    .filter((row) => row.dreamId === dreamId && (row.kind === 'dream' || row.kind === 'goal'))
    .map((row) => row.key));
}

/**
 * Where a bar on the overview or full timeline opens: the dream's map, which
 * opens that record's edit form from the `edit` value (TL-FR-27).
 */
export function editLinkFor(target: { kind: TimelineKind; id: number; dreamId: number }) {
  return `/dreams/${target.dreamId}?view=timeline&edit=${target.kind}-${target.id}`;
}

/** TL-FR-31: where "+" on the overview or full timeline opens: the dream's tree, on that row's add field. */
export function addLinkFor(target: { kind: TimelineKind; id: number; dreamId: number }) {
  return `/dreams/${target.dreamId}?add=${target.kind}-${target.id}`;
}

/** Reads an `edit` value ("goal-10") back; null when it is not one. */
export function parseEditTarget(value: string | null): { kind: TimelineKind; id: number } | null {
  const match = value?.match(/^(dream|goal|step|task)-(\d+)$/);
  return match ? { kind: match[1] as TimelineKind, id: Number(match[2]) } : null;
}

/** TL-FR-13: dated things that belong on a dream's row, drawn as small marks. */
export type TimelineMarkerType = 'commitment' | 'milestone' | 'review' | 'follow-up';

export type TimelineMarker = { key: string; type: TimelineMarkerType; dreamId: number; day: number; label: string };

export const MARKER_TYPES: TimelineMarkerType[] = ['commitment', 'milestone', 'review', 'follow-up'];

/**
 * The marks for these dreams: commitment start, milestones, reviews linked to
 * a dream, and follow-up dates of messages still waiting on an answer. A
 * message counts for a dream when it is linked to the dream, one of its goals,
 * or a task under one of its goals.
 */
export function buildMarkers(
  dreams: Dream[],
  goals: Goal[],
  steps: VisionStep[],
  tasks: TaskItem[],
  milestones: Milestone[],
  reviews: Review[],
  messages: CommunicationMessage[],
): TimelineMarker[] {
  const dreamIds = new Set(dreams.map((dream) => dream.id));
  const goalDream = new Map(goals.map((goal) => [goal.id, goal.dreamId]));
  const stepGoal = new Map(steps.map((step) => [step.id, step.goalId]));
  const taskDream = new Map(tasks.map((task) => [task.id, goalDream.get(stepGoal.get(task.stepId) ?? -1)]));
  const markers: TimelineMarker[] = [];

  for (const dream of dreams) {
    if (dream.commitmentStartDate) {
      markers.push({ key: `commitment-${dream.id}`, type: 'commitment', dreamId: dream.id, day: toDay(dream.commitmentStartDate), label: 'Commitment start' });
    }
  }
  for (const milestone of milestones) {
    if (dreamIds.has(milestone.dreamId) && milestone.achievedOn) {
      markers.push({ key: `milestone-${milestone.id}`, type: 'milestone', dreamId: milestone.dreamId, day: toDay(milestone.achievedOn), label: milestone.title });
    }
  }
  for (const review of reviews) {
    if (!review.archived && review.relatedDreamId && dreamIds.has(review.relatedDreamId) && review.reviewDate) {
      markers.push({ key: `review-${review.id}`, type: 'review', dreamId: review.relatedDreamId, day: localDayOf(review.reviewDate), label: review.reviewType });
    }
  }
  for (const message of messages) {
    if (message.archived || !message.followUpDate || message.status === 'CLOSED' || message.status === 'REPLIED') continue;
    const dreamId = message.relatedDreamId
      ?? (message.relatedGoalId ? goalDream.get(message.relatedGoalId) : undefined)
      ?? (message.relatedTaskId ? taskDream.get(message.relatedTaskId) : undefined);
    if (dreamId !== undefined && dreamIds.has(dreamId)) {
      markers.push({ key: `follow-up-${message.id}`, type: 'follow-up', dreamId, day: toDay(message.followUpDate), label: message.subject ?? '' });
    }
  }
  return markers.sort((a, b) => a.day - b.day);
}

/**
 * TL-FR-35: how many unfinished goals, steps and tasks are under way in each
 * period, for the busy band. A row counts in every period its bar touches.
 */
export function busyCounts(rows: TimelineRow[], periods: { start: number; days: number }[]): number[] {
  const work = rows.filter((row) => row.kind !== 'dream' && row.status !== 'COMPLETED');
  return periods.map((period) => {
    const periodEnd = period.start + period.days - 1;
    return work.filter((row) => row.start <= periodEnd && row.end >= period.start).length;
  });
}

