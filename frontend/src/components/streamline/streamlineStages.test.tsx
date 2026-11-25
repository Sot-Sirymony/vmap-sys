import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderPage } from '../../test/render';
import type { StreamlineItem, StreamlineReview } from '../../types/streamline';
import { AutomateStage, SimplifyStage, SpeedStage, type StageActions } from './StreamlineStages';
import { days, hours } from './streamlineLabels';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/streamlineApi');

const { getAiStatus } = await import('../../api/aiApi');
const streamlineApi = await import('../../api/streamlineApi');
const { StreamlineSuggestionsPanel, aiUnavailableMessage } = await import('./StreamlineSuggestionsPanel');

// V32 D-32-1: no saved suggestions unless a test says so.
beforeEach(() => {
  vi.mocked(streamlineApi.getSavedStreamlineSuggestions).mockResolvedValue(null);
  vi.mocked(streamlineApi.dismissStreamlineSuggestion).mockResolvedValue(undefined);
  vi.mocked(streamlineApi.listStreamlineBatches).mockResolvedValue([]);
  vi.mocked(streamlineApi.getStreamlineDrafts).mockResolvedValue({ stages: [], canDraftAll: true, unavailableReason: null });
});

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

  it('chooses how often a "Remind me" item reminds, and shows it (D-32-9)', async () => {
    const user = userEvent.setup();
    const act = actions();
    renderPage(<AutomateStage review={review([item({ automation: 'REMIND', remindCadence: 'WEEKLY', remindDay: 3 })])}
      readOnly={false} actions={act} partners={[]} />);

    const row = screen.getByTestId('automate-T-001');
    expect(within(row).getByTestId('remind-T-001')).toHaveTextContent('Reminds you every Wednesday');
    await user.click(within(row).getByRole('combobox', { name: 'On' }));
    await user.click(await screen.findByRole('option', { name: 'Friday' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { remindCadence: 'WEEKLY', remindDay: 5 });
    await user.click(within(row).getByRole('combobox', { name: 'Remind me how often' }));
    await user.click(await screen.findByRole('option', { name: 'Every month' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { remindCadence: 'MONTHLY' });
  });

  it('shows a monthly reminder and its day of the month', () => {
    renderPage(<AutomateStage review={review([item({ automation: 'REMIND', remindCadence: 'MONTHLY', remindDay: 12 })])}
      readOnly={false} actions={actions()} partners={[]} />);
    expect(screen.getByTestId('remind-T-001')).toHaveTextContent('Reminds you on day 12 of each month');
    expect(screen.getByRole('combobox', { name: 'Day of the month' })).toBeInTheDocument();
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
  beforeEach(() => sessionStorage.clear());

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
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task, other])} stage={2} actions={act} onReview={vi.fn()} />);

    // V30 D-30-5: the suggestions load by themselves.
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
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={1} actions={actions()} onReview={vi.fn()} />);

    expect(await screen.findByText('AI help is temporarily unavailable.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Suggest again' }));
    expect(await screen.findByText('No suggestions for this stage.')).toBeInTheDocument();
  });
});

