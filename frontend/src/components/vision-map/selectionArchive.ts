/**
 * V29 (D-29-5, AR-FR-08): which ticked Vision Map rows to archive. Keys are
 * the tree's row keys: `d{id}` dream, `g{id}` goal, `s{id}` step, `t{id}` task.
 * A row under another ticked row is skipped, since archiving its parent
 * archives it through the cascade. A ticked dream covers everything else.
 * Order: dream, goals, steps, tasks.
 */
export type SelectedItem = { kind: 'dream' | 'goal' | 'step' | 'task'; id: number };

export function topLevelSelection(
  keys: Iterable<string>,
  steps: { id: number; goalId: number }[],
  tasks: { id: number; stepId: number }[],
): SelectedItem[] {
  const chosen = new Set(keys);
  const dreamKey = [...chosen].find((key) => key.startsWith('d'));
  if (dreamKey) {
    // The tree shows one dream, so archiving it archives every other ticked row.
    return [{ kind: 'dream', id: Number(dreamKey.slice(1)) }];
  }
  const goalOfStep = new Map(steps.map((step) => [step.id, step.goalId]));
  const stepOfTask = new Map(tasks.map((task) => [task.id, task.stepId]));
  const result: SelectedItem[] = [];
  for (const key of chosen) {
    const id = Number(key.slice(1));
    if (key.startsWith('g')) {
      result.push({ kind: 'goal', id });
    } else if (key.startsWith('s')) {
      if (!chosen.has(`g${goalOfStep.get(id)}`)) result.push({ kind: 'step', id });
    } else if (key.startsWith('t')) {
      const stepId = stepOfTask.get(id);
      const goalId = stepId === undefined ? undefined : goalOfStep.get(stepId);
      if (!chosen.has(`s${stepId}`) && !chosen.has(`g${goalId}`)) result.push({ kind: 'task', id });
    }
  }
  const order = { dream: 0, goal: 1, step: 2, task: 3 };
  return result.sort((first, second) => order[first.kind] - order[second.kind] || first.id - second.id);
}
