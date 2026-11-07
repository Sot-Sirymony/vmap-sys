import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiApi from '../api/aiApi';
import * as aiCoachApi from '../api/aiCoachApi';
import * as cohortApi from '../api/cohortApi';
import { setCurrentLanguage } from '../i18n/translate';
import { aDream, aGoal, aStep, aTask } from '../test/builders';
import { renderPage } from '../test/render';
import type { CoachParticipantDetail } from '../types/vision';
import { CoachParticipantPage } from './CoachParticipantPage';

vi.mock('../api/aiApi');
vi.mock('../api/aiCoachApi');
vi.mock('../api/cohortApi');

const detail = (overrides: Partial<CoachParticipantDetail> = {}): CoachParticipantDetail => ({
  summary: {
    userId: 11, fullName: 'Sok Chan', email: 'chan@example.com', cohortId: 1, cohortName: 'Spring 2027', lastActiveAt: '2026-11-01T08:00:00Z',
    daysInactive: 2, atRisk: false, riskReasons: [], aiConsent: true,
    metrics: { metrics: [
      { id: 'plan', label: 'A plan with dated steps', value: '3 of 4', target: '4', met: false },
      { id: 'review', label: 'Weekly reviews', value: '4 of 4', target: '4', met: true },
    ] },
  },
  dreams: [aDream({ id: 1, title: 'Open a bakery', status: 'ACTIVE' })],
  goals: [aGoal({ id: 2, dreamId: 1, title: 'Find a location', status: 'IN_PROGRESS' })],
  steps: [aStep({ id: 3, goalId: 2, title: 'Visit five shops', owner: 'Chan', targetDate: '2026-12-01', status: 'IN_PROGRESS' })],
  tasks: [aTask({ id: 4, stepId: 3, status: 'COMPLETED' }), aTask({ id: 5, stepId: 3, status: 'NOT_STARTED' })],
  journalsShared: false,
  fearEntries: null,
  attitudeCheckIns: null,
  comments: [{ id: 9, targetType: 'GOAL', targetId: 2, targetTitle: 'Find a location', body: 'Good progress on the shortlist.', createdAt: '2026-11-03T09:00:00Z', readAt: null }],
  pitch: null,
  ...overrides,
});

const open = () => renderPage(<CoachParticipantPage />, { role: 'COACH', route: '/coach/participants/11', path: '/coach/participants/:userId' });
const cardTitled = (title: string) => screen.getByText(title).closest('.MuiCard-root') as HTMLElement;

