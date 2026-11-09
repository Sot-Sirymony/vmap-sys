import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiApi from '../api/aiApi';
import * as aiDailyApi from '../api/aiDailyApi';
import * as communicationApi from '../api/communicationApi';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as partnerApi from '../api/partnerApi';
import * as taskApi from '../api/taskApi';
import { setCurrentLanguage } from '../i18n/translate';
import { answerEmptyLists, emptyPage, pageOf } from '../test/apiMocks';
import { aDream, aPartner } from '../test/builders';
import { renderPage } from '../test/render';
import type { CommunicationMessage } from '../types/vision';
import { CommunicationBuilderPage } from './CommunicationBuilderPage';

vi.mock('../api/aiApi');
vi.mock('../api/aiDailyApi');
vi.mock('../api/communicationApi');
vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/partnerApi');
vi.mock('../api/taskApi');

const request: CommunicationMessage = {
  id: 1, partnerId: 5, relatedDreamId: 1, audience: 'Dr. Mentor', purpose: 'a concept note', subject: 'Request for guidance',
  request: 'Reviewing my draft', messageBody: 'Dear Dr. Mentor, ...', status: 'SENT', followUpDate: '2026-11-20', archived: false,
} as CommunicationMessage;
const thanks: CommunicationMessage = { id: 2, subject: '', audience: '', status: 'DRAFT', archived: false } as CommunicationMessage;

const row = (text: string) => screen.getByText(text, { selector: 'td, td *' }).closest('tr') as HTMLElement;
const field = (dialog: HTMLElement, name: RegExp) => within(dialog).getByRole('textbox', { name });

/** Fills a field the way pasting does: typing long text key by key re-renders this large form too often. */
async function fill(dialog: HTMLElement, name: RegExp, text: string) {
  await userEvent.click(field(dialog, name));
  await userEvent.paste(text);
}

async function openNewMessage() {
  await userEvent.click(screen.getByRole('button', { name: 'Save message' }));
  return screen.findByRole('dialog');
}

