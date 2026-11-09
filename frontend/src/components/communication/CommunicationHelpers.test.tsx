import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { checkPageArea, recordPageOutcome } from '../../api/aiPageApi';
import { communicationHelp, getDraftPriority, getFollowUpDue } from '../../api/communicationHelpApi';
import type { AiStatus, CommunicationHelpAnswer, CommunicationStatus } from '../../types/vision';
import { CommunicationHelpers } from './CommunicationHelpers';
import { DraftPriorityCard, FollowUpDueCard } from './CommunicationListHelpers';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPageApi', () => ({ checkPageArea: vi.fn(), recordPageOutcome: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({ ratePlanAnswer: vi.fn() }));
vi.mock('../../api/aiDailyApi', () => ({ getPitchFeedback: vi.fn() }));
vi.mock('../../api/communicationHelpApi', () => ({
  communicationHelp: vi.fn(),
  getFollowUpDue: vi.fn(),
  getDraftPriority: vi.fn(),
}));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 5 };
const off: AiStatus = { ...ready, available: false, reason: 'OFF' };
const empty: CommunicationHelpAnswer = {
  requestId: 5, mode: 'FILL', note: '', changes: [], issues: [], message: null, task: null, ranking: [], sources: [],
};

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname}{location.search}</p>;
}

function Form({ status = 'DRAFT', messageId = 8, onNew = vi.fn(), onRebuild = vi.fn() }: {
  status?: CommunicationStatus; messageId?: number | null; onNew?: () => void; onRebuild?: () => void;
}) {
  const [hook, setHook] = useState('');
  const [request, setRequest] = useState('Twenty minutes of feedback');
  return (
    <MemoryRouter initialEntries={['/communication']}>
      <Routes>
        <Route path="*" element={(
          <>
            <CommunicationHelpers token="token" messageId={messageId} status={status} partnerName="Dr Lina"
              progressText="Concept note is now 80% done."
              read={() => ({ partnerId: '3', subject: 'Concept note review', request, hook, benefitToPartner: 'Early data', relatedTaskId: '12' })}
              apply={(field, value) => (field === 'hook' ? setHook(value) : setRequest(value))}
              onRebuildWithTemplate={onRebuild} onNewMessage={onNew} />
            <p>Hook: {hook}</p>
            <p>Request: {request}</p>
            <Where />
          </>
        )} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Communication helpers without AI (V15 Phase 3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAiStatus).mockResolvedValue(off);
  });

  it('checks the message by the rules and rebuilds it with the program template (AH-FR-46, AH-FR-63)', async () => {
    vi.mocked(checkPageArea).mockResolvedValue({
      page: 'COMMUNICATION', area: 'message', canPropose: true,
      issues: [{ field: 'callToAction', problem: 'There is no call to action.', fix: 'End with one clear next step.' }],
    });
    const onRebuild = vi.fn();
    render(<Form onRebuild={onRebuild} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Check my message' }));
    expect(await screen.findByText(/There is no call to action\./)).toBeInTheDocument();
    expect(communicationHelp).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Rebuild message' }));
    expect(onRebuild).toHaveBeenCalled();
    // AI-only helpers are hidden.
    expect(screen.queryByRole('button', { name: 'Prepare for objections' })).not.toBeInTheDocument();
  });

  it('drafts a follow-up from the program template for a sent message (AH-FR-50)', async () => {
    const onNew = vi.fn();
    render(<Form status="SENT" onNew={onNew} />);
    await userEvent.click(screen.getByRole('button', { name: 'Draft a follow-up' }));
    expect(onNew).toHaveBeenCalledWith(expect.objectContaining({
      partnerId: 3, subject: 'Following up: Concept note review', request: 'Twenty minutes of feedback', relatedTaskId: 12,
    }));
    const body = onNew.mock.calls[0][0].messageBody as string;
    expect(body).toContain('Dear Dr Lina,');
    expect(body).toContain('"Concept note review"');
    expect(screen.queryByRole('button', { name: 'Thank and report back' })).not.toBeInTheDocument();
  });

  it('thanks the partner with progress from the linked work (AH-FR-65)', async () => {
    const onNew = vi.fn();
    render(<Form status="REPLIED" onNew={onNew} />);
    await userEvent.click(screen.getByRole('button', { name: 'Thank and report back' }));
    expect(onNew.mock.calls[0][0].messageBody).toContain('Concept note is now 80% done.');
    expect(onNew.mock.calls[0][0].request).toBe('');
  });

  it('offers no follow-up or thank-you on a new, unsaved message', async () => {
    render(<Form status="SENT" messageId={null} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Draft a follow-up' })).not.toBeInTheDocument();
  });
});

