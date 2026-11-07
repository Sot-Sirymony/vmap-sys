import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiApi from '../api/aiApi';
import * as aiDailyApi from '../api/aiDailyApi';
import * as dreamApi from '../api/dreamApi';
import * as reviewApi from '../api/reviewApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { setCurrentLanguage } from '../i18n/translate';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aReview, aVisionArea } from '../test/builders';
import { renderPage } from '../test/render';
import { ReviewsPage } from './ReviewsPage';

vi.mock('../api/aiApi');
vi.mock('../api/aiDailyApi');
vi.mock('../api/dreamApi');
vi.mock('../api/reviewApi');
vi.mock('../api/visionAreaApi');

const allDiligence = {
  diligenceClearVision: true, diligenceWorkedPlan: true, diligenceUsedLeverage: true, diligencePriorityFirst: true, diligenceSmarterRoute: false,
  diligenceRightlyPlanned: true, diligenceRightlyPerformed: true, diligenceExpeditious: false, diligenceEfficient: true, diligenceQualityOutcome: true,
};
const weekly = aReview({ id: 1, reviewType: 'WEEKLY', reviewDate: '2026-11-01T18:00:00', summary: 'A steady week', relatedDreamId: 1, ...allDiligence, diligenceScorePercent: 80 });
const daily = aReview({ id: 2, reviewType: 'DAILY', reviewDate: '2026-11-06T21:00:00', summary: 'Wrote two pages', lessonsLearned: 'Mornings work best' });
const monthly = aReview({ id: 3, reviewType: 'MONTHLY', reviewDate: '2026-10-31T20:00:00', summary: '' });

const DILIGENCE = [
  /vision for this area/, /worked from a plan/, /used tools or partners/, /highest-priority work/, /hard way/,
  /planned this properly/, /standard I would stand behind/, /moved on this promptly/, /time and tools well/, /actually solid/,
];

const row = (text: string) => screen.getByText(text, { selector: 'td, td *' }).closest('tr') as HTMLElement;
const field = (dialog: HTMLElement, name: RegExp) => within(dialog).getByRole('textbox', { name });

async function openNewReview() {
  await userEvent.click(screen.getByRole('button', { name: 'Create review' }));
  return screen.findByRole('dialog');
}

async function chooseType(dialog: HTMLElement, type: string) {
  await userEvent.click(within(dialog).getByRole('combobox', { name: 'Type' }));
  await userEvent.click(await screen.findByRole('option', { name: type }));
}

async function answer(dialog: HTMLElement, question: RegExp, choice: 'Yes' | 'No') {
  await userEvent.click(within(within(dialog).getByRole('group', { name: question })).getByRole('button', { name: choice }));
}

