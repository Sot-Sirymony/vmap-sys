import type {
  Dream, DreamRequest, Goal, GoalRequest, TaskItem, TaskItemRequest, VisionStep, VisionStepRequest,
} from '../types/vision';

/**
 * Full-update request builders: quick actions (rename, status, completion
 * nudges) send the entity back unchanged except for the edited field — the
 * same pattern the list pages use for board moves.
 */
export const dreamRequest = (dream: Dream): DreamRequest => ({
  visionAreaId: dream.visionAreaId,
  title: dream.title,
  description: dream.description,
  whyImportant: dream.whyImportant,
  successDefinition: dream.successDefinition,
  dreamType: dream.dreamType,
  priority: dream.priority,
  targetDate: dream.targetDate,
  status: dream.status,
  moonshot: dream.moonshot,
  moonshotVision: dream.moonshotVision,
});

export const goalRequest = (goal: Goal): GoalRequest => ({
  dreamId: goal.dreamId,
  title: goal.title,
  description: goal.description,
  successCriteria: goal.successCriteria,
  priority: goal.priority,
  targetDate: goal.targetDate,
  status: goal.status,
  moonshot: goal.moonshot,
  moonshotVision: goal.moonshotVision,
});

export const stepRequest = (step: VisionStep): VisionStepRequest => ({
  goalId: step.goalId,
  title: step.title,
  description: step.description,
  sequenceNumber: step.sequenceNumber,
  complex: step.complex,
  priority: step.priority,
  targetDate: step.targetDate,
  status: step.status,
});

export const taskRequest = (task: TaskItem): TaskItemRequest => ({
  stepId: task.stepId,
  title: task.title,
  description: task.description,
  owner: task.owner,
  priority: task.priority,
  startDate: task.startDate,
  dueDate: task.dueDate,
  status: task.status,
  progressPercent: task.progressPercent,
  estimatedHours: task.estimatedHours,
  actualHours: task.actualHours,
  blockerReason: task.blockerReason,
  nextAction: task.nextAction,
});