describe('Streamline AI suggestions, V30 (BRD 30)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
  });

  const base = { detail: 'Detail', reason: 'Reason', targetItemId: null, verdict: null, lever: null, cadence: null, title: 'x' } as const;

  it('loads suggestions once per review and stage in the session; Suggest again asks again (D-30-5)', async () => {
    const user = userEvent.setup();
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 2, suggestions: [] });
    const { unmount } = renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={actions()} onReview={vi.fn()} />);
    await waitFor(() => expect(streamlineApi.suggestStreamline).toHaveBeenCalledTimes(1));
    unmount();

    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={actions()} onReview={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: 'Suggest again' }));
    expect(streamlineApi.suggestStreamline).toHaveBeenCalledTimes(2);
  });

  it('applies the ticked cards as one batch, in order, then all the rest (D-30-6, D-32-4)', async () => {
    const user = userEvent.setup();
    const task = item({ id: 2, code: 'T-001' });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 5, suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'QUESTION' },
      { ...base, itemId: 2, code: 'T-001', action: 'DELEGATE' },
      { ...base, itemId: 2, code: 'T-001', action: 'REMIND' },
      { ...base, itemId: 2, code: 'T-001', action: 'REPEAT', cadence: 'WEEKLY' },
    ] });
    const updated = review([task]);
    vi.mocked(streamlineApi.applyStreamlineBatch).mockResolvedValue({ batchId: 7, applied: 2, total: 2, failedAtPosition: null, error: null, review: updated });
    const onReview = vi.fn();
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task])} stage={5} actions={actions()} onReview={onReview} />);

    // A question has nothing to apply, so it has no tick box.
    await screen.findAllByTestId('ai-suggestion');
    expect(screen.queryByRole('checkbox', { name: 'Select T-001 Ask yourself' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Select T-001 Remind me' }));
    await user.click(screen.getByRole('checkbox', { name: 'Select T-001 Delegate' }));
    await user.click(screen.getByRole('button', { name: 'Apply selected (2)' }));
    expect(await screen.findByText('Applied 2 suggestion(s).')).toBeInTheDocument();
    expect(streamlineApi.applyStreamlineBatch).toHaveBeenCalledWith('token', 9, 5, [
      { type: 'UPDATE', itemId: 2, update: { automation: 'DELEGATE' } },
      { type: 'UPDATE', itemId: 2, update: { automation: 'REMIND' } },
    ]);
    expect(onReview).toHaveBeenCalledWith(updated);
    expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Got it' }));
    expect(screen.getAllByTestId('ai-suggestion')).toHaveLength(1);
  });

  it('stops at the first failure, says how many were applied and why (D-30-6)', async () => {
    const user = userEvent.setup();
    const task = item({ id: 2, code: 'T-001' });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 5, suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'DELEGATE' },
      { ...base, itemId: 2, code: 'T-001', action: 'REPEAT', cadence: 'WEEKLY' },
      { ...base, itemId: 2, code: 'T-001', action: 'REMIND' },
    ] });
    vi.mocked(streamlineApi.applyStreamlineBatch).mockResolvedValue({ batchId: 8, applied: 1, total: 3, failedAtPosition: 2,
      error: 'Restore this task before making it repeat.', review: review([task]) });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task])} stage={5} actions={actions()} onReview={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: 'Apply all' }));
    expect(await screen.findByText(/Applied 1 of 3. The rest were not applied. Restore this task before making it repeat./)).toBeInTheDocument();
    expect(streamlineApi.applyStreamlineBatch).toHaveBeenCalledWith('token', 9, 5, [
      { type: 'UPDATE', itemId: 2, update: { automation: 'DELEGATE' } },
      { type: 'REPEAT', itemId: 2, cadence: 'WEEKLY' },
      { type: 'UPDATE', itemId: 2, update: { automation: 'REMIND' } },
    ]);
    expect(screen.getAllByTestId('ai-suggestion')).toHaveLength(2);
  });

  it('saves an edited stage 1 draft answer (D-30-7)', async () => {
    const user = userEvent.setup();
    const task = item({ id: 2, code: 'T-001', requirementSource: null, requirementOwner: null, verdict: null });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 1, suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'ANSWER', verdict: 'NEEDED', requirementSource: 'MENTOR', requirementOwner: 'Mentor',
        whyNeeded: 'Needed for the ethics form' },
    ] });
    const act = actions();
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task])} stage={1} actions={act} onReview={vi.fn()} />);

    const owner = await screen.findByRole('textbox', { name: 'Asked for by (a role)' });
    await user.clear(owner);
    await user.type(owner, 'Research mentor');
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { requirementSource: 'MENTOR', requirementOwner: 'Research mentor',
      whyNeeded: 'Needed for the ethics form', verdict: 'NEEDED' });
  });

  it('changes the title with an edited Simplify suggestion (D-30-8)', async () => {
    const user = userEvent.setup();
    const task = item({ id: 2, code: 'T-001' });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 3, suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'SIMPLIFY', newTitle: 'One-page report' },
    ] });
    const act = actions();
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task])} stage={3} actions={act} onReview={vi.fn()} />);

    const title = await screen.findByRole('textbox', { name: 'New title' });
    await user.clear(title);
    await user.type(title, 'Half-page report');
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    expect(act.simplify).toHaveBeenCalledWith(task, { title: 'Half-page report', note: 'Detail' });
  });
});

