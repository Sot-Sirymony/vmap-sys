import { describe, expect, it } from 'vitest';
import type { Goal, VisionStep } from '../../types/vision';
import {
  addClarification, addGoals, addSteps, addTask, applyAssistantChange, canMove, mergeWithNext, moveItem, splitItem, countNew, emptyGoal, emptyStep, emptyTask, ensureStepContainer, goalFromSuggestion,
  isReplaceable, readiness, removeGoal, removeStep, replaceGoals, stepsFromSuggestions, syncWithPlan, taskFromSuggestion, updateGoal,
  updateTask,
} from './draftModel';

const goal = (id: number, dreamId = 5, archived = false) =>
  ({ id, dreamId, title: `Goal ${id}`, priority: 'HIGH', targetDate: '2027-01-01', archived }) as Goal;
const step = (id: number, goalId: number, complex = false) =>
  ({ id, goalId, title: `Step ${id}`, owner: 'Me', priority: 'MEDIUM', complex, archived: false, sequenceNumber: 1 }) as VisionStep;

describe('Build my plan draft (V11 Phase 2)', () => {
  it('starts from the dream\'s live goals and keeps new goals after them (PB-FR-03)', () => {
    const fresh = { ...emptyGoal(), title: 'New one' };
    const synced = syncWithPlan({ goals: [fresh] }, 5, [goal(1), goal(2, 5, true), goal(3, 9)]);
    expect(synced.goals.map((item) => item.existingId ?? item.title)).toEqual([1, 'New one']);
  });

  it('keeps new steps under an existing goal and drops a container whose goal is gone', () => {
    let content = syncWithPlan({ goals: [] }, 5, [goal(1), goal(2)]);
    content = addSteps(content, content.goals[0].key, [{ ...emptyStep(), title: 'Call bank' }]);
    const resynced = syncWithPlan(content, 5, [goal(1, 5), goal(2, 5, true)]);
    expect(resynced.goals).toHaveLength(1);
    expect(resynced.goals[0].steps[0].title).toBe('Call bank');
  });

  it('never removes an existing goal, and marks AI suggestions edited when changed (PB-FR-35)', () => {
    let content = syncWithPlan({ goals: [] }, 5, [goal(1)]);
    content = removeGoal(content, content.goals[0].key);
    expect(content.goals).toHaveLength(1);

    const suggested = goalFromSuggestion({ title: 'Save', priority: 'HIGH', moonshot: false }, 7);
    content = addGoals(content, [suggested, { ...emptyGoal(), title: 'Mine' }]);
    content = updateGoal(content, suggested.key, { title: 'Save more' });
    content = updateGoal(content, content.goals[2].key, { title: 'Mine, changed' });
    expect(content.goals[1]).toMatchObject({ title: 'Save more', requestId: 7, edited: true });
    expect(content.goals[2].edited).toBe(false);
  });

  it('counts only new items, and adds tasks under an existing complex step through a container', () => {
    let content = syncWithPlan({ goals: [] }, 5, [goal(1)]);
    const [withContainer, key] = ensureStepContainer(content, content.goals[0].key, step(8, 1, true));
    content = addTask(withContainer, content.goals[0].key, key, { ...emptyTask(), title: 'Ring', dueDate: '2026-12-01' });
    expect(countNew(content)).toEqual({ goals: 0, steps: 0, tasks: 1, helpers: 0, risks: 0, total: 1 });
    // A second call reuses the container.
    expect(ensureStepContainer(content, content.goals[0].key, step(8, 1, true))[1]).toBe(key);
  });

  it('lists what blocks adding and what is worth a look (PB-FR-04)', () => {
    let content = addGoals({ goals: [] }, [{ ...emptyGoal(), title: 'Fix credit', targetDate: '2026-12-01' }, emptyGoal()]);
    content = addSteps(content, content.goals[0].key, [
      { ...emptyStep(), title: 'Late step', targetDate: '2027-02-01', complex: true },
      { ...emptyStep(), title: 'No date', owner: '' },
    ]);
    const messages = readiness(content, []).map(({ item, values }) => `${item.level}: ${item.message} ${Object.values(values).join(',')}`.trim());
    expect(messages).toEqual(expect.arrayContaining([
      'error: A new goal needs a title.',
      'warning: Step "{step}" is due after its goal. Late step',
      'warning: Complex step "{step}" has no tasks yet. Late step',
      'error: Step "{step}" needs an owner. No date',
      'error: Step "{step}" needs a date. No date',
    ]));
  });

  it('does not warn about an existing goal that already has steps', () => {
    const content = syncWithPlan({ goals: [] }, 5, [goal(1)]);
    expect(readiness(content, [step(4, 1)])).toEqual([]);
    expect(readiness(content, [])).toHaveLength(1);
  });

  it('keeps answers through every change and at most six of them (PB-FR-09)', () => {
    let content = { goals: [], clarifications: [] } as Parameters<typeof addClarification>[0];
    for (let index = 1; index <= 7; index++) {
      content = addClarification(content, { question: `Q${index}`, answer: `A${index}` });
    }
    expect(content.clarifications?.map((item) => item.question)).toEqual(['Q2', 'Q3', 'Q4', 'Q5', 'Q6', 'Q7']);
    content = addGoals(content, [emptyGoal()]);
    content = syncWithPlan(content, 5, [goal(1)]);
    expect(content.clarifications).toHaveLength(6);
  });

  it('replaces only untouched AI suggestions, in place, and locking is not editing (PB-FR-20)', () => {
    const a = goalFromSuggestion({ title: 'A', priority: 'HIGH', moonshot: false }, 1);
    const b = goalFromSuggestion({ title: 'B', priority: 'HIGH', moonshot: false }, 1);
    const c = goalFromSuggestion({ title: 'C', priority: 'HIGH', moonshot: false }, 1);
    let content = addGoals({ goals: [] }, [a, b, c, { ...emptyGoal(), title: 'Mine' }]);
    content = updateGoal(content, a.key, { locked: true });
    expect(content.goals[0]).toMatchObject({ locked: true, edited: false });
    content = updateGoal(content, c.key, { title: 'C, changed' });
    expect(content.goals.map(isReplaceable)).toEqual([false, true, false, false]);

    const fresh = [goalFromSuggestion({ title: 'B1', priority: 'LOW', moonshot: false }, 2),
      goalFromSuggestion({ title: 'B2', priority: 'LOW', moonshot: false }, 2)];
    content = replaceGoals(content, [b.key], fresh);
    expect(content.goals.map((item) => item.title)).toEqual(['A', 'B1', 'B2', 'C, changed', 'Mine']);
  });

  it('turns "starts after" positions into draft keys and forgets them when that step goes (PB-FR-12)', () => {
    const steps = stepsFromSuggestions([
      { title: 'Papers', owner: 'Me', priority: 'HIGH', complex: false, estimatedHours: 1.5 },
      { title: 'Banks', owner: 'Me', complex: true, startsAfter: 1 },
      { title: 'Odd', owner: 'Me', startsAfter: 3 },
    ], 4);
    expect(steps[0]).toMatchObject({ priority: 'HIGH', estimatedHours: 1.5, complex: false, requestId: 4 });
    expect(steps[1]).toMatchObject({ complex: true, priority: 'MEDIUM', startsAfterKey: steps[0].key });
    expect(steps[2].startsAfterKey).toBeUndefined();

    let content = addGoals({ goals: [] }, [{ ...emptyGoal(), title: 'Save' }]);
    content = addSteps(content, content.goals[0].key, steps);
    content = removeStep(content, content.goals[0].key, steps[0].key);
    expect(content.goals[0].steps[0].startsAfterKey).toBeNull();
  });

  it('warns when a step is due before the step it waits for, and tracks edits to suggested tasks', () => {
    const [first, second] = stepsFromSuggestions([
      { title: 'Papers', owner: 'Me', targetDate: '2027-02-01' },
      { title: 'Banks', owner: 'Me', targetDate: '2027-01-01', startsAfter: 1 },
    ]);
    let content = addGoals({ goals: [] }, [{ ...emptyGoal(), title: 'Save' }]);
    content = addSteps(content, content.goals[0].key, [first, second]);
    const messages = readiness(content, []).map(({ item }) => item.message);
    expect(messages).toContain('Step "{step}" is due before the step it waits for.');

    const task = taskFromSuggestion({ title: 'Call A', owner: '', priority: 'LOW', dueDate: '2027-01-01' }, 9);
    expect(task).toMatchObject({ owner: 'Me', requestId: 9, edited: false });
    content = addTask(content, content.goals[0].key, second.key, task);
    content = updateTask(content, content.goals[0].key, second.key, task.key, { locked: true });
    expect(content.goals[0].steps[1].tasks[0]).toMatchObject({ locked: true, edited: false });
    content = updateTask(content, content.goals[0].key, second.key, task.key, { title: 'Call bank A' });
    expect(content.goals[0].steps[1].tasks[0].edited).toBe(true);
  });
});

