import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { checkPlanDraft, fixPlanDraft, savePlanCapacity } from '../../api/aiPlanApi';
import type { DraftStep, PlanCapacity, PlanDraftContent } from '../../types/vision';
import { ReviewStage } from './ReviewStage';
import {
  type DraftUpdate, addSteps, applyAiFix, applyIssueFix, emptyGoal, emptyStep, emptyTask, overloadedWeeks, spreadDates, weekStart,
} from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({
  checkPlanDraft: vi.fn(), fixPlanDraft: vi.fn(), savePlanCapacity: vi.fn(), ratePlanAnswer: vi.fn(),
}));

const step = (patch: Partial<DraftStep>): DraftStep => ({ ...emptyStep(), title: 'Step', ...patch });

function draftWith(steps: DraftStep[], goalDate = '2027-03-31'): PlanDraftContent {
  const goal = { ...emptyGoal(), key: 'g1', title: 'Save', targetDate: goalDate };
  return addSteps({ goals: [goal] }, 'g1', steps);
}

function Harness({ initial, capacity = null, saveDraft = vi.fn().mockResolvedValue(undefined) }: {
  initial: PlanDraftContent;
  capacity?: PlanCapacity | null;
  saveDraft?: () => Promise<void>;
}) {
  const [content, setContent] = useState(initial);
  const [cap, setCap] = useState(capacity);
  const change = (update: DraftUpdate) => setContent((current) => (typeof update === 'function' ? update(current) : update));
  return (
    <MemoryRouter>
      <ReviewStage content={content} onChange={change} existingSteps={[]} busy={false} onGoTo={vi.fn()} onAdd={vi.fn()}
        dreamId={5} token="t" language="en" aiReady saveDraft={saveDraft} capacity={cap} onCapacityChange={setCap} />
      <output data-testid="draft">{JSON.stringify(content)}</output>
    </MemoryRouter>
  );
}

const draft = () => JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as PlanDraftContent;

