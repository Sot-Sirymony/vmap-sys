import type {
  AssistantChange, DraftFix, DraftGoal, DraftHelper, DraftIssue, DraftRisk, DraftStep, DraftTask, Dream, Goal, GoalSuggestion, HelperSuggestion, PlanClarification,
  Priority, RiskSuggestion, PlanDraftContent, PlanStage, StepSuggestion,
  TaskSuggestion, VisionStep, WeekLoad,
} from '../../types/vision';

/**
 * V11 Phase 2: the Build my plan draft, kept as plain data so every change is a
 * pure function (easy to test and to save as-is). Items with existingId stand
 * for goals and steps already in the plan; they are shown, never changed.
 */

/**
 * A change to the draft: the new draft, or a function of the current one. AI
 * answers arrive later, so they use the function form and never overwrite
 * edits made while the participant was waiting.
 */
export type DraftUpdate = PlanDraftContent | ((current: PlanDraftContent) => PlanDraftContent);

export const STAGES: { id: PlanStage; label: string }[] = [
  { id: 'CLARIFY', label: 'Clarify' },
  { id: 'GOALS', label: 'Goals' },
  { id: 'STEPS', label: 'Steps' },
  { id: 'TASKS', label: 'Tasks' },
  { id: 'HELPERS', label: 'Helpers and risks' },
  { id: 'REVIEW', label: 'Check and add' },
];

let counter = 0;
export function newKey(prefix: string) {
  counter += 1;
  return `${prefix}${Date.now().toString(36)}${counter}`.slice(0, 40);
}

export function emptyGoal(): DraftGoal {
  return { key: newKey('g'), title: '', priority: 'MEDIUM', moonshot: false, edited: false, steps: [] };
}

export function emptyStep(): DraftStep {
  return { key: newKey('s'), title: '', owner: 'Me', priority: 'MEDIUM', complex: false, edited: false, tasks: [] };
}

export function emptyTask(): DraftTask {
  return { key: newKey('t'), title: '', owner: 'Me', priority: 'MEDIUM' };
}

export function goalFromSuggestion(suggestion: GoalSuggestion, requestId?: number | null): DraftGoal {
  return {
    key: newKey('g'),
    title: suggestion.title,
    description: suggestion.description ?? null,
    successCriteria: suggestion.successCriteria ?? null,
    targetDate: suggestion.targetDate ?? null,
    priority: suggestion.priority,
    moonshot: suggestion.moonshot,
    requestId: requestId ?? null,
    edited: false,
    steps: [],
  };
}

export function stepFromSuggestion(suggestion: StepSuggestion, requestId?: number | null, peopleLabel = 'People'): DraftStep {
  return {
    key: newKey('s'),
    title: suggestion.title,
    description: suggestion.description ?? null,
    owner: suggestion.owner || 'Me',
    targetDate: suggestion.targetDate ?? null,
    priority: suggestion.priority ?? 'MEDIUM',
    complex: suggestion.complex ?? false,
    estimatedHours: suggestion.estimatedHours ?? null,
    resources: [suggestion.resources, suggestion.people ? `${peopleLabel}: ${suggestion.people}` : null].filter(Boolean).join('\n') || null,
    requestId: requestId ?? null,
    edited: false,
    tasks: [],
  };
}

/** PB-FR-12: a set of suggested steps, with "starts after" turned from positions into draft keys. */
export function stepsFromSuggestions(suggestions: StepSuggestion[], requestId?: number | null, peopleLabel = 'People'): DraftStep[] {
  const steps = suggestions.map((suggestion) => stepFromSuggestion(suggestion, requestId, peopleLabel));
  return steps.map((step, index) => {
    const after = suggestions[index].startsAfter;
    return after && after >= 1 && after <= index ? { ...step, startsAfterKey: steps[after - 1].key } : step;
  });
}

export function taskFromSuggestion(suggestion: TaskSuggestion, requestId?: number | null): DraftTask {
  return {
    key: newKey('t'),
    title: suggestion.title,
    owner: suggestion.owner || 'Me',
    dueDate: suggestion.dueDate ?? null,
    priority: suggestion.priority,
    estimatedHours: suggestion.estimatedHours ?? null,
    requestId: requestId ?? null,
    edited: false,
  };
}

/**
 * PB-FR-03: the draft starts from the dream's plan. Every live goal of the dream
 * gets a container (so new steps can go under it); containers for goals that
 * were archived or moved since are dropped, with anything new inside them.
 * Existing goals come first, in their plan order, then new goals.
 */
export function syncWithPlan(content: PlanDraftContent, dreamId: number, goals: Goal[]): PlanDraftContent {
  const live = goals.filter((goal) => goal.dreamId === dreamId && !goal.archived);
  const containers = new Map(content.goals.filter((goal) => goal.existingId != null).map((goal) => [goal.existingId as number, goal]));
  const existing = live.map((goal) => {
    const kept = containers.get(goal.id);
    return {
      ...(kept ?? { key: newKey('g'), priority: goal.priority, moonshot: false, edited: false, steps: [] }),
      existingId: goal.id,
      title: goal.title,
      targetDate: goal.targetDate ?? null,
    } as DraftGoal;
  });
  const fresh = content.goals.filter((goal) => goal.existingId == null);
  return { ...content, goals: [...existing, ...fresh] };
}

