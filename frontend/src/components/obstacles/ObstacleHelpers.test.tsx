import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { recordPageOutcome } from '../../api/aiPageApi';
import { getBlockedWithoutObstacle, getObstaclePriority, getPartnerMatches, obstacleHelp } from '../../api/obstacleHelpApi';
import type { AiStatus, ObstacleType } from '../../types/vision';
import { ObstacleHelpers } from './ObstacleHelpers';
import { BlockedTasksCard, ObstaclePriorityCard } from './ObstacleListHelpers';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPageApi', () => ({ recordPageOutcome: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({ ratePlanAnswer: vi.fn() }));
vi.mock('../../api/obstacleHelpApi', () => ({
  obstacleHelp: vi.fn(),
  getPartnerMatches: vi.fn(),
  getObstaclePriority: vi.fn(),
  getBlockedWithoutObstacle: vi.fn(),
}));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 5 };
const off: AiStatus = { ...ready, available: false, reason: 'OFF' };

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname}{location.search}</p>;
}

/** An obstacle form with the fields the helpers fill, like the Obstacles page. */
function Form({ type = 'KNOWLEDGE', partner = '' }: { type?: ObstacleType; partner?: string }) {
  const [obstacleType, setObstacleType] = useState<ObstacleType>(type);
  const [rootCause, setRootCause] = useState('');
  const [requiredPartnerId, setRequiredPartnerId] = useState(partner);
  return (
    <MemoryRouter initialEntries={['/obstacles']}>
      <Routes>
        <Route path="*" element={(
          <>
            <ObstacleHelpers token="token" obstacleId={7} obstacleType={obstacleType} title="No library access"
              description="My access expired" solution="Renew it" requiredPartnerId={requiredPartnerId}
              read={() => ({ title: 'No library access', obstacleType, rootCause })}
              apply={(field, value) => (field === 'obstacleType' ? setObstacleType(value as ObstacleType) : setRootCause(value))}
              onSetRequiredPartner={setRequiredPartnerId} partnerName="Dr Lina" />
            <p>Type: {obstacleType}</p>
            <p>Root cause: {rootCause}</p>
            <p>Required: {requiredPartnerId}</p>
            <Where />
          </>
        )} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Obstacle helpers (V15 Phase 2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(recordPageOutcome).mockResolvedValue(undefined);
  });

  it('shows who could help and matches the participant\'s own partners without AI (AH-FR-34, AH-FR-35)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getPartnerMatches).mockResolvedValue({
      obstacleType: 'KNOWLEDGE', suggestedSupportType: 'MENTOR',
      partners: [{ id: 3, name: 'Dr Lina', role: 'Research lead', supportType: 'MENTOR', status: 'ACTIVE' }],
    });
    render(<Form />);
    expect(screen.getByText('Who could help: Mentor or expert')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Match my partners' }));
    expect(getPartnerMatches).toHaveBeenCalledWith('token', 'KNOWLEDGE');
    await userEvent.click(await screen.findByRole('button', { name: 'Set as required partner' }));
    expect(screen.getByText('Required: 3')).toBeInTheDocument();
    // AI helpers are hidden while AI is off.
    expect(screen.queryByRole('button', { name: 'Suggest type and severity' })).not.toBeInTheDocument();
    expect(obstacleHelp).not.toHaveBeenCalled();
  });

  it('offers to add a partner with the suggested support type when none fits', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getPartnerMatches).mockResolvedValue({ obstacleType: 'MONEY', suggestedSupportType: 'FINANCIAL', partners: [] });
    render(<Form type="MONEY" />);
    await userEvent.click(screen.getByRole('button', { name: 'Match my partners' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add a partner' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/partners?create=partner&supportType=FINANCIAL&status=TO_CONTACT');
  });

  it('drafts a message to the required partner on the Communication page (AH-FR-36)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    render(<Form partner="3" />);
    await userEvent.click(screen.getByRole('button', { name: 'Draft a message to them' }));
    const where = screen.getByTestId('where').textContent ?? '';
    expect(where).toContain('/communication?create=message&partnerId=3');
    expect(decodeURIComponent(where.replace(/\+/g, ' '))).toContain('problem=My access expired');
    expect(decodeURIComponent(where.replace(/\+/g, ' '))).toContain('audience=Dr Lina');
  });

  it('classify cards fill the form and are counted (AH-FR-31)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(obstacleHelp).mockResolvedValue({
      requestId: 9, mode: 'CLASSIFY', note: '', question: null, task: null, ranking: [], sources: [],
      changes: [{ field: 'obstacleType', value: 'TIME', reason: 'It is about hours.' }],
    });
    render(<Form />);
    await userEvent.click(await screen.findByRole('button', { name: 'Suggest type and severity' }));
    expect(obstacleHelp).toHaveBeenCalledWith('token', 'en', expect.objectContaining({ mode: 'CLASSIFY', obstacleId: 7 }));
    const card = await screen.findByRole('region', { name: 'Suggestion for Type' });
    await userEvent.click(within(card).getByRole('button', { name: 'Apply to the form' }));
    expect(screen.getByText('Type: TIME')).toBeInTheDocument();
    expect(recordPageOutcome).toHaveBeenCalledWith('token', 9, 1, 0);
  });

  it('asks why questions one at a time, answered in place, then proposes the root cause (AH-FR-32)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(obstacleHelp)
      .mockResolvedValueOnce({ requestId: 1, mode: 'ROOT_CAUSE', note: '', question: 'Why did it expire?', task: null, ranking: [], sources: [], changes: [] })
      .mockResolvedValueOnce({
        requestId: 2, mode: 'ROOT_CAUSE', note: '', question: null, task: null, ranking: [], sources: [],
        changes: [{ field: 'rootCause', value: 'Nobody reminds me to renew.', reason: '' }],
      });
    render(<Form />);
    await userEvent.click(await screen.findByRole('button', { name: 'Find the root cause' }));
    expect(await screen.findByText('Question 1 of 3')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'Why did it expire?' }), 'I forgot the date');
    await userEvent.click(screen.getByRole('button', { name: 'Answer' }));
    expect(obstacleHelp).toHaveBeenLastCalledWith('token', 'en', expect.objectContaining({
      mode: 'ROOT_CAUSE', answers: [{ question: 'Why did it expire?', answer: 'I forgot the date' }],
    }));
    const card = await screen.findByRole('region', { name: 'Suggestion for Root cause' });
    await userEvent.click(within(card).getByRole('button', { name: 'Apply to the form' }));
    expect(screen.getByText('Root cause: Nobody reminds me to renew.')).toBeInTheDocument();
  });

  it('turns the obstacle into a task by opening the normal task form, filled (AH-FR-37)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(obstacleHelp).mockResolvedValue({
      requestId: 4, mode: 'NEXT_TASK', note: 'Unblocks the search.', question: null, ranking: [], sources: [], changes: [],
      task: { title: 'Email the librarian', description: 'Ask to renew.', owner: 'Me', dueDate: '2026-11-20', priority: 'HIGH',
        stepId: 5, stepTitle: 'Search literature', latestDate: '2026-12-09' },
    });
    render(<Form />);
    await userEvent.click(await screen.findByRole('button', { name: 'Turn this into a task' }));
    const card = await screen.findByRole('region', { name: 'Suggested task' });
    expect(within(card).getByText('Under step: Search literature')).toBeInTheDocument();
    expect(within(card).getByRole('textbox', { name: 'Owner' })).toHaveValue('Me');
    await userEvent.click(within(card).getByRole('button', { name: 'Open the task form' }));
    const where = decodeURIComponent((screen.getByTestId('where').textContent ?? '').replace(/\+/g, ' '));
    expect(where).toContain('/tasks?create=task&title=Email the librarian&owner=Me&priority=HIGH&parent=5&dueDate=2026-11-20');
    expect(recordPageOutcome).toHaveBeenCalledWith('token', 4, 1, 0);
  });
});