describe('Build my plan, phase 6', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(checkPlanDraft).mockReset();
    vi.mocked(fixPlanDraft).mockReset();
  });

  it('applies one-click fixes by key (PB-FR-29)', () => {
    const task = { ...emptyTask(), key: 't1', title: 'Call', dueDate: '2027-05-01' };
    let content = draftWith([step({ key: 's1', owner: '', targetDate: '2027-04-10', tasks: [task] })]);
    content = applyIssueFix(content, { kind: 'MISSING_OWNER', itemType: 'STEP', itemKey: 's1', problem: '', fixAction: 'SET_OWNER_ME', fromAi: false });
    content = applyIssueFix(content, { kind: 'AFTER_GOAL_DATE', itemType: 'STEP', itemKey: 's1', problem: '', fixAction: 'SET_DATE',
      fixValue: '2027-03-31', fromAi: false });
    content = applyIssueFix(content, { kind: 'TASK_AFTER_STEP', itemType: 'TASK', itemKey: 't1', problem: '', fixAction: 'SET_DATE',
      fixValue: '2027-03-31', fromAi: false });
    expect(content.goals[0].steps[0]).toMatchObject({ owner: 'Me', targetDate: '2027-03-31' });
    expect(content.goals[0].steps[0].tasks[0].dueDate).toBe('2027-03-31');

    content = applyAiFix(content, { itemKey: 's1', kind: 'VAGUE_STEP', title: 'Open a savings account' });
    content = applyAiFix(content, { itemKey: 'g1', kind: 'GAP', newStepTitle: 'Compare lenders', newStepOwner: 'Me', requestId: 9 });
    expect(content.goals[0].steps.map((item) => item.title)).toEqual(['Open a savings account', 'Compare lenders']);
    expect(content.goals[0].steps[1].requestId).toBe(9);
  });

  it('flags weeks over the hours a week and spreads new work within its limits (PB-FR-16)', () => {
    expect(weekStart('2027-01-14')).toBe('2027-01-11');
    expect(weekStart('2027-01-11')).toBe('2027-01-11');
    const content = draftWith([
      step({ key: 'a', targetDate: '2027-01-12', estimatedHours: 4 }),
      step({ key: 'b', targetDate: '2027-01-14', estimatedHours: 3 }),
      step({ key: 'c', targetDate: '2027-01-13', estimatedHours: 9 }),
    ], '2027-01-25');
    const planned = [{ weekStart: '2027-01-11', hours: 2 }];
    expect(overloadedWeeks(content, planned, 8)).toEqual([{ weekStart: '2027-01-11', saved: 2, draft: 16, total: 18 }]);
    expect(overloadedWeeks(content, planned, null)).toEqual([]);

    const { content: spread, moved } = spreadDates(content, planned, 8);
    const dates = Object.fromEntries(spread.goals[0].steps.map((item) => [item.key, item.targetDate]));
    // The 9-hour step fits no week, so it stays; the others move a week, never past the goal's date.
    expect(dates.c).toBe('2027-01-13');
    expect(dates.b).toBe('2027-01-21');
    expect(dates.a).toBe('2027-01-19');
    expect(moved).toBe(2);
  });

  it('checks the draft after saving it, fixes in one click and applies an AI proposal only when asked (PB-FR-28)', async () => {
    const saveDraft = vi.fn().mockResolvedValue(undefined);
    vi.mocked(checkPlanDraft).mockResolvedValue({ aiChecked: true, requestId: 4, issues: [
      { kind: 'MISSING_OWNER', itemType: 'STEP', itemKey: 's1', itemTitle: 'Do finance stuff', problem: 'This step has no owner.',
        fix: 'Make it yours for now.', fixAction: 'SET_OWNER_ME', fromAi: false },
      { kind: 'VAGUE_STEP', itemType: 'STEP', itemKey: 's1', itemTitle: 'Do finance stuff', problem: 'Too vague to act on',
        fixAction: 'FIX_WITH_AI', fromAi: true },
    ] });
    vi.mocked(fixPlanDraft).mockResolvedValue({ itemKey: 's1', kind: 'VAGUE_STEP', title: 'Open a savings account', note: 'Clearer' });
    render(<Harness initial={draftWith([step({ key: 's1', title: 'Do finance stuff', owner: '', targetDate: '2027-02-01' })])}
      saveDraft={saveDraft} />);

    await userEvent.click(screen.getByRole('button', { name: 'Check my draft' }));
    expect(saveDraft).toHaveBeenCalled();
    expect(checkPlanDraft).toHaveBeenCalledWith('t', 5, 'en');
    await userEvent.click(await screen.findByRole('button', { name: 'Make it mine' }));
    expect(draft().goals[0].steps[0].owner).toBe('Me');
    expect(screen.queryByText('This step has no owner.')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Fix with AI' }));
    expect(fixPlanDraft).toHaveBeenCalledWith('t', 5, 'en', { itemKey: 's1', kind: 'VAGUE_STEP', problem: 'Too vague to act on' });
    expect(await screen.findByText('Rename to "Open a savings account"')).toBeInTheDocument();
    expect(draft().goals[0].steps[0].title).toBe('Do finance stuff');
    await userEvent.click(screen.getByRole('button', { name: 'Apply to my draft' }));
    expect(draft().goals[0].steps[0].title).toBe('Open a savings account');
  });

  it('remembers the hours a week and offers to spread the dates', async () => {
    vi.mocked(savePlanCapacity).mockResolvedValue({ hoursPerWeek: 5, planned: [] });
    render(<Harness initial={draftWith([step({ key: 'a', targetDate: '2027-01-12', estimatedHours: 4 }),
      step({ key: 'b', targetDate: '2027-01-13', estimatedHours: 4 })], '2027-03-01')} capacity={{ hoursPerWeek: null, planned: [] }} />);

    await userEvent.type(screen.getByRole('spinbutton', { name: 'Hours a week you have for your plans' }), '5');
    await userEvent.tab();
    expect(savePlanCapacity).toHaveBeenCalledWith('t', 5);
    expect(await screen.findByText('These weeks have more work than your 5 hours:')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Spread the dates' }));
    expect(screen.getByText('Moved 1 item(s) a week or more later.')).toBeInTheDocument();
    expect(screen.queryByText('These weeks have more work than your 5 hours:')).not.toBeInTheDocument();
  });
});
