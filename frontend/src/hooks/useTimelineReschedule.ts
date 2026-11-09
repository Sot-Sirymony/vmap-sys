import { useCallback } from 'react';
import { updateStep } from '../api/stepApi';
import { updateTask } from '../api/taskApi';
import { useToast } from '../context/ToastContext';
import { tr } from '../i18n/translate';
import type { TaskItem, VisionStep } from '../types/vision';
import { stepRequest, taskRequest } from '../utils/entityRequests';

/** A new date (or dates) for one task or step, from a drag or the keyboard. */
export type DateChange =
  | { kind: 'task'; id: number; startDate: string | null; dueDate: string }
  | { kind: 'step'; id: number; targetDate: string };

/** How long the Undo offer stays (TL-FR-41). */
const UNDO_MS = 10_000;

/**
 * V16 (TL-FR-39 to TL-FR-41): saves a date change from the timeline through
 * the same update endpoints as the forms, so BR-40, FR-64 and every other rule
 * still apply. A refused change throws with the server's message, so the bar
 * can snap back and say why. A saved one offers Undo for ten seconds.
 */
export function useTimelineReschedule(
  token: string | null | undefined,
  steps: VisionStep[],
  tasks: TaskItem[],
  reload: () => Promise<void> | void,
) {
  const { showToast } = useToast();

  return useCallback(async (change: DateChange) => {
    if (!token) return;
    if (change.kind === 'task') {
      const task = tasks.find((item) => item.id === change.id);
      if (!task) return;
      const before = taskRequest(task);
      await updateTask(token, task.id, { ...before, startDate: change.startDate ?? undefined, dueDate: change.dueDate });
      await reload();
      showToast(tr('Moved {title} to {date}.', { title: task.title, date: change.dueDate }), {
        duration: UNDO_MS,
        action: {
          label: tr('Undo'),
          onClick: () => {
            void updateTask(token, task.id, before).then(() => reload())
              .catch(() => showToast(tr('Could not undo the change.')));
          },
        },
      });
    } else {
      const step = steps.find((item) => item.id === change.id);
      if (!step) return;
      const before = stepRequest(step);
      await updateStep(token, step.id, { ...before, targetDate: change.targetDate });
      await reload();
      showToast(tr('Moved {title} to {date}.', { title: step.title, date: change.targetDate }), {
        duration: UNDO_MS,
        action: {
          label: tr('Undo'),
          onClick: () => {
            void updateStep(token, step.id, before).then(() => reload())
              .catch(() => showToast(tr('Could not undo the change.')));
          },
        },
      });
    }
  }, [token, steps, tasks, reload, showToast]);
}
