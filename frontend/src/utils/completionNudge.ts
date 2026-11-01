import { updateGoalStatus } from '../api/goalApi';
import { updateStepStatus } from '../api/stepApi';
import type { Goal, TaskItem, VisionStep } from '../types/vision';

type ShowToast = (message: string, options?: { action?: { label: string; onClick: () => void }; duration?: number }) => void;

type NudgeContext = {
  token: string;
  /** The task that was just marked Completed. */
  completedTaskId: number;
  tasks: TaskItem[];
  steps: VisionStep[];
  goals: Goal[];
  showToast: ShowToast;
  /** Reload the caller's data after an accepted nudge. */
  onApplied: () => void;
};

/**
 * Business rule 11 (FR-25.1): when the last task of a step completes, offer —
 * never force — to mark the parent step completed; accepting that checks the
 * goal the same way. Non-blocking (a toast with an action), fires once per
 * completion event, and the actual status change goes through the normal
 * update endpoints so every existing rule still applies.
 */
export function nudgeAfterTaskComplete({ token, completedTaskId, tasks, steps, goals, showToast, onApplied }: NudgeContext) {
  const completed = tasks.find((task) => task.id === completedTaskId);
  if (!completed) {
    return;
  }
  const step = steps.find((item) => item.id === completed.stepId);
  if (!step || step.status === 'COMPLETED') {
    return;
  }
  const siblings = tasks.filter((task) => task.stepId === step.id && !task.archived);
  const allDone = siblings.every((task) => task.id === completedTaskId || task.status === 'COMPLETED');
  if (!allDone) {
    return;
  }

  showToast(`All tasks done — mark step "${step.title}" completed?`, {
    duration: 8000,
    action: {
      label: 'Complete step',
      onClick: () => {
        void (async () => {
          await updateStepStatus(token, step.id, 'COMPLETED');
          onApplied();
          nudgeAfterStepComplete({ token, completedStepId: step.id, steps, goals, showToast, onApplied });
        })();
      },
    },
  });
}

type StepNudgeContext = {
  token: string;
  completedStepId: number;
  steps: VisionStep[];
  goals: Goal[];
  showToast: ShowToast;
  onApplied: () => void;
};

/** The cascade: a completed step may have been its goal's last open step. */
export function nudgeAfterStepComplete({ token, completedStepId, steps, goals, showToast, onApplied }: StepNudgeContext) {
  const step = steps.find((item) => item.id === completedStepId);
  if (!step) {
    return;
  }
  const goal = goals.find((item) => item.id === step.goalId);
  if (!goal || goal.status === 'COMPLETED') {
    return;
  }
  const siblings = steps.filter((item) => item.goalId === goal.id && !item.archived);
  const allDone = siblings.every((item) => item.id === completedStepId || item.status === 'COMPLETED');
  if (!allDone) {
    return;
  }

  showToast(`Every step is done — mark goal "${goal.title}" completed?`, {
    duration: 8000,
    action: {
      label: 'Complete goal',
      onClick: () => {
        void (async () => {
          await updateGoalStatus(token, goal.id, 'COMPLETED');
          onApplied();
        })();
      },
    },
  });
}
