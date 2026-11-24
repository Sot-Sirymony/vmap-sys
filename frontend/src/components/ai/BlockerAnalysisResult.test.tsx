import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { BlockerAnalysis, BlockerItem, TaskItem } from '../../types/vision';
import { BlockerAnalysisResult } from './BlockerAnalysisResult';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn().mockResolvedValue({ available: false }) }));
vi.mock('../../api/aiPlanApi', () => ({ suggestSmallerTasks: vi.fn() }));
vi.mock('../../api/taskApi', () => ({ splitTask: vi.fn() }));

const base: BlockerItem = {
  itemType: 'TASK', itemId: 70, code: 'T-070', title: 'Ask for the dataset', kind: 'BLOCKED', goalId: 9, goalTitle: 'Finish the study',
  stepId: 31, stepTitle: 'Get the data', reason: 'No reply from the data office', priority: 'CRITICAL', dueDate: '2026-12-01',
  overdue: false, daysStuck: 6.4, timesBlocked: 2, heldUpSteps: 2, goalAtRisk: true, obstacleType: 'KNOWLEDGE',
  obstacleTypeGuessed: false, obstacleId: null, hasHelper: false, score: 30,
};

function analysis(overrides: Partial<BlockerAnalysis> = {}): BlockerAnalysis {
  return { blockers: [base], blockedCount: 1, waitingCount: 0, gapCount: 0, heldUpSteps: 2, aiChecked: false, ...overrides };
}

function show(value: BlockerAnalysis, tasks: TaskItem[] = []) {
  render(
    <MemoryRouter>
      <BlockerAnalysisResult analysis={value} dreamId={5} tasks={tasks} token="token" onDataChange={vi.fn().mockResolvedValue(undefined)} />
    </MemoryRouter>,
  );
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
});