describe('plan assistant cards in the draft (V11 Phase 7, PB-FR-46)', () => {
  const draft = () => {
    let content = syncWithPlan({ goals: [] }, 5, [goal(1)]);
    const fresh = { ...emptyGoal(), key: 'g1', title: 'Fix credit' };
    content = addGoals(content, [fresh]);
    content = addSteps(content, 'g1', [{ ...emptyStep(), key: 's1', title: 'Do credit stuff' }]);
    return content;
  };

  it('changes only new items in the draft', () => {
    let content = applyAssistantChange(draft(), { kind: 'EDIT', itemType: 'STEP', itemRef: 's1', field: 'TITLE', value: 'Request a credit report' });
    content = applyAssistantChange(content, { kind: 'EDIT', itemType: 'GOAL', itemRef: 'g1', field: 'DATE', value: '2027-03-01' });
    const existingKey = content.goals[0].key;
    const unchanged = applyAssistantChange(content, { kind: 'EDIT', itemType: 'GOAL', itemRef: existingKey, field: 'TITLE', value: 'Renamed' });
    expect(content.goals[1].steps[0].title).toBe('Request a credit report');
    expect(content.goals[1].targetDate).toBe('2027-03-01');
    expect(unchanged).toBe(content);
  });

  it('adds steps, tasks and risks the participant chose, with no request id', () => {
    let content = applyAssistantChange(draft(), { kind: 'ADD_STEP', itemType: 'STEP', parentRef: 'g1', title: 'Pay off the card', owner: '', date: null });
    content = applyAssistantChange(content, { kind: 'ADD_TASK', itemType: 'TASK', parentRef: 's1', title: 'Call the bureau', owner: 'Me', date: '2027-01-10' });
    content = applyAssistantChange(content, { kind: 'ADD_RISK', itemType: 'OBSTACLE', parentRef: 's1', title: 'Bureau is slow', obstacleType: 'TIME', severity: 'LOW' });
    const steps = content.goals[1].steps;
    expect(steps.map((item) => item.title)).toEqual(['Do credit stuff', 'Pay off the card']);
    expect(steps[1].owner).toBe('Me');
    expect(steps[1].requestId ?? null).toBeNull();
    expect(steps[0].tasks[0]).toMatchObject({ title: 'Call the bureau', dueDate: '2027-01-10' });
    expect(content.risks?.[0]).toMatchObject({ title: 'Bureau is slow', obstacleType: 'TIME', linkKey: 's1' });
    // A parent that is not in the draft changes nothing.
    expect(applyAssistantChange(content, { kind: 'ADD_STEP', itemType: 'STEP', parentRef: 'gone', title: 'x' })).toBe(content);
  });
});

