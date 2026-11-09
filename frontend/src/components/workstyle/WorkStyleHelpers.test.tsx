import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { getAssessmentPractice, getWhatChanged } from '../../api/aiModuleApi';
import { getPartnerBalance, setWorkStyleAiUse, workStyleHelp } from '../../api/workStyleProfileApi';
import type { AiStatus, ChainAssessment, WorkStyleBalance, WorkStyleProfile } from '../../types/vision';
import { axisSentences, estimateStyle } from '../../utils/workStyleContent';
import { PracticeCard, WhatChangedCard } from '../assessment/AssessmentHelpers';
import { AiUseSwitch, PartnerBalanceCard, StyleResultHelpers } from './WorkStyleHelpers';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({ ratePlanAnswer: vi.fn() }));
vi.mock('../../api/aiModuleApi', () => ({ getAssessmentPractice: vi.fn(), getWhatChanged: vi.fn() }));
vi.mock('../../api/workStyleProfileApi', () => ({ getPartnerBalance: vi.fn(), setWorkStyleAiUse: vi.fn(), workStyleHelp: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const off: AiStatus = { ...ready, available: false, reason: 'OFF' };
const driver: WorkStyleProfile = { dominant: 'DRIVER', secondary: 'PLANNER', paceFastScore: 6, paceDeliberateScore: 2, focusTaskScore: 5,
  focusPeopleScore: 3, aiUseWorkStyle: false };
const balance: WorkStyleBalance = {
  dominant: 'DRIVER', complementary: ['PLANNER'], counts: { DRIVER: 0, CONNECTOR: 1, STEADIER: 0, PLANNER: 0 }, noStyle: 1, missing: ['PLANNER'],
  partners: [
    { id: 3, name: 'Dr Lina', role: 'Research lead', supportType: 'MENTOR', workStyleType: 'CONNECTOR', primaryMotivator: null },
    { id: 4, name: 'Mr Dara', role: null, supportType: null, workStyleType: null, primaryMotivator: null },
  ],
};
const latest = { id: 1, takenOn: '2026-11-01', settlingScore: 4, fearScore: 2, criticismScore: 5, visionScore: 3, knowledgeScore: 4,
  resourcesScore: 5, averageScore: 3.8 } as ChainAssessment;

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname}{location.search}</p>;
}

function withRouter(node: React.ReactNode) {
  return render(<MemoryRouter initialEntries={['/work-style']}><Routes><Route path="*" element={<>{node}<Where /></>} /></Routes></MemoryRouter>);
}

describe('Work style rules (V15 Phase 5)', () => {
  it('explains each axis margin from the pole counts without AI (AH-FR-70)', () => {
    expect(axisSentences(driver)).toEqual([
      'Pace is a clear lean: 6 of 8 picks were fast.',
      'Focus was close: 5 on the task and 3 on people, so you use both.',
    ]);
  });

  it('estimates a partner\'s style from four answers (AH-FR-74)', () => {
    expect(estimateStyle(['a', 'a', 'a', 'a'])).toBe('DRIVER');
    expect(estimateStyle(['a', 'b', 'b', 'b'])).toBe('CONNECTOR');
    expect(estimateStyle(['b', 'b', 'b', 'b'])).toBe('STEADIER');
    expect(estimateStyle(['b', 'b', 'a', 'b'])).toBe('PLANNER');
  });
});

describe('Work Style helpers (V15 Phase 5)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('turns the use-my-style switch on (AH-FR-76)', async () => {
    vi.mocked(setWorkStyleAiUse).mockResolvedValue({ ...driver, aiUseWorkStyle: true });
    const onChange = vi.fn();
    render(<AiUseSwitch token="token" profile={driver} onChange={onChange} />);
    const toggle = screen.getByRole('switch', { name: 'Let AI helpers use my work style' });
    expect(toggle).not.toBeChecked();
    await userEvent.click(toggle);
    expect(setWorkStyleAiUse).toHaveBeenCalledWith('token', true);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ aiUseWorkStyle: true }));
  });

  it('without AI, explains by rule and offers the program\'s three practices with app actions (AH-FR-70, AH-FR-72)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    withRouter(<StyleResultHelpers token="token" profile={driver} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'What does my result mean?' }));
    expect(await screen.findByText('Pace is a clear lean: 6 of 8 picks were fast.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Put my style to work' }));
    expect(await screen.findByText('Give each of your next three steps an owner and a date before you start them.')).toBeInTheDocument();
    expect(workStyleHelp).not.toHaveBeenCalled();
    await userEvent.click(screen.getAllByRole('button', { name: 'Add as a habit' })[0]);
    expect(decodeURIComponent(screen.getByTestId('where').textContent ?? '')).toBe('/today?habit=Date before I start');
  });

  it('with AI, shows Claude\'s practices with their actions (AH-FR-71)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(workStyleHelp).mockResolvedValue({ requestId: 7, mode: 'TIPS', text: null, tips: [],
      practices: [{ text: 'Date your three undated steps.', action: 'UNDATED_STEPS', habit: 'Date steps' }] });
    withRouter(<StyleResultHelpers token="token" profile={driver} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Put my style to work' }));
    expect(await screen.findByText('Date your three undated steps.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Do it now' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/steps/undated');
  });

  it('balances partners, estimates a style by rule and opens the partner form with it (AH-FR-73, AH-FR-74)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getPartnerBalance).mockResolvedValue(balance);
    withRouter(<PartnerBalanceCard token="token" />);
    expect(await screen.findByText('None of your partners is a Planner yet.')).toBeInTheDocument();
    expect(screen.getByText('You described them as a Connector')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Estimate their style' }));
    const estimate = screen.getByRole('region', { name: 'Estimate their style' });
    for (const statement of ['They take time to decide.', 'They like to plan before starting.', 'They talk mostly about the work and results.',
      'They prefer facts and details.']) {
      await userEvent.click(within(estimate).getByRole('button', { name: statement }));
    }
    expect(within(estimate).getByText('Your estimate: Planner')).toBeInTheDocument();
    await userEvent.click(within(estimate).getByRole('button', { name: 'Open the partner form' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/partners?edit=4&workStyleType=PLANNER');
    expect(workStyleHelp).not.toHaveBeenCalled();
  });

  it('gives the program tip for working with a partner without AI, and AI tips with it (AH-FR-75)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getPartnerBalance).mockResolvedValue(balance);
    const { unmount } = withRouter(<PartnerBalanceCard token="token" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Working with them' }));
    await userEvent.click(screen.getByRole('button', { name: 'How do I work well with this person?' }));
    expect(screen.getByText(/Pair enthusiasm with one specific, dated commitment/)).toBeInTheDocument();
    unmount();

    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(workStyleHelp).mockResolvedValue({ requestId: 8, mode: 'PARTNER', text: null, practices: [], tips: ['Start with the shared purpose.'] });
    withRouter(<PartnerBalanceCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalledTimes(2));
    await userEvent.click(await screen.findByRole('button', { name: 'Working with them' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'How do they usually work? (optional, not saved)' }), 'Talks a lot');
    await userEvent.click(screen.getByRole('button', { name: 'How do I work well with this person?' }));
    expect(await screen.findByText('Start with the shared purpose.')).toBeInTheDocument();
    expect(workStyleHelp).toHaveBeenCalledWith('token', 'en', { mode: 'PARTNER', partnerId: 3, partnerNotes: 'Talks a lot' });
    await userEvent.click(screen.getByRole('button', { name: 'Adapt my message' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/communication?create=message&partnerId=3');
  });
});

describe('Assessment helpers (V15 Phase 5)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('offers the program practice for each barrier scored 3 or less without AI (AH-FR-27)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    withRouter(<PracticeCard token="token" latest={latest} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'What should I practise this week?' }));
    expect(await screen.findByText('Fear of failure: 2 of 5')).toBeInTheDocument();
    expect(screen.getByText('Unclear vision: 3 of 5')).toBeInTheDocument();
    expect(screen.queryByText(/Avoiding criticism/)).not.toBeInTheDocument();
    expect(getAssessmentPractice).not.toHaveBeenCalled();
    await userEvent.click(screen.getAllByRole('button', { name: 'Do it now' })[0]);
    expect(screen.getByTestId('where')).toHaveTextContent('/journal');
  });

  it('uses Claude\'s practice with AI, and the program\'s for any barrier it left out (AH-FR-26)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getAssessmentPractice).mockResolvedValue({ requestId: 2, practices: [
      { chainKey: 'fearScore', label: 'Fear of failure', score: 2, practice: 'Test one small fear this week.', habit: 'Small fear test' }] });
    withRouter(<PracticeCard token="token" latest={latest} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'What should I practise this week?' }));
    expect(await screen.findByText('Test one small fear this week.')).toBeInTheDocument();
    expect(screen.getByText(/rewrite your top dream's success definition/)).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Add as a habit' })[0]);
    expect(decodeURIComponent(screen.getByTestId('where').textContent ?? '')).toBe('/today?habit=Small fear test');
  });

  it('compares the last two takes with AI (AH-FR-29)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getWhatChanged).mockResolvedValue({ requestId: 4, summary: 'Your vision score rose.', modulesBetween: ['See Your Dream Clearly'] });
    withRouter(<WhatChangedCard token="token" />);
    await userEvent.click(await screen.findByRole('button', { name: 'What changed since last time?' }));
    expect(await screen.findByText('Your vision score rose.')).toBeInTheDocument();
    expect(screen.getByText('Modules completed in between: See Your Dream Clearly')).toBeInTheDocument();
  });
});
