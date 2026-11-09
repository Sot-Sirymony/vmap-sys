import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as workStyleProfileApi from '../api/workStyleProfileApi';
import { setCurrentLanguage } from '../i18n/translate';
import { renderPage } from '../test/render';
import type { WorkStyleProfile } from '../types/vision';
import { QUIZ_ITEMS } from '../utils/workStyleAssessment';
import { WorkStylePage } from './WorkStylePage';

vi.mock('../api/workStyleProfileApi');

const noProfile: WorkStyleProfile = { dominant: null, secondary: null, paceFastScore: null, paceDeliberateScore: null, focusTaskScore: null, focusPeopleScore: null };
const driver: WorkStyleProfile = { dominant: 'DRIVER', secondary: 'PLANNER', paceFastScore: 8, paceDeliberateScore: 0, focusTaskScore: 5, focusPeopleScore: 3 };

/** Picks the first statement ('a') for every question. */
async function answerAll(choice: 'a' | 'b' = 'a') {
  for (const [index, item] of QUIZ_ITEMS.entries()) {
    const group = screen.getByRole('group', { name: `Question ${index + 1}` });
    const statement = choice === 'a' ? item.a : item.b;
    await userEvent.click(group.querySelector(`button[value="${choice}"]`) ?? screen.getByText(statement));
  }
}

describe('WorkStylePage (FR-49)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(workStyleProfileApi.getWorkStyleProfile).mockResolvedValue(noProfile);
    vi.mocked(workStyleProfileApi.submitWorkStyleAssessment).mockResolvedValue(driver);
    // V15 Phase 5: the partner balance card; no partners here.
    vi.mocked(workStyleProfileApi.getPartnerBalance).mockResolvedValue({
      dominant: 'DRIVER', complementary: ['PLANNER'], counts: { DRIVER: 0, CONNECTOR: 0, STEADIER: 0, PLANNER: 0 }, noStyle: 0,
      missing: ['PLANNER'], partners: [],
    });
  });
  afterEach(() => setCurrentLanguage('en'));

  it('starts the quiz for someone with no profile, and only scores it once every question is answered', async () => {
    renderPage(<WorkStylePage />);
    expect(await screen.findByText('16 quick picks')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'See my result' })).toBeDisabled();
    expect(screen.getByText(/0 of 16 answered/)).toBeInTheDocument();

    await answerAll('a');
    expect(screen.getByText(/16 of 16 answered/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'See my result' }));

    // Every 'a' pick is the fast pace or the task focus.
    expect(workStyleProfileApi.submitWorkStyleAssessment).toHaveBeenCalledWith('token', {
      paceFastScore: 8, paceDeliberateScore: 0, focusTaskScore: 8, focusPeopleScore: 0,
    });
    expect(await screen.findByText('Dominant: Driver')).toBeInTheDocument();
    expect(screen.getByText('Secondary: Planner')).toBeInTheDocument();
  }, 20000);

  it('shows a saved result with its strengths, blind spot and tip, and lets the person retake it', async () => {
    vi.mocked(workStyleProfileApi.getWorkStyleProfile).mockResolvedValue({ ...driver, secondary: null });
    renderPage(<WorkStylePage />);
    expect(await screen.findByText('Dominant: Driver')).toBeInTheDocument();
    expect(screen.getByText(/Decides fast/)).toBeInTheDocument();
    expect(screen.getByText(/^Likely blind spot: /)).toBeInTheDocument();
    expect(screen.getByText(/^Working with them: /)).toBeInTheDocument();
    expect(screen.queryByText(/^Secondary:/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Retake the assessment' }));
    expect(screen.getByText('16 quick picks')).toBeInTheDocument();
    expect(screen.getByText(/0 of 16 answered/)).toBeInTheDocument();
  });

  it('shows why the result could not be saved', async () => {
    vi.mocked(workStyleProfileApi.submitWorkStyleAssessment).mockRejectedValue(new Error('Scores are out of range'));
    renderPage(<WorkStylePage />);
    await screen.findByText('16 quick picks');
    await answerAll('b');
    await userEvent.click(screen.getByRole('button', { name: 'See my result' }));
    expect(await screen.findByText('Scores are out of range')).toBeInTheDocument();
    expect(screen.getByText('16 quick picks')).toBeInTheDocument();
  }, 20000);

  it('shows why the profile could not load', async () => {
    vi.mocked(workStyleProfileApi.getWorkStyleProfile).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<WorkStylePage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });

  it('shows the statements and the result in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    vi.mocked(workStyleProfileApi.getWorkStyleProfile).mockResolvedValue(driver);
    renderPage(<WorkStylePage />);
    expect(await screen.findByText('របៀបចម្បង៖ អ្នកជំរុញ')).toBeInTheDocument();
    expect(screen.getByText('របៀបបន្ទាប់បន្សំ៖ អ្នករៀបផែនការ')).toBeInTheDocument();
    expect(screen.getByText(/សម្រេចចិត្តរហ័ស/)).toBeInTheDocument();
    expect(screen.queryByText(/Decides fast/)).not.toBeInTheDocument();
  });

  it('shows the Khmer statement alone in the quiz in Khmer', async () => {
    setCurrentLanguage('km');
    renderPage(<WorkStylePage />);
    await waitFor(() => expect(screen.getByText(QUIZ_ITEMS[0].aKm)).toBeInTheDocument());
    expect(screen.queryByText(QUIZ_ITEMS[0].a)).not.toBeInTheDocument();
  });
});
