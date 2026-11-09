import { describe, expect, it } from 'vitest';
import { aDream, aGoal, aStep, aTask, aVisionArea } from '../test/builders';
import {
  buildDreamTimeline, buildMarkers, buildPlanTimeline, busyCounts, defaultExpanded, editLinkFor, expandedForDream, filterTimelineRows, fromDay, parseEditTarget,
  toDay, visibleRows,
} from './timeline';

const TODAY = toDay('2026-12-15');

function rowOf(rows: ReturnType<typeof buildDreamTimeline>['rows'], key: string) {
  const row = rows.find((item) => item.key === key);
  if (!row) throw new Error(`no row ${key}`);
  return { ...row, startDate: fromDay(row.start), endDate: fromDay(row.end) };
}

describe('timeline date rules (BRD 16.0.0)', () => {
  const dream = aDream({ id: 1, targetDate: '2027-06-30', commitmentStartDate: '2026-11-01' });
  const goal = aGoal({ id: 10, dreamId: 1, targetDate: '2027-03-31' });

  it('draws a task from its start to its due date, or as one day on its due date (TLC-02)', () => {
    const step = aStep({ id: 20, goalId: 10, targetDate: '2027-01-31' });
    const { rows } = buildDreamTimeline(dream, [goal], [step], [
      aTask({ id: 30, stepId: 20, startDate: '2026-12-03', dueDate: '2026-12-10' }),
      aTask({ id: 31, stepId: 20, dueDate: '2026-12-20' }),
    ], TODAY);

    expect(rowOf(rows, 'task-30')).toMatchObject({ startDate: '2026-12-03', endDate: '2026-12-10', startWorkedOut: null });
    expect(rowOf(rows, 'task-31')).toMatchObject({ startDate: '2026-12-20', endDate: '2026-12-20', startWorkedOut: 'task-no-start' });
  });

  it('starts a step with no tasks on the day after the previous step ends (TLC-03)', () => {
    const { rows } = buildDreamTimeline(dream, [goal], [
      aStep({ id: 20, goalId: 10, sequenceNumber: 1, targetDate: '2026-12-05' }),
      aStep({ id: 21, goalId: 10, sequenceNumber: 2, targetDate: '2026-12-20' }),
    ], [aTask({ id: 30, stepId: 20, startDate: '2026-12-01', dueDate: '2026-12-05' })], TODAY);

    expect(rowOf(rows, 'step-20')).toMatchObject({ startDate: '2026-12-01', startWorkedOut: 'from-first-task' });
    expect(rowOf(rows, 'step-21')).toMatchObject({ startDate: '2026-12-06', endDate: '2026-12-20', startWorkedOut: 'after-previous-step' });
  });

  it('starts the dream on its commitment date, else on its first goal (TLC-04)', () => {
    const step = aStep({ id: 20, goalId: 10 });
    const task = aTask({ id: 30, stepId: 20, startDate: '2026-12-01', dueDate: '2026-12-05' });

    const committed = buildDreamTimeline(dream, [goal], [step], [task], TODAY);
    expect(rowOf(committed.rows, 'dream-1')).toMatchObject({ startDate: '2026-11-01', startWorkedOut: null });

    const uncommitted = buildDreamTimeline(aDream({ id: 1, targetDate: '2027-06-30', commitmentStartDate: null }), [goal], [step], [task], TODAY);
    expect(rowOf(uncommitted.rows, 'dream-1')).toMatchObject({ startDate: '2026-12-01', startWorkedOut: 'from-first-goal' });
    expect(rowOf(uncommitted.rows, 'goal-10')).toMatchObject({ startDate: '2026-12-01', startWorkedOut: 'from-first-step' });
  });

  it('falls back to the day the dream was created when nothing else has a date', () => {
    const { rows } = buildDreamTimeline(aDream({ id: 1, commitmentStartDate: null, createdAt: '2026-10-10T08:00:00Z' }), [], [], [], TODAY);
    expect(rowOf(rows, 'dream-1')).toMatchObject({ startDate: '2026-10-10', startWorkedOut: 'from-created' });
  });

  it('marks a task overdue only when it is past due and not completed (TLC-05)', () => {
    const step = aStep({ id: 20, goalId: 10 });
    const { rows } = buildDreamTimeline(dream, [goal], [step], [
      aTask({ id: 30, stepId: 20, dueDate: '2026-12-14', status: 'IN_PROGRESS' }),
      aTask({ id: 31, stepId: 20, dueDate: '2026-12-14', status: 'COMPLETED' }),
      aTask({ id: 32, stepId: 20, dueDate: '2026-12-15', status: 'NOT_STARTED' }),
    ], TODAY);

    expect(rowOf(rows, 'task-30').overdueDays).toBe(1);
    expect(rowOf(rows, 'task-31').overdueDays).toBe(0);
    expect(rowOf(rows, 'task-32').overdueDays).toBe(0);
  });

  it('carries a blocked task and its reason (TLC-06)', () => {
    const { rows } = buildDreamTimeline(dream, [goal], [aStep({ id: 20, goalId: 10 })], [
      aTask({ id: 30, stepId: 20, status: 'BLOCKED', blockerReason: 'No budget' }),
    ], TODAY);
    expect(rowOf(rows, 'task-30')).toMatchObject({ blocked: true, blockerReason: 'No budget' });
  });

  it('hatches the part of a goal that runs past its dream and shows the overrun text (TLC-07)', () => {
    const fixedDream = aDream({
      id: 1, targetDate: '2027-01-10', scheduleMode: 'TOP_DOWN_FIXED', scheduleOverrun: true,
      scheduleOverrunDetail: 'Goal G-001 ends 20 days after this dream.', commitmentStartDate: '2026-11-01',
    });
    const lateGoal = aGoal({ id: 10, dreamId: 1, targetDate: '2027-01-30' });
    const { rows } = buildDreamTimeline(fixedDream, [lateGoal], [], [], TODAY);

    expect(fromDay(rowOf(rows, 'goal-10').overrunFrom!)).toBe('2027-01-11');
    expect(rowOf(rows, 'dream-1').overrunDetail).toBe('Goal G-001 ends 20 days after this dream.');
  });

  it('lists a step with no date and no tasks as undated instead of guessing (TLC-08)', () => {
    const { rows, undated } = buildDreamTimeline(dream, [goal], [aStep({ id: 20, goalId: 10, targetDate: undefined })], [], TODAY);
    expect(rows.some((row) => row.key === 'step-20')).toBe(false);
    expect(undated).toEqual([expect.objectContaining({ key: 'step-20', kind: 'step', code: 'S-001' })]);
  });

  it('works out a missing end from the children and says so', () => {
    const { rows } = buildDreamTimeline(dream, [aGoal({ id: 10, dreamId: 1, targetDate: undefined })], [
      aStep({ id: 20, goalId: 10, targetDate: undefined }),
    ], [aTask({ id: 30, stepId: 20, dueDate: '2027-02-01' })], TODAY);
    expect(rowOf(rows, 'step-20')).toMatchObject({ endDate: '2027-02-01', endWorkedOut: 'from-last-task' });
    expect(rowOf(rows, 'goal-10')).toMatchObject({ endDate: '2027-02-01', endWorkedOut: 'from-last-step' });
  });

  it('never lets a worked-out start run past the end', () => {
    const { rows } = buildDreamTimeline(aDream({ id: 1, commitmentStartDate: '2027-05-01', targetDate: '2027-06-30' }),
      [aGoal({ id: 10, dreamId: 1, targetDate: '2027-01-31' })], [], [], TODAY);
    expect(rowOf(rows, 'goal-10')).toMatchObject({ startDate: '2027-01-31', endDate: '2027-01-31' });
  });

  it('keeps rows in the tree order: goals by sort order, steps by sequence, tasks by sort order (TL-FR-07)', () => {
    const { rows } = buildDreamTimeline(dream, [
      aGoal({ id: 11, dreamId: 1, sortOrder: 1 }), aGoal({ id: 10, dreamId: 1, sortOrder: 0 }),
    ], [aStep({ id: 21, goalId: 10, sequenceNumber: 2 }), aStep({ id: 20, goalId: 10, sequenceNumber: 1 })], [], TODAY);
    expect(rows.map((row) => row.key)).toEqual(['dream-1', 'goal-10', 'step-20', 'step-21', 'goal-11']);
  });
});

