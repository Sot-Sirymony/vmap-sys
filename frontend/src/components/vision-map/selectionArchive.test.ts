import { describe, expect, it } from 'vitest';
import { topLevelSelection } from './selectionArchive';

const steps = [{ id: 20, goalId: 10 }, { id: 21, goalId: 11 }];
const tasks = [{ id: 30, stepId: 20 }, { id: 31, stepId: 21 }];

describe('topLevelSelection (V29 D-29-5)', () => {
  it('keeps only rows with no selected parent, goals first', () => {
    expect(topLevelSelection(['t31', 's20', 't30', 'g11'], steps, tasks)).toEqual([
      { kind: 'goal', id: 11 },
      { kind: 'step', id: 20 },
    ]);
  });

  it('keeps a task when neither its step nor its goal is selected', () => {
    expect(topLevelSelection(['t30', 's21'], steps, tasks)).toEqual([
      { kind: 'step', id: 21 },
      { kind: 'task', id: 30 },
    ]);
  });

  it('skips a task whose goal is selected even when its step is not', () => {
    expect(topLevelSelection(['g10', 't30'], steps, tasks)).toEqual([{ kind: 'goal', id: 10 }]);
  });
});
