import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiApi from '../api/aiApi';
import * as chainAssessmentApi from '../api/chainAssessmentApi';
import { renderPage } from '../test/render';
import type { ChainAssessment, ChainAssessmentSummary } from '../types/vision';
import { localDateIso } from '../utils/localDate';
import { AssessmentPage } from './AssessmentPage';

vi.mock('../api/chainAssessmentApi');
vi.mock('../api/aiApi');
vi.mock('../api/aiModuleApi');

const BARRIERS = ['Settling for average', 'Fear of failure', 'Avoiding criticism', 'Unclear vision', 'Not knowing how', 'Lack of resources'];

const take = (overrides: Partial<ChainAssessment>): ChainAssessment => ({
  id: 1, takenOn: '2026-08-01', settlingScore: 2, fearScore: 1, criticismScore: 2, visionScore: 3, knowledgeScore: 2, resourcesScore: 2,
  averageScore: 2, ...overrides,
});

const none: ChainAssessmentSummary = { assessments: [], improvementPercent: null, retakeSuggested: false };

describe('AssessmentPage (FR-71)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(chainAssessmentApi.getChainAssessments).mockResolvedValue(none);
    vi.mocked(chainAssessmentApi.saveChainAssessment).mockResolvedValue(take({}) as never);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OFF' } as never);
  });

  it('asks for a baseline, and saves only when all six barriers are scored', async () => {
    renderPage(<AssessmentPage />);
    expect(await screen.findByText('Take your baseline')).toBeInTheDocument();
    const save = screen.getByRole('button', { name: 'Save assessment' });
    for (const [index, barrier] of BARRIERS.entries()) {
      expect(save).toBeDisabled();
      await userEvent.click(within(screen.getByRole('group', { name: barrier })).getByRole('button', { name: String((index % 5) + 1) }));
    }
    expect(save).toBeEnabled();
    await userEvent.click(save);

    await waitFor(() => expect(chainAssessmentApi.saveChainAssessment).toHaveBeenCalledWith('token', {
      takenOn: localDateIso(), settlingScore: 1, fearScore: 2, criticismScore: 3, visionScore: 4, knowledgeScore: 5, resourcesScore: 1,
    }));
    expect(chainAssessmentApi.getChainAssessments).toHaveBeenCalledTimes(2);
  });

  it('compares the baseline with the latest result and shows the improvement', async () => {
    vi.mocked(chainAssessmentApi.getChainAssessments).mockResolvedValue({
      assessments: [take({}), take({ id: 2, takenOn: '2026-11-01', fearScore: 4, averageScore: 2.5 })],
      improvementPercent: 25,
      retakeSuggested: false,
    });
    renderPage(<AssessmentPage />);
    expect(await screen.findByText('+25% since your baseline on 2026-08-01')).toBeInTheDocument();
    const fearRow = screen.getByRole('cell', { name: 'Fear of failure' }).closest('tr') as HTMLElement;
    expect(within(fearRow).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['Fear of failure', '1', '4']);
    expect(screen.getByText('Take it again')).toBeInTheDocument();
  });

  it('describes a single baseline by its average, and suggests a retake after 12 weeks', async () => {
    vi.mocked(chainAssessmentApi.getChainAssessments).mockResolvedValue({ assessments: [take({})], improvementPercent: null, retakeSuggested: true });
    renderPage(<AssessmentPage />);
    expect(await screen.findByText('Baseline taken 2026-08-01, averaging 2 of 5')).toBeInTheDocument();
    expect(screen.getByText(/12 weeks or more since your last assessment/)).toBeInTheDocument();
  });

  it('shows a drop without a plus sign', async () => {
    vi.mocked(chainAssessmentApi.getChainAssessments).mockResolvedValue({
      assessments: [take({}), take({ id: 2 })], improvementPercent: -10, retakeSuggested: false,
    });
    renderPage(<AssessmentPage />);
    expect(await screen.findByText('-10% since your baseline on 2026-08-01')).toBeInTheDocument();
  });

  it('shows why saving or loading failed', async () => {
    vi.mocked(chainAssessmentApi.saveChainAssessment).mockRejectedValue(new Error('Scores must be 1 to 5'));
    renderPage(<AssessmentPage />);
    await screen.findByText('Take your baseline');
    for (const barrier of BARRIERS) {
      await userEvent.click(within(screen.getByRole('group', { name: barrier })).getByRole('button', { name: '3' }));
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save assessment' }));
    expect(await screen.findByText('Scores must be 1 to 5')).toBeInTheDocument();
  });

  it('shows why the assessment could not load', async () => {
    vi.mocked(chainAssessmentApi.getChainAssessments).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<AssessmentPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });
});
