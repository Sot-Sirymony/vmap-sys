import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clarifyDream, saveClarifiedField, suggestGoals } from '../../api/aiPlanApi';
import type { Dream, PlanDraftContent } from '../../types/vision';
import { ClarifyStage } from './ClarifyStage';
import { GoalsStage } from './GoalsStage';
import { type DraftUpdate, goalFromSuggestion } from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({
  clarifyDream: vi.fn(), saveClarifiedField: vi.fn(), suggestGoals: vi.fn(), ratePlanAnswer: vi.fn(),
}));

const dream = { id: 5, title: 'Own a home', whyImportant: '', successDefinition: 'Keys', archived: false } as Dream;

/** Holds the draft like the page does, so updates flow back into the stage. */
function Harness({ initial, stage, onDream = vi.fn() }: {
  initial: PlanDraftContent;
  stage: 'clarify' | 'goals';
  onDream?: (dream: Dream) => void;
}) {
  const [content, setContent] = useState(initial);
  const change = (update: DraftUpdate) => setContent((current) => (typeof update === 'function' ? update(current) : update));
  return (
    <MemoryRouter>
      {stage === 'clarify'
        ? <ClarifyStage dream={dream} content={content} onChange={change} onDreamChange={onDream} token="t" language="en" aiReady onDone={vi.fn()} />
        : <GoalsStage dream={dream} content={content} onChange={change} token="t" language="en" aiReady />}
      <output data-testid="draft">{JSON.stringify(content)}</output>
    </MemoryRouter>
  );
}

const draft = () => JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as PlanDraftContent;

describe('Build my plan, phase 3', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Queued answers survive clearAllMocks; start each test with none.
    vi.mocked(suggestGoals).mockReset();
    vi.mocked(clarifyDream).mockReset();
  });

  it('asks about the dream one question at a time and saves to the dream only when ticked (PB-FR-08, PB-FR-10)', async () => {
    vi.mocked(clarifyDream)
      .mockResolvedValueOnce({ question: 'Why does a home matter now?', field: 'WHY_IMPORTANT', done: false })
      .mockResolvedValueOnce({ question: 'By when?', field: 'TARGET_DATE', done: false })
      .mockResolvedValueOnce({ done: true });
    vi.mocked(saveClarifiedField).mockResolvedValue({ ...dream, whyImportant: 'Room for the kids' });
    const onDream = vi.fn();
    render(<Harness initial={{ goals: [] }} stage="clarify" onDream={onDream} />);

    await userEvent.click(screen.getByRole('button', { name: 'Ask me questions first' }));
    expect(await screen.findByText('Why does a home matter now?')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'Your answer' }), 'Room for the kids');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Also save this to my dream as "Why it matters"' }));
    await userEvent.click(screen.getByRole('button', { name: 'Answer' }));

    expect(saveClarifiedField).toHaveBeenCalledWith('t', 5, 'WHY_IMPORTANT', 'Room for the kids');
    expect(onDream).toHaveBeenCalledWith(expect.objectContaining({ whyImportant: 'Room for the kids' }));
    expect(clarifyDream).toHaveBeenLastCalledWith('t', 5, 'en', [{ question: 'Why does a home matter now?', answer: 'Room for the kids' }]);
    expect(await screen.findByText('By when?')).toBeInTheDocument();
    expect(draft().clarifications).toHaveLength(1);

    // Not ticked: answered, but the dream is not changed.
    await userEvent.type(screen.getByLabelText('Your answer'), '2028-06-01');
    await userEvent.click(screen.getByRole('button', { name: 'Answer' }));
    expect(saveClarifiedField).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('Your dream is clear enough to plan.')).toBeInTheDocument();
    expect(draft().clarifications?.[1]).toEqual({ question: 'By when?', answer: '2028-06-01' });
  });

  it('answers a goal question in place, then suggests with the answer, or suggests anyway (PB-FR-09, PB-FR-11)', async () => {
    vi.mocked(suggestGoals)
      .mockResolvedValueOnce({ goals: [], clarifyingQuestion: 'Which city?', requestId: 1 })
      .mockResolvedValueOnce({ goals: [{ title: 'Save for Phnom Penh', priority: 'HIGH', moonshot: false }], requestId: 2 })
      .mockResolvedValueOnce({ goals: [], clarifyingQuestion: 'Which street?', requestId: 3 })
      .mockResolvedValueOnce({ goals: [{ title: 'Near a school', priority: 'LOW', moonshot: false }], requestId: 6 });
    render(<Harness initial={{ goals: [], clarifications: [{ question: 'Why?', answer: 'Kids' }] }} stage="goals" />);

    await userEvent.click(screen.getByRole('button', { name: 'Suggest goals' }));
    expect(suggestGoals).toHaveBeenLastCalledWith('t', 5, 'en', false, { answers: [{ question: 'Why?', answer: 'Kids' }] });
    await userEvent.type(await screen.findByRole('textbox', { name: 'Your answer' }), 'Phnom Penh');
    await userEvent.click(screen.getByRole('button', { name: 'Answer and suggest' }));

    expect(suggestGoals).toHaveBeenLastCalledWith('t', 5, 'en', false, { answers: [
      { question: 'Why?', answer: 'Kids' }, { question: 'Which city?', answer: 'Phnom Penh' },
    ] });
    expect(await screen.findByDisplayValue('Save for Phnom Penh')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Suggest goals' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Suggest anyway' }));
    expect(suggestGoals).toHaveBeenLastCalledWith('t', 5, 'en', false, expect.objectContaining({ suggestAnyway: true }));
    expect(await screen.findByDisplayValue('Near a school')).toBeInTheDocument();
  });

  it('refines only untouched suggestions and swaps one for another option (PB-FR-19 to PB-FR-21)', async () => {
    const kept = { ...goalFromSuggestion({ title: 'Kept', priority: 'HIGH', moonshot: false }, 1), locked: true };
    const loose = goalFromSuggestion({ title: 'Loose', priority: 'HIGH', moonshot: false }, 1);
    const other = goalFromSuggestion({ title: 'Other', priority: 'HIGH', moonshot: false }, 1);
    vi.mocked(suggestGoals)
      .mockResolvedValueOnce({ goals: [{ title: 'Smaller one', priority: 'LOW', moonshot: false }], requestId: 4 })
      .mockResolvedValueOnce({ goals: [{ title: 'Swapped', priority: 'LOW', moonshot: false }], requestId: 5 });
    render(<Harness initial={{ goals: [kept, loose, other] }} stage="goals" />);

    expect(screen.getByText('Change the 2 suggestion(s) you have not edited or locked')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Smaller' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Anything else? (optional)' }), 'one month each');
    await userEvent.click(screen.getByRole('button', { name: 'Refine' }));

    expect(suggestGoals).toHaveBeenLastCalledWith('t', 5, 'en', false, {
      answers: [], refine: 'SMALLER', note: 'one month each', keep: ['Kept'], replace: ['Loose', 'Other'],
    });
    expect(await screen.findByDisplayValue('Smaller one')).toBeInTheDocument();
    expect(draft().goals.map((goal) => goal.title)).toEqual(['Kept', 'Smaller one']);

    await userEvent.click(screen.getByRole('button', { name: 'Another option' }));
    expect(suggestGoals).toHaveBeenLastCalledWith('t', 5, 'en', false, expect.objectContaining({
      refine: 'ANOTHER', replace: ['Smaller one'], keep: ['Kept'],
    }));
    expect(await screen.findByDisplayValue('Swapped')).toBeInTheDocument();
    expect(draft().goals.map((goal) => goal.title)).toEqual(['Kept', 'Swapped']);
  });
});