export function updateGoal(content: PlanDraftContent, goalKey: string, patch: Partial<DraftGoal>): PlanDraftContent {
  return {
    ...content,
    goals: content.goals.map((goal) => (goal.key === goalKey ? markEdited(goal, patch) : goal)),
  };
}

export function removeGoal(content: PlanDraftContent, goalKey: string): PlanDraftContent {
  return { ...content, goals: content.goals.filter((goal) => goal.key !== goalKey || goal.existingId != null) };
}

export function addGoals(content: PlanDraftContent, goals: DraftGoal[]): PlanDraftContent {
  return { ...content, goals: [...content.goals, ...goals] };
}

export function addSteps(content: PlanDraftContent, goalKey: string, steps: DraftStep[]): PlanDraftContent {
  return { ...content, goals: content.goals.map((goal) => (goal.key === goalKey ? { ...goal, steps: [...goal.steps, ...steps] } : goal)) };
}

export function updateStep(content: PlanDraftContent, goalKey: string, stepKey: string, patch: Partial<DraftStep>): PlanDraftContent {
  return {
    ...content,
    goals: content.goals.map((goal) => (goal.key !== goalKey ? goal : {
      ...goal,
      steps: goal.steps.map((step) => (step.key === stepKey ? markEdited(step, patch) : step)),
    })),
  };
}

export function removeStep(content: PlanDraftContent, goalKey: string, stepKey: string): PlanDraftContent {
  return {
    ...content,
    goals: content.goals.map((goal) => (goal.key !== goalKey ? goal : {
      ...goal,
      steps: forgetOrder(goal.steps.filter((step) => step.key !== stepKey), [stepKey]),
    })),
  };
}

/** A container for an existing step, so new tasks can go under it. Returns the step's key in the draft. */
export function ensureStepContainer(content: PlanDraftContent, goalKey: string, step: VisionStep): [PlanDraftContent, string] {
  const goal = content.goals.find((item) => item.key === goalKey);
  const found = goal?.steps.find((item) => item.existingId === step.id);
  if (!goal || found) {
    return [content, found?.key ?? ''];
  }
  const container: DraftStep = {
    key: newKey('s'), existingId: step.id, title: step.title, owner: step.owner, targetDate: step.targetDate ?? null,
    priority: step.priority, complex: true, edited: false, tasks: [],
  };
  return [addSteps(content, goalKey, [container]), container.key];
}

export function addTask(content: PlanDraftContent, goalKey: string, stepKey: string, task: DraftTask): PlanDraftContent {
  return mapStep(content, goalKey, stepKey, (step) => ({ ...step, tasks: [...step.tasks, task] }));
}

export function updateTask(content: PlanDraftContent, goalKey: string, stepKey: string, taskKey: string,
  patch: Partial<DraftTask>): PlanDraftContent {
  return mapStep(content, goalKey, stepKey, (step) => ({
    ...step,
    tasks: step.tasks.map((task) => (task.key === taskKey
      ? markEdited({ ...task, edited: task.edited ?? false }, patch)
      : task)),
  }));
}

export function addTasks(content: PlanDraftContent, goalKey: string, stepKey: string, tasks: DraftTask[]): PlanDraftContent {
  return mapStep(content, goalKey, stepKey, (step) => ({ ...step, tasks: [...step.tasks, ...tasks] }));
}

/**
 * Adds tasks under a step, making a container first when the step is already
 * in the plan. Used inside a draft update, so it always sees the latest draft.
 */
export function addTasksToStep(content: PlanDraftContent, goalKey: string, step: { key?: string; existing?: VisionStep },
  tasks: DraftTask[]): PlanDraftContent {
  if (step.key) {
    return addTasks(content, goalKey, step.key, tasks);
  }
  const [next, key] = ensureStepContainer(content, goalKey, step.existing as VisionStep);
  return addTasks(next, goalKey, key, tasks);
}

export function replaceTasks(content: PlanDraftContent, goalKey: string, stepKey: string, replacedKeys: string[],
  fresh: DraftTask[]): PlanDraftContent {
  return mapStep(content, goalKey, stepKey, (step) => ({ ...step, tasks: swapIn(step.tasks, replacedKeys, fresh) }));
}

export function removeTask(content: PlanDraftContent, goalKey: string, stepKey: string, taskKey: string): PlanDraftContent {
  return mapStep(content, goalKey, stepKey, (step) => ({ ...step, tasks: step.tasks.filter((task) => task.key !== taskKey) }));
}

// ---------- V11 Phase 3: answers and refinement ----------

export const MAX_CLARIFICATIONS = 6;

/** PB-FR-09: answers are kept in the draft and sent with every later suggestion. */
export function addClarification(content: PlanDraftContent, item: PlanClarification): PlanDraftContent {
  return { ...content, clarifications: [...(content.clarifications ?? []), item].slice(-MAX_CLARIFICATIONS) };
}