describe('Streamline saved suggestions, V32 (BRD 32 phase 1)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(streamlineApi.suggestStreamline).mockClear();
  });

  const base = { detail: 'Detail', reason: 'Reason', targetItemId: null, verdict: null, lever: null, cadence: null, title: 'x' } as const;
  const saved = {
    requestId: 4, stage: 2, savedAt: '2026-11-24T08:00:00Z',
    suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'KEEP' as const },
      { ...base, itemId: 2, code: 'T-001', action: 'QUESTION' as const },
    ],
  };

  it('shows saved cards at once, labelled with their date, without asking the AI (SF-FR-01)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(streamlineApi.getSavedStreamlineSuggestions).mockResolvedValue(saved);
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={actions()} onReview={vi.fn()} />);

    await waitFor(() => expect(screen.getAllByTestId('ai-suggestion')).toHaveLength(2));
    expect(screen.getByTestId('suggested-on')).toHaveTextContent('Suggested on');
    expect(screen.getByRole('button', { name: 'Suggest again' })).toBeEnabled();
    expect(streamlineApi.suggestStreamline).not.toHaveBeenCalled();
  });

  it('asks the AI by itself when nothing saved still fits (D-30-5 kept)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 2, suggestions: [] });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={actions()} onReview={vi.fn()} />);

    await waitFor(() => expect(streamlineApi.suggestStreamline).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId('suggested-on')).not.toBeInTheDocument();
  });

  it('keeps saved cards usable without AI, and says why new ones are missing (SF-FR-02)', async () => {
    const user = userEvent.setup();
    vi.mocked(getAiStatus).mockResolvedValue({ available: false, reason: 'DAILY_LIMIT', consentGiven: true, dailyLimit: 20, messagesLeftToday: 0 });
    vi.mocked(streamlineApi.getSavedStreamlineSuggestions).mockResolvedValue(saved);
    const act = actions();
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={act} onReview={vi.fn()} />);

    await waitFor(() => expect(screen.getAllByTestId('ai-suggestion')).toHaveLength(2));
    expect(screen.getByTestId('ai-unavailable')).toHaveTextContent('These saved suggestions can still be applied.');
    expect(screen.getByRole('button', { name: 'Suggest again' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    expect(act.updateItem).toHaveBeenCalledWith(2, { keep: true });
    expect(streamlineApi.suggestStreamline).not.toHaveBeenCalled();
  });

  it('removes a dismissed card from the saved set too (D-32-1)', async () => {
    const user = userEvent.setup();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(streamlineApi.getSavedStreamlineSuggestions).mockResolvedValue(saved);
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={actions()} onReview={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: 'Got it' }));
    expect(streamlineApi.dismissStreamlineSuggestion).toHaveBeenCalledWith('token', 9, 2, expect.objectContaining({ itemId: 2, action: 'QUESTION' }));
    expect(screen.getAllByTestId('ai-suggestion')).toHaveLength(1);
  });

  it('shows only the reason when AI is off and nothing is saved', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ available: false, reason: 'NO_CONSENT', consentGiven: false, dailyLimit: 20, messagesLeftToday: 20 });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={actions()} onReview={vi.fn()} />);

    expect(await screen.findByTestId('ai-unavailable')).toHaveTextContent('AI consent');
    expect(screen.queryByRole('button', { name: 'Suggest again' })).not.toBeInTheDocument();
  });
});