describe('Communication helpers with AI (V15 Phase 3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(recordPageOutcome).mockResolvedValue(undefined);
  });

  it('practises the ask with the request and benefit filled in (AH-FR-54)', async () => {
    render(<Form />);
    await userEvent.click(await screen.findByRole('button', { name: 'Practise this ask' }));
    expect(screen.getByRole('textbox', { name: 'Your pitch' })).toHaveValue('Twenty minutes of feedback Early data');
  });

  it('fills empty fields from one sentence, as cards that only fill the form (AH-FR-44)', async () => {
    vi.mocked(communicationHelp).mockResolvedValue({ ...empty, changes: [{ field: 'hook', value: 'Your talk stayed with me.', reason: '' }] });
    render(<Form />);
    await userEvent.type(await screen.findByRole('textbox', { name: 'What do you want to ask, in one sentence?' }), 'ask for a review');
    await userEvent.click(screen.getByRole('button', { name: 'Fill the empty fields' }));
    expect(communicationHelp).toHaveBeenCalledWith('token', 'en', expect.objectContaining({ mode: 'FILL', intent: 'ask for a review', messageId: 8 }));
    const card = await screen.findByRole('region', { name: 'Suggestion for Hook' });
    await userEvent.click(within(card).getByRole('button', { name: 'Apply to the form' }));
    expect(screen.getByText('Hook: Your talk stayed with me.')).toBeInTheDocument();
    expect(recordPageOutcome).toHaveBeenCalledWith('token', 5, 1, 0);
  });

  it('checks the message with fix cards (AH-FR-45)', async () => {
    vi.mocked(communicationHelp).mockResolvedValue({
      ...empty, mode: 'CHECK',
      issues: [
        { problem: 'The request does not say when.', fix: { field: 'request', value: 'Twenty minutes before Friday?', reason: '' } },
        { problem: 'The tone sounds demanding.', fix: null },
      ],
    });
    render(<Form />);
    await userEvent.click(await screen.findByRole('button', { name: 'Check my message' }));
    expect(await screen.findByText('The tone sounds demanding.')).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('region', { name: 'Suggestion for Request' })).getByRole('button', { name: 'Apply to the form' }));
    expect(screen.getByText('Request: Twenty minutes before Friday?')).toBeInTheDocument();
    expect(checkPageArea).not.toHaveBeenCalled();
  });

  it('changes the tone with one card for the body (AH-FR-47)', async () => {
    vi.mocked(communicationHelp).mockResolvedValue({ ...empty, mode: 'TONE', changes: [{ field: 'messageBody', value: 'Short.', reason: '' }] });
    render(<Form />);
    await userEvent.click(await screen.findByRole('button', { name: 'In Khmer' }));
    expect(communicationHelp).toHaveBeenCalledWith('token', 'en', expect.objectContaining({ mode: 'TONE', tone: 'KHMER' }));
    expect(await screen.findByRole('region', { name: 'Suggestion for Message' })).toBeInTheDocument();
  });

  it('opens an AI follow-up as a new message (AH-FR-49)', async () => {
    const draft = { partnerId: 3, subject: 'Following up', request: 'Tuesday or Thursday?', messageBody: 'Dear Dr...', relatedDreamId: null, relatedGoalId: null, relatedTaskId: 12 };
    vi.mocked(communicationHelp).mockResolvedValue({ ...empty, mode: 'FOLLOW_UP', message: draft });
    const onNew = vi.fn();
    render(<Form status="FOLLOWED_UP" onNew={onNew} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Draft a follow-up' }));
    await vi.waitFor(() => expect(onNew).toHaveBeenCalledWith(draft));
    expect(recordPageOutcome).toHaveBeenCalledWith('token', 5, 1, 0);
  });

  it('prepares for an agreed meeting with a task titled by role (AH-FR-64)', async () => {
    vi.mocked(communicationHelp).mockResolvedValue({
      ...empty, mode: 'MEETING_PREP', task: { title: 'Meet Research lead', description: 'Outcome: Agree the method', owner: 'Me' },
    });
    render(<Form status="REPLIED" />);
    await userEvent.click(await screen.findByRole('checkbox', { name: 'A meeting or call was agreed' }));
    await userEvent.click(screen.getByRole('button', { name: 'Prepare for the meeting' }));
    const card = await screen.findByRole('region', { name: 'Meeting plan' });
    await userEvent.type(within(card).getByLabelText('Due date'), '2027-01-15');
    await userEvent.click(within(card).getByRole('button', { name: 'Open the task form' }));
    const where = decodeURIComponent((screen.getByTestId('where').textContent ?? '').replace(/\+/g, ' '));
    expect(where).toContain('/tasks?create=task&title=Meet Research lead&description=Outcome: Agree the method&owner=Me&dueDate=2027-01-15');
  });
});