/** PB-FR-20: an AI suggestion the participant has not edited or locked may be replaced by a refined set. */
export function isReplaceable(item: { requestId?: number | null; edited?: boolean; locked?: boolean; existingId?: number | null }) {
  return item.existingId == null && item.requestId != null && !item.edited && !item.locked;
}

/** The refined items take the place of the first replaced one; the rest of the replaced items go. */
function swapIn<T extends { key: string }>(items: T[], replacedKeys: string[], fresh: T[]): T[] {
  const replaced = new Set(replacedKeys);
  const at = items.findIndex((item) => replaced.has(item.key));
  const kept = items.filter((item) => !replaced.has(item.key));
  const position = at < 0 ? kept.length : items.slice(0, at).filter((item) => !replaced.has(item.key)).length;
  return [...kept.slice(0, position), ...fresh, ...kept.slice(position)];
}

export function replaceGoals(content: PlanDraftContent, replacedKeys: string[], fresh: DraftGoal[]): PlanDraftContent {
  return { ...content, goals: swapIn(content.goals, replacedKeys, fresh) };
}

export function replaceSteps(content: PlanDraftContent, goalKey: string, replacedKeys: string[], fresh: DraftStep[]): PlanDraftContent {
  return {
    ...content,
    goals: content.goals.map((goal) => (goal.key === goalKey
      ? { ...goal, steps: forgetOrder(swapIn(goal.steps, replacedKeys, fresh), replacedKeys) }
      : goal)),
  };
}

// ---------- V11 Phase 5: helpers and risks ----------

/** The draft's goals and steps, by key, for helpers and risks to point at. */
export function planItems(content: PlanDraftContent) {
  const items: { key: string; kind: 'GOAL' | 'STEP'; title: string; resources?: string | null; goalKey?: string }[] = [];
  for (const goal of content.goals.filter((item) => item.title.trim())) {
    items.push({ key: goal.key, kind: 'GOAL', title: goal.title });
    for (const step of goal.steps.filter((item) => item.title.trim())) {
      items.push({ key: step.key, kind: 'STEP', title: step.title, resources: step.resources ?? null, goalKey: goal.key });
    }
  }
  return items.slice(0, 60);
}

/** A suggested helper links the best-matching partner by default; the participant can add a new contact instead. */
export function helperFromSuggestion(suggestion: HelperSuggestion, requestId?: number | null): DraftHelper {
  const best = suggestion.matches[0];
  return {
    key: newKey('h'),
    role: suggestion.role,
    supportType: suggestion.supportType,
    name: suggestion.role,
    partnerId: best?.partnerId ?? null,
    partnerName: best?.name ?? null,
    linkKey: suggestion.forKey ?? null,
    requestId: requestId ?? null,
    edited: false,
    matches: suggestion.matches,
  };
}

/** A suggested risk is tied to a helper of the kind that fits it, when the same answer suggested one. */
export function riskFromSuggestion(suggestion: RiskSuggestion, helpers: DraftHelper[], requestId?: number | null): DraftRisk {
  return {
    key: newKey('r'),
    title: suggestion.title,
    description: suggestion.description ?? null,
    obstacleType: suggestion.obstacleType,
    severity: suggestion.severity,
    solution: suggestion.solution ?? null,
    linkKey: suggestion.forKey ?? null,
    helperKey: helpers.find((helper) => helper.supportType === suggestion.helperType)?.key ?? null,
    fromFears: suggestion.fromFears,
    requestId: requestId ?? null,
    edited: false,
  };
}

export function emptyHelper(): DraftHelper {
  return { key: newKey('h'), role: '', supportType: 'OTHER', name: '', edited: false };
}

export function emptyRisk(): DraftRisk {
  return { key: newKey('r'), title: '', obstacleType: 'OTHER', severity: 'MEDIUM', edited: false };
}

export function addHelpersAndRisks(content: PlanDraftContent, helpers: DraftHelper[], risks: DraftRisk[]): PlanDraftContent {
  return {
    ...content,
    helpers: [...(content.helpers ?? []), ...helpers].slice(0, 10),
    risks: [...(content.risks ?? []), ...risks].slice(0, 10),
  };
}

export function updateHelper(content: PlanDraftContent, key: string, patch: Partial<DraftHelper>): PlanDraftContent {
  return { ...content, helpers: (content.helpers ?? []).map((helper) => (helper.key === key ? markEdited(helper, patch) : helper)) };
}

export function removeHelper(content: PlanDraftContent, key: string): PlanDraftContent {
  return {
    ...content,
    helpers: (content.helpers ?? []).filter((helper) => helper.key !== key),
    risks: (content.risks ?? []).map((risk) => (risk.helperKey === key ? { ...risk, helperKey: null } : risk)),
  };
}

export function updateRisk(content: PlanDraftContent, key: string, patch: Partial<DraftRisk>): PlanDraftContent {
  return { ...content, risks: (content.risks ?? []).map((risk) => (risk.key === key ? markEdited(risk, patch) : risk)) };
}