describe('Streamline batch undo, V32 (BRD 32 phase 2)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
  });

  const base = { detail: 'Detail', reason: 'Reason', targetItemId: null, verdict: null, lever: null, cadence: null, title: 'x' } as const;

  it('undoes the batch just applied and names items changed since (D-32-5, BR-32-6)', async () => {
    const user = userEvent.setup();
    const task = item({ id: 2, code: 'T-001' });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 5, suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'DELEGATE' },
      { ...base, itemId: 2, code: 'T-001', action: 'REMIND' },
    ] });
    vi.mocked(streamlineApi.applyStreamlineBatch).mockResolvedValue({ batchId: 7, applied: 2, total: 2, failedAtPosition: null, error: null, review: review([task]) });
    const restored = review([task]);
    vi.mocked(streamlineApi.undoStreamlineBatch).mockResolvedValue({ undone: 1, skipped: [{ code: 'S-014', reason: 'Changed since, left as it is' }], review: restored });
    const onReview = vi.fn();
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task])} stage={5} actions={actions()} onReview={onReview} />);

    await user.click(await screen.findByRole('button', { name: 'Apply all' }));
    await user.click(await screen.findByRole('button', { name: 'Undo' }));

    expect(streamlineApi.undoStreamlineBatch).toHaveBeenCalledWith('token', 9, 7);
    expect(await screen.findByText('Undid 1 change(s). Changed since, so left as they are: S-014.')).toBeInTheDocument();
    expect(onReview).toHaveBeenLastCalledWith(restored);
    expect(screen.queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument();
  });

  it('adds several levers for one item instead of overwriting them', async () => {
    const user = userEvent.setup();
    const task = item({ id: 2, code: 'T-001', speedLevers: ['BATCH'] });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 4, suggestions: [
      { ...base, itemId: 2, code: 'T-001', action: 'LEVER', lever: 'PARALLEL' },
      { ...base, itemId: 2, code: 'T-001', action: 'LEVER', lever: 'REMOVE_WAIT' },
      { ...base, itemId: 2, code: 'T-001', action: 'LEVER', lever: 'BATCH' },
    ] });
    vi.mocked(streamlineApi.applyStreamlineBatch).mockResolvedValue({ batchId: 3, applied: 2, total: 2, failedAtPosition: null, error: null, review: review([task]) });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([task], { currentStage: 4 })} stage={4} actions={actions()} onReview={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: 'Apply all' }));
    await waitFor(() => expect(streamlineApi.applyStreamlineBatch).toHaveBeenCalledWith('token', 9, 4, [
      { type: 'UPDATE', itemId: 2, update: { speedLevers: ['BATCH', 'PARALLEL'] } },
      { type: 'UPDATE', itemId: 2, update: { speedLevers: ['BATCH', 'PARALLEL', 'REMOVE_WAIT'] } },
    ]));
    // The lever already chosen has nothing to do, so its card goes.
    await waitFor(() => expect(screen.queryAllByTestId('ai-suggestion')).toHaveLength(0));
  });

  it('lists recent changes on the stage with Undo while it is still possible (SF-FR-07)', async () => {
    const user = userEvent.setup();
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 2, suggestions: [] });
    vi.mocked(streamlineApi.listStreamlineBatches).mockResolvedValue([
      { id: 5, stage: 2, applied: 2, total: 2, failedAtPosition: null, createdAt: '2026-11-25T08:00:00Z', undoneAt: null, canUndo: true,
        changes: [{ code: 'S-003', action: 'DELETE' }, { code: 'T-001', action: 'MERGE' }] },
      { id: 4, stage: 2, applied: 1, total: 1, failedAtPosition: null, createdAt: '2026-11-25T07:00:00Z', undoneAt: '2026-11-25T07:05:00Z',
        canUndo: false, changes: [{ code: 'S-002', action: 'UPDATE' }] },
    ]);
    vi.mocked(streamlineApi.undoStreamlineBatch).mockResolvedValue({ undone: 2, skipped: [], review: review([item({})]) });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={2} actions={actions()} onReview={vi.fn()} />);

    const list = await screen.findByTestId('recent-changes');
    expect(within(list).getByText('S-003 deleted, T-001 merged')).toBeInTheDocument();
    expect(within(list).getByText(/Undone/)).toBeInTheDocument();
    expect(within(list).getAllByRole('button', { name: /Undo these/ })).toHaveLength(1);
    await user.click(within(list).getByRole('button', { name: 'Undo these 2 changes' }));
    expect(streamlineApi.undoStreamlineBatch).toHaveBeenCalledWith('token', 9, 5);
    expect(await screen.findByText('Undid 2 change(s).')).toBeInTheDocument();
  });
});

