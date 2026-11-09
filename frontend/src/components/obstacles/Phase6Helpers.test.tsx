import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { recordPageOutcome } from '../../api/aiPageApi';
import { communicationHelp } from '../../api/communicationHelpApi';
import { obstacleHelp } from '../../api/obstacleHelpApi';
import type { AiStatus, AssistantPage, CommunicationHelpAnswer } from '../../types/vision';
import { aiOnFor } from '../ai/aiFocus';
import { PageSwitchesField } from '../ai/page-assistant/PageSwitchesField';
import { CommunicationHelpers } from '../communication/CommunicationHelpers';
import { ConflictHelp } from './ConflictHelp';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPageApi', () => ({ checkPageArea: vi.fn(), recordPageOutcome: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({ ratePlanAnswer: vi.fn() }));
vi.mock('../../api/aiDailyApi', () => ({ getPitchFeedback: vi.fn() }));
vi.mock('../../api/obstacleHelpApi', () => ({ obstacleHelp: vi.fn() }));
vi.mock('../../api/communicationHelpApi', () => ({ communicationHelp: vi.fn(), getFollowUpDue: vi.fn(), getDraftPriority: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };

describe('Page switches (V15, AH-FR-57)', () => {
  it('treats a page an admin switched off as off, even when AI is available', () => {
    expect(aiOnFor(ready, 'OBSTACLES')).toBe(true);
    expect(aiOnFor({ ...ready, pagesOff: ['OBSTACLES'] }, 'OBSTACLES')).toBe(false);
    expect(aiOnFor({ ...ready, pagesOff: ['OBSTACLES'] }, 'COMMUNICATION')).toBe(true);
    expect(aiOnFor({ ...ready, available: false }, 'COMMUNICATION')).toBe(false);
    expect(aiOnFor(null, 'MODULES')).toBe(false);
  });

  it('ticks the pages that are on and reports the ones turned off', async () => {
    function Field() {
      const [off, setOff] = useState<AssistantPage[]>(['WORK_STYLE']);
      return <><PageSwitchesField legend="Page assistant and helpers are on for" pagesOff={off} onChange={setOff} /><p>Off: {off.join(',')}</p></>;
    }
    render(<Field />);
    expect(screen.getByRole('checkbox', { name: 'Work Style' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Obstacles' })).toBeChecked();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Obstacles' }));
    expect(screen.getByText('Off: WORK_STYLE,OBSTACLES')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Work Style' }));
    expect(screen.getByText('Off: OBSTACLES')).toBeInTheDocument();
  });
});

describe('Conflict and criticism help (V15, AH-FR-40)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(recordPageOutcome).mockResolvedValue(undefined);
  });

  function Conflict({ note = 'I felt humiliated' }: { note?: string }) {
    const [substance, setSubstance] = useState('');
    return (
      <>
        <ConflictHelp token="token" obstacleId={7} read={() => ({ title: 'Criticised in a meeting' })} privateNote={note}
          apply={(_field, value) => setSubstance(value)} />
        <p>Substance: {substance}</p>
      </>
    );
  }

  it('sends the private reaction only when ticked, and its cards only fill the form', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(obstacleHelp).mockResolvedValue({ requestId: 3, mode: 'CONFLICT', note: '', question: null, task: null, ranking: [], sources: [],
      changes: [{ field: 'criticismSubstance', value: 'The methods need more detail.', reason: '' }] });
    render(<Conflict />);
    await userEvent.click(await screen.findByRole('button', { name: 'Help me think this through' }));
    expect(obstacleHelp).toHaveBeenLastCalledWith('token', 'en', { mode: 'CONFLICT', obstacleId: 7, form: { title: 'Criticised in a meeting' },
      includePrivateNote: false });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Include my private reaction, for this request only' }));
    await userEvent.click(screen.getByRole('button', { name: 'Help me think this through' }));
    expect(obstacleHelp).toHaveBeenLastCalledWith('token', 'en', expect.objectContaining({
      includePrivateNote: true, form: { title: 'Criticised in a meeting', conflictPrivateNote: 'I felt humiliated' },
    }));
    await userEvent.click(within(await screen.findByRole('region', { name: 'Suggestion for The useful point' }))
      .getByRole('button', { name: 'Apply to the form' }));
    expect(screen.getByText('Substance: The methods need more detail.')).toBeInTheDocument();
  });

  it('shows the safety response for crisis wording', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(obstacleHelp).mockResolvedValue({ requestId: null, mode: 'CRISIS', note: 'Please reach out to someone you trust. Help lines: 1234',
      question: null, task: null, ranking: [], sources: [], changes: [] });
    render(<Conflict />);
    await userEvent.click(await screen.findByRole('button', { name: 'Help me think this through' }));
    expect(await screen.findByText(/Please reach out to someone you trust/)).toBeInTheDocument();
  });

  it('is hidden when the Obstacles page is switched off', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, pagesOff: ['OBSTACLES'] });
    const { container } = render(<Conflict />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    expect(container.querySelector('button')).toBeNull();
  });
});

describe('Read a reply (V15, AH-FR-51)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(recordPageOutcome).mockResolvedValue(undefined);
  });

  it('reads a pasted reply and offers a status card and a reply draft', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    const reply: CommunicationHelpAnswer = {
      requestId: 9, mode: 'REPLY', note: 'Dr Lina will help and offers Tuesday.', issues: [], task: null, ranking: [], sources: [],
      changes: [{ field: 'status', value: 'REPLIED', reason: '' }],
      message: { partnerId: 3, subject: 'Re: Concept note', request: '', messageBody: 'Tuesday suits me.', relatedDreamId: null, relatedGoalId: null,
        relatedTaskId: null },
      reading: { kind: 'YES', nextAction: 'Confirm Tuesday.' },
    };
    vi.mocked(communicationHelp).mockResolvedValue(reply);
    const onNew = vi.fn();
    render(
      <MemoryRouter>
        <CommunicationHelpers token="token" messageId={8} status="SENT" partnerName="Dr Lina" progressText=""
          read={() => ({ subject: 'Concept note' })} apply={vi.fn()} onRebuildWithTemplate={vi.fn()} onNewMessage={onNew} />
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Read a reply' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Paste the reply here (not saved)' }), 'Happy to help, Tuesday?');
    await userEvent.click(screen.getByRole('button', { name: 'Read it' }));
    expect(communicationHelp).toHaveBeenCalledWith('token', 'en', expect.objectContaining({ mode: 'REPLY', reply: 'Happy to help, Tuesday?' }));
    expect(await screen.findByText('Dr Lina will help and offers Tuesday.')).toBeInTheDocument();
    expect(screen.getByText('A yes')).toBeInTheDocument();
    expect(screen.getByText('Confirm Tuesday.')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Suggestion for Status' })).toBeInTheDocument();
    // The reply draft opens only when chosen.
    expect(onNew).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Use the reply draft' }));
    expect(onNew).toHaveBeenCalledWith(reply.message);
  });
});
