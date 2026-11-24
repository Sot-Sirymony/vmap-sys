import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { markBlocked } from '../../api/blockApi';
import type { BlockerAnalysis, BlockerCandidate, BlockerItem, TaskItem } from '../../types/vision';
import { BlockerAnalysisResult } from './BlockerAnalysisResult';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn().mockResolvedValue({ available: false }) }));
vi.mock('../../api/aiPlanApi', () => ({ suggestSmallerTasks: vi.fn() }));
vi.mock('../../api/taskApi', () => ({ splitTask: vi.fn() }));
vi.mock('../../api/blockApi', () => ({ markBlocked: vi.fn() }));

const base: BlockerItem = {
  itemType: 'TASK', itemId: 70, code: 'T-070', title: 'Ask for the dataset', kind: 'BLOCKED', goalId: 9, goalTitle: 'Finish the study',
  stepId: 31, stepTitle: 'Get the data', reason: 'No reply from the data office', priority: 'CRITICAL', dueDate: '2026-12-01',
  overdue: false, daysStuck: 6.4, timesBlocked: 2, heldUpSteps: 2, goalAtRisk: true, obstacleType: 'KNOWLEDGE',
  obstacleTypeGuessed: false, obstacleId: null, hasHelper: false, score: 30,
};

function analysis(overrides: Partial<BlockerAnalysis> = {}): BlockerAnalysis {
  return { blockers: [base], blockedCount: 1, waitingCount: 0, gapCount: 0, heldUpSteps: 2, aiChecked: false, candidates: [], ...overrides };
}

const overdueTask: BlockerCandidate = {
  itemType: 'TASK', itemId: 81, code: 'T-081', title: 'Send the survey', goalId: 9, goalTitle: 'Finish the study', stepId: 31,
  stepTitle: 'Get the data', signal: 'OVERDUE_NO_CHANGE', daysOverdue: 9, daysSinceChange: 12, reschedules: 0, fromAi: false,
};

function show(value: BlockerAnalysis, tasks: TaskItem[] = [], onMarked = vi.fn()) {
  render(
    <MemoryRouter>
      <BlockerAnalysisResult analysis={value} dreamId={5} tasks={tasks} token="token" onDataChange={vi.fn().mockResolvedValue(undefined)}
        onMarked={onMarked} />
    </MemoryRouter>,
  );
  return onMarked;
}

describe('BlockerAnalysisResult', () => {
  it('says so when nothing is stuck', () => {
    show(analysis({ blockers: [], blockedCount: 0, heldUpSteps: 0 }));
    expect(screen.getByText('Nothing is blocked or waiting in this dream. Keep going.')).toBeInTheDocument();
  });

  it('shows the built-in facts, the helper to look for and links that prefill the next page', () => {
    show(analysis());
    const card = screen.getByLabelText('Blocker 1: Ask for the dataset');
    expect(within(card).getByText('Stuck 6 day(s)')).toBeInTheDocument();
    expect(within(card).getByText('Blocked 2 times')).toBeInTheDocument();
    expect(within(card).getByText('Holds up 2 later step(s)')).toBeInTheDocument();
    expect(within(card).getByText('Goal date at risk')).toBeInTheDocument();
    expect(within(card).getByText('No reply from the data office')).toBeInTheDocument();
    expect(within(card).getByText(/Mentor or expert/)).toBeInTheDocument();
    expect(screen.getByText(/Showing the built-in analysis only/)).toBeInTheDocument();

    const record = within(card).getByRole('link', { name: 'Record as an obstacle' }).getAttribute('href') ?? '';
    expect(record).toContain('create=obstacle');
    expect(record).toContain('taskId=70');
    expect(record).toContain('stepId=31');
    const helper = within(card).getByRole('link', { name: 'Find a helper' }).getAttribute('href') ?? '';
    expect(helper).toContain('relatedGoalId=9');
    expect(helper).toContain('supportType=MENTOR');
    expect(within(card).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/dreams/5?edit=task-70');
  });

  it('shows the AI reading and pattern, and links an existing obstacle instead of creating one', () => {
    show(analysis({
      aiChecked: true, requestId: 12, pattern: 'Most blockers wait on other people.',
      blockers: [{ ...base, obstacleId: 4, hasHelper: true, obstacleType: 'PARTNER', obstacleTypeGuessed: true,
        rootCause: 'The request went to a general inbox.', firstMove: 'Phone the data office.', helper: 'Data officer' }],
    }));
    expect(screen.getByText('Most blockers wait on other people.')).toBeInTheDocument();
    expect(screen.getByText('The request went to a general inbox.')).toBeInTheDocument();
    expect(screen.getByText('Phone the data office.')).toBeInTheDocument();
    expect(screen.getByText('(AI guess)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the obstacle' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Find a helper' })).not.toBeInTheDocument();
  });

  it('opens the break-down dialog for a blocked task in the plan', async () => {
    const task = { id: 70, title: 'Ask for the dataset', dueDate: '2026-12-01', stepId: 31 } as TaskItem;
    show(analysis(), [task]);
    await userEvent.click(screen.getByRole('button', { name: 'Break into smaller tasks' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });
  it('lists possibly blocked work with its evidence, and Not blocked hides it for this view', async () => {
    show(analysis({ blockers: [], blockedCount: 0, heldUpSteps: 0, candidates: [overdueTask] }));
    expect(screen.getByText('Nothing is marked as blocked yet, but some work looks stuck.')).toBeInTheDocument();
    const card = screen.getByLabelText('Possibly blocked: Send the survey');
    expect(within(card).getByText('Overdue, no change')).toBeInTheDocument();
    expect(within(card).getByText('Overdue 9 day(s)')).toBeInTheDocument();
    expect(within(card).getByText('No change for 12 day(s)')).toBeInTheDocument();

    await userEvent.click(within(card).getByRole('button', { name: 'Not blocked' }));
    expect(screen.queryByLabelText('Possibly blocked: Send the survey')).not.toBeInTheDocument();
  });

  it('marks a possibly blocked item with the AI suggestion pre-filled, then reports back', async () => {
    vi.mocked(markBlocked).mockResolvedValue({ itemType: 'TASK', itemId: 81, obstacleId: 3, obstacleCreated: true });
    const onMarked = show(analysis({ candidates: [{ ...overdueTask, signal: 'AI', fromAi: true, suggestedType: 'DECISION',
      suggestedReason: 'Waiting on the director.' }] }));
    const card = screen.getByLabelText('Possibly blocked: Send the survey');
    expect(within(card).getByText('Waiting on the director.')).toBeInTheDocument();

    await userEvent.click(within(card).getByRole('button', { name: 'Mark as blocked' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByDisplayValue('Waiting on the director.')).toBeInTheDocument();
    expect(within(dialog).getByText(/Who can help/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark as blocked' }));

    expect(markBlocked).toHaveBeenCalledWith('token', { itemType: 'TASK', itemId: 81, obstacleType: 'DECISION',
      reason: 'Waiting on the director.', severity: 'MEDIUM' });
    await vi.waitFor(() => expect(onMarked).toHaveBeenCalled());
  });
});
