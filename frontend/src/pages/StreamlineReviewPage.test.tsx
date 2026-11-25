import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderPage } from '../test/render';
import type { StreamlineItem, StreamlineReview } from '../types/streamline';

vi.mock('../api/streamlineApi');
vi.mock('../api/partnerApi', () => ({ listPartners: vi.fn().mockResolvedValue({ content: [] }) }));
vi.mock('../api/aiApi', () => ({ getAiStatus: vi.fn() }));

const api = await import('../api/streamlineApi');
const { getAiStatus } = await import('../api/aiApi');
const { StreamlineReviewPage } = await import('./StreamlineReviewPage');

function item(overrides: Partial<StreamlineItem>): StreamlineItem {
  return {
    id: 1, kind: 'TASK', stepId: null, taskId: 11, parentStepId: 5, code: 'T-001', title: 'Search PubMed', status: 'NOT_STARTED',
    archived: false, estimatedHours: 2, requirementOwner: null, requirementSource: null, partnerId: null, whyNeeded: null, verdict: null,
    decision: null, mergeIntoItemId: null, keepReason: null, simplified: false, simplifyNote: null, speedLevers: [], automation: null,
    delegatePartnerId: null, archivedWithParent: false, archivedAt: null, restoredAt: null, cycleTimeDays: null, waitingDays: 0,
    blockedDays: 0, repeatCandidate: false, repeatRuleId: null, ...overrides,
  };
}

function review(overrides: Partial<StreamlineReview> = {}): StreamlineReview {
  const currentStage = overrides.currentStage ?? 1;
  return {
    id: 9, code: 'SR-001', anchorType: 'GOAL', goalId: 3, stepId: null, anchorCode: 'G-001', anchorTitle: 'Prepare a concept note',
    status: 'OPEN', currentStage,
    stages: [1, 2, 3, 4, 5].map((number) => ({ number, state: number < currentStage ? 'DONE' : 'PENDING', reason: null, unlocked: number <= currentStage })),
    baselineCycleDays: null, targetCycleDays: null, currentCycleDays: null, waitingDays: 0, blockedDays: 0,
    startedAt: '2026-11-24T00:00:00Z', completedAt: null,
    items: [
      item({ id: 1, kind: 'STEP', stepId: 5, taskId: null, parentStepId: null, code: 'S-001', title: 'Search literature' }),
      item({ id: 2, code: 'T-001', title: 'Search PubMed' }),
      item({ id: 3, code: 'T-002', title: 'Search Google Scholar', taskId: 12 }),
    ],
    summary: {
      deleted: 0, kept: 0, merged: 0, addedBack: 0, stepsBefore: 1, stepsAfter: 1, tasksBefore: 2, tasksAfter: 2, hoursBefore: 4, hoursAfter: 4,
      cycleBefore: null, cycleTarget: null, cycleNow: null, repeating: 0, reminders: 0, delegated: 0, nothingBroughtBack: false,
    },
    ...overrides,
  };
}

function renderReview() {
  return renderPage(<StreamlineReviewPage />, { route: '/streamline/9', path: '/streamline/:id' });
}

