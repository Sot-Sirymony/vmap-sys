import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { checkPlanDraft } from '../../api/aiPlanApi';
import { getChainAssessments } from '../../api/chainAssessmentApi';
import { updateDream } from '../../api/dreamApi';
import type { ChainAssessment, DraftIssue, Dream, PlanDraftContent } from '../../types/vision';
import { ClarifyStage } from './ClarifyStage';
import { DatesStage } from './DatesStage';
import { InnerStage, chainsToWatch } from './InnerStage';
import { type DraftUpdate, addSteps, addTask, applyParentDateFix, currentStage, emptyGoal, emptyStep, emptyTask } from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({ checkPlanDraft: vi.fn(), clarifyDream: vi.fn(), saveClarifiedField: vi.fn() }));
vi.mock('../../api/chainAssessmentApi', () => ({ getChainAssessments: vi.fn() }));
vi.mock('../../api/dreamApi', () => ({ updateDream: vi.fn() }));

const take = (patch: Partial<ChainAssessment>): ChainAssessment => ({
  id: 1, takenOn: '2027-01-01', settlingScore: 4, fearScore: 4, criticismScore: 4, visionScore: 4, prioritiesScore: 4,
  persistenceScore: 4, averageScore: 4, ...patch,
});

function draft(): PlanDraftContent {
  const goal = { ...emptyGoal(), key: 'g1', title: 'Prepare a concept note', successCriteria: 'Accepted', targetDate: '2027-05-31' };
  const step = { ...emptyStep(), key: 's1', title: 'Search literature', targetDate: '2027-04-20' };
  return addTask(addSteps({ goals: [goal] }, 'g1', [step]), 'g1', 's1', { ...emptyTask(), key: 't1', title: 'Table', dueDate: '2027-04-25' });
}

