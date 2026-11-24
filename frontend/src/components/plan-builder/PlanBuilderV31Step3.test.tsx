import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { checkPlanDraft } from '../../api/aiPlanApi';
import { getReviewRhythm } from '../../api/reviewApi';
import type { Dream, PlanDraftContent } from '../../types/vision';
import { ReviewStage, rhythmWords } from './ReviewStage';
import { ScheduleStage, monthCounts } from './ScheduleStage';
import { type DraftUpdate, addSteps, addTask, defaultRhythm, emptyGoal, emptyStep, emptyTask, planEnd } from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({ checkPlanDraft: vi.fn(), fixPlanDraft: vi.fn(), savePlanCapacity: vi.fn(), ratePlanAnswer: vi.fn() }));
vi.mock('../../api/reviewApi', () => ({ getReviewRhythm: vi.fn() }));

const TODAY = new Date('2027-01-10T00:00:00');

function draft(goalDate = '2027-03-31'): PlanDraftContent {
  const goal = { ...emptyGoal(), key: 'g1', title: 'Prepare a concept note', successCriteria: 'Accepted', targetDate: goalDate };
  const step = { ...emptyStep(), key: 's1', title: 'Search literature', targetDate: '2027-02-20', gapKind: 'KNOWLEDGE' as const };
  const content = addTask(addSteps({ goals: [goal] }, 'g1', [step]), 'g1', 's1',
    { ...emptyTask(), key: 't1', title: 'Search PubMed', dueDate: '2027-02-10', status: 'BLOCKED', blockerReason: 'No card' });
  return { ...content, helpers: [{ key: 'h1', role: 'Mentor', supportType: 'MENTOR', linkKey: 's1' }] };
}

function Harness({ initial, children }: { initial: PlanDraftContent; children: (c: PlanDraftContent, change: (u: DraftUpdate) => void) => React.ReactNode }) {
  const [content, setContent] = useState(initial);
  const change = (update: DraftUpdate) => setContent((current) => (typeof update === 'function' ? update(current) : update));
  return (
    <MemoryRouter>
      {children(content, change)}
      <output data-testid="draft">{JSON.stringify(content)}</output>
    </MemoryRouter>
  );
}

const current = () => JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as PlanDraftContent;

describe('Build my plan, V31 v1 step 3', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('suggests a review rhythm for the plan length (DC-FR-49) and counts new work by month (DC-FR-47)', () => {
    expect(planEnd(draft(), '2027-12-31')).toBe('2027-12-31');
    expect(defaultRhythm(draft(), null, TODAY)).toEqual({ daily: true, weeklyDay: 'SUN', monthlyDay: null, quarterly: false });
    expect(defaultRhythm(draft(), '2027-09-30', TODAY)).toEqual({ daily: true, weeklyDay: 'SUN', monthlyDay: 1, quarterly: false });
    expect(defaultRhythm(draft(), '2029-01-01', TODAY)).toEqual({ daily: false, weeklyDay: 'SUN', monthlyDay: 1, quarterly: true });
    expect(monthCounts(draft())).toEqual([{ month: '2027-02', count: 2 }, { month: '2027-03', count: 1 }]);
    expect(rhythmWords({ daily: true, weeklyDay: 'FRI', monthlyDay: 15, quarterly: false }))
      .toBe('Reviews: daily check-in, weekly on Friday, monthly on day 15.');
    expect(rhythmWords(null)).toBe('No review rhythm chosen yet.');
  });

  it('starts from the dream saved rhythm and keeps changes in the draft (DC-FR-48)', async () => {
    vi.mocked(getReviewRhythm).mockResolvedValue({ dreamId: 5, daily: false, weeklyDay: 'FRI', monthlyDay: null, quarterly: false });
    render(<Harness initial={draft()}>{(content, change) => (
      <ScheduleStage content={content} onChange={change} dreamId={5} dreamDate="2027-06-30" token="t" capacity={null}
        onCapacityChange={vi.fn()} today={TODAY} />
    )}</Harness>);

    expect(await screen.findByRole('combobox', { name: 'Weekly review on' })).toHaveTextContent('Friday');
    expect(current().reviewRhythm).toEqual({ daily: false, weeklyDay: 'FRI', monthlyDay: null, quarterly: false });
    await userEvent.click(screen.getByRole('checkbox', { name: 'A review every quarter' }));
    expect(current().reviewRhythm?.quarterly).toBe(true);
    await userEvent.click(screen.getByRole('button', { name: 'Use the suggested rhythm' }));
    expect(current().reviewRhythm).toEqual({ daily: true, weeklyDay: 'SUN', monthlyDay: 1, quarterly: false });
    expect(screen.getByText('Feb 2027')).toBeInTheDocument();
  });

  it('uses the suggestion when the dream has no rhythm yet', async () => {
    vi.mocked(getReviewRhythm).mockResolvedValue(null);
    render(<Harness initial={draft()}>{(content, change) => (
      <ScheduleStage content={content} onChange={change} dreamId={5} dreamDate={null} token="t" capacity={null}
        onCapacityChange={vi.fn()} today={TODAY} />
    )}</Harness>);
    await vi.waitFor(() => expect(current().reviewRhythm).toEqual({ daily: true, weeklyDay: 'SUN', monthlyDay: null, quarterly: false }));
  });

  it('shows the whole plan as a tree on Check and add (DC-FR-51)', () => {
    vi.mocked(checkPlanDraft).mockResolvedValue({ issues: [], aiChecked: false });
    const dream = { id: 5, title: 'Become a researcher', targetDate: '2027-06-30' } as Dream;
    render(<Harness initial={{ ...draft(), reviewRhythm: { daily: false, weeklyDay: 'SUN', monthlyDay: null, quarterly: false } }}>
      {(content, change) => (
        <ReviewStage content={content} onChange={change} existingSteps={[]} busy={false} onGoTo={vi.fn()} onAdd={vi.fn()} dreamId={5}
          token="t" language="en" aiReady={false} saveDraft={vi.fn().mockResolvedValue(undefined)} dream={dream} existingGoals={[]} />
      )}
    </Harness>);
    const tree = screen.getByRole('region', { name: 'Your plan' });
    expect(tree).toHaveTextContent('Dream: Become a researcher');
    expect(tree).toHaveTextContent('Prepare a concept note');
    expect(tree).toHaveTextContent('Search literature');
    expect(tree).toHaveTextContent('Gap: Knowledge');
    expect(tree).toHaveTextContent('Helper: Mentor');
    expect(tree).toHaveTextContent('Search PubMed');
    expect(tree).toHaveTextContent('Blocked');
    expect(tree).toHaveTextContent('Reviews: weekly on Sunday.');
  });
});