async function chooseOption(dialog: HTMLElement, name: string, option: string) {
  await userEvent.click(within(dialog).getByRole('combobox', { name }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
}

// Long form pages: each test may take several seconds on a busy CI runner (COV-NFR-05).
describe('CommunicationBuilderPage (CLAUDE.md page 9)', { timeout: 30000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([dreamApi, goalApi, taskApi]);
    vi.mocked(communicationApi.listCommunicationMessages).mockResolvedValue(pageOf([request, thanks]));
    vi.mocked(partnerApi.listPartners).mockResolvedValue(pageOf([aPartner({ id: 5, name: 'Dr. Mentor' })]));
    vi.mocked(dreamApi.listDreams).mockResolvedValue([aDream({ id: 1, title: 'Become a researcher' })]);
    vi.mocked(communicationApi.createCommunicationMessage).mockResolvedValue(request);
    vi.mocked(communicationApi.updateCommunicationMessage).mockResolvedValue(request);
    vi.mocked(communicationApi.archiveCommunicationMessage).mockResolvedValue(undefined as never);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('lists messages with their audience, status and follow-up date', async () => {
    renderPage(<CommunicationBuilderPage />);
    expect(await screen.findByText('Request for guidance', { selector: 'td, td *' })).toBeInTheDocument();
    expect(within(row('Request for guidance')).getAllByRole('cell').map((cell) => cell.textContent).slice(1, 5))
      .toEqual(['Request for guidance', 'Dr. Mentor', 'SENT', '2026-11-20']);
    expect(communicationApi.listCommunicationMessages).toHaveBeenCalledWith('token', 0, 10, false, 'id,desc', '', { partnerId: '', status: '' });
  });

  it('opens a new message filled from an obstacle link (V15, AH-FR-36)', async () => {
    renderPage(<CommunicationBuilderPage />, {
      route: '/communication?create=message&audience=Dr%20Lina&purpose=Help%20with%3A%20No%20access&problem=My%20access%20expired&request=Renew%20it',
    });
    const dialog = await screen.findByRole('dialog');
    expect(field(dialog, /^Audience/)).toHaveValue('Dr Lina');
    expect(field(dialog, /^Purpose/)).toHaveValue('Help with: No access');
    expect(field(dialog, /^Problem/)).toHaveValue('My access expired');
    expect(field(dialog, /^Request/)).toHaveValue('Renew it');
    expect(communicationApi.createCommunicationMessage).not.toHaveBeenCalled();
  });

  it('builds the message from audience, purpose, request, outcome and benefit in the agreed format', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    const dialog = await openNewMessage();
    await fill(dialog, /^Audience/, 'Dr. Mentor');
    await fill(dialog, /^Purpose/, 'a concept note on maternal health');
    await fill(dialog, /^Request/, 'Reviewing my draft methods.');
    await fill(dialog, /^Expected Outcome/, 'Me submit a stronger proposal.');
    await fill(dialog, /^Benefit to Partner/, 'Co-authorship on the paper.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Generate message' }));

    expect(field(dialog, /^Message/)).toHaveValue([
      'Dear Dr. Mentor,',
      'I am working on a concept note on maternal health.',
      'I would be grateful for your help with reviewing my draft methods.',
      'Your support would help me submit a stronger proposal. I also hope this could be worthwhile for you: co-authorship on the paper.',
      'Would you be open to a short conversation about this? No pressure either way, and thank you for considering it.',
      'Best regards',
    ].join('\n\n'));
  });

  it('opens with the hook, folds in the problem, and closes with the call to action when given', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    const dialog = await openNewMessage();
    await chooseOption(dialog, 'Partner', 'Dr. Mentor');
    await fill(dialog, /^Hook/, 'Your talk on stunting changed how I see the data.');
    await fill(dialog, /^Problem/, 'My sample is too small.');
    await fill(dialog, /^Call to Action/, 'Could we meet for 20 minutes next week?');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Generate message' }));

    // With no audience, the partner's name is used.
    expect(field(dialog, /^Message/)).toHaveValue([
      'Dear Dr. Mentor,',
      'Your talk on stunting changed how I see the data.',
      'Right now, my sample is too small.',
      'Could we meet for 20 minutes next week?',
      'Best regards',
    ].join('\n\n'));
  });

  it('writes constructive feedback in its own tone', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    const dialog = await openNewMessage();
    await fill(dialog, /^Audience/, 'Dara');
    await fill(dialog, /^Hook/, 'Your charts were very clear.');
    await fill(dialog, /^Request/, 'Send the draft two days earlier.');
    await chooseOption(dialog, 'Generate tone', 'Constructive feedback');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Generate message' }));
    expect(field(dialog, /^Message/)).toHaveValue(
      "Dear Dara,\n\nYour charts were very clear.\n\nOne thing I'd love to see change: send the draft two days earlier.\n\nBest regards",
    );
  });

  it('saves a message linked to a partner and dream, with its status and follow-up date', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    const dialog = await openNewMessage();
    await chooseOption(dialog, 'Partner', 'Dr. Mentor');
    await chooseOption(dialog, 'Dream', 'Become a researcher');
    await chooseOption(dialog, 'Status', 'Sent');
    await fill(dialog, /^Subject/, 'Quick question');
    await userEvent.type(within(dialog).getByLabelText(/^Follow Up/), '2026-11-21');
    await fill(dialog, /^Message/, 'Hello');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Save message' }).at(-1)!);

    await waitFor(() => expect(communicationApi.createCommunicationMessage).toHaveBeenCalledWith('token', expect.objectContaining({
      partnerId: 5, relatedDreamId: 1, subject: 'Quick question', status: 'SENT', followUpDate: '2026-11-21', messageBody: 'Hello',
    })));
  });

  it('records a follow-up by editing the status', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    await userEvent.click(within(row('Request for guidance')).getByRole('button', { name: 'Message actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Message' });
    expect(field(dialog, /^Subject/)).toHaveValue('Request for guidance');
    await chooseOption(dialog, 'Status', 'Followed Up');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(communicationApi.updateCommunicationMessage).toHaveBeenCalledWith('token', 1, expect.objectContaining({
      status: 'FOLLOWED_UP', partnerId: 5, relatedDreamId: 1, request: 'Reviewing my draft',
    })));
  });

  it('moves a message on the board by sending the whole message with the new status', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    expect(screen.getByText('Dr. Mentor · Follow up 2026-11-20')).toBeInTheDocument();
    expect(screen.getByText('(No subject)')).toBeInTheDocument();
    expect(screen.getByText('No audience')).toBeInTheDocument();
    const card = screen.getByText('Request for guidance').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Replied' }));
    await waitFor(() => expect(communicationApi.updateCommunicationMessage).toHaveBeenCalledWith('token', 1, expect.objectContaining({
      subject: 'Request for guidance', messageBody: 'Dear Dr. Mentor, ...', status: 'REPLIED',
    })));
  });

  it('shows why a board move failed', async () => {
    vi.mocked(communicationApi.updateCommunicationMessage).mockRejectedValue(new Error('Message was archived'));
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Request for guidance').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Closed' }));
    expect(await screen.findByText('Message was archived')).toBeInTheDocument();
  });

  it('drafts the message with AI from the request, in the current language', async () => {
    vi.mocked(aiDailyApi.draftOutreach).mockResolvedValue({ subject: 'A short request', message: 'Dear Dr. Mentor, would you review my methods?' } as never);
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    const dialog = await openNewMessage();
    await chooseOption(dialog, 'Partner', 'Dr. Mentor');
    await fill(dialog, /^Request/, 'Review my methods');
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Draft with AI' }));

    expect(aiDailyApi.draftOutreach).toHaveBeenCalledWith('token', { partnerId: 5, relatedDreamId: null, purpose: '', request: 'Review my methods' }, 'en');
    await waitFor(() => expect(field(dialog, /^Message/)).toHaveValue('Dear Dr. Mentor, would you review my methods?'));
    expect(field(dialog, /^Subject/)).toHaveValue('A short request');
  });

  it('asks for the request before drafting with AI, and shows why a draft failed', async () => {
    vi.mocked(aiDailyApi.draftOutreach).mockRejectedValue(new Error('AI help is temporarily unavailable.'));
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    const dialog = await openNewMessage();
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Draft with AI' }));
    expect(within(dialog).getByText('Write what you are asking for in Request first.')).toBeInTheDocument();
    expect(aiDailyApi.draftOutreach).not.toHaveBeenCalled();

    await fill(dialog, /^Request/, 'Review my methods');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Draft with AI' }));
    expect(await within(dialog).findByText('AI help is temporarily unavailable.')).toBeInTheDocument();
  });

  it('offers no AI drafting or pitch coach while AI is off (AI-NFR-02)', async () => {
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ reason: 'OFF' } as never);
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    const dialog = await openNewMessage();
    await waitFor(() => expect(aiApi.getAiStatus).toHaveBeenCalled());
    expect(within(dialog).getByRole('button', { name: 'Generate message' })).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Draft with AI' })).not.toBeInTheDocument();
    expect(screen.queryByText('Pitch coach')).not.toBeInTheDocument();
  });

  it('archives a message with a way to undo', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    await userEvent.click(within(row('Request for guidance')).getByRole('button', { name: 'Message actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(communicationApi.archiveCommunicationMessage).toHaveBeenCalledWith('token', 1));
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();
  });

  it('filters on the server by status and search, and says when nothing matches', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    vi.mocked(communicationApi.listCommunicationMessages).mockResolvedValue(emptyPage());
    await userEvent.click(within(screen.getByText('Status', { selector: 'label' })).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Replied' }));
    await waitFor(() => expect(communicationApi.listCommunicationMessages).toHaveBeenLastCalledWith('token', 0, 10, false, 'id,desc', '', { partnerId: '', status: 'REPLIED' }));
    expect(await screen.findByText('No messages match these filters.')).toBeInTheDocument();

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search messages' }), 'mentor');
    await waitFor(() => expect(communicationApi.listCommunicationMessages).toHaveBeenLastCalledWith('token', 0, 10, false, 'id,desc', 'mentor', expect.anything()));
  });

  it('cancels an edit and starts the next message blank', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    await userEvent.click(within(row('Request for guidance')).getByRole('button', { name: 'Message actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const editDialog = await screen.findByRole('dialog', { name: 'Edit Message' });
    await userEvent.click(within(editDialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit Message' })).not.toBeInTheDocument());
    const dialog = await openNewMessage();
    expect(field(dialog, /^Subject/)).toHaveValue('');
    expect(within(dialog).getByRole('combobox', { name: 'Status' })).toHaveTextContent('Draft');
    expect(communicationApi.updateCommunicationMessage).not.toHaveBeenCalled();
  });

  it('archives several messages at once, and restores an archived one', async () => {
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Request for guidance' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select message' }));
    await userEvent.click(screen.getByRole('button', { name: 'Archive selected (2)' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(communicationApi.archiveCommunicationMessage).toHaveBeenCalledTimes(2));

    vi.mocked(communicationApi.listCommunicationMessages).mockResolvedValue(pageOf([{ ...request, archived: true }]));
    vi.mocked(communicationApi.restoreCommunicationMessage).mockResolvedValue(undefined);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show archived' }));
    await waitFor(() => expect(communicationApi.listCommunicationMessages).toHaveBeenLastCalledWith('token', 0, 10, true, 'id,desc', '', expect.anything()));
    await userEvent.click(within(row('Request for guidance')).getByRole('button', { name: 'Message actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    await waitFor(() => expect(communicationApi.restoreCommunicationMessage).toHaveBeenCalledWith('token', 1));
  });

  it('says when there are no messages yet', async () => {
    vi.mocked(communicationApi.listCommunicationMessages).mockResolvedValue(emptyPage());
    renderPage(<CommunicationBuilderPage />);
    expect(await screen.findByText('No messages yet.')).toBeInTheDocument();
  });

  it('shows the builder in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<CommunicationBuilderPage />);
    await screen.findByText('Request for guidance', { selector: 'td, td *' });
    expect(screen.getByRole('button', { name: 'រក្សាទុកសារ' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /ប្រធានបទ/ })).toBeInTheDocument();
  });
});
