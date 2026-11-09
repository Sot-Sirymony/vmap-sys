import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as accountApi from '../api/accountApi';
import * as aiApi from '../api/aiApi';
import * as aiPlanApi from '../api/aiPlanApi';
import * as dreamApi from '../api/dreamApi';
import * as planApi from '../api/planApi';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import type { PlanStatus } from '../types/vision';
import { saveBlob } from '../utils/saveBlob';
import { PlanPage } from './PlanPage';

vi.mock('../api/accountApi');
vi.mock('../api/aiApi');
vi.mock('../api/aiPlanApi');
vi.mock('../api/dreamApi');
vi.mock('../api/planApi');
vi.mock('../utils/saveBlob');

const dreams = [aDream({ id: 1, title: 'Become a researcher' }), aDream({ id: 2, title: 'Run a marathon' })];

function status(dreamId: number, doneKeys: string[]): PlanStatus {
  const stages = [
    { key: 'STATEMENT', label: 'Dream statement', detail: 'Say what you want and why.' },
    { key: 'GOALS', label: 'Goals', detail: 'At least two goals.' },
    { key: 'DATES', label: 'Dates', detail: 'Every step has a date.' },
    { key: 'PEOPLE', label: 'People', detail: 'Who will help.' },
  ];
  return { dreamId, planReady: doneKeys.length === stages.length, stages: stages.map((stage) => ({ ...stage, done: doneKeys.includes(stage.key) })) };
}

