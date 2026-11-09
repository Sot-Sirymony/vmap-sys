import { act, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import { ToastProvider } from '../context/ToastContext';
import { aStep, aTask } from '../test/builders';
import { useTimelineReschedule } from './useTimelineReschedule';

vi.mock('../api/stepApi');
vi.mock('../api/taskApi');

const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;
const task = aTask({ id: 30, stepId: 20, title: 'Search PubMed', startDate: '2026-12-01', dueDate: '2026-12-10', owner: 'Mony', progressPercent: 40 });
const step = aStep({ id: 20, goalId: 10, title: 'Search literature', targetDate: '2027-01-31' });

describe('useTimelineReschedule (TL-FR-39 to TL-FR-41)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(taskApi.updateTask).mockResolvedValue(task);
    vi.mocked(stepApi.updateStep).mockResolvedValue(step);
  });

  it('saves a task’s new dates with everything else unchanged, then offers Undo', async () => {
    const reload = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useTimelineReschedule('token', [step], [task], reload), { wrapper });

    await act(() => result.current({ kind: 'task', id: 30, startDate: '2026-12-03', dueDate: '2026-12-12' }));

    expect(taskApi.updateTask).toHaveBeenCalledWith('token', 30, expect.objectContaining({
      stepId: 20, title: 'Search PubMed', owner: 'Mony', progressPercent: 40, startDate: '2026-12-03', dueDate: '2026-12-12',
    }));
    expect(reload).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Moved Search PubMed to 2026-12-12.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Undo' }));
    expect(taskApi.updateTask).toHaveBeenLastCalledWith('token', 30, expect.objectContaining({ startDate: '2026-12-01', dueDate: '2026-12-10' }));
  });

  it('saves a step’s new target date', async () => {
    const { result } = renderHook(() => useTimelineReschedule('token', [step], [task], vi.fn()), { wrapper });
    await act(() => result.current({ kind: 'step', id: 20, targetDate: '2027-02-10' }));
    expect(stepApi.updateStep).toHaveBeenCalledWith('token', 20, expect.objectContaining({ goalId: 10, title: 'Search literature', targetDate: '2027-02-10' }));
  });

  it('lets a refusal reach the timeline, so the bar can go back and say why', async () => {
    vi.mocked(stepApi.updateStep).mockRejectedValue(new Error('Too late for its goal.'));
    const { result } = renderHook(() => useTimelineReschedule('token', [step], [task], vi.fn()), { wrapper });
    await expect(result.current({ kind: 'step', id: 20, targetDate: '2027-12-31' })).rejects.toThrow('Too late for its goal.');
  });
});