export function removeRisk(content: PlanDraftContent, key: string): PlanDraftContent {
  return { ...content, risks: (content.risks ?? []).filter((risk) => risk.key !== key) };
}

// ---------- V11 Phase 6: fixes and capacity ----------

/** Where a draft step or task lives, by key. */
function locate(content: PlanDraftContent, key: string) {
  for (const goal of content.goals) {
    for (const step of goal.steps) {
      if (step.key === key) {
        return { goal, step, task: undefined as DraftTask | undefined };
      }
      const task = step.tasks.find((item) => item.key === key);
      if (task) {
        return { goal, step, task };
      }
    }
  }
  return null;
}

/** PB-FR-29: the one-click fix of a rule issue, applied to the draft (nothing is saved). */
export function applyIssueFix(content: PlanDraftContent, issue: DraftIssue): PlanDraftContent {
  const found = locate(content, issue.itemKey);
  if (!found) {
    return content;
  }
  const patch = issue.fixAction === 'SET_OWNER_ME' ? { owner: 'Me' }
    : issue.fixAction === 'SET_DATE' && issue.fixValue ? (found.task ? { dueDate: issue.fixValue } : { targetDate: issue.fixValue })
      : null;
  if (!patch) {
    return content;
  }
  return found.task
    ? updateTask(content, found.goal.key, found.step.key, found.task.key, patch as Partial<DraftTask>)
    : updateStep(content, found.goal.key, found.step.key, patch as Partial<DraftStep>);
}

/** PB-FR-29: Claude's proposed change, applied only when the participant chooses it. */
export function applyAiFix(content: PlanDraftContent, fix: DraftFix): PlanDraftContent {
  if (fix.kind === 'GAP' && fix.newStepTitle) {
    const step: DraftStep = { ...emptyStep(), title: fix.newStepTitle, owner: fix.newStepOwner || 'Me',
      targetDate: fix.newStepTargetDate ?? null, requestId: fix.requestId ?? null };
    return addSteps(content, fix.itemKey, [step]);
  }
  const found = locate(content, fix.itemKey);
  if (!found || found.task) {
    return content;
  }
  const patch: Partial<DraftStep> = fix.kind === 'VAGUE_STEP' && fix.title ? { title: fix.title }
    : fix.kind === 'UNREALISTIC_TIMING' && fix.targetDate ? { targetDate: fix.targetDate } : {};
  return Object.keys(patch).length ? updateStep(content, found.goal.key, found.step.key, patch) : content;
}

/**
 * V11 Phase 7 (PB-FR-46): one plan assistant card the participant chose,
 * applied to the draft only. Items it adds carry no request id: the card was
 * already counted when it was applied, and the participant chose it, so a
 * later refine leaves it alone. Changes to the dream itself are not draft
 * changes; the page saves those to the dream.
 */
export function applyAssistantChange(content: PlanDraftContent, change: AssistantChange): PlanDraftContent {
  if (change.kind === 'EDIT' && change.itemRef && change.value != null) {
    const value = change.value;
    if (change.itemType === 'GOAL') {
      const goal = content.goals.find((item) => item.key === change.itemRef && item.existingId == null);
      const patch: Partial<DraftGoal> | null = change.field === 'TITLE' ? { title: value }
        : change.field === 'SUCCESS' ? { successCriteria: value }
          : change.field === 'DATE' ? { targetDate: value } : null;
      return goal && patch ? updateGoal(content, goal.key, patch) : content;
    }
    const found = locate(content, change.itemRef);
    if (!found || (!found.task && found.step.existingId != null)) {
      return content;
    }
    if (found.task) {
      const patch: Partial<DraftTask> | null = change.field === 'TITLE' ? { title: value }
        : change.field === 'OWNER' ? { owner: value || 'Me' }
          : change.field === 'DATE' ? { dueDate: value } : null;
      return patch ? updateTask(content, found.goal.key, found.step.key, found.task.key, patch) : content;
    }
    const patch: Partial<DraftStep> | null = change.field === 'TITLE' ? { title: value }
      : change.field === 'OWNER' ? { owner: value || 'Me' }
        : change.field === 'DATE' ? { targetDate: value } : null;
    return patch ? updateStep(content, found.goal.key, found.step.key, patch) : content;
  }
  if (change.kind === 'ADD_STEP' && change.parentRef && change.title) {
    if (!content.goals.some((goal) => goal.key === change.parentRef)) {
      return content;
    }
    const step: DraftStep = { ...emptyStep(), title: change.title, owner: change.owner || 'Me', targetDate: change.date ?? null, edited: true };
    return addSteps(content, change.parentRef, [step]);
  }
  if (change.kind === 'ADD_TASK' && change.parentRef && change.title) {
    const found = locate(content, change.parentRef);
    if (!found || found.task) {
      return content;
    }
    const task: DraftTask = { ...emptyTask(), title: change.title, owner: change.owner || 'Me', dueDate: change.date ?? null, edited: true };
    return addTask(content, found.goal.key, found.step.key, task);
  }
  if (change.kind === 'ADD_RISK' && change.title) {
    const risk: DraftRisk = { ...emptyRisk(), title: change.title, obstacleType: change.obstacleType || 'OTHER',
      severity: change.severity || 'MEDIUM', solution: change.solution ?? null, linkKey: change.parentRef ?? null, edited: true };
    return addHelpersAndRisks(content, [], [risk]);
  }
  return content;
}