function AdaptForm() {
  return (
    <MemoryRouter>
      <CommunicationHelpers token="token" messageId={8} status="DRAFT" partnerName="Dr Lina" progressText=""
        read={() => ({ partnerId: '3', request: 'Review my note' })} apply={vi.fn()} onRebuildWithTemplate={vi.fn()} onNewMessage={vi.fn()}
        partnerStyle="PLANNER" partnerMotivator="RECOGNITION" />
    </MemoryRouter>
  );
}

describe('Adapt to this partner (V15 Phase 5)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows the fixed style and motivator tips without AI (AH-FR-60)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    render(<AdaptForm />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Adapt to this partner' }));
    const tips = screen.getByRole('list', { name: 'Tips for this partner' });
    expect(within(tips).getByText(/^You described them as a Planner: Bring the plan and the data/)).toBeInTheDocument();
    expect(within(tips).getByText('Say how their part will be seen and credited.')).toBeInTheDocument();
    expect(communicationHelp).not.toHaveBeenCalled();
  });

  it('asks Claude for cards with AI (AH-FR-59)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(communicationHelp).mockResolvedValue({ ...empty, mode: 'ADAPT',
      changes: [{ field: 'callToAction', value: 'Could we fix Tuesday 14 January?', reason: 'Planners like a date.' }] });
    render(<AdaptForm />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Adapt to this partner' }));
    expect(communicationHelp).toHaveBeenCalledWith('token', 'en', expect.objectContaining({ mode: 'ADAPT' }));
    expect(await screen.findByRole('region', { name: 'Suggestion for Call to Action' })).toBeInTheDocument();
  });
});

describe('Communication list helpers (V15 Phase 3)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists follow-ups due and drafts one from the template without AI (AH-FR-48, AH-FR-50)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getFollowUpDue).mockResolvedValue([{ id: 4, subject: 'First ask', request: 'A short review', status: 'SENT',
      followUpDate: '2026-11-01', partnerId: 3, partnerName: 'Dr Lina', daysOverdue: 8 }]);
    const onNew = vi.fn();
    render(<FollowUpDueCard token="token" reloadKey={0} onNewMessage={onNew} />);
    expect(await screen.findByText('First ask')).toBeInTheDocument();
    expect(screen.getByText(/8 day\(s\) overdue/)).toBeInTheDocument();
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Draft a follow-up' }));
    expect(onNew).toHaveBeenCalledWith(expect.objectContaining({ partnerId: 3, subject: 'Following up: First ask', request: 'A short review' }));
    expect(communicationHelp).not.toHaveBeenCalled();
  });

  it('sorts drafts by the rule without AI (AH-FR-66)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(getDraftPriority).mockResolvedValue([{ id: 1, subject: 'Data access', partnerName: 'Dr Lina', taskDueDate: '2026-11-20',
      taskBlocked: true, daysWaiting: 3, reason: null }]);
    render(<DraftPriorityCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Which message should I send first?' }));
    expect(await screen.findByText('Data access')).toBeInTheDocument();
    expect(screen.getByText(/its task is blocked/)).toBeInTheDocument();
  });

  it('adds AI reasons to the top drafts when AI is on', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(communicationHelp).mockResolvedValue({ ...empty, mode: 'PRIORITISE', requestId: 6,
      ranking: [{ id: 1, subject: 'Data access', partnerName: null, taskDueDate: null, taskBlocked: false, daysWaiting: 9, reason: 'It has waited longest.' }] });
    render(<DraftPriorityCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', { name: 'Which message should I send first?' }));
    expect(await screen.findByText('It has waited longest.')).toBeInTheDocument();
    expect(getDraftPriority).not.toHaveBeenCalled();
  });
});
