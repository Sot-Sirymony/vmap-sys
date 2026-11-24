import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { renderPage } from '../../test/render';
import type { StreamlineItem, StreamlineReview } from '../../types/streamline';
import { AutomateStage, SimplifyStage, SpeedStage, type StageActions } from './StreamlineStages';
import { days, hours } from './streamlineLabels';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/streamlineApi');

const { getAiStatus } = await import('../../api/aiApi');
const streamlineApi = await import('../../api/streamlineApi');
const { StreamlineSuggestionsPanel, aiUnavailableMessage } = await import('./StreamlineSuggestionsPanel');

function item(overrides: Partial<StreamlineItem>): StreamlineItem {
  return {
    id: 2, kind: 'TASK', stepId: null, taskId: 11, parentStepId: 5, code: 'T-001', title: 'Weekly report', status: 'NOT_STARTED',
    archived: false, estimatedHours: 2, requirementOwner: 'Me', requirementSource: 'SELF', partnerId: null, whyNeeded: null,
    verdict: 'NEEDED', decision: 'KEEP', mergeIntoItemId: null, keepReason: null, simplified: false, simplifyNote: null, speedLevers: [],
    automation: null, delegatePartnerId: null, archivedWithParent: false, archivedAt: null, restoredAt: null, cycleTimeDays: 3.5,
    waitingDays: 1, blockedDays: 0, repeatCandidate: false, repeatRuleId: null, ...overrides,
  };
}

function review(items: StreamlineItem[], overrides: Partial<StreamlineReview> = {}): StreamlineReview {
  return {
    id: 9, code: 'SR-001', anchorType: 'STEP', goalId: null, stepId: 5, anchorCode: 'S-001', anchorTitle: 'Reporting', status: 'OPEN',
    currentStage: 5, stages: [1, 2, 3, 4, 5].map((number) => ({ number, state: 'PENDING' as const, reason: null, unlocked: true })),
    baselineCycleDays: 4, targetCycleDays: null, currentCycleDays: 3.5, waitingDays: 1, blockedDays: 0, startedAt: '2026-11-24T00:00:00Z',
    completedAt: null, items,
    summary: { deleted: 0, kept: 1, merged: 0, addedBack: 0, stepsBefore: 1, stepsAfter: 1, tasksBefore: 2, tasksAfter: 2, hoursBefore: 4,
      hoursAfter: 3, cycleBefore: 4, cycleTarget: null, cycleNow: 3.5, repeating: 0, reminders: 0, delegated: 0, nothingBroughtBack: false },
    ...overrides,
  };
}

function actions(): StageActions {
  return {
    updateItem: vi.fn(), bulkQuestion: vi.fn(), deleteItem: vi.fn(), restoreItem: vi.fn(), mergeItem: vi.fn(), simplify: vi.fn(),
    setTarget: vi.fn(), repeat: vi.fn(),
  };
}

