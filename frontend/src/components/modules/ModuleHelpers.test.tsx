import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { getModuleHelp, getModuleJourney, getNextModuleByRule, suggestNextModule } from '../../api/aiModuleApi';
import type { AiStatus, LearningModuleDetail, ModuleRow } from '../../types/vision';
import { ModuleHelpCard } from '../ai/ModuleHelpCard';
import { ModuleElsewhereCard } from './ModuleElsewhereCard';
import { ModuleReadinessCheck } from './ModuleReadinessCheck';
import { JourneyCard, ModuleNextCard } from './ModulesListHelpers';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({ ratePlanAnswer: vi.fn() }));
vi.mock('../../api/aiModuleApi', () => ({
  getModuleHelp: vi.fn(),
  suggestNextModule: vi.fn(),
  getNextModuleByRule: vi.fn(),
  getModuleJourney: vi.fn(),
}));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const off: AiStatus = { ...ready, available: false, reason: 'OFF' };
const beliefs = {
  number: 1,
  title: 'Rewrite Limiting Beliefs',
  fields: [{ key: 'belief', label: 'Limiting belief', type: 'text' }, { key: 'replacement', label: 'Replacement statement', type: 'text' }],
  rows: [],
  status: 'AVAILABLE',
} as unknown as LearningModuleDetail;

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname}</p>;
}

function withRouter(node: React.ReactNode) {
  return render(<MemoryRouter initialEntries={['/modules']}><Routes><Route path="*" element={<>{node}<Where /></>} /></Routes></MemoryRouter>);
}

/** The module form with the helper, as on the module page. */
function ExerciseWithHelper() {
  const [rows, setRows] = useState<ModuleRow[]>([{ belief: '', replacement: '' }]);
  return (
    <>
      <ModuleHelpCard token="token" module={beliefs} rows={rows} onUseDraft={setRows} />
      <label>Belief 1<input value={rows[0]?.belief ?? ''} onChange={(event) => setRows((current) => [{ ...current[0], belief: event.target.value }, ...current.slice(1)])} /></label>
    </>
  );
}

describe('Modules list helpers (V15 Phase 4)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('names the next module by the rule without AI, and opens it (AH-FR-14)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getNextModuleByRule).mockResolvedValue({ moduleNumber: 1, title: 'Rewrite Limiting Beliefs',
      reason: 'This is the next module in the program\'s order.', rule: 'PROGRAM_ORDER', source: 'RULE', requestId: null });
    withRouter(<ModuleNextCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Which module should I do next?' }));
    expect(await screen.findByText('Rewrite Limiting Beliefs')).toBeInTheDocument();
    expect(suggestNextModule).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Open this module' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/modules/1');
  });

  it('shows Claude\'s reason when AI is on (AH-FR-13)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestNextModule).mockResolvedValue({ moduleNumber: 4, title: 'See Your Dream Clearly',
      reason: 'You have no dreams yet.', rule: 'AI', source: 'AI', requestId: 3 });
    withRouter(<ModuleNextCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Which module should I do next?' }));
    expect(await screen.findByText('You have no dreams yet.')).toBeInTheDocument();
    expect(screen.getByText('AI suggestion')).toBeInTheDocument();
  });

  it('summarises the journey: stages, barrier changes and the next stage (AH-FR-15)', async () => {
    vi.mocked(getModuleJourney).mockResolvedValue({
      done: 3, total: 15, assessments: 2, nextStage: 'BREAK_CHAINS',
      stages: [{ stage: 'BREAK_CHAINS', done: 3, total: 6 }, { stage: 'LAUNCH', done: 0, total: 2 }, { stage: 'BOOST', done: 0, total: 7 }],
      chains: [{ key: 'fearScore', label: 'Fear of failure', first: 1, latest: 3 }, { key: 'visionScore', label: 'Unclear vision', first: 2, latest: 2 }],
    });
    withRouter(<JourneyCard token="token" />);
    await userEvent.click(screen.getByRole('button', { name: 'How far have I come?' }));
    expect(await screen.findByText('3 of 15 modules done')).toBeInTheDocument();
    expect(screen.getByText('Break barriers: 3 of 6')).toBeInTheDocument();
    expect(screen.getByText('Fear of failure: 1 to 3 of 5')).toBeInTheDocument();
    expect(screen.queryByText(/Unclear vision/)).not.toBeInTheDocument();
    expect(screen.getByText('Next stage to finish: Break barriers')).toBeInTheDocument();
  });
});