describe('Obstacle list helpers (V15 Phase 2)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists Blocked tasks without an obstacle and makes one into an obstacle (AH-FR-39)', async () => {
    vi.mocked(getBlockedWithoutObstacle).mockResolvedValue([
      { id: 11, code: 'T-011', title: 'Get the survey data', blockerReason: 'Waiting for the ministry', stepId: 5, stepTitle: 'Collect data', goalId: 2, dreamId: 1 },
    ]);
    const onMake = vi.fn();
    render(<BlockedTasksCard token="token" reloadKey={0} onMakeObstacle={onMake} />);
    expect(await screen.findByText(/Waiting for the ministry/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Make this an obstacle' }));
    expect(onMake).toHaveBeenCalledWith(expect.objectContaining({ id: 11, stepId: 5 }));
  });

  it('shows nothing when every Blocked task has an obstacle', async () => {
    vi.mocked(getBlockedWithoutObstacle).mockResolvedValue([]);
    const { container } = render(<BlockedTasksCard token="token" reloadKey={0} onMakeObstacle={vi.fn()} />);
    await vi.waitFor(() => expect(getBlockedWithoutObstacle).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('sorts by the rule without AI (AH-FR-41)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getObstaclePriority).mockResolvedValue([
      { id: 1, title: 'Ethics approval', severity: 'CRITICAL', blockedTasks: 2, daysOpen: 10, reason: null },
    ]);
    render(<ObstaclePriorityCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Which obstacle should I tackle first?' }));
    expect(await screen.findByText('Ethics approval')).toBeInTheDocument();
    expect(screen.getByText('Sorted by severity, then blocked tasks held up, then how long each has been open.')).toBeInTheDocument();
    expect(obstacleHelp).not.toHaveBeenCalled();
  });

  it('adds AI reasons to the top obstacles when AI is on', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(obstacleHelp).mockResolvedValue({
      requestId: 3, mode: 'PRIORITISE', note: '', question: null, task: null, changes: [], sources: [],
      ranking: [{ id: 1, title: 'Ethics approval', severity: 'CRITICAL', blockedTasks: 2, daysOpen: 10, reason: 'Nothing else can start.' }],
    });
    render(<ObstaclePriorityCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(await screen.findByRole('button', { name: 'Which obstacle should I tackle first?' }));
    expect(await screen.findByText('Nothing else can start.')).toBeInTheDocument();
    expect(getObstaclePriority).not.toHaveBeenCalled();
  });
});