describe('PlanPage (V9 written plan)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    answerEmptyLists([aiPlanApi, dreamApi, planApi]);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ available: false, reason: 'OFF', consentGiven: false, dailyLimit: 0, messagesLeftToday: 0 });
    vi.mocked(aiApi.listAiConversations).mockResolvedValue([] as never);
    vi.mocked(dreamApi.listDreams).mockResolvedValue(dreams);
    vi.mocked(planApi.getPlanStatus).mockImplementation(async (_token, dreamId) => status(dreamId, ['STATEMENT']));
    vi.mocked(planApi.listMilestones).mockResolvedValue([]);
    vi.mocked(planApi.createMilestone).mockResolvedValue({ id: 5, dreamId: 1, title: 'First paper read', achievedOn: '2026-11-05' });
    vi.mocked(planApi.archiveMilestone).mockResolvedValue(undefined);
  });

  it('shows the plan stages in order, marking what is done, and points to the next one', async () => {
    renderPage(<PlanPage />);
    expect(await screen.findByText('Dream statement')).toBeInTheDocument();
    expect(screen.getByText('Work through these in order.')).toBeInTheDocument();
    expect(screen.getAllByLabelText('Done')).toHaveLength(1);
    expect(screen.getAllByLabelText('Not done')).toHaveLength(3);
    expect(screen.getAllByRole('button', { name: 'Do this next' })).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Do this next' }));
    expect(currentLocation()).toBe('/dreams/1');
  });

  it('sends each next stage to the page where it is done', async () => {
    vi.mocked(planApi.getPlanStatus).mockResolvedValueOnce(status(1, ['STATEMENT', 'GOALS']));
    const first = renderPage(<PlanPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Do this next' }));
    expect(currentLocation()).toBe('/steps');
    first.unmount();

    vi.mocked(planApi.getPlanStatus).mockResolvedValueOnce(status(1, ['STATEMENT', 'GOALS', 'DATES']));
    renderPage(<PlanPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Do this next' }));
    expect(currentLocation()).toBe('/partners?create=partner');
  });

  it('sends the Strengths and Gaps stage to that page for the same dream (V8.1 FR-84)', async () => {
    vi.mocked(planApi.getPlanStatus).mockResolvedValueOnce({
      dreamId: 1,
      planReady: false,
      stages: [
        { key: 'STATEMENT', label: 'Dream statement', detail: 'Written.', done: true },
        { key: 'STRENGTHS', label: 'Strengths and gaps', detail: 'Take an honest look.', done: false },
      ],
    });
    renderPage(<PlanPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Do this next' }));
    expect(currentLocation()).toBe('/strengths?dreamId=1');
  });

  it('says when the core of the plan is written', async () => {
    vi.mocked(planApi.getPlanStatus).mockResolvedValue(status(1, ['STATEMENT', 'GOALS', 'DATES', 'PEOPLE']));
    renderPage(<PlanPage />);
    expect(await screen.findByText('The core of this plan is written. Keep adding what it still needs.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Do this next' })).not.toBeInTheDocument();
  });

  it('switches to another dream’s plan', async () => {
    renderPage(<PlanPage />);
    await screen.findByText('Dream statement');
    await userEvent.click(screen.getByRole('combobox', { name: 'Dream' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Run a marathon' }));
    await waitFor(() => expect(planApi.getPlanStatus).toHaveBeenLastCalledWith('token', 2));
    expect(currentLocation()).toBe('/?dreamId=2');
  });

  it('adds a milestone reached, and removes one', async () => {
    vi.mocked(planApi.listMilestones).mockResolvedValueOnce([]).mockResolvedValue([{ id: 5, dreamId: 1, title: 'First paper read', achievedOn: '2026-11-05' }]);
    renderPage(<PlanPage />);
    expect(await screen.findByText('No milestones yet. Your first one goes here when you reach it.')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'Milestone' }), '  First paper read ');
    const date = screen.getByLabelText('Date reached');
    await userEvent.clear(date);
    await userEvent.type(date, '2026-11-05');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(planApi.createMilestone).toHaveBeenCalledWith('token', { dreamId: 1, title: 'First paper read', achievedOn: '2026-11-05' });
    expect(await screen.findByText(/· First paper read/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove First paper read' }));
    expect(planApi.archiveMilestone).toHaveBeenCalledWith('token', 5);
  });

  it('downloads the plan as a PDF', async () => {
    const pdf = new Blob(['%PDF'], { type: 'application/pdf' });
    vi.mocked(accountApi.downloadPlanPdf).mockResolvedValue(pdf);
    renderPage(<PlanPage />);
    await screen.findByText('Dream statement');
    await userEvent.click(screen.getByRole('button', { name: 'Download PDF' }));
    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(pdf, 'dream-plan-1.pdf'));
  });

  it('shows why a milestone, a removal or the PDF failed', async () => {
    vi.mocked(planApi.listMilestones).mockResolvedValue([{ id: 5, dreamId: 1, title: 'First paper read', achievedOn: '2026-11-05' }]);
    vi.mocked(planApi.createMilestone).mockRejectedValue(new Error('Date cannot be in the future.'));
    vi.mocked(planApi.archiveMilestone).mockRejectedValue(new Error('Already removed.'));
    vi.mocked(accountApi.downloadPlanPdf).mockRejectedValue(new Error('PDF service unavailable.'));
    renderPage(<PlanPage />);
    await screen.findByText('Dream statement');
    await userEvent.type(screen.getByRole('textbox', { name: 'Milestone' }), 'Too soon');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(await screen.findByText('Date cannot be in the future.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove First paper read' }));
    expect(await screen.findByText('Already removed.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Download PDF' }));
    expect(await screen.findByText('PDF service unavailable.')).toBeInTheDocument();
  });

  it('starts from a dream, and shows why dreams or a plan could not load', async () => {
    vi.mocked(dreamApi.listDreams).mockResolvedValueOnce([]);
    const empty = renderPage(<PlanPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Create your first dream' }));
    expect(currentLocation()).toBe('/dreams?create=dream');
    empty.unmount();

    vi.mocked(planApi.getPlanStatus).mockRejectedValue(new Error('Plan service is down.'));
    renderPage(<PlanPage />);
    const alert = await screen.findByText('Plan service is down.');
    expect(within(alert.closest('[role="alert"]') ?? document.body).getByText('Plan service is down.')).toBeInTheDocument();
  });
});
