import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getProgressMetrics } from '../../api/accountApi';
import { archiveGratitudeEntry, createGratitudeEntry } from '../../api/gratitudeEntryApi';
import { aDream, aGoal, aStep, aTask, aVisionArea } from '../../test/builders';
import { renderPage } from '../../test/render';
import type { DashboardAttention, DashboardSummary as SummaryData, GratitudeEntry } from '../../types/vision';
import { AttentionPanel } from './AttentionPanel';
import { DashboardSummary } from './DashboardSummary';
import { EnergyBudgetCard } from './EnergyBudgetCard';
import { GettingStarted } from './GettingStarted';
import { GratitudeCard } from './GratitudeCard';
import { ProgressMetricsCard } from './ProgressMetricsCard';

vi.mock('../../api/accountApi');
vi.mock('../../api/gratitudeEntryApi');

const summary = {
  totalVisionAreas: 3, activeDreams: 4, activeGoals: 6, activeTasks: 12, completedTasks: 9, overdueTasks: 2, blockedTasks: 1,
  averageProgress: 41.6, tasksDueThisWeek: 5, tasksDueInPeriod: 5, completedTasksInPeriod: 7,
} as SummaryData;

const card = (label: string) => screen.getByText(label).closest('a, .MuiCard-root, [class*="MuiPaper"]') as HTMLElement;

describe('DashboardSummary (CLAUDE.md page 1)', () => {
  it('shows each summary figure, with task pressure linked to the matching filtered list', () => {
    renderPage(<DashboardSummary summary={summary} periodLabel="this week" dueInPeriodLink="/tasks?due=week" />);
    expect(within(card('Overdue Tasks')).getByText('2')).toBeInTheDocument();
    expect(screen.getByText('Overdue Tasks').closest('a')).toHaveAttribute('href', '/tasks?overdue=true');
    expect(screen.getByText('Blocked Tasks').closest('a')).toHaveAttribute('href', '/tasks?status=BLOCKED');
    expect(screen.getByText('Due this week').closest('a')).toHaveAttribute('href', '/tasks?due=week');
    expect(within(card('Vision Areas')).getByText('3')).toBeInTheDocument();
    expect(within(card('Active Dreams')).getByText('4')).toBeInTheDocument();
    expect(within(card('Active Goals')).getByText('6')).toBeInTheDocument();
    expect(within(card('Open Tasks')).getByText('12')).toBeInTheDocument();
    expect(within(card('Completed this week')).getByText('7')).toBeInTheDocument();
    expect(within(card('Average Progress')).getByText('42%')).toBeInTheDocument();
  });

  it('keeps the chosen vision area in every link, and shows zeros before data arrives', () => {
    renderPage(<DashboardSummary summary={null} periodLabel="this week" dueInPeriodLink="/tasks" visionAreaId="2" />);
    expect(screen.getByText('Overdue Tasks').closest('a')).toHaveAttribute('href', '/tasks?overdue=true&visionAreaId=2');
    expect(screen.getByText('Active Dreams').closest('a')).toHaveAttribute('href', '/dreams?status=ACTIVE&visionAreaId=2');
    expect(within(card('Average Progress')).getByText('0%')).toBeInTheDocument();
  });
});

describe('AttentionPanel', () => {
  const empty: DashboardAttention = {
    blockedTasksWithoutPartner: [], complexStepsWithoutTasks: [], dreamsWithoutGoals: [], goalsWithoutSteps: [],
    inactiveMoonshotGoals: [], inactiveMoonshotDreams: [], starvedVisionAreas: [],
  };

  it('shows nothing before data arrives, and a reassurance when the map has no dead ends', () => {
    const { unmount } = renderPage(<AttentionPanel attention={undefined} />);
    expect(screen.queryByText('Needs your attention')).not.toBeInTheDocument();
    unmount();
    renderPage(<AttentionPanel attention={empty} />);
    expect(screen.getByText('Your map holds together')).toBeInTheDocument();
  });

  it('lists each dead end with a count and a link to fix it, scoped to the chosen area', () => {
    renderPage(
      <AttentionPanel
        visionAreaId="2"
        attention={{
          ...empty,
          blockedTasksWithoutPartner: [aTask({ status: 'BLOCKED' }), aTask({ id: 2, status: 'BLOCKED' })],
          complexStepsWithoutTasks: [aStep({ complex: true })],
          dreamsWithoutGoals: [aDream()],
          goalsWithoutSteps: [aGoal()],
          starvedVisionAreas: [aVisionArea({ id: 5 })],
        }}
      />,
    );
    expect(screen.getByText('Needs your attention')).toBeInTheDocument();
    expect(screen.getByText('Blocked tasks with no partner')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Find a partner/ })).toHaveAttribute('href', '/tasks?status=BLOCKED&visionAreaId=2');
    expect(screen.getByRole('link', { name: /Break into tasks/ })).toHaveAttribute('href', '/steps?complex=true&visionAreaId=2');
    expect(screen.getByText('Complex steps with no tasks')).toBeInTheDocument();
    expect(screen.queryByText('Your map holds together')).not.toBeInTheDocument();
  });
});