/** The Monday of a YYYY-MM-DD date's week, as YYYY-MM-DD. */
export function weekStart(date: string) {
  const [year, month, day] = date.split('-').map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  value.setUTCDate(value.getUTCDate() - ((value.getUTCDay() + 6) % 7));
  return value.toISOString().slice(0, 10);
}

function addDays(date: string, days: number) {
  const [year, month, day] = date.split('-').map(Number);
  const value = new Date(Date.UTC(year, month - 1, day + days));
  return value.toISOString().slice(0, 10);
}

/** New work in the draft with hours and a date: tasks, and new steps that have no tasks. */
function draftWork(content: PlanDraftContent) {
  const work: { goalKey: string; stepKey: string; taskKey?: string; date: string; hours: number; latest?: string | null }[] = [];
  for (const goal of content.goals) {
    for (const step of goal.steps) {
      for (const task of step.tasks) {
        if (task.dueDate && task.estimatedHours) {
          work.push({ goalKey: goal.key, stepKey: step.key, taskKey: task.key, date: task.dueDate, hours: task.estimatedHours,
            latest: step.targetDate });
        }
      }
      if (step.existingId == null && step.tasks.length === 0 && step.targetDate && step.estimatedHours) {
        work.push({ goalKey: goal.key, stepKey: step.key, date: step.targetDate, hours: step.estimatedHours, latest: goal.targetDate });
      }
    }
  }
  return work;
}

/** PB-FR-16: hours per week, saved plan plus draft, for weeks over the participant's hours. */
export function overloadedWeeks(content: PlanDraftContent, planned: WeekLoad[], hoursPerWeek?: number | null) {
  if (!hoursPerWeek) {
    return [];
  }
  const totals = new Map<string, { saved: number; draft: number }>();
  for (const week of planned) {
    totals.set(week.weekStart, { saved: Number(week.hours), draft: 0 });
  }
  for (const item of draftWork(content)) {
    const key = weekStart(item.date);
    const entry = totals.get(key) ?? { saved: 0, draft: 0 };
    entry.draft += item.hours;
    totals.set(key, entry);
  }
  return [...totals.entries()]
    .filter(([, value]) => value.draft > 0 && value.saved + value.draft > hoursPerWeek)
    .map(([week, value]) => ({ weekStart: week, saved: value.saved, draft: value.draft, total: value.saved + value.draft }))
    .sort((left, right) => left.weekStart.localeCompare(right.weekStart));
}

/** Total hours per week, saved plan plus draft. */
function weekLoads(content: PlanDraftContent, planned: WeekLoad[]) {
  const load = new Map<string, number>();
  for (const week of planned) {
    load.set(week.weekStart, Number(week.hours));
  }
  for (const item of draftWork(content)) {
    const key = weekStart(item.date);
    load.set(key, (load.get(key) ?? 0) + item.hours);
  }
  return load;
}

/**
 * PB-FR-16: move new work out of weeks that are over the participant's hours,
 * a week at a time, latest-due first, never past its step's or goal's date.
 * Saved work is never moved. Returns the new draft and how many items moved.
 */
export function spreadDates(content: PlanDraftContent, planned: WeekLoad[], hoursPerWeek: number) {
  let next = content;
  let moved = 0;
  for (let round = 0; round < 200; round++) {
    // Only into a week that still has room for it, so nothing is pushed on and on.
    const load = weekLoads(next, planned);
    let candidate: (ReturnType<typeof draftWork>[number] & { later: string }) | undefined;
    for (const week of overloadedWeeks(next, planned, hoursPerWeek)) {
      candidate = draftWork(next)
        .filter((item) => weekStart(item.date) === week.weekStart)
        .map((item) => ({ ...item, later: addDays(item.date, 7) }))
        .filter((item) => !item.latest || item.later <= item.latest)
        .filter((item) => (load.get(weekStart(item.later)) ?? 0) + item.hours <= hoursPerWeek)
        .sort((left, right) => right.date.localeCompare(left.date))[0];
      if (candidate) {
        break;
      }
    }
    if (!candidate) {
      break;
    }
    next = candidate.taskKey
      ? updateTask(next, candidate.goalKey, candidate.stepKey, candidate.taskKey, { dueDate: candidate.later })
      : updateStep(next, candidate.goalKey, candidate.stepKey, { targetDate: candidate.later });
    moved++;
  }
  return { content: next, moved };
}

/** Everything Add plan would save. */
export function countNew(content: PlanDraftContent) {
  let goals = 0;
  let steps = 0;
  let tasks = 0;
  for (const goal of content.goals) {
    goals += goal.existingId == null ? 1 : 0;
    for (const step of goal.steps) {
      steps += step.existingId == null ? 1 : 0;
      tasks += step.tasks.length;
    }
  }
  const helpers = content.helpers?.length ?? 0;
  const risks = content.risks?.length ?? 0;
  return { goals, steps, tasks, helpers, risks, total: goals + steps + tasks + helpers + risks };
}

