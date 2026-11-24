import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { renderPage } from '../../test/render';
import type { StreamlineListItem } from '../../types/streamline';

vi.mock('../../api/streamlineApi');

const api = await import('../../api/streamlineApi');
const { streamlineChipLabel, StreamlineChip } = await import('./StreamlineChip');
const { RepeatingTasksCard } = await import('./RepeatingTasksCard');

function listItem(overrides: Partial<StreamlineListItem>): StreamlineListItem {
  return {
    id: 4, code: 'SR-004', anchorType: 'GOAL', goalId: 3, stepId: null, anchorTitle: 'Save a deposit', status: 'COMPLETED', currentStage: 5,
    startedAt: '2026-11-10T00:00:00Z', completedAt: '2026-11-12T09:00:00Z', deleted: 5, addedBack: 0, stepsBefore: 9, stepsAfter: 4,
    tasksBefore: 30, tasksAfter: 22, ...overrides,
  };
}

describe('Streamline summary chip (BRD 27 §5)', () => {
  it('counts steps cut for a goal and tasks cut for a step', () => {
    expect(streamlineChipLabel(listItem({}))).toMatch(/^Streamlined .+: −5 steps$/);
    expect(streamlineChipLabel(listItem({ anchorType: 'STEP', goalId: null, stepId: 8 }))).toMatch(/: −8 tasks$/);
    expect(streamlineChipLabel(listItem({ stepsAfter: 9 }))).not.toContain('−');
  });

  it('opens the review it summarises', async () => {
    const user = userEvent.setup();
    renderPage(<StreamlineChip review={listItem({})} />);
    await user.click(screen.getByRole('button', { name: /Streamlined/ }));
    expect(screen.getByRole('status', { name: 'Current location', hidden: true })).toHaveTextContent('/streamline/4');
  });

  it('shows nothing for a goal never streamlined', () => {
    const { container } = renderPage(<StreamlineChip review={undefined} />);
    expect(container.querySelector('.MuiChip-root')).toBeNull();
  });
});

describe('Repeating tasks (SR-FR-09)', () => {
  it('lists rules and stops one, keeping it listed as stopped', async () => {
    const user = userEvent.setup();
    const rule = { id: 7, stepId: 2, sourceTaskId: 3, lastTaskId: 3, title: 'Weekly report', cadence: 'WEEKLY' as const, nextDue: '2026-12-01', active: true };
    vi.mocked(api.listTaskRepeatRules).mockResolvedValue([rule]);
    vi.mocked(api.updateTaskRepeatRule).mockResolvedValue({ ...rule, active: false });
    renderPage(<RepeatingTasksCard token="token" />);

    const row = await screen.findByTestId('repeat-rule-7');
    expect(within(row).getByText('Weekly report')).toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Stop repeating' }));
    expect(api.updateTaskRepeatRule).toHaveBeenCalledWith('token', 7, { active: false });
    expect(await within(row).findByText('Stopped')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Restart' })).toBeInTheDocument();
  });

  it('shows nothing when no task repeats', async () => {
    vi.mocked(api.listTaskRepeatRules).mockResolvedValue([]);
    renderPage(<RepeatingTasksCard token="token" />);
    await vi.waitFor(() => expect(api.listTaskRepeatRules).toHaveBeenCalled());
    expect(screen.queryByText('Repeating tasks')).not.toBeInTheDocument();
  });
});