describe('Draft all stages, V32 (BRD 32 phase 3)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(streamlineApi.suggestStreamline).mockResolvedValue({ requestId: 1, stage: 2, suggestions: [] });
  });

  const base = { detail: 'Detail', reason: 'Reason', targetItemId: null, verdict: null, lever: null, cadence: null, title: 'x' } as const;
  const stage2 = { requestId: 5, stage: 2, suggestions: [{ ...base, itemId: 2, code: 'T-001', action: 'KEEP' as const }] };
  const stage3 = { requestId: 5, stage: 3, suggestions: [{ ...base, itemId: 2, code: 'T-001', action: 'SIMPLIFY' as const, newTitle: 'One call' }] };

  it('drafts every stage in one request and shows later stages as a read-only preview (SF-FR-08)', async () => {
    const user = userEvent.setup();
    vi.mocked(streamlineApi.draftAllStreamline).mockResolvedValue({ requestId: 5, stages: [stage2, stage3] });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})], { currentStage: 2 })} stage={2} actions={actions()} onReview={vi.fn()} />);
    await waitFor(() => expect(streamlineApi.suggestStreamline).toHaveBeenCalled());

    vi.mocked(streamlineApi.getStreamlineDrafts).mockResolvedValue({ canDraftAll: true, unavailableReason: null, stages: [
      { stage: 2, preview: false, suggestions: stage2 },
      { stage: 3, preview: true, suggestions: stage3 },
    ] });
    await user.click(screen.getByRole('button', { name: 'Draft all stages' }));

    expect(streamlineApi.draftAllStreamline).toHaveBeenCalledWith('token', 9, 'en');
    await waitFor(() => expect(screen.getAllByTestId('ai-suggestion')).toHaveLength(1));
    const preview = await screen.findByTestId('draft-preview');
    await user.click(within(preview).getByRole('button', { name: 'Stage 3: Simplify (1)' }));
    expect(within(preview).getByText('You can apply these when you reach this stage.')).toBeInTheDocument();
    expect(within(preview).getAllByTestId('draft-card')).toHaveLength(1);
    expect(within(preview).getByText('New title: One call')).toBeInTheDocument();
    expect(within(preview).queryByRole('button', { name: 'Apply' })).not.toBeInTheDocument();
  });

  it('is disabled with the reason when the budget has no room (D-32-8)', async () => {
    vi.mocked(streamlineApi.getStreamlineDrafts).mockResolvedValue({ stages: [], canDraftAll: false,
      unavailableReason: "This month's AI budget does not have room to draft every stage at once. Ask for one stage at a time." });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})], { currentStage: 2 })} stage={2} actions={actions()} onReview={vi.fn()} />);

    expect(await screen.findByRole('button', { name: 'Draft all stages' })).toBeDisabled();
    expect(screen.getByTestId('draft-all-reason')).toHaveTextContent('does not have room');
  });

  it('labels cards drafted before the earlier stages were finished (D-32-7)', async () => {
    vi.mocked(streamlineApi.getSavedStreamlineSuggestions).mockResolvedValue({ ...stage3, savedAt: '2026-11-25T08:00:00Z', draftedBeforeChanges: true });
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})], { currentStage: 3 })} stage={3} actions={actions()} onReview={vi.fn()} />);

    expect(await screen.findByTestId('drafted-early')).toHaveTextContent('Drafted before you finished the earlier stages.');
  });

  it('offers no Draft all on the last stage', async () => {
    renderPage(<StreamlineSuggestionsPanel token="token" review={review([item({})])} stage={5} actions={actions()} onReview={vi.fn()} />);

    await screen.findByRole('button', { name: /Suggest/ });
    await waitFor(() => expect(streamlineApi.getStreamlineDrafts).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Draft all stages' })).not.toBeInTheDocument();
  });
});