export type ReadinessItem = { level: 'error' | 'warning'; stage: PlanStage; message: string };

/**
 * PB-FR-04: what still needs attention before adding. Errors would be refused by
 * the server (the same rules as the normal forms); warnings are worth a look.
 * Messages are English keys for tr(), with {values}.
 */
export function readiness(content: PlanDraftContent, existingSteps: VisionStep[]): { item: ReadinessItem; values: Record<string, string> }[] {
  const out: { item: ReadinessItem; values: Record<string, string> }[] = [];
  const push = (level: ReadinessItem['level'], stage: PlanStage, message: string, values: Record<string, string> = {}) =>
    out.push({ item: { level, stage, message }, values });
  for (const goal of content.goals) {
    const goalName = goal.title.trim() || '…';
    if (goal.existingId == null && !goal.title.trim()) {
      push('error', 'GOALS', 'A new goal needs a title.');
    }
    const hasExistingSteps = goal.existingId != null && existingSteps.some((step) => step.goalId === goal.existingId && !step.archived);
    if (goal.existingId == null && goal.steps.length === 0) {
      push('warning', 'STEPS', 'Goal "{goal}" has no steps yet.', { goal: goalName });
    } else if (goal.existingId != null && !hasExistingSteps && goal.steps.length === 0) {
      push('warning', 'STEPS', 'Goal "{goal}" has no steps yet.', { goal: goalName });
    }
    for (const step of goal.steps) {
      const stepName = step.title.trim() || '…';
      if (step.existingId == null) {
        if (!step.title.trim()) {
          push('error', 'STEPS', 'A new step under "{goal}" needs a title.', { goal: goalName });
        }
        if (!step.owner.trim()) {
          push('error', 'STEPS', 'Step "{step}" needs an owner.', { step: stepName });
        }
        if (!step.targetDate) {
          push('error', 'STEPS', 'Step "{step}" needs a date.', { step: stepName });
        } else if (goal.targetDate && step.targetDate > goal.targetDate) {
          push('warning', 'STEPS', 'Step "{step}" is due after its goal.', { step: stepName });
        }
        const earlier = step.startsAfterKey ? goal.steps.find((item) => item.key === step.startsAfterKey) : undefined;
        if (step.targetDate && earlier?.targetDate && step.targetDate < earlier.targetDate) {
          push('warning', 'STEPS', 'Step "{step}" is due before the step it waits for.', { step: stepName });
        }
        if (step.complex && step.tasks.length === 0) {
          push('warning', 'TASKS', 'Complex step "{step}" has no tasks yet.', { step: stepName });
        }
      }
      for (const task of step.tasks) {
        const taskName = task.title.trim() || '…';
        if (!task.title.trim()) {
          push('error', 'TASKS', 'A new task under "{step}" needs a title.', { step: stepName });
        }
        if (!task.owner.trim()) {
          push('error', 'TASKS', 'Task "{task}" needs an owner.', { task: taskName });
        }
        if (!task.dueDate) {
          push('error', 'TASKS', 'Task "{task}" needs a date.', { task: taskName });
        } else if (step.targetDate && task.dueDate > step.targetDate) {
          push('warning', 'TASKS', 'Task "{task}" is due after its step.', { task: taskName });
        }
      }
    }
  }
  for (const helper of content.helpers ?? []) {
    if (helper.partnerId == null && !(helper.name ?? '').trim() && !helper.role.trim()) {
      push('error', 'HELPERS', 'A new helper needs a name or a role.');
    }
  }
  for (const risk of content.risks ?? []) {
    if (!risk.title.trim()) {
      push('error', 'HELPERS', 'A risk needs a title.');
    }
  }
  return out;
}

/** CLAUDE.md: a dream is clear enough to plan when it says why, what success looks like and when. */
export function missingDreamParts(dream: Dream) {
  return [
    !dream.whyImportant?.trim() ? 'Why it matters' : null,
    !dream.successDefinition?.trim() ? 'What success looks like' : null,
    !dream.targetDate ? 'Target date' : null,
  ].filter((part): part is string => part !== null);
}

/** A step that waited for a removed or replaced step can start any time. */
// ---------- V11 Phase 8: reorder, merge and split before adding (PB-FR-22) ----------

/** Where an item sits: a goal, a step of a goal, or a task of a step. */
export type ItemPath = { goalKey: string; stepKey?: string; taskKey?: string };

const PRIORITY_ORDER: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** The higher of two priorities. */
function higher(left: Priority, right: Priority): Priority {
  return PRIORITY_ORDER.indexOf(left) >= PRIORITY_ORDER.indexOf(right) ? left : right;
}

/** The later of two dates, or whichever is set. */
function later(left?: string | null, right?: string | null) {
  if (!left) return right ?? null;
  if (!right) return left;
  return left >= right ? left : right;
}

function joinText(left?: string | null, right?: string | null) {
  return [left, right].map((text) => text?.trim()).filter(Boolean).join('\n') || null;
}

