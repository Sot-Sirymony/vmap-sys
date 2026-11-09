import { describe, expect, it } from 'vitest';
import { aDream, aGoal, aStep, aTask } from '../test/builders';
import { buildDreamTimeline, defaultExpanded, filterTimelineRows, fromDay, toDay, visibleRows } from './timeline';

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
