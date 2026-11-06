import type {
  DraftGoal, DraftHelper, DraftRisk, DraftStep, DraftTask, Dream, Goal, GoalSuggestion, HelperSuggestion, PlanClarification,
  RiskSuggestion, PlanDraftContent, PlanStage, StepSuggestion,
  TaskSuggestion, VisionStep,
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