describe('StreamlineReviewPage (BRD 27)', () => {
  beforeEach(() => sessionStorage.clear());
  beforeEach(() => {
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    // V32 D-32-1: no saved suggestions.
    vi.mocked(api.getSavedStreamlineSuggestions).mockResolvedValue(null);
    vi.mocked(api.dismissStreamlineSuggestion).mockResolvedValue(undefined);
    vi.mocked(api.listStreamlineBatches).mockResolvedValue([]);
    vi.mocked(api.getStreamlineDrafts).mockResolvedValue({ stages: [], canDraftAll: true, unavailableReason: null });
  });

  it('shows the five stages in order with the later ones locked (D-27-3)', async () => {
    vi.mocked(api.getStreamlineReview).mockResolvedValue(review());
    renderReview();

    expect(await screen.findByRole('heading', { name: 'Stage 1: Question' })).toBeInTheDocument();
    // MUI renders the non-linear stepper as a tab list.
    expect(screen.getByRole('tab', { name: /Question.*Not done/ })).toHaveAttribute('aria-selected', 'true');
    for (const name of ['Delete', 'Simplify', 'Speed up', 'Automate']) {
      expect(screen.getByRole('tab', { name: new RegExp(`${name}.*Locked`) })).toBeDisabled();
    }
    // The coaching prompts for the stage are shown (BRD 27 §6.1).
    expect(screen.getByText("Who asked for this? If the answer is 'I'm not sure', start here.")).toBeInTheDocument();
  });

  it('answers several items at once with bulk marking (Q-27-2)', async () => {
    const user = userEvent.setup();
    vi.mocked(api.getStreamlineReview).mockResolvedValue(review());
    vi.mocked(api.bulkQuestionStreamline).mockResolvedValue(review());
    renderReview();

    await user.click(await screen.findByRole('checkbox', { name: /Select all/ }));
    const bulk = screen.getByRole('group', { name: 'Answer several at once' });
    await user.click(within(bulk).getByRole('combobox', { name: 'Verdict' }));
    await user.click(await screen.findByRole('option', { name: 'Needed' }));
    await user.click(within(bulk).getByRole('button', { name: 'Apply to selected' }));

    expect(api.bulkQuestionStreamline).toHaveBeenCalledWith('token', 9, expect.objectContaining({ itemIds: [1, 2, 3], verdict: 'NEEDED' }));
  });

  it('deletes at once and offers Undo, which restores the item (Q-27-3)', async () => {
    const user = userEvent.setup();
    const atStage2 = review({ currentStage: 2 });
    vi.mocked(api.getStreamlineReview).mockResolvedValue(atStage2);
    vi.mocked(api.deleteStreamlineItem).mockResolvedValue(atStage2);
    vi.mocked(api.restoreStreamlineItem).mockResolvedValue(atStage2);
    renderReview();

    const row = await screen.findByTestId('delete-T-001');
    await user.click(within(row).getByRole('button', { name: 'Delete' }));
    expect(api.deleteStreamlineItem).toHaveBeenCalledWith('token', 9, 2, false);
    await user.click(await screen.findByRole('button', { name: 'Undo' }));
    expect(api.restoreStreamlineItem).toHaveBeenCalledWith('token', 9, 2);
  });

  it('asks before leaving the goal with no open steps (BR-27-8)', async () => {
    const user = userEvent.setup();
    const atStage2 = review({ currentStage: 2 });
    vi.mocked(api.getStreamlineReview).mockResolvedValue(atStage2);
    const { ApiError } = await import('../api/apiClient');
    vi.mocked(api.deleteStreamlineItem)
      .mockRejectedValueOnce(new ApiError('This would leave the goal with no open steps. Confirm that the goal is finished or no longer needed.', 400))
      .mockResolvedValueOnce(atStage2);
    renderReview();

    await user.click(within(await screen.findByTestId('delete-S-001')).getByRole('button', { name: 'Delete' }));
    await user.click(await screen.findByRole('button', { name: 'Yes, it is finished or not needed' }));
    await waitFor(() => expect(api.deleteStreamlineItem).toHaveBeenLastCalledWith('token', 9, 1, true));
  });

  it('needs a reason of at least 10 characters to mark a stage not needed', async () => {
    const user = userEvent.setup();
    vi.mocked(api.getStreamlineReview).mockResolvedValue(review());
    vi.mocked(api.skipStreamlineStage).mockResolvedValue(review({ currentStage: 2 }));
    renderReview();

    await user.click(await screen.findByRole('button', { name: 'Not needed…' }));
    const confirm = screen.getByRole('button', { name: 'Mark not needed' });
    await user.type(screen.getByLabelText(/Reason/), 'too short');
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText(/Reason/), ' really');
    await user.click(confirm);
    expect(api.skipStreamlineStage).toHaveBeenCalledWith('token', 9, 1, 'too short really');
  });

  it('says why AI suggestions are missing when the monthly budget is used up (D-27-9)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ available: false, reason: 'BUDGET_REACHED', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(api.getStreamlineReview).mockResolvedValue(review());
    renderReview();

    expect(await screen.findByTestId('ai-unavailable')).toHaveTextContent("This month's AI budget is used up");
    expect(screen.queryByRole('button', { name: 'Suggest for this stage' })).not.toBeInTheDocument();
  });

  it('applies an AI suggestion only when the user chooses to (D-27-9)', async () => {
    const user = userEvent.setup();
    const atStage2 = review({ currentStage: 2 });
    vi.mocked(api.getStreamlineReview).mockResolvedValue(atStage2);
    vi.mocked(api.suggestStreamline).mockResolvedValue({ requestId: 4, stage: 2, suggestions: [
      { itemId: 3, code: 'T-002', title: 'Search Google Scholar', action: 'DELETE', detail: '', reason: 'Duplicates PubMed', targetItemId: null, verdict: null, lever: null, cadence: null },
    ] });
    vi.mocked(api.deleteStreamlineItem).mockResolvedValue(atStage2);
    renderReview();

    // V30 D-30-5: the suggestions load by themselves; nothing is applied until the user chooses.
    const card = await screen.findByTestId('ai-suggestion');
    expect(api.deleteStreamlineItem).not.toHaveBeenCalled();
    await user.click(within(card).getByRole('button', { name: 'Apply' }));
    expect(api.deleteStreamlineItem).toHaveBeenCalledWith('token', 9, 3, false);
  });

  it('shows the summary as counts and hints when nothing was brought back (D-27-5, Q-27-7)', async () => {
    const done = review({
      status: 'COMPLETED', currentStage: 5, completedAt: '2026-11-24T10:00:00Z',
      stages: [1, 2, 3, 4, 5].map((number) => ({ number, state: 'DONE' as const, reason: null, unlocked: true })),
    });
    done.summary = { ...done.summary, deleted: 2, merged: 1, addedBack: 0, tasksAfter: 1, nothingBroughtBack: true };
    vi.mocked(api.getStreamlineReview).mockResolvedValue(done);
    renderReview();

    expect(await screen.findByText('0 of 3')).toBeInTheDocument();
    expect(screen.getByTestId('add-back-hint')).toHaveTextContent('You brought nothing back.');
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });

  it('gives advice on a completed review only when asked (V30 D-30-9)', async () => {
    const user = userEvent.setup();
    const done = review({
      status: 'COMPLETED', currentStage: 5, completedAt: '2026-11-24T10:00:00Z',
      stages: [1, 2, 3, 4, 5].map((number) => ({ number, state: 'DONE' as const, reason: null, unlocked: true })),
    });
    vi.mocked(api.getStreamlineReview).mockResolvedValue(done);
    vi.mocked(api.adviseStreamlineSummary).mockResolvedValue({ requestId: 5, points: [
      { text: 'Start this step a week earlier.', itemCode: 'S-001' },
      { text: 'Batch the bank calls.', itemCode: null },
    ] });
    renderReview();

    await user.click(await screen.findByRole('button', { name: 'Get AI advice' }));
    expect(api.adviseStreamlineSummary).toHaveBeenCalledWith('token', 9, 'en');
    expect(await screen.findByText('Batch the bank calls.')).toBeInTheDocument();
    expect(screen.getByText('S-001:')).toBeInTheDocument();
  });

  it('offers no AI advice to a coach (V30 D-30-11)', async () => {
    vi.mocked(api.getParticipantStreamlineReview).mockResolvedValue(review({
      status: 'COMPLETED', currentStage: 5, completedAt: '2026-11-24T10:00:00Z',
      stages: [1, 2, 3, 4, 5].map((number) => ({ number, state: 'DONE' as const, reason: null, unlocked: true })),
    }));
    renderPage(<StreamlineReviewPage coach />, { role: 'COACH', route: '/coach/participants/7/streamline/9', path: '/coach/participants/:userId/streamline/:reviewId' });
    expect(await screen.findByText(/Coaches can read it but not change it/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Get AI advice' })).not.toBeInTheDocument();
  });

  it('shows a coach none of the V32 controls (D-32-13)', async () => {
    const remind = review({ currentStage: 5 });
    vi.mocked(api.getParticipantStreamlineReview).mockResolvedValue({
      ...remind,
      stages: [1, 2, 3, 4, 5].map((number) => ({ number, state: number < 5 ? 'DONE' as const : 'PENDING' as const, reason: null, unlocked: true })),
      items: remind.items.map((entry) => ({ ...entry, automation: 'REMIND' as const, remindCadence: 'WEEKLY' as const, remindDay: 1 })),
    });
    renderPage(<StreamlineReviewPage coach />, { role: 'COACH', route: '/coach/participants/7/streamline/9', path: '/coach/participants/:userId/streamline/:reviewId' });

    expect(await screen.findByText(/Coaches can read it but not change it/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Draft all stages' })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'AI suggestions' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('recent-changes')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Remind me how often' })).not.toBeInTheDocument();
    expect(api.getSavedStreamlineSuggestions).not.toHaveBeenCalled();
    expect(api.getStreamlineDrafts).not.toHaveBeenCalled();
    expect(api.listStreamlineBatches).not.toHaveBeenCalled();
  });

  it('is read-only for a coach (D-27-10)', async () => {
    vi.mocked(api.getParticipantStreamlineReview).mockResolvedValue(review({ currentStage: 2 }));
    renderPage(<StreamlineReviewPage coach />, { role: 'COACH', route: '/coach/participants/7/streamline/9', path: '/coach/participants/:userId/streamline/:reviewId' });

    expect(await screen.findByText(/Coaches can read it but not change it/)).toBeInTheDocument();
    expect(api.getParticipantStreamlineReview).toHaveBeenCalledWith('token', 7, 9);
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Done, next stage' })).not.toBeInTheDocument();
  });
});