describe('Streamline stage 3: simplify', () => {
  it('saves a simpler title, estimate, owner and note, and marks a step simple', async () => {
    const user = userEvent.setup();
    const act = actions();
    const step = item({ id: 1, kind: 'STEP', stepId: 5, taskId: null, parentStepId: null, code: 'S-001', title: 'Write the report', estimatedHours: 6 });
    renderPage(<SimplifyStage review={review([step])} readOnly={false} actions={act} partners={[]} />);

    const row = screen.getByTestId('simplify-S-001');
    await user.click(within(row).getByRole('button', { name: 'Simplify' }));
    await user.clear(within(row).getByLabelText('Simpler title or scope'));
    await user.type(within(row).getByLabelText('Simpler title or scope'), 'One-page report');
    await user.clear(within(row).getByLabelText('Estimate (hours)'));
    await user.type(within(row).getByLabelText('Estimate (hours)'), '2');
    await user.type(within(row).getByLabelText('One owner, no hand-off'), 'Dara');
    await user.type(within(row).getByLabelText('What you simplified'), 'Dropped the appendix');
    await user.click(within(row).getByLabelText('Now a simple step'));
    await user.click(within(row).getByRole('button', { name: 'Save' }));

    expect(act.simplify).toHaveBeenCalledWith(step, { title: 'One-page report', estimatedHours: 2, owner: 'Dara', markSimple: true, note: 'Dropped the appendix' });
    expect(screen.getByRole('group', { name: 'Before and now' })).toHaveTextContent('Estimated hours');
  });

  it('shows what was simplified and no buttons when read-only', () => {
    renderPage(<SimplifyStage review={review([item({ simplified: true, simplifyNote: 'One owner now' })])} readOnly actions={actions()} partners={[]} />);
    expect(screen.getByText('Simplified')).toBeInTheDocument();
    expect(screen.getByText('One owner now')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Simplify' })).not.toBeInTheDocument();
  });
});

describe('Streamline stage 4: speed up', () => {
  it('toggles speed-up levers and sets a target', async () => {
    const user = userEvent.setup();
    const act = actions();
    const slow = item({ speedLevers: ['BATCH'] });
    renderPage(<SpeedStage review={review([slow, item({ id: 3, code: 'T-002', title: 'Quick task', cycleTimeDays: null, waitingDays: null })])} readOnly={false} actions={act} partners={[]} />);

    expect(screen.getByText(/Took 3\.5 days · waiting 1\.0 days · blocked 0\.0 days/)).toBeInTheDocument();
    const levers = screen.getByRole('group', { name: 'Ways to speed up T-001' });
    await user.click(within(levers).getByRole('button', { name: 'Do in parallel' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { speedLevers: ['BATCH', 'PARALLEL'] });
    await user.click(within(levers).getByRole('button', { name: 'Batch' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { speedLevers: [] });

    await user.type(screen.getByLabelText('Target days from start to done'), '2.5');
    await user.click(screen.getByRole('button', { name: 'Set target' }));
    expect(act.setTarget).toHaveBeenCalledWith(2.5);
  });

  it('says when there are not enough finished tasks yet', () => {
    renderPage(<SpeedStage review={review([item({})], { currentCycleDays: null })} readOnly actions={actions()} partners={[]} />);
    expect(screen.getByText('Not enough finished tasks yet')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Set target' })).not.toBeInTheDocument();
  });
});

describe('Streamline stage 5: automate', () => {
  it('makes a task repeat, sets a reminder, delegates to a partner or leaves it manual', async () => {
    const user = userEvent.setup();
    const act = actions();
    const task = item({ repeatCandidate: true });
    renderPage(<AutomateStage review={review([task, item({ id: 1, kind: 'STEP', stepId: 5, taskId: null, code: 'S-001', title: 'Reporting' })])}
      readOnly={false} actions={act} partners={[{ id: 4, name: 'A Mentor' }]} />);

    const row = screen.getByTestId('automate-T-001');
    expect(within(row).getByText('Done before: a repeat candidate')).toBeInTheDocument();
    await user.click(within(row).getByRole('combobox', { name: 'How often' }));
    await user.click(await screen.findByRole('option', { name: 'Every month' }));
    await user.click(within(row).getByRole('button', { name: 'Make it repeat' }));
    expect(act.repeat).toHaveBeenCalledWith(task, 'MONTHLY');

    await user.click(within(row).getByRole('button', { name: 'Remind me' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { automation: 'REMIND' });
    await user.click(within(row).getByRole('combobox', { name: 'Delegate or automate outside the app' }));
    await user.click(await screen.findByRole('option', { name: 'A Mentor' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { automation: 'DELEGATE', delegatePartnerId: 4 });
    await user.click(within(row).getByRole('combobox', { name: 'Delegate or automate outside the app' }));
    await user.click(await screen.findByRole('option', { name: 'No partner' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { automation: 'DELEGATE' });
    await user.click(within(row).getByRole('button', { name: 'Leave manual' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { automation: 'MANUAL' });

    // A step cannot repeat, so it has no repeat button.
    expect(within(screen.getByTestId('automate-S-001')).queryByRole('button', { name: 'Make it repeat' })).not.toBeInTheDocument();
  });

  it('shows chosen automation and the repeat badge read-only', () => {
    renderPage(<AutomateStage review={review([item({ automation: 'REPEAT', repeatRuleId: 3 })])} readOnly actions={actions()} partners={[]} />);
    expect(screen.getByText('Repeats')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remind me' })).not.toBeInTheDocument();
  });
});

describe('Streamline labels', () => {
  it('formats days and hours, with a dash for nothing', () => {
    expect(days(null)).toBe('—');
    expect(days(2)).toBe('2.0 days');
    expect(hours(undefined)).toBe('—');
    expect(hours(3)).toBe('3 h');
    expect(hours(2.5)).toBe('2.5 h');
  });
});

describe('Streamline AI suggestions (SR-FR-14, D-27-9)', () => {
  it('names the reason for every unavailable state', () => {
    expect(aiUnavailableMessage('NO_CONSENT')).toContain('AI consent');
    expect(aiUnavailableMessage('DAILY_LIMIT')).toContain("today's AI requests");
    expect(aiUnavailableMessage('BUDGET_REACHED')).toContain("month's AI budget");
    expect(aiUnavailableMessage('OFF')).toContain('switched off');
  });

  it('applies each kind of suggestion through the normal actions, and dismisses questions', async () => {
    const user = userEvent.setup();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    const task = item({ id: 2, code: 'T-001' });
    const other = item({ id: 3, code: 'T-002', taskId: 12 });
    const base = { detail: 'Detail', reason: 'Reason', targetItemId: null, verdict: null, lever: null, cadence: null, title: 'x' } as const;
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 2, suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'QUESTION' },
      { ...base, itemId: 2, code: 'T-001', action: 'VERDICT', verdict: 'NOT_NEEDED' },
      { ...base, itemId: 2, code: 'T-001', action: 'KEEP' },
      { ...base, itemId: 2, code: 'T-001', action: 'MERGE', targetItemId: 3 },
      { ...base, itemId: 2, code: 'T-001', action: 'SIMPLIFY' },
      { ...base, itemId: 2, code: 'T-001', action: 'LEVER', lever: 'BATCH' },
      { ...base, itemId: 2, code: 'T-001', action: 'REPEAT', cadence: 'WEEKLY' },
      { ...base, itemId: 2, code: 'T-001', action: 'DELEGATE' },
      { ...base, itemId: 2, code: 'T-001', action: 'REMIND' },
    ] });
    const act = actions();
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task, other])} stage={2} actions={act} />);

    await user.click(await screen.findByRole('button', { name: 'Suggest for this stage' }));
    await waitFor(() => expect(screen.getAllByTestId('ai-suggestion')).toHaveLength(9));
    await user.click(screen.getByRole('button', { name: 'Got it' }));
    while (screen.queryAllByRole('button', { name: 'Apply' }).length > 0) {
      await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);
    }
    expect(act.updateItem).toHaveBeenCalledWith(2, { verdict: 'NOT_NEEDED' });
    expect(act.updateItem).toHaveBeenCalledWith(2, { keep: true });
    expect(act.mergeItem).toHaveBeenCalledWith(task, 3);
    expect(act.simplify).toHaveBeenCalledWith(task, { note: 'Detail' });
    expect(act.updateItem).toHaveBeenCalledWith(2, { speedLevers: ['BATCH'] });
    expect(act.repeat).toHaveBeenCalledWith(task, 'WEEKLY');
    expect(act.updateItem).toHaveBeenCalledWith(2, { automation: 'DELEGATE' });
    expect(act.updateItem).toHaveBeenCalledWith(2, { automation: 'REMIND' });
    expect(screen.queryAllByTestId('ai-suggestion')).toHaveLength(0);
  });

  it('shows the server reason when a request fails, and an empty answer plainly', async () => {
    const user = userEvent.setup();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(streamlineApi.suggestStreamline).mockRejectedValueOnce(new Error('AI help is temporarily unavailable.'))
      .mockResolvedValueOnce({ requestId: 2, stage: 1, suggestions: [] });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={1} actions={actions()} />);

    await user.click(await screen.findByRole('button', { name: 'Suggest for this stage' }));
    expect(await screen.findByText('AI help is temporarily unavailable.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Suggest for this stage' }));
    expect(await screen.findByText('No suggestions for this stage.')).toBeInTheDocument();
  });
});