describe('GettingStarted', () => {
  it('walks a new participant through area, dream and goals, marking what is done', () => {
    renderPage(<GettingStarted hasArea hasDream={false} hasGoal={false} />);
    expect(screen.getByText('1. Create a Vision Area')).toBeInTheDocument();
    expect(screen.getByText('3. Break it down')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Add a dream/ })).toHaveAttribute('href', '/dreams?create=dream');
    expect(screen.queryByRole('link', { name: /Create a vision area/ })).not.toBeInTheDocument();
  });
});

describe('EnergyBudgetCard (FR-34)', () => {
  it('says there is nothing to weigh when no tasks are due', () => {
    renderPage(<EnergyBudgetCard budget={{ charge: 0, neutral: 0, drain: 0, net: 0 }} />);
    expect(screen.getByText('No tasks due this week — nothing to weigh yet.')).toBeInTheDocument();
  });

  it('shows the counts and whether the week leans draining, energising or balanced', () => {
    const { unmount } = renderPage(<EnergyBudgetCard budget={{ charge: 1, neutral: 2, drain: 4, net: -3 }} />);
    expect(screen.getByText(/Net -3 — this week leans draining/)).toBeInTheDocument();
    unmount();
    const second = renderPage(<EnergyBudgetCard budget={{ charge: 3, neutral: 0, drain: 1, net: 2 }} />);
    expect(screen.getByText(/Net \+2 — this week leans energising/)).toBeInTheDocument();
    second.unmount();
    renderPage(<EnergyBudgetCard budget={{ charge: 2, neutral: 1, drain: 2, net: 0 }} />);
    expect(screen.getByText('Balanced — charging and draining work are even this week.')).toBeInTheDocument();
  });
});

describe('GratitudeCard', () => {
  const entry = (id: number, description: string): GratitudeEntry => ({
    id, category: 'PERSON', description, archived: false, createdAt: '2026-11-06T08:00:00Z', updatedAt: '2026-11-06T08:00:00Z',
  });

  beforeEach(() => vi.clearAllMocks());

  it('logs a new entry and tells the dashboard to refresh', async () => {
    vi.mocked(createGratitudeEntry).mockResolvedValue(entry(3, 'My mentor replied'));
    const onChanged = vi.fn();
    renderPage(<GratitudeCard token="token" gratitude={{ countThisWeek: 0, recent: [] }} onChanged={onChanged} />);
    expect(screen.getByText('Nothing logged yet — the first entry starts the list.')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'What are you grateful for?' }), 'My mentor replied');
    await userEvent.click(screen.getByRole('button', { name: 'Log it' }));
    expect(createGratitudeEntry).toHaveBeenCalledWith('token', expect.objectContaining({ description: 'My mentor replied' }));
    expect(onChanged).toHaveBeenCalled();
  });

  it('lists recent entries, removes one, and shows why saving failed', async () => {
    vi.mocked(archiveGratitudeEntry).mockResolvedValue(undefined);
    vi.mocked(createGratitudeEntry).mockRejectedValue(new Error('Description is too long.'));
    const onChanged = vi.fn();
    renderPage(<GratitudeCard token="token" gratitude={{ countThisWeek: 2, recent: [entry(1, 'Slept well'), entry(2, 'A good walk')] }} onChanged={onChanged} />);
    expect(screen.getByText(/2 logged this week/)).toBeInTheDocument();
    expect(screen.getByText(/— Slept well/)).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Remove' })[0]);
    expect(archiveGratitudeEntry).toHaveBeenCalledWith('token', 1);

    await userEvent.type(screen.getByRole('textbox', { name: 'What are you grateful for?' }), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Log it' }));
    expect(await screen.findByText('Description is too long.')).toBeInTheDocument();
  });
});

describe('ProgressMetricsCard (BRD success measures)', () => {
  it('shows each measure against its target, marking those reached', async () => {
    vi.mocked(getProgressMetrics).mockResolvedValue({ metrics: [
      { id: 'a', label: 'Dreams with goals', value: '3 of 4', target: '75%', met: true },
      { id: 'b', label: 'Weekly reviews', value: '1', target: '4', met: false },
    ] });
    renderPage(<ProgressMetricsCard token="token" />);
    expect(await screen.findByText('Dreams with goals: 3 of 4')).toBeInTheDocument();
    expect(screen.getByText('Target: 75%')).toBeInTheDocument();
    expect(screen.getByLabelText('Reached')).toBeInTheDocument();
    expect(screen.getByLabelText('Not reached yet')).toBeInTheDocument();
  });
});