function joinTitle(left: string, right: string) {
  return [left.trim(), right.trim()].filter(Boolean).join(' and ').slice(0, 220);
}

function addHours(left?: number | null, right?: number | null) {
  return left == null && right == null ? null : Math.min(999, (left ?? 0) + (right ?? 0));
}

/** The index of the next (dir 1) or previous (dir -1) new sibling, or -1. Items already in the plan never move. */
function neighbour<T extends { key: string; existingId?: number | null }>(items: T[], key: string, dir: 1 | -1) {
  const index = items.findIndex((item) => item.key === key);
  if (index < 0 || items[index].existingId != null) {
    return [-1, -1];
  }
  for (let other = index + dir; other >= 0 && other < items.length; other += dir) {
    if (items[other].existingId == null) {
      return [index, other];
    }
  }
  return [index, -1];
}

function swap<T>(items: T[], first: number, second: number) {
  const next = [...items];
  [next[first], next[second]] = [next[second], next[first]];
  return next;
}

/** A step may only start after an earlier new step of its goal; any other link is cleared. */
function keepOrderValid(steps: DraftStep[]) {
  return steps.map((step, index) => (step.startsAfterKey && !steps.slice(0, index).some((earlier) => earlier.key === step.startsAfterKey)
    ? { ...step, startsAfterKey: null } : step));
}

/** Points links (starts after, helpers, risks) from one key to another. */
function relink(content: PlanDraftContent, from: string, to: string): PlanDraftContent {
  return {
    ...content,
    goals: content.goals.map((goal) => ({
      ...goal,
      steps: goal.steps.map((step) => (step.startsAfterKey === from ? { ...step, startsAfterKey: to === step.key ? null : to } : step)),
    })),
    helpers: content.helpers?.map((helper) => (helper.linkKey === from ? { ...helper, linkKey: to } : helper)),
    risks: content.risks?.map((risk) => (risk.linkKey === from ? { ...risk, linkKey: to } : risk)),
  };
}

function withSteps(content: PlanDraftContent, goalKey: string, change: (steps: DraftStep[]) => DraftStep[]): PlanDraftContent {
  return { ...content, goals: content.goals.map((goal) => (goal.key === goalKey ? { ...goal, steps: keepOrderValid(change(goal.steps)) } : goal)) };
}

/** PB-FR-22: moves a new goal, step or task up (-1) or down (1) among its new siblings. */
export function moveItem(content: PlanDraftContent, path: ItemPath, dir: 1 | -1): PlanDraftContent {
  if (path.taskKey && path.stepKey) {
    const step = content.goals.find((goal) => goal.key === path.goalKey)?.steps.find((item) => item.key === path.stepKey);
    const [index, other] = step ? neighbour(step.tasks, path.taskKey, dir) : [-1, -1];
    return other < 0 ? content : mapStep(content, path.goalKey, path.stepKey, (item) => ({ ...item, tasks: swap(item.tasks, index, other) }));
  }
  if (path.stepKey) {
    const goal = content.goals.find((item) => item.key === path.goalKey);
    const [index, other] = goal ? neighbour(goal.steps, path.stepKey, dir) : [-1, -1];
    return other < 0 ? content : withSteps(content, path.goalKey, (steps) => swap(steps, index, other));
  }
  const [index, other] = neighbour(content.goals, path.goalKey, dir);
  return other < 0 ? content : { ...content, goals: swap(content.goals, index, other) };
}

/** True when the item has a new sibling in that direction, so it can move or merge. */
export function canMove(content: PlanDraftContent, path: ItemPath, dir: 1 | -1) {
  return moveItem(content, path, dir) !== content;
}

/**
 * PB-FR-22: joins a new goal, step or task with the next new one. Titles and
 * text are joined, the later date and higher priority kept, hours added and
 * children kept together. The joined item counts as edited; links to the
 * second item move to the first.
 */