describe('timeline filters and folding', () => {
  const dream = aDream({ id: 1, commitmentStartDate: '2026-11-01' });
  const { rows } = buildDreamTimeline(dream, [aGoal({ id: 10, dreamId: 1 }), aGoal({ id: 11, dreamId: 1 })], [
    aStep({ id: 20, goalId: 10 }), aStep({ id: 21, goalId: 11 }),
  ], [
    aTask({ id: 30, stepId: 20, status: 'BLOCKED', blockerReason: 'No budget' }),
    aTask({ id: 31, stepId: 21, status: 'COMPLETED' }),
  ], TODAY);

  it('keeps a matching row with its ancestors and drops the rest (TLC-10)', () => {
    const keys = filterTimelineRows(rows, { status: 'BLOCKED' }).map((row) => row.key);
    expect(keys).toEqual(['dream-1', 'goal-10', 'step-20', 'task-30']);
  });

  it('hides completed rows when asked', () => {
    const keys = filterTimelineRows(rows, { showCompleted: false }).map((row) => row.key);
    expect(keys).not.toContain('task-31');
    expect(keys).toContain('task-30');
  });

  it('opens with goals and steps showing and tasks folded away (TL-FR-25)', () => {
    const keys = visibleRows(rows, defaultExpanded(rows)).map((row) => row.key);
    expect(keys).toEqual(['dream-1', 'goal-10', 'step-20', 'goal-11', 'step-21']);
  });
});