// Long form pages: each test may take several seconds on a busy CI runner (COV-NFR-05).
describe('ReviewsPage (CLAUDE.md page 10)', { timeout: 30000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([dreamApi, reviewApi, visionAreaApi]);
    vi.mocked(reviewApi.listReviews).mockResolvedValue([weekly, daily, monthly]);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1, name: 'Career' }), aVisionArea({ id: 2, name: 'Health' })]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([
      aDream({ id: 1, visionAreaId: 1, title: 'Become a researcher' }),
      aDream({ id: 2, visionAreaId: 2, title: 'Run a marathon' }),
    ]);
    vi.mocked(reviewApi.createReview).mockResolvedValue(weekly);
    vi.mocked(reviewApi.updateReview).mockResolvedValue(weekly);
    vi.mocked(reviewApi.archiveReview).mockResolvedValue(undefined as never);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('lists reviews newest first, with the diligence score when the checkup was done', async () => {
    renderPage(<ReviewsPage />);
    expect(await screen.findByText('A steady week', { selector: 'td, td *' })).toBeInTheDocument();
    const dates = screen.getAllByRole('row').slice(1).map((tableRow) => within(tableRow).queryAllByRole('cell')[2]?.textContent);
    expect(dates).toEqual(['2026-11-06T21:00:00', '2026-11-01T18:00:00', '2026-10-31T20:00:00']);
    expect(within(row('A steady week')).getByText('Done (80%)')).toBeInTheDocument();
    expect(within(row('Wrote two pages')).getByText('DAILY')).toBeInTheDocument();
  });

  it.each([
    ['Daily', ['What moved forward today?', 'What is delayed or blocked?', 'What is the single next action for tomorrow?']],
    ['Weekly', ['Which goals moved this week?', 'Which tasks are overdue or blocked?', 'Which partner needs a follow-up?', 'What are the top 3 priorities for next week?']],
    ['Monthly', ['Which dreams still matter?', 'Which dreams should be revised up, paused, or removed?', 'What new dreams, partners, or resources are missing?']],
    ['Quarterly', ['Are these still the right vision areas?', 'What worked and what failed this quarter?', 'What changes for next quarter?']],
  ])('shows the %s review questions', async (type, questions) => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    const dialog = await openNewReview();
    await chooseType(dialog, type);
    expect(within(dialog).getByText(`${type} review questions`)).toBeInTheDocument();
    for (const question of questions) {
      expect(within(dialog).getByText(question)).toBeInTheDocument();
    }
  });

  it('saves a daily review without the diligence checkup', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    const dialog = await openNewReview();
    await chooseType(dialog, 'Daily');
    expect(within(dialog).queryByText('Diligence checkup')).not.toBeInTheDocument();
    await userEvent.type(field(dialog, /^Summary/), 'Finished the outline');
    await userEvent.type(field(dialog, /^Next Actions/), 'Write the methods');
    await userEvent.type(field(dialog, /grateful for/), 'My supervisor');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create review' }).at(-1)!);

    await waitFor(() => expect(reviewApi.createReview).toHaveBeenCalledWith('token', expect.objectContaining({
      reviewType: 'DAILY', summary: 'Finished the outline', nextActions: 'Write the methods', gratitudeNote: 'My supervisor',
      diligenceClearVision: undefined, diligenceNote: undefined,
    })));
  });

  it('refuses a half-answered diligence checkup on a weekly review', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    const dialog = await openNewReview();
    await answer(dialog, DILIGENCE[0], 'Yes');
    await answer(dialog, DILIGENCE[1], 'No');
    expect(within(dialog).getByText('(2 of 10 answered so far)')).toBeInTheDocument();
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create review' }).at(-1)!);
    expect(await screen.findByText('Answer every diligence question, or skip the whole checklist.')).toBeInTheDocument();
    expect(reviewApi.createReview).not.toHaveBeenCalled();
  });

  it('saves a monthly review with every diligence answer and the checkup note', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    const dialog = await openNewReview();
    await chooseType(dialog, 'Monthly');
    for (const [index, question] of DILIGENCE.entries()) {
      await answer(dialog, question, index === 4 ? 'No' : 'Yes');
    }
    await userEvent.type(field(dialog, /^Checkup note/), 'Too much manual data entry');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create review' }).at(-1)!);

    await waitFor(() => expect(reviewApi.createReview).toHaveBeenCalledWith('token', expect.objectContaining({
      reviewType: 'MONTHLY', diligenceClearVision: true, diligenceSmarterRoute: false, diligenceQualityOutcome: true,
      diligenceNote: 'Too much manual data entry', gratitudeNote: undefined,
    })));
  });

  it('offers only the dreams in the chosen vision area, and clears a dream from another area', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    const dialog = await openNewReview();
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Dream' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Run a marathon' }));
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Vision Area' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Career' }));
    expect(within(dialog).getByRole('combobox', { name: 'Dream' })).not.toHaveTextContent('Run a marathon');
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Dream' }));
    expect(screen.getByRole('option', { name: 'Become a researcher' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Run a marathon' })).not.toBeInTheDocument();
  });

  it('fills a weekly review from the AI draft, which the participant then edits and saves', async () => {
    vi.mocked(aiDailyApi.draftWeeklyReview).mockResolvedValue({
      summary: 'Good progress on the concept note.', delayedTasks: 'Reference table', completedTasks: 'Search PubMed',
      blockedTasks: 'Ethics form', lessonsLearned: 'Start earlier', nextActions: 'Email the mentor',
    } as never);
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    const dialog = await openNewReview();
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Draft with AI' }));

    expect(aiDailyApi.draftWeeklyReview).toHaveBeenCalledWith('token', 'en');
    await waitFor(() => expect(field(dialog, /^Summary/)).toHaveValue('Good progress on the concept note.\n\nDelayed:\nReference table'));
    expect(field(dialog, /^Completed Tasks/)).toHaveValue('Search PubMed');
    expect(field(dialog, /^Blocked Tasks/)).toHaveValue('Ethics form');
    expect(field(dialog, /^Lessons Learned/)).toHaveValue('Start earlier');
    expect(field(dialog, /^Next Actions/)).toHaveValue('Email the mentor');
    expect(reviewApi.createReview).not.toHaveBeenCalled();
  });

  it('shows why the AI draft failed', async () => {
    vi.mocked(aiDailyApi.draftWeeklyReview).mockRejectedValue(new Error('AI help is temporarily unavailable.'));
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    const dialog = await openNewReview();
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Draft with AI' }));
    expect(await within(dialog).findByText('AI help is temporarily unavailable.')).toBeInTheDocument();
  });

  it('offers the AI draft only for weekly reviews, and not at all while AI is off (AI-NFR-02)', async () => {
    const { unmount } = renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    let dialog = await openNewReview();
    expect(await within(dialog).findByRole('button', { name: 'Draft with AI' })).toBeInTheDocument();
    await chooseType(dialog, 'Daily');
    expect(within(dialog).queryByRole('button', { name: 'Draft with AI' })).not.toBeInTheDocument();
    unmount();

    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OFF' } as never);
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    dialog = await openNewReview();
    await waitFor(() => expect(aiApi.getAiStatus).toHaveBeenCalled());
    expect(within(dialog).getByText('Weekly review questions')).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Draft with AI' })).not.toBeInTheDocument();
  });

  it('edits a review with its saved checkup answers, and never carries the gratitude note over', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    await userEvent.click(within(row('A steady week')).getByRole('button', { name: 'Review actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Review' });
    expect(field(dialog, /^Summary/)).toHaveValue('A steady week');
    expect(within(within(dialog).getByRole('group', { name: DILIGENCE[0] })).getByRole('button', { name: 'Yes' })).toHaveAttribute('aria-pressed', 'true');
    expect(field(dialog, /grateful for/)).toHaveValue('');
    await userEvent.type(field(dialog, /^Lessons Learned/), 'Protect mornings');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(reviewApi.updateReview).toHaveBeenCalledWith('token', 1, expect.objectContaining({
      reviewType: 'WEEKLY', lessonsLearned: 'Protect mornings', relatedDreamId: 1, diligenceSmarterRoute: false, diligenceExpeditious: false,
    })));
  });

  it('archives a review with a way to undo', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    await userEvent.click(within(row('A steady week')).getByRole('button', { name: 'Review actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(reviewApi.archiveReview).toHaveBeenCalledWith('token', 1));
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();
  });

  it('filters by review type, dream and search, and says when nothing matches', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    await userEvent.click(within(screen.getByText('Review Type', { selector: 'label' })).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Daily' }));
    expect(screen.getByText('Wrote two pages', { selector: 'td, td *' })).toBeInTheDocument();
    expect(screen.queryByText('A steady week', { selector: 'td, td *' })).not.toBeInTheDocument();

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search reviews' }), 'mornings');
    expect(screen.getByText('Wrote two pages', { selector: 'td, td *' })).toBeInTheDocument();
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search reviews' }), 'zzz');
    expect(screen.getByText('No reviews match these filters.')).toBeInTheDocument();
  });

  it('cancels an edit and starts the next review blank', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    await userEvent.click(within(row('Wrote two pages')).getByRole('button', { name: 'Review actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const editDialog = await screen.findByRole('dialog', { name: 'Edit Review' });
    await userEvent.click(within(editDialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit Review' })).not.toBeInTheDocument());
    const dialog = await openNewReview();
    expect(within(dialog).getByRole('combobox', { name: 'Type' })).toHaveTextContent('Weekly');
    expect(field(dialog, /^Summary/)).toHaveValue('');
  });

  it('archives several reviews at once, and restores an archived one', async () => {
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Weekly review 2026-11-01T18:00:00' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Daily review 2026-11-06T21:00:00' }));
    await userEvent.click(screen.getByRole('button', { name: 'Archive selected (2)' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(reviewApi.archiveReview).toHaveBeenCalledTimes(2));

    vi.mocked(reviewApi.listReviews).mockResolvedValue([{ ...weekly, archived: true }]);
    vi.mocked(reviewApi.restoreReview).mockResolvedValue(undefined);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show archived' }));
    await waitFor(() => expect(reviewApi.listReviews).toHaveBeenLastCalledWith('token', true));
    expect(await screen.findByText('ARCHIVED')).toBeInTheDocument();
    await userEvent.click(within(row('A steady week')).getByRole('button', { name: 'Review actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    await waitFor(() => expect(reviewApi.restoreReview).toHaveBeenCalledWith('token', 1));
  });

  it('filters by vision area and rank', async () => {
    vi.mocked(reviewApi.listReviews).mockResolvedValue([{ ...weekly, relatedVisionAreaId: 1, letterRank: 'A' }, daily]);
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    await userEvent.click(within(screen.getByText('Vision Area', { selector: 'label' })).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Health' }));
    expect(screen.getByText('No reviews match these filters.')).toBeInTheDocument();
    await userEvent.click(within(screen.getByText('Vision Area', { selector: 'label' })).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Career' }));
    expect(screen.getByText('A steady week', { selector: 'td, td *' })).toBeInTheDocument();
    expect(screen.queryByText('Wrote two pages', { selector: 'td, td *' })).not.toBeInTheDocument();
  });

  it('says when there are no reviews yet', async () => {
    vi.mocked(reviewApi.listReviews).mockResolvedValue([]);
    renderPage(<ReviewsPage />);
    expect(await screen.findByText('No reviews yet.')).toBeInTheDocument();
  });

  it('shows the review form in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<ReviewsPage />);
    await screen.findByText('A steady week', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'បង្កើតការពិនិត្យ' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('ការត្រួតពិនិត្យការខិតខំ')).toBeInTheDocument();
  });
});