export function mergeWithNext(content: PlanDraftContent, path: ItemPath): PlanDraftContent {
  if (path.taskKey && path.stepKey) {
    const step = content.goals.find((goal) => goal.key === path.goalKey)?.steps.find((item) => item.key === path.stepKey);
    const [index, other] = step ? neighbour(step.tasks, path.taskKey, 1) : [-1, -1];
    if (!step || other < 0) {
      return content;
    }
    const [first, second] = [step.tasks[index], step.tasks[other]];
    const merged: DraftTask = { ...first, title: joinTitle(first.title, second.title), dueDate: later(first.dueDate, second.dueDate),
      priority: higher(first.priority, second.priority), estimatedHours: addHours(first.estimatedHours, second.estimatedHours),
      edited: true, locked: false };
    return mapStep(content, path.goalKey, path.stepKey, (item) => ({
      ...item, tasks: item.tasks.filter((task) => task.key !== second.key).map((task) => (task.key === first.key ? merged : task)),
    }));
  }
  if (path.stepKey) {
    const goal = content.goals.find((item) => item.key === path.goalKey);
    const [index, other] = goal ? neighbour(goal.steps, path.stepKey, 1) : [-1, -1];
    if (!goal || other < 0) {
      return content;
    }
    const [first, second] = [goal.steps[index], goal.steps[other]];
    const merged: DraftStep = { ...first, title: joinTitle(first.title, second.title), description: joinText(first.description, second.description),
      targetDate: later(first.targetDate, second.targetDate), priority: higher(first.priority, second.priority),
      complex: first.complex || second.complex || first.tasks.length + second.tasks.length > 0,
      resources: joinText(first.resources, second.resources)?.slice(0, 1000) ?? null,
      estimatedHours: addHours(first.estimatedHours, second.estimatedHours), tasks: [...first.tasks, ...second.tasks].slice(0, 15),
      edited: true, locked: false };
    // Steps that waited for the second one now wait for the joined one, before the order is checked.
    const joined = withSteps(content, path.goalKey, (steps) => steps
      .filter((step) => step.key !== second.key)
      .map((step) => (step.key === first.key ? merged : step))
      .map((step) => (step.startsAfterKey === second.key ? { ...step, startsAfterKey: first.key } : step)));
    return relink(joined, second.key, first.key);
  }
  const [index, other] = neighbour(content.goals, path.goalKey, 1);
  if (other < 0) {
    return content;
  }
  const [first, second] = [content.goals[index], content.goals[other]];
  const merged: DraftGoal = { ...first, title: joinTitle(first.title, second.title), description: joinText(first.description, second.description),
    successCriteria: joinText(first.successCriteria, second.successCriteria), targetDate: later(first.targetDate, second.targetDate),
    priority: higher(first.priority, second.priority), moonshot: first.moonshot || second.moonshot,
    steps: keepOrderValid([...first.steps, ...second.steps]).slice(0, 25), edited: true, locked: false };
  const joined = { ...content, goals: content.goals.filter((goal) => goal.key !== second.key).map((goal) => (goal.key === first.key ? merged : goal)) };
  return relink(joined, second.key, first.key);
}

/**
 * PB-FR-22: splits a new goal, step or task in two: a second item right
 * after it, with the same date, owner and priority and an empty title for the
 * participant to write. Children stay with the first.
 */
export function splitItem(content: PlanDraftContent, path: ItemPath): PlanDraftContent {
  if (path.taskKey && path.stepKey) {
    const taskKey = path.taskKey;
    return mapStep(content, path.goalKey, path.stepKey, (step) => {
      const index = step.tasks.findIndex((task) => task.key === taskKey);
      if (index < 0 || step.tasks.length >= 15) {
        return step;
      }
      const source = step.tasks[index];
      const copy: DraftTask = { ...emptyTask(), owner: source.owner, dueDate: source.dueDate ?? null, priority: source.priority, edited: true };
      const tasks = [...step.tasks];
      tasks.splice(index + 1, 0, copy);
      return { ...step, tasks: tasks.map((task) => (task.key === source.key ? { ...task, edited: true } : task)) };
    });
  }
  if (path.stepKey) {
    const stepKey = path.stepKey;
    return withSteps(content, path.goalKey, (steps) => {
      const index = steps.findIndex((step) => step.key === stepKey);
      if (index < 0 || steps[index].existingId != null || steps.length >= 25) {
        return steps;
      }
      const source = steps[index];
      const copy: DraftStep = { ...emptyStep(), owner: source.owner, targetDate: source.targetDate ?? null, priority: source.priority,
        startsAfterKey: source.key, edited: true };
      const next = [...steps];
      next.splice(index + 1, 0, copy);
      return next.map((step) => (step.key === source.key ? { ...step, edited: true } : step));
    });
  }
  const index = content.goals.findIndex((goal) => goal.key === path.goalKey);
  if (index < 0 || content.goals[index].existingId != null || content.goals.length >= 15) {
    return content;
  }
  const source = content.goals[index];
  const copy: DraftGoal = { ...emptyGoal(), targetDate: source.targetDate ?? null, priority: source.priority, edited: true };
  const goals = [...content.goals];
  goals.splice(index + 1, 0, copy);
  return { ...content, goals: goals.map((goal) => (goal.key === source.key ? { ...goal, edited: true } : goal)) };
}

function forgetOrder(steps: DraftStep[], goneKeys: string[]): DraftStep[] {
  return steps.map((step) => (step.startsAfterKey && goneKeys.includes(step.startsAfterKey) ? { ...step, startsAfterKey: null } : step));
}

function mapStep(content: PlanDraftContent, goalKey: string, stepKey: string, change: (step: DraftStep) => DraftStep): PlanDraftContent {
  return {
    ...content,
    goals: content.goals.map((goal) => (goal.key !== goalKey ? goal : {
      ...goal,
      steps: goal.steps.map((step) => (step.key === stepKey ? change(step) : step)),
    })),
  };
}

/** PB-FR-35: changing a field of an AI suggestion marks it edited. */
function markEdited<T extends { requestId?: number | null; edited?: boolean }>(item: T, patch: Partial<T>): T {
  // Locking keeps an item as it is; it is not an edit.
  const changesContent = Object.keys(patch).some((field) => field !== 'locked');
  return { ...item, ...patch, edited: item.requestId && changesContent ? true : item.edited };
}