describe('every dream on one timeline (TL-FR-32 to TL-FR-34)', () => {
  const areas = [
    aVisionArea({ id: 1, name: 'Health', priority: 'LOW' }),
    aVisionArea({ id: 2, name: 'Career', priority: 'HIGH' }),
  ];
  const dreams = [
    aDream({ id: 1, code: 'D-001', visionAreaId: 1, title: 'Run a marathon', createdAt: '2026-10-01T00:00:00Z' }),
    aDream({ id: 2, code: 'D-002', visionAreaId: 2, title: 'Write a book', createdAt: '2026-10-01T00:00:00Z' }),
    aDream({ id: 3, code: 'D-003', visionAreaId: 2, title: 'Lead a team', letterRank: 'A', createdAt: '2026-10-01T00:00:00Z' }),
    aDream({ id: 4, code: 'D-004', visionAreaId: 99, title: 'Learn to sail', createdAt: '2026-10-01T00:00:00Z' }),
  ];
  const goals = [aGoal({ id: 10, dreamId: 3 })];

  it('groups dreams by vision area: areas by priority, dreams by letter rank, unknown areas last', () => {
    const { rows } = buildPlanTimeline(areas, dreams, goals, [], [], TODAY, 'Unassigned');
    const dreamRows = rows.filter((row) => row.kind === 'dream');
    expect(dreamRows.map((row) => [row.groupLabel, row.title])).toEqual([
      ['Career', 'Lead a team'],
      ['Career', 'Write a book'],
      ['Health', 'Run a marathon'],
      ['Unassigned', 'Learn to sail'],
    ]);
    expect(rows.find((row) => row.key === 'goal-10')).toMatchObject({ dreamId: 3, groupKey: 'area-2' });
  });

  it('opens on one dream with its goals showing and every other dream folded', () => {
    const { rows } = buildPlanTimeline(areas, dreams, goals, [aStep({ id: 20, goalId: 10 })], [], TODAY);
    const expanded = expandedForDream(rows, 3);
    expect([...expanded].sort()).toEqual(['dream-3', 'goal-10']);
    expect(visibleRows(rows, expanded).map((row) => row.key)).toEqual(['dream-3', 'goal-10', 'step-20', 'dream-2', 'dream-1', 'dream-4']);
  });

  it('links a bar to its dream map with the record to edit, and reads that back', () => {
    const link = editLinkFor({ kind: 'step', id: 20, dreamId: 3 });
    expect(link).toBe('/dreams/3?view=timeline&edit=step-20');
    expect(parseEditTarget('step-20')).toEqual({ kind: 'step', id: 20 });
    expect(parseEditTarget('area-2')).toBeNull();
    expect(parseEditTarget(null)).toBeNull();
  });
});

