import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createGoalSynergyLink, deleteGoalSynergyLink, listGoalSynergyLinks } from '../../api/goalSynergyLinkApi';
import { aGoal } from '../../test/builders';
import type { GoalSynergyLink } from '../../types/vision';
import { GoalSynergyDialog } from './GoalSynergyDialog';

vi.mock('../../api/goalSynergyLinkApi');

const goal = aGoal({ id: 1, title: 'Learn AI research tools' });
const goals = [
  goal,
  aGoal({ id: 2, title: 'Build a literature workflow' }),
  aGoal({ id: 3, title: 'Run three times a week' }),
  aGoal({ id: 4, title: 'Old goal', archived: true }),
];
const link: GoalSynergyLink = {
  id: 50, goalId: 1, relatedGoalId: 3, relatedGoalCode: 'G-003', relatedGoalTitle: 'Run three times a week',
  relatedGoalVisionAreaName: 'Health', crossVisionArea: true, note: 'Clear head for research', createdAt: '2026-11-01T00:00:00Z',
};

describe('GoalSynergyDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(createGoalSynergyLink).mockResolvedValue(link);
    vi.mocked(deleteGoalSynergyLink).mockResolvedValue(undefined);
  });

  it('shows existing links with their area, and marks links across vision areas', async () => {
    vi.mocked(listGoalSynergyLinks).mockResolvedValue([link]);
    render(<GoalSynergyDialog goal={goal} goals={goals} token="token" onClose={vi.fn()} />);
    expect(screen.getByText('Synergy links — Learn AI research tools')).toBeInTheDocument();
    expect(await screen.findByText('Run three times a week')).toBeInTheDocument();
    expect(screen.getByText('G-003 · Health')).toBeInTheDocument();
    expect(screen.getByText('Cross-area')).toBeInTheDocument();
    expect(screen.getByText('Clear head for research')).toBeInTheDocument();
  });

  it('offers only other, active, not-yet-linked goals, and links one with a note', async () => {
    vi.mocked(listGoalSynergyLinks).mockResolvedValueOnce([]).mockResolvedValue([link]);
    render(<GoalSynergyDialog goal={goal} goals={goals} token="token" onClose={vi.fn()} />);
    expect(await screen.findByText('No synergy links yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add link' })).toBeDisabled();

    await userEvent.click(screen.getByRole('combobox', { name: 'Link to another goal' }));
    const offered = (await screen.findAllByRole('option')).map((option) => option.textContent);
    expect(offered).toEqual(['Link to another goal…', 'Build a literature workflow', 'Run three times a week']);
    await userEvent.click(screen.getByRole('option', { name: 'Run three times a week' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Synergy note' }), '  Clear head for research ');
    await userEvent.click(screen.getByRole('button', { name: 'Add link' }));

    expect(createGoalSynergyLink).toHaveBeenCalledWith('token', 1, { relatedGoalId: 3, note: 'Clear head for research' });
    expect(await screen.findByText('Cross-area')).toBeInTheDocument();
  });

  it('shows why a link could not be made', async () => {
    vi.mocked(listGoalSynergyLinks).mockResolvedValue([]);
    vi.mocked(createGoalSynergyLink).mockRejectedValue(new Error('These goals are already linked.'));
    render(<GoalSynergyDialog goal={goal} goals={goals} token="token" onClose={vi.fn()} />);
    await userEvent.click(await screen.findByRole('combobox', { name: 'Link to another goal' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Build a literature workflow' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add link' }));
    expect(await screen.findByText('These goals are already linked.')).toBeInTheDocument();
  });

  it('removes a link, and shows the list again even if removing fails', async () => {
    vi.mocked(listGoalSynergyLinks).mockResolvedValueOnce([link]).mockResolvedValue([]);
    const onClose = vi.fn();
    render(<GoalSynergyDialog goal={goal} goals={goals} token="token" onClose={onClose} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Remove link to Run three times a week' }));
    expect(deleteGoalSynergyLink).toHaveBeenCalledWith('token', 50);
    expect(await screen.findByText('No synergy links yet.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('shows no links when they cannot be loaded', async () => {
    vi.mocked(listGoalSynergyLinks).mockRejectedValue(new Error('offline'));
    render(<GoalSynergyDialog goal={goal} goals={goals} token="token" onClose={vi.fn()} />);
    await waitFor(() => expect(listGoalSynergyLinks).toHaveBeenCalled());
    expect(screen.getByText('No synergy links yet.')).toBeInTheDocument();
  });
});