describe('CoachParticipantPage (V9 FR-17; BR-14, D-6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail());
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OFF' } as never);
    vi.mocked(aiCoachApi.getParticipantSummary).mockResolvedValue(null as never);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('shows the participant’s objectives and plan, read-only', async () => {
    open();
    expect(await screen.findByRole('heading', { name: 'Sok Chan' })).toBeInTheDocument();
    expect(cohortApi.getCoachParticipant).toHaveBeenCalledWith('token', 11);
    expect(screen.getByText('Spring 2027 · chan@example.com')).toBeInTheDocument();
    expect(screen.getByText('○ A plan with dated steps: 3 of 4')).toBeInTheDocument();
    expect(screen.getByText('✓ Weekly reviews: 4 of 4')).toBeInTheDocument();

    const plan = cardTitled('Plan');
    expect(within(plan).getByText('Open a bakery')).toBeInTheDocument();
    expect(within(plan).getByText('Find a location')).toBeInTheDocument();
    expect(within(plan).getByText('Visit five shops · Chan · 2026-12-01 · In Progress · 1 of 2 tasks done')).toBeInTheDocument();
    // A coach may read, never change (D-6): the plan offers nothing to edit.
    expect(within(plan).queryByRole('button')).not.toBeInTheDocument();
    expect(within(plan).queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('keeps journals private unless the participant shares them', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({
      journalsShared: false,
      // Even if entries were sent, they are not shown while sharing is off.
      fearEntries: [{ id: 1, fear: 'Losing my savings', worstCase: 'Debt', likelihood: 'MEDIUM', firstTest: 'Sell at a market', archived: false, createdAt: '', updatedAt: '' }],
    }));
    open();
    await screen.findByText('Journals');
    const journals = cardTitled('Journals');
    expect(within(journals).getByText(/Journals are private/)).toBeInTheDocument();
    expect(within(journals).queryByText(/Losing my savings/)).not.toBeInTheDocument();
  });

  it('shows the fear log and recent attitude check-ins, newest first, when journals are shared', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({
      journalsShared: true,
      fearEntries: [{ id: 1, fear: 'Losing my savings', worstCase: 'Debt', likelihood: 'MEDIUM', firstTest: 'Sell at a market', archived: false, createdAt: '', updatedAt: '' }],
      attitudeCheckIns: [
        { id: 1, checkInDate: '2026-11-05', score: 3 },
        { id: 2, checkInDate: '2026-11-06', score: 4, note: 'Found a shop' },
      ],
    }));
    open();
    await screen.findByText('Journals');
    const journals = cardTitled('Journals');
    expect(within(journals).getByText('Losing my savings · Medium · First test: Sell at a market')).toBeInTheDocument();
    const checkIns = within(journals).getAllByText(/^2026-11-0/).map((element) => element.textContent);
    expect(checkIns).toEqual(['2026-11-06 · 4 of 5 · Found a shop', '2026-11-05 · 3 of 5']);
  });

  it('says when shared journals are still empty', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({ journalsShared: true, fearEntries: [], attitudeCheckIns: [] }));
    open();
    expect(await screen.findByText('No fears written down yet.')).toBeInTheDocument();
    expect(screen.getByText('No check-ins yet.')).toBeInTheDocument();
  });

  it('warns when the participant has been inactive', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({ summary: { ...detail().summary, atRisk: true, daysInactive: 9 } }));
    open();
    expect(await screen.findByText('No activity for 9 days. A short check-in may help.')).toBeInTheDocument();
  });

  it('sends a comment about one goal, which the participant will see', async () => {
    vi.mocked(cohortApi.addCoachComment).mockResolvedValue({} as never);
    open();
    await screen.findByText('Good progress on the shortlist.');
    expect(screen.getByText('Not read yet')).toBeInTheDocument();
    const send = screen.getByRole('button', { name: 'Send comment' });
    expect(send).toBeDisabled();
    await userEvent.click(screen.getByRole('combobox', { name: 'About' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Goal: Find a location' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Comment' }), 'Try the market street too.');
    await userEvent.click(send);

    expect(cohortApi.addCoachComment).toHaveBeenCalledWith('token', 11, 'GOAL', 2, 'Try the market street too.');
    await waitFor(() => expect(cohortApi.getCoachParticipant).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('textbox', { name: 'Comment' })).toHaveValue('');
  });

  it('shows why a comment could not be sent', async () => {
    vi.mocked(cohortApi.addCoachComment).mockRejectedValue(new Error('You no longer coach this participant.'));
    open();
    await screen.findByText('Good progress on the shortlist.');
    await userEvent.type(screen.getByRole('textbox', { name: 'Comment' }), 'Hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send comment' }));
    expect(await screen.findByText('You no longer coach this participant.')).toBeInTheDocument();
  });

  it('drafts a comment with AI only when AI is on and the participant consented, and sends nothing by itself', async () => {
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
    vi.mocked(aiCoachApi.draftCoachFeedback).mockResolvedValue({ message: 'Well done on the shortlist.' });
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'Draft with AI' }));
    expect(aiCoachApi.draftCoachFeedback).toHaveBeenCalledWith('token', 11, 'GENERAL', null, 'en');
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Comment' })).toHaveValue('Well done on the shortlist.'));
    expect(screen.getByText(/Nothing is sent until you click Send comment/)).toBeInTheDocument();
    expect(cohortApi.addCoachComment).not.toHaveBeenCalled();
  });

  it('offers no AI draft without the participant’s AI consent', async () => {
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({ summary: { ...detail().summary, aiConsent: false } }));
    open();
    await screen.findByText('Good progress on the shortlist.');
    await waitFor(() => expect(aiApi.getAiStatus).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Draft with AI' })).not.toBeInTheDocument();
  });

  it('shows why an AI draft failed', async () => {
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
    vi.mocked(aiCoachApi.draftCoachFeedback).mockRejectedValue(new Error('AI help is temporarily unavailable.'));
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'Draft with AI' }));
    expect(await screen.findByText('AI help is temporarily unavailable.')).toBeInTheDocument();
  });

  it('rates the participant’s saved pitch', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({
      pitch: { rows: [{ pitch: 'Fresh bread for every school.', practice: 'Told my sister', rating: '3' }], coachRating: null },
    }));
    vi.mocked(cohortApi.ratePitch).mockResolvedValue({} as never);
    open();
    expect(await screen.findByText('Fresh bread for every school.')).toBeInTheDocument();
    expect(screen.getByText('Practised: Told my sister')).toBeInTheDocument();
    expect(screen.getByText('Their own rating: 3 of 5')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('combobox', { name: 'Your rating' }));
    await userEvent.click(await screen.findByRole('option', { name: '4 of 5' }));
    await userEvent.type(screen.getByLabelText('Feedback on clarity, benefit and the ask'), 'Say what the school gets.');
    await userEvent.click(screen.getByRole('button', { name: 'Save rating' }));
    expect(cohortApi.ratePitch).toHaveBeenCalledWith('token', 11, 4, 'Say what the school gets.');
  });

  it('shows an earlier rating, offers to update it, and shows why a rating failed', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({
      pitch: { rows: [{ pitch: 'Fresh bread.' }], coachRating: 2, coachFeedback: 'Too vague', coachRatedAt: '2026-11-02T10:00:00Z' },
    }));
    vi.mocked(cohortApi.ratePitch).mockRejectedValue(new Error('Rating must be 1 to 5'));
    open();
    expect(await screen.findByText(/You rated it 2 of 5/)).toBeInTheDocument();
    expect(screen.getByLabelText('Feedback on clarity, benefit and the ask')).toHaveValue('Too vague');
    await userEvent.click(screen.getByRole('button', { name: 'Update rating' }));
    expect(await screen.findByText('Rating must be 1 to 5')).toBeInTheDocument();
  });

  it('says when there is no plan or pitch yet', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockResolvedValue(detail({ dreams: [], goals: [], steps: [], tasks: [], comments: [] }));
    open();
    expect(await screen.findByText('No dreams yet.')).toBeInTheDocument();
    expect(screen.getByText('No pitch saved yet.')).toBeInTheDocument();
  });

  it('shows why the participant could not be loaded, for example one the coach does not coach', async () => {
    vi.mocked(cohortApi.getCoachParticipant).mockRejectedValue(new Error('Not found'));
    open();
    expect(await screen.findByText('Not found')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Participant' })).toBeInTheDocument();
  });

  it('shows the read-only view in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    open();
    await screen.findByRole('heading', { name: 'Sok Chan' });
    expect(screen.getByText('កំណត់ហេតុ')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ផ្ញើមតិ' })).toBeInTheDocument();
  });
});