describe('Module page helpers (V15 Phase 4)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('says what to do on another page and whether it is done (AH-FR-19)', async () => {
    const fears = { ...beliefs, number: 2, fields: [], linkPath: '/journal', requirementMessage: 'Log at least one fear.' } as unknown as LearningModuleDetail;
    withRouter(<ModuleElsewhereCard module={fears} />);
    expect(screen.getByText('Log one fear in your Journal and plan the smallest safe test.')).toBeInTheDocument();
    expect(screen.getByText('Still to do: Log at least one fear.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open Journal' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/journal');
  });

  it('shows that a module done elsewhere is already done', () => {
    const done = { ...beliefs, number: 9, fields: [], linkPath: '/partners', requirementMessage: null } as unknown as LearningModuleDetail;
    withRouter(<ModuleElsewhereCard module={done} />);
    expect(screen.getByText('Your data already shows this. You can mark the module as done.')).toBeInTheDocument();
  });

  it('lists empty fields before saving without AI (AH-FR-20)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    render(<ModuleReadinessCheck token="token" module={beliefs} rows={[{ belief: 'I am too old', replacement: '' }]} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Check before I save' }));
    expect(await screen.findByText('Replacement statement is empty.')).toBeInTheDocument();
    expect(getModuleHelp).not.toHaveBeenCalled();
  });

  it('gives a tip per field with AI', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getModuleHelp).mockResolvedValue({ mode: 'CHECK', questions: [], rows: [], improvements: [],
      checks: [{ row: 0, field: 'belief', tip: 'Say when you first thought this.' }] });
    render(<ModuleReadinessCheck token="token" module={beliefs} rows={[{ belief: 'Old', replacement: 'New' }]} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Check before I save' }));
    expect(await screen.findByText('Limiting belief: Say when you first thought this.')).toBeInTheDocument();
  });

  it('explains the module (AH-FR-18)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getModuleHelp).mockResolvedValue({ mode: 'EXPLAIN', questions: [], rows: [], improvements: [],
      explanation: 'A belief can hold you back.', whyItMatters: 'It frees your plan.', example: 'Sophea thought she was too late.' });
    render(<ExerciseWithHelper />);
    await userEvent.click(await screen.findByRole('button', { name: 'Explain this module' }));
    expect(await screen.findByText('A belief can hold you back.')).toBeInTheDocument();
    expect(screen.getByText('It frees your plan.')).toBeInTheDocument();
    expect(getModuleHelp).toHaveBeenCalledWith('token', 1, 'EXPLAIN', [], 'en', {});
  });

  it('sends answers given in place with the draft, then refines while keeping edited rows (AH-FR-16, AH-FR-17)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getModuleHelp)
      .mockResolvedValueOnce({ mode: 'START', questions: ['What do you tell yourself?'], rows: [], improvements: [] })
      .mockResolvedValueOnce({ mode: 'DRAFT', questions: [], rows: [{ belief: 'Too old', replacement: 'I can learn' }], improvements: [] })
      .mockResolvedValueOnce({ mode: 'REFINE', questions: [], rows: [{ belief: 'My own words', replacement: 'Shorter' }], improvements: [] });
    render(<ExerciseWithHelper />);
    await userEvent.click(await screen.findByRole('button', { name: 'Help me start' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Your answer' }), 'I am too old to learn research');
    await userEvent.click(screen.getByRole('button', { name: 'Draft my answer' }));
    expect(getModuleHelp).toHaveBeenLastCalledWith('token', 1, 'DRAFT', expect.any(Array), 'en', {
      answers: [{ question: 'What do you tell yourself?', answer: 'I am too old to learn research' }],
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Put this draft in the form' }));
    expect(screen.getByLabelText('Belief 1')).toHaveValue('Too old');

    // The participant edits row 1, so it is kept when refining.
    await userEvent.type(screen.getByLabelText('Belief 1'), ' to start');
    await userEvent.click(screen.getByRole('button', { name: 'Shorter' }));
    expect(getModuleHelp).toHaveBeenLastCalledWith('token', 1, 'REFINE', [{ belief: 'Too old to start', replacement: 'I can learn' }], 'en',
      expect.objectContaining({ refine: 'SHORTER', keepRows: [0] }));
  });
});
