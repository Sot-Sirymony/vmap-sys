import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BreakDownDialog } from './BreakDownDialog';
import { WhatChangedDialog } from './WhatChangedDialog';
import type { TaskItem } from '../../types/vision';

const splitTask = vi.fn();
const getAiStatus = vi.fn();
const suggestSmallerTasks = vi.fn();
vi.mock('../../api/taskApi', () => ({ splitTask: (...args: unknown[]) => splitTask(...args) }));
vi.mock('../../api/aiApi', () => ({ getAiStatus: (...args: unknown[]) => getAiStatus(...args) }));
vi.mock('../../api/aiPlanApi', () => ({ suggestSmallerTasks: (...args: unknown[]) => suggestSmallerTasks(...args) }));

const task = { id: 31, stepId: 12, title: 'Write the whole report', owner: 'Dara', priority: 'HIGH', dueDate: '2099-01-31',
  status: 'BLOCKED', blockerReason: 'Too big to start', progressPercent: 0, archived: false } as TaskItem;

describe('Break into smaller tasks (V17 BL-FR-25, BL-FR-26, BL-FR-29)', () => {
  beforeEach(() => {
    splitTask.mockReset().mockResolvedValue({ created: [], original: task });
    getAiStatus.mockReset().mockResolvedValue({ available: false });
    suggestSmallerTasks.mockReset();
  });

  it('saves the rows with the chosen outcome, keeping the original by default', async () => {
    const onSaved = vi.fn();
    render(<BreakDownDialog token="t" task={task} onClose={vi.fn()} onSaved={onSaved} />);

    const titles = ['Outline the sections', 'Draft the summary', 'Draft the findings'];
    for (const [index, title] of titles.entries()) {
      await userEvent.type(screen.getByRole('textbox', { name: `Smaller task ${index + 1}` }), title);
    }
    // No row ticked as startable: a soft note, never a block.
    expect(screen.getByText('None of these feels startable yet. Try making the first one smaller.')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Keep it as a checklist parent' })).toBeChecked();
    await userEvent.click(screen.getByRole('radio', { name: 'Replace it (archived, can be restored)' }));

    await userEvent.click(screen.getByRole('button', { name: 'Save smaller tasks' }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    const [, id, request] = splitTask.mock.calls[0];
    expect(id).toBe(31);
    expect(request.originalOutcome).toBe('REPLACE');
    expect(request.rows.map((row: { title: string }) => row.title)).toEqual(titles);
    expect(request.rows[2].dueDate).toBe('2099-01-31');
  });

  it('keeps between two and five rows', async () => {
    render(<BreakDownDialog token="t" task={task} onClose={vi.fn()} onSaved={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Remove task 3' }));
    expect(screen.queryByRole('button', { name: /Remove task/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add another' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add another' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add another' }));
    expect(screen.getAllByRole('textbox', { name: /Smaller task/ })).toHaveLength(5);
    expect(screen.queryByRole('button', { name: 'Add another' })).not.toBeInTheDocument();
  });

  it('fills the rows from AI suggestions only when AI is on, without saving', async () => {
    getAiStatus.mockResolvedValue({ available: true });
    suggestSmallerTasks.mockResolvedValue({ tasks: [
      { title: 'Open a blank document', owner: 'Me', priority: 'HIGH', dueDate: '2099-01-10' },
      { title: 'Write three headings', owner: 'Me', priority: 'HIGH', dueDate: null },
    ] });
    render(<BreakDownDialog token="t" task={task} onClose={vi.fn()} onSaved={vi.fn()} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Suggest smaller tasks' }));
    expect(await screen.findByDisplayValue('Open a blank document')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Write three headings')).toBeInTheDocument();
    expect(splitTask).not.toHaveBeenCalled();
  });
});

describe('What changed? (V17 BL-FR-27)', () => {
  it('sends the note, or nothing when skipped', async () => {
    const onSubmit = vi.fn();
    const { unmount } = render(<WhatChangedDialog taskTitle="Analyse the data" onSubmit={onSubmit} onCancel={vi.fn()} />);
    await userEvent.type(screen.getByRole('textbox', { name: 'What changed, or what will you do differently?' }), 'Got the data from Dara');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(onSubmit).toHaveBeenLastCalledWith('Got the data from Dara');
    unmount();

    render(<WhatChangedDialog taskTitle="Analyse the data" onSubmit={onSubmit} onCancel={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Skip' }));
    expect(onSubmit).toHaveBeenLastCalledWith(undefined);
  });
});
