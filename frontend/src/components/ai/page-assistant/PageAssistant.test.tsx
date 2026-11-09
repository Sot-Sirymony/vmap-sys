import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus, listAiConversations } from '../../../api/aiApi';
import { checkPageArea, proposePageChanges, recordPageOutcome } from '../../../api/aiPageApi';
import type { AiStatus, AssistantPage, PageCheck } from '../../../types/vision';
import { HelpWithThis, PageAssistantProvider, PageHelpButton, pageSection } from './PageAssistant';

vi.mock('../../../context/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../../../api/aiApi', () => ({
  getAiStatus: vi.fn(),
  setAiConsent: vi.fn(),
  listAiConversations: vi.fn(),
  getAiConversation: vi.fn(),
  streamAiMessage: vi.fn(),
  rateAiMessage: vi.fn(),
}));
vi.mock('../../../api/aiPageApi', () => ({
  checkPageArea: vi.fn(),
  proposePageChanges: vi.fn(),
  recordPageOutcome: vi.fn(),
}));
vi.mock('../../../api/aiPlanApi', () => ({ ratePlanAnswer: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 5 };
const off: AiStatus = { ...ready, available: false, reason: 'OFF' };
const obstacleCheck: PageCheck = {
  page: 'OBSTACLES',
  area: 'form',
  canPropose: true,
  issues: [{ field: 'rootCause', problem: 'No root cause yet.', fix: 'Ask yourself why this happens, then why again.' }],
};

/** An obstacle form with a title and root cause, like the Obstacles page, plus a save counter. */
function ObstacleForm({ onSave = vi.fn() }: { onSave?: () => void }) {
  const [title, setTitle] = useState('I keep putting off the search');
  const [rootCause, setRootCause] = useState('');
  return (
    <MemoryRouter initialEntries={['/obstacles']}>
      <PageAssistantProvider page="OBSTACLES" current={{ area: 'form', itemId: 7, title }}
        form={{ itemId: 7, read: () => ({ title, rootCause }), apply: (field, value) => (field === 'rootCause' ? setRootCause(value) : setTitle(value)) }}>
        <PageHelpButton />
        <HelpWithThis area="form" itemId={7} title={title} />
        <label>Root cause<input value={rootCause} onChange={(event) => setRootCause(event.target.value)} /></label>
        <button type="button" onClick={onSave}>Save</button>
      </PageAssistantProvider>
    </MemoryRouter>
  );
}

function SimplePage({ page, area, title }: { page: AssistantPage; area: string; title: string }) {
  return (
    <MemoryRouter initialEntries={['/']}>
      <PageAssistantProvider page={page}>
        <HelpWithThis area={area} title={title} />
      </PageAssistantProvider>
    </MemoryRouter>
  );
}

describe('Page assistant (V15 Phase 1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAiConversations).mockResolvedValue([]);
    vi.mocked(checkPageArea).mockResolvedValue(obstacleCheck);
    vi.mocked(recordPageOutcome).mockResolvedValue(undefined);
  });

  it('opens on the area with its help, checks and three quick actions, and reads the unsaved form (AH-FR-02 to AH-FR-05)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    render(<ObstacleForm />);
    await userEvent.type(screen.getByLabelText('Root cause'), 'Too many sources');
    await userEvent.click(screen.getByRole('button', { name: 'Help with I keep putting off the search' }));

    const panel = await screen.findByRole('dialog');
    expect(within(panel).getByText('Helping with: this obstacle: I keep putting off the search')).toBeInTheDocument();
    expect(within(panel).getByText('What exactly is in the way?')).toBeInTheDocument();
    expect(await within(panel).findByText(/No root cause yet\./)).toBeInTheDocument();
    for (const action of ['Help me understand this obstacle', 'What could I try?', 'Who could help?']) {
      expect(await within(panel).findAllByText(action)).not.toHaveLength(0);
    }
    expect(checkPageArea).toHaveBeenCalledWith('token', {
      page: 'OBSTACLES', area: 'form', itemId: 7, form: { title: 'I keep putting off the search', rootCause: 'Too many sources' },
    });
    expect(listAiConversations).toHaveBeenCalledWith('token', { pageSection: 'PAGE:OBSTACLES:7' });
  });

  it('a card fills the form only; nothing is saved until the page\'s own Save (AH-FR-06, AH-FR-09)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(proposePageChanges).mockResolvedValue({
      requestId: 41,
      note: '',
      sources: ['page', 'obstacle'],
      changes: [
        { field: 'rootCause', value: 'The search has no clear end.', reason: 'Names the real cause.' },
        { field: 'title', value: 'Literature search keeps slipping', reason: 'Clearer.' },
      ],
    });
    const onSave = vi.fn();
    render(<ObstacleForm onSave={onSave} />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with I keep putting off the search' }));
    const panel = await screen.findByRole('dialog');
    await userEvent.click(await within(panel).findByRole('button', { name: 'Suggest changes' }));

    expect(await within(panel).findByText('Based on this page and this obstacle')).toBeInTheDocument();
    const rootCard = within(panel).getByRole('region', { name: 'Suggestion for Root cause' });
    await userEvent.click(within(rootCard).getByRole('button', { name: 'Apply to the form' }));
    expect(screen.getByLabelText('Root cause')).toHaveValue('The search has no clear end.');
    expect(recordPageOutcome).toHaveBeenCalledWith('token', 41, 1, 0);
    expect(within(panel).getByText('Added to the form. Nothing is saved until you press Save.')).toBeInTheDocument();
    expect(onSave).not.toHaveBeenCalled();

    // Dismissing changes nothing and counts nothing.
    const titleCard = within(panel).getByRole('region', { name: 'Suggestion for Title' });
    await userEvent.click(within(titleCard).getByRole('button', { name: 'Dismiss' }));
    expect(within(panel).queryByRole('region', { name: 'Suggestion for Title' })).not.toBeInTheDocument();
    expect(recordPageOutcome).toHaveBeenCalledTimes(1);
  });

  it('an edited card is counted as edited', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(proposePageChanges).mockResolvedValue({
      requestId: 42, note: '', sources: [], changes: [{ field: 'rootCause', value: 'No end point.', reason: '' }],
    });
    render(<ObstacleForm />);
    await userEvent.click(screen.getByRole('button', { name: 'Help' }));
    const panel = await screen.findByRole('dialog');
    await userEvent.click(await within(panel).findByRole('button', { name: 'Suggest changes' }));
    const box = await within(panel).findByRole('textbox', { name: 'Root cause' });
    await userEvent.type(box, ' Yet.');
    await userEvent.click(within(panel).getByRole('button', { name: 'Apply to the form' }));
    expect(screen.getByLabelText('Root cause')).toHaveValue('No end point. Yet.');
    expect(recordPageOutcome).toHaveBeenCalledWith('token', 42, 0, 1);
  });

  it('without AI the help and checks still work, with no AI controls (AH-FR-10)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    render(<ObstacleForm />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with I keep putting off the search' }));
    const panel = await screen.findByRole('dialog');
    expect(await within(panel).findByText(/No root cause yet\./)).toBeInTheDocument();
    expect(within(panel).getByText('What exactly is in the way?')).toBeInTheDocument();
    expect(await within(panel).findByText('AI help is not available right now. The help and checks above still work.')).toBeInTheDocument();
    expect(within(panel).queryByRole('button', { name: 'Suggest changes' })).not.toBeInTheDocument();
    expect(proposePageChanges).not.toHaveBeenCalled();
  });

  it('self-assessments get help but never change cards (AH-FR-24, AH-FR-69)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(checkPageArea).mockResolvedValue({ page: 'ASSESSMENT', area: 'statement:fearScore', canPropose: false, issues: [] });
    render(<SimplePage page="ASSESSMENT" area="statement:fearScore" title="Fear of failure" />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with Fear of failure' }));
    const panel = await screen.findByRole('dialog');
    expect(within(panel).getByText('Pick the number that fits how you usually act, from 1 (rarely true) to 5 (almost always true).'))
      .toBeInTheDocument();
    expect(await within(panel).findAllByText('What does this statement mean?')).not.toHaveLength(0);
    expect(within(panel).queryByRole('button', { name: 'Suggest changes' })).not.toBeInTheDocument();
  });

  it('closing returns focus to the button that opened it (AH-NFR-09)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    render(<SimplePage page="WORK_STYLE" area="question:3" title="Question 3" />);
    const opener = screen.getByRole('button', { name: 'Help with Question 3' });
    await userEvent.click(opener);
    const panel = await screen.findByRole('dialog');
    expect(within(panel).getByRole('heading', { name: 'Page assistant' })).toHaveFocus();
    await userEvent.click(within(panel).getByRole('button', { name: 'Close' }));
    await vi.waitFor(() => expect(opener).toHaveFocus());
  });

  it('shows the field guide for one message field, with no AI (AH-FR-61)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    vi.mocked(checkPageArea).mockResolvedValue({ page: 'COMMUNICATION', area: 'field:request', canPropose: true, issues: [] });
    render(<SimplePage page="COMMUNICATION" area="field:request" title="Request" />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with Request' }));
    const panel = await screen.findByRole('dialog');
    expect(within(panel).getByText('Exactly what you are asking for: what, when and how long.')).toBeInTheDocument();
    expect(within(panel).getByText(/^Example: Could you review my two-page method section/)).toBeInTheDocument();
  });

  it('tags conversations by page and item', () => {
    expect(pageSection('OBSTACLES', 12)).toBe('PAGE:OBSTACLES:12');
    expect(pageSection('MODULES')).toBe('PAGE:MODULES');
  });
});