describe('arranging new items before adding (V11 Phase 8, PB-FR-22)', () => {
  const draft = () => {
    let content = syncWithPlan({ goals: [] }, 5, [goal(1)]);
    content = addGoals(content, [
      { ...emptyGoal(), key: 'g1', title: 'Save', targetDate: '2027-01-01', priority: 'LOW' },
      { ...emptyGoal(), key: 'g2', title: 'Invest', targetDate: '2027-06-01', priority: 'HIGH' },
    ]);
    content = addSteps(content, 'g1', [
      { ...emptyStep(), key: 's1', title: 'Open account', estimatedHours: 1 },
      { ...emptyStep(), key: 's2', title: 'Move money', estimatedHours: 2, startsAfterKey: 's1' },
      { ...emptyStep(), key: 's3', title: 'Check rate', startsAfterKey: 's2' },
    ]);
    return content;
  };

  it('moves only among new items and keeps "starts after" valid', () => {
    const content = draft();
    expect(canMove(content, { goalKey: content.goals[0].key }, 1)).toBe(false);
    expect(canMove(content, { goalKey: 'g1' }, -1)).toBe(false);
    expect(moveItem(content, { goalKey: 'g1' }, 1).goals.map((item) => item.key).slice(1)).toEqual(['g2', 'g1']);
    const moved = moveItem(content, { goalKey: 'g1', stepKey: 's2' }, -1);
    expect(moved.goals[1].steps.map((step) => step.key)).toEqual(['s2', 's1', 's3']);
    // s2 now comes before the step it started after.
    expect(moved.goals[1].steps[0].startsAfterKey).toBeNull();
  });

  it('merges with the next item, keeping the later date, higher priority, hours and children', () => {
    const steps = mergeWithNext(draft(), { goalKey: 'g1', stepKey: 's1' }).goals[1].steps;
    expect(steps.map((step) => step.title)).toEqual(['Open account and Move money', 'Check rate']);
    expect(steps[0]).toMatchObject({ estimatedHours: 3, edited: true });
    // The link to the merged-away step now points at the joined one.
    expect(steps[1].startsAfterKey).toBe('s1');

    const goals = mergeWithNext(draft(), { goalKey: 'g1' }).goals;
    expect(goals).toHaveLength(2);
    expect(goals[1]).toMatchObject({ title: 'Save and Invest', targetDate: '2027-06-01', priority: 'HIGH' });
    expect(goals[1].steps).toHaveLength(3);
  });

  it('splits an item into two, with an empty title to write', () => {
    const steps = splitItem(draft(), { goalKey: 'g1', stepKey: 's1' }).goals[1].steps;
    expect(steps.map((step) => step.title)).toEqual(['Open account', '', 'Move money', 'Check rate']);
    expect(steps[1].startsAfterKey).toBe('s1');
    expect(splitItem(draft(), { goalKey: draft().goals[0].key }).goals).toHaveLength(3);
  });
});