function Harness({ initial, children }: { initial: PlanDraftContent; children: (content: PlanDraftContent, change: (u: DraftUpdate) => void) => React.ReactNode }) {
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

describe('Build my plan, V31 v1 step 2', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('maps the old Helpers stage into Steps and moves a new parent later (DC-FR-35, DC-FR-42)', () => {
    expect(currentStage('HELPERS')).toBe('STEPS');
    expect(currentStage('DATES')).toBe('DATES');
    const issue = (patch: Partial<DraftIssue>): DraftIssue => ({ kind: 'X', itemKey: 't1', problem: '', fromAi: false, ...patch });
    let content = applyParentDateFix(draft(), issue({ parentKey: 's1', altFixAction: 'MOVE_PARENT_DATE', altFixValue: '2027-04-25' }));
    expect(content.goals[0].steps[0].targetDate).toBe('2027-04-25');
    content = applyParentDateFix(content, issue({ itemKey: 's1', parentKey: 'g1', altFixAction: 'MOVE_PARENT_DATE', altFixValue: '2027-06-10' }));
    expect(content.goals[0].targetDate).toBe('2027-06-10');
    // Anything else leaves the draft alone.
    expect(applyParentDateFix(content, issue({ parentKey: 's1', altFixAction: 'MOVE_DREAM_DATE', altFixValue: '2027-09-01' }))).toBe(content);
  });

  it('names at most the two chains that hold back most (DC-FR-20)', () => {
    expect(chainsToWatch(undefined)).toEqual([]);
    expect(chainsToWatch(take({}))).toEqual([]);
    expect(chainsToWatch(take({ fearScore: 2, criticismScore: 3, persistenceScore: 1 }))).toEqual(['persistenceScore', 'fearScore']);
  });

  it('shows the practices for the chains to watch and keeps the choice (DC-FR-19 to DC-FR-22)', async () => {
    vi.mocked(getChainAssessments).mockResolvedValue({ assessments: [take({ takenOn: '2027-01-01', fearScore: 1 })], retakeSuggested: false });
    const onDone = vi.fn();
    render(<Harness initial={draft()}>{(content, change) => (
      <InnerStage content={content} onChange={change} token="t" onDone={onDone} today={new Date('2027-01-20T00:00:00')} />
    )}</Harness>);

    expect(await screen.findByText('Fear of failure')).toBeInTheDocument();
    expect(screen.getByText(/log one fear in your Journal/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Take the 2-minute check' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: "I'm ready to plan" }));
    expect(current().innerBlockers).toBe('READY');
    expect(onDone).toHaveBeenCalled();
  });

  it('offers the 2-minute check when there is no recent result, and Skip still continues', async () => {
    vi.mocked(getChainAssessments).mockResolvedValue({ assessments: [take({ takenOn: '2026-06-01' })], retakeSuggested: false });
    render(<Harness initial={draft()}>{(content, change) => (
      <InnerStage content={content} onChange={change} token="t" onDone={vi.fn()} today={new Date('2027-01-20T00:00:00')} />
    )}</Harness>);
    expect(await screen.findByText('Your last check was more than three months ago.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Take the 2-minute check' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Skip for now' }));
    expect(current().innerBlockers).toBe('SKIPPED');
  });

  it('lists date conflicts with both fixes and moves the dream through the page (DC-FR-41, DC-FR-42)', async () => {
    vi.mocked(checkPlanDraft).mockResolvedValue({ aiChecked: false, issues: [
      { kind: 'TASK_AFTER_STEP', itemType: 'TASK', itemKey: 't1', parentKey: 's1', itemTitle: 'Table', problem: 'This task is due after its step.',
        fixAction: 'SET_DATE', fixValue: '2027-04-20', fromAi: false, rule: 'R-04', level: 'MUST',
        altFixAction: 'MOVE_PARENT_DATE', altFixValue: '2027-04-25' },
      { kind: 'GOAL_AFTER_DREAM', itemType: 'GOAL', itemKey: 'g1', itemTitle: 'Prepare a concept note', problem: 'This goal is due after its dream.',
        fixAction: 'SET_DATE', fixValue: '2027-03-31', fromAi: false, rule: 'R-04', level: 'MUST',
        altFixAction: 'MOVE_DREAM_DATE', altFixValue: '2027-05-31' },
      { kind: 'GAP_NO_KIND', itemKey: 's1', problem: 'Not a date issue', fromAi: false, rule: 'R-06', level: 'SHOULD' },
    ] });
    const onMoveDreamDate = vi.fn().mockResolvedValue(undefined);
    render(<Harness initial={draft()}>{(content, change) => (
      <DatesStage content={content} onChange={change} dreamId={5} token="t" language="en" saveDraft={vi.fn().mockResolvedValue(undefined)}
        onMoveDreamDate={onMoveDreamDate} />
    )}</Harness>);

    expect(await screen.findByText('This task is due after its step.', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.queryByText('Not a date issue')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /^Move the parent to/ }));
    expect(current().goals[0].steps[0].targetDate).toBe('2027-04-25');
    await userEvent.click(screen.getByRole('button', { name: /^Move the dream to/ }));
    expect(onMoveDreamDate).toHaveBeenCalledWith('2027-05-31');
  });

  it('edits the dream in place on Clarify (DC-FR-18)', async () => {
    const dream = { id: 5, title: 'Own a home', priority: 'MEDIUM', visionAreaId: 3, dreamType: 'LONG_TERM', status: 'IDEA',
      moonshot: false, scheduleMode: 'BOTTOM_UP', archived: false } as Dream;
    vi.mocked(updateDream).mockResolvedValue({ ...dream, whyImportant: 'Family' });
    const onDreamChange = vi.fn();
    render(<Harness initial={draft()}>{(content, change) => (
      <ClarifyStage dream={dream} content={content} onChange={change} onDreamChange={onDreamChange} token="t" language="en"
        aiReady={false} onDone={vi.fn()} />
    )}</Harness>);

    await userEvent.click(screen.getByRole('button', { name: 'Edit the dream here' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Why it matters' }), 'Family');
    await userEvent.click(screen.getByRole('button', { name: 'Save the dream' }));
    expect(updateDream).toHaveBeenCalledWith('t', 5, expect.objectContaining({ title: 'Own a home', whyImportant: 'Family', status: 'IDEA' }));
    expect(onDreamChange).toHaveBeenCalledWith(expect.objectContaining({ whyImportant: 'Family' }));
  });
});