describe('marks and busy periods (TL-FR-13, TL-FR-15, TL-FR-35)', () => {
  const dream = aDream({ id: 1, commitmentStartDate: '2026-11-01' });
  const other = aDream({ id: 2 });
  const goals = [aGoal({ id: 10, dreamId: 1 })];
  const steps = [aStep({ id: 20, goalId: 10 })];
  const tasks = [aTask({ id: 30, stepId: 20 })];

  it('collects commitment, milestones, dream reviews and open follow-ups for the dreams shown', () => {
    const markers = buildMarkers([dream], goals, steps, tasks,
      [
        { id: 1, dreamId: 1, title: 'First draft done', achievedOn: '2026-12-01' } as never,
        { id: 2, dreamId: 2, title: 'Another dream', achievedOn: '2026-12-02' } as never,
      ],
      [
        { id: 5, reviewType: 'WEEKLY', reviewDate: '2026-11-20T09:00:00', relatedDreamId: 1, archived: false } as never,
        { id: 6, reviewType: 'DAILY', reviewDate: '2026-11-21T09:00:00', archived: false } as never,
      ],
      [
        { id: 7, relatedTaskId: 30, subject: 'Ask for data', status: 'SENT', followUpDate: '2026-11-25', archived: false } as never,
        { id: 8, relatedGoalId: 10, subject: 'Thanks', status: 'REPLIED', followUpDate: '2026-11-26', archived: false } as never,
      ]);
    expect(markers.map((marker) => [marker.type, fromDay(marker.day), marker.label])).toEqual([
      ['commitment', '2026-11-01', 'Commitment start'],
      ['review', '2026-11-20', 'WEEKLY'],
      ['follow-up', '2026-11-25', 'Ask for data'],
      ['milestone', '2026-12-01', 'First draft done'],
    ]);
    expect(buildMarkers([other], goals, steps, tasks, [], [], []).length).toBe(0);
  });

  it('keeps the day a task was really completed (TL-FR-15)', () => {
    const { rows } = buildDreamTimeline(dream, goals, steps, [
      aTask({ id: 30, stepId: 20, dueDate: '2026-12-10', status: 'COMPLETED', completedAt: '2026-12-07T10:00:00Z' }),
      aTask({ id: 31, stepId: 20, dueDate: '2026-12-10', status: 'IN_PROGRESS', completedAt: null }),
    ], TODAY);
    expect(fromDay(rows.find((row) => row.key === 'task-30')!.completedOn!)).toBe('2026-12-07');
    expect(rows.find((row) => row.key === 'task-31')!.completedOn).toBeNull();
  });

  it('counts unfinished goals, steps and tasks in every period their bar touches', () => {
    const { rows } = buildDreamTimeline(dream, goals, steps, [
      aTask({ id: 30, stepId: 20, startDate: '2026-11-05', dueDate: '2026-12-05' }),
      aTask({ id: 31, stepId: 20, startDate: '2026-11-10', dueDate: '2026-11-12', status: 'COMPLETED' }),
    ], TODAY);
    const november = { start: toDay('2026-11-01'), days: 30 };
    const december = { start: toDay('2026-12-01'), days: 31 };
    const counts = busyCounts(rows.filter((row) => row.kind === 'task'), [november, december]);
    expect(counts).toEqual([1, 1]);
  });
});

