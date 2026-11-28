import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getChainAssessments } from '../../api/chainAssessmentApi';
import {
  archiveInnerBlocker,
  changeInnerBlockerStatus,
  createInnerBlocker,
  getInnerBlockerCheckins,
  listInnerBlockers,
  makeInnerBlockerHabit,
} from '../../api/innerBlockerApi';
import type { ChainAssessment, InnerBlocker, PlanDraftContent } from '../../types/vision';
import { InnerStage } from '../plan-builder/InnerStage';
import { InnerBlockerCard } from './InnerBlockerCard';
import { InnerBlockerForm } from './InnerBlockerForm';
import { InnerBlockerList, habitNameFrom } from './InnerBlockerList';
import { InnerBlockersNote } from './InnerBlockersNote';
import { WeeklyInnerBlockerCheckins, type CheckinAnswers } from './WeeklyInnerBlockerCheckins';

vi.mock('../../api/innerBlockerApi', () => ({
  listInnerBlockers: vi.fn(),
  createInnerBlocker: vi.fn(),
  updateInnerBlocker: vi.fn(),
  changeInnerBlockerStatus: vi.fn(),
  archiveInnerBlocker: vi.fn(),
  restoreInnerBlocker: vi.fn(),
  makeInnerBlockerHabit: vi.fn(),
  getInnerBlockerCheckins: vi.fn(),
  saveInnerBlockerCheckins: vi.fn(),
}));
vi.mock('../../api/chainAssessmentApi', () => ({ getChainAssessments: vi.fn() }));

const blocker = (patch: Partial<InnerBlocker> = {}): InnerBlocker => ({
  id: 1,
  blocker: 'Inconsistency',
  showsUp: 'Big bursts, then weeks of nothing',
  practice: 'Never miss twice. A bad day still counts if you do 15 minutes.',
  chainKey: 'persistenceScore',
  dreamId: null,
  dreamTitle: null,
  habitId: null,
  habitName: null,
  habitArchived: false,
  habitStreak: null,
  status: 'ACTIVE',
  resolvedAt: null,
  archived: false,
  lastAnswers: [],
  practiceNotHelping: false,
  createdAt: '2026-11-28T00:00:00Z',
  updatedAt: '2026-11-28T00:00:00Z',
  ...patch,
});

describe('V34 inner blockers', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInnerBlockers).mockResolvedValue([]);
  });

  it('cuts a long practice to a habit name at a word boundary (D-34-6)', () => {
    expect(habitNameFrom('  Never   miss twice ')).toBe('Never miss twice');
    const long = `${'Write code before you feel ready, '.repeat(5)}every day`;
    const name = habitNameFrom(long);
    expect(name.length).toBeLessThanOrEqual(120);
    expect(long.startsWith(name)).toBe(true);
    expect(name.endsWith(',')).toBe(false);
  });

  it('requires the blocker and the practice, then saves all fields (§2.3)', async () => {
    const onSaved = vi.fn();
    vi.mocked(createInnerBlocker).mockResolvedValue(blocker());
    render(<InnerBlockerForm token="t" dreams={[{ id: 7, title: 'Learn to code' }]} onSaved={onSaved} onCancel={vi.fn()} />);

    await userEvent.type(screen.getByRole('textbox', { name: /Possible blocker/ }), 'Tutorial trap');
    await userEvent.click(screen.getByRole('button', { name: 'Save blocker' }));
    // The practice is required, so the form is not sent.
    expect(createInnerBlocker).not.toHaveBeenCalled();

    await userEvent.type(screen.getByRole('textbox', { name: /How it shows up/ }), 'Certificates, no projects');
    await userEvent.type(screen.getByRole('textbox', { name: /Short practice/ }), 'Each goal ends with my own project');
    await userEvent.click(screen.getByRole('combobox', { name: 'Dream' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Learn to code' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save blocker' }));

    await waitFor(() => expect(createInnerBlocker).toHaveBeenCalledWith('t', {
      blocker: 'Tutorial trap', showsUp: 'Certificates, no projects', practice: 'Each goal ends with my own project', chainKey: '', dreamId: 7,
    }));
    expect(onSaved).toHaveBeenCalled();
  });

  it('shows the three parts, the habit, the latest answers and the "isn\'t helping" hint (D-34-10)', () => {
    render(<InnerBlockerCard blocker={blocker({
      habitId: 3, habitName: 'Never miss twice', habitStreak: 4, lastAnswers: ['NO', 'NO', 'YES'], practiceNotHelping: true,
    })} />);
    expect(screen.getByRole('heading', { name: 'Inconsistency' })).toBeInTheDocument();
    expect(screen.getByText('Big bursts, then weeks of nothing')).toBeInTheDocument();
    expect(screen.getByText('Daily habit: Never miss twice · 4-day streak')).toBeInTheDocument();
    expect(screen.getByText('Giving up too soon')).toBeInTheDocument();
    expect(screen.getByText('For all my dreams')).toBeInTheDocument();
    expect(screen.getAllByText('No')).toHaveLength(2);
    expect(screen.getByText("This practice isn't helping yet. Try changing it.")).toBeInTheDocument();
  });

  it('makes a practice a daily habit and asks before stopping it (D-34-6, BR-34-5)', async () => {
    vi.mocked(listInnerBlockers).mockResolvedValue([blocker()]);
    vi.mocked(makeInnerBlockerHabit).mockResolvedValue(blocker({ habitId: 3, habitName: 'Never miss twice' }));
    render(<MemoryRouter><InnerBlockerList token="t" dreams={[]} /></MemoryRouter>);

    await userEvent.click(await screen.findByRole('button', { name: 'Make it a daily habit' }));
    const dialog = screen.getByRole('dialog');
    const name = within(dialog).getByRole('textbox', { name: 'Habit name' });
    await userEvent.clear(name);
    await userEvent.type(name, 'Never miss twice');
    vi.mocked(listInnerBlockers).mockResolvedValue([blocker({ habitId: 3, habitName: 'Never miss twice' })]);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add habit' }));
    await waitFor(() => expect(makeInnerBlockerHabit).toHaveBeenCalledWith('t', 1, expect.any(String), 'Never miss twice'));
    expect(await screen.findByText('Daily habit: Never miss twice', { exact: false })).toBeInTheDocument();
    vi.mocked(archiveInnerBlocker).mockResolvedValue(undefined);
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    expect(await screen.findByText('Also stop the habit?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Keep the habit' }));
    await waitFor(() => expect(archiveInnerBlocker).toHaveBeenCalledWith('t', 1, false));
  });

  it('resolves a blocker without a habit at once and disables adding at ten active (BR-34-3)', async () => {
    const ten = Array.from({ length: 10 }, (_, index) => blocker({ id: index + 1, blocker: `Blocker ${index + 1}` }));
    vi.mocked(listInnerBlockers).mockResolvedValue(ten);
    vi.mocked(changeInnerBlockerStatus).mockResolvedValue(blocker({ status: 'RESOLVED' }));
    render(<MemoryRouter><InnerBlockerList token="t" dreams={[]} /></MemoryRouter>);

    expect(await screen.findByRole('button', { name: 'Add your own blocker' })).toBeDisabled();
    expect(screen.getByText('You have 10 active blockers. Resolve or archive one first.')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'No longer holds me back' })[1]);
    await waitFor(() => expect(changeInnerBlockerStatus).toHaveBeenCalledWith('t', 1, 'RESOLVED', false));
    expect(screen.queryByText('Also stop the habit?')).not.toBeInTheDocument();
  });

  it('filters by status on the Assessment page (§2.2)', async () => {
    render(<MemoryRouter><InnerBlockerList token="t" dreams={[]} /></MemoryRouter>);
    await screen.findByText('No inner blockers yet.');
    await userEvent.click(screen.getByRole('button', { name: 'Archived' }));
    await waitFor(() => expect(listInnerBlockers).toHaveBeenCalledWith('t', expect.objectContaining({ archived: true, status: undefined })));
    expect(await screen.findByText('Nothing here.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add your own blocker' })).not.toBeInTheDocument();
  });

  it('adds a chain as my blocker in the Inner blockers stage, prefilled and editable (D-34-5)', async () => {
    const take: ChainAssessment = {
      id: 1, takenOn: '2027-01-01', settlingScore: 4, fearScore: 4, criticismScore: 4, visionScore: 4, prioritiesScore: 4,
      persistenceScore: 2, averageScore: 3.7,
    };
    vi.mocked(getChainAssessments).mockResolvedValue({ assessments: [take], retakeSuggested: false });
    vi.mocked(createInnerBlocker).mockResolvedValue(blocker());
    function Harness() {
      const [content, setContent] = useState<PlanDraftContent>({ goals: [] });
      return (
        <MemoryRouter>
          <InnerStage content={content} token="t" dreamId={9} today={new Date('2027-01-20T00:00:00')} onDone={vi.fn()}
            onChange={(update) => setContent((current) => (typeof update === 'function' ? update(current) : update))} />
        </MemoryRouter>
      );
    }
    render(<Harness />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add as my blocker' }));
    expect(screen.getByRole('textbox', { name: /Possible blocker/ })).toHaveValue('Giving up too soon');
    expect((screen.getByRole('textbox', { name: /Short practice/ }) as HTMLTextAreaElement).value).toContain('small daily habit');
    expect(createInnerBlocker).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Save blocker' }));
    await waitFor(() => expect(createInnerBlocker).toHaveBeenCalledWith('t', expect.objectContaining({ chainKey: 'persistenceScore', dreamId: 9 })));
    expect(listInnerBlockers).toHaveBeenCalledWith('t', expect.objectContaining({ dreamId: 9, includeAllDreams: true }));
  });

  it('names this dream\'s blockers in Check and add, and nothing when there are none (D-34-9)', async () => {
    vi.mocked(listInnerBlockers).mockResolvedValue([blocker(), blocker({ id: 2, blocker: 'Tutorial trap' })]);
    const { unmount } = render(<InnerBlockersNote token="t" dreamId={9} />);
    expect(await screen.findByText(/Inner blockers for this dream: 2/)).toHaveTextContent('Inconsistency, Tutorial trap');
    unmount();
    vi.mocked(listInnerBlockers).mockResolvedValue([]);
    const { container } = render(<InnerBlockersNote token="t" dreamId={9} />);
    await waitFor(() => expect(listInnerBlockers).toHaveBeenCalledTimes(2));
    expect(container).toBeEmptyDOMElement();
  });

  it('asks in a weekly review whether each practice helped, and loads saved answers (§2.4)', async () => {
    vi.mocked(listInnerBlockers).mockResolvedValue([blocker()]);
    vi.mocked(getInnerBlockerCheckins).mockResolvedValue({ checkins: [{ innerBlockerId: 1, reviewId: 5, answer: 'PARTLY' }] });
    function Harness({ reviewId }: { reviewId: number | null }) {
      const [answers, setAnswers] = useState<CheckinAnswers>({});
      return (
        <MemoryRouter>
          <WeeklyInnerBlockerCheckins token="t" reviewId={reviewId} value={answers} onChange={setAnswers} />
          <output data-testid="answers">{JSON.stringify(answers)}</output>
        </MemoryRouter>
      );
    }
    const { unmount } = render(<Harness reviewId={null} />);
    await userEvent.click(await screen.findByRole('button', { name: 'No' }));
    expect(screen.getByTestId('answers')).toHaveTextContent('{"1":"NO"}');
    await userEvent.click(screen.getByRole('button', { name: 'No' }));
    expect(screen.getByTestId('answers')).toHaveTextContent('{}');
    unmount();

    render(<Harness reviewId={5} />);
    await waitFor(() => expect(screen.getByTestId('answers')).toHaveTextContent('{"1":"PARTLY"}'));
    expect(screen.getByRole('button', { name: 'Partly' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('points to the Assessment page in a weekly review when there are no blockers', async () => {
    render(<MemoryRouter><WeeklyInnerBlockerCheckins token="t" reviewId={null} value={{}} onChange={vi.fn()} /></MemoryRouter>);
    expect(await screen.findByRole('link', { name: 'Add them on the Assessment page' })).toHaveAttribute('href', '/assessment');
  });
});
