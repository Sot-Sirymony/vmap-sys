import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { deleteAiConversation, deleteAllAiConversations, getAiConversation, getAiStatus, listAiConversations, rateAiMessage, streamAiMessage } from '../api/aiApi';
import type { AiChatTurn, AiStatus } from '../types/vision';
import { DreamCoachPage } from './DreamCoachPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../api/aiApi', () => ({
  getAiStatus: vi.fn(),
  setAiConsent: vi.fn(),
  listAiConversations: vi.fn(),
  getAiConversation: vi.fn(),
  deleteAiConversation: vi.fn(),
  deleteAllAiConversations: vi.fn(),
  streamAiMessage: vi.fn(),
  rateAiMessage: vi.fn(),
}));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 };

const turn: AiChatTurn = {
  conversationId: 7,
  title: 'Open a bakery',
  participantMessage: { id: 1, role: 'USER', content: 'Open a bakery', createdAt: '2026-11-03T08:00:00Z' },
  reply: { id: 2, role: 'ASSISTANT', content: 'What would success look like?', contextSources: ['plan', 'tasks'], createdAt: '2026-11-03T08:00:01Z' },
};

describe('DreamCoachPage (V9 Phases 3 and 4)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAiConversations).mockResolvedValue([]);
  });

  it('says AI is unavailable and keeps the box closed when AI is off', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'OFF' });
    render(<DreamCoachPage />);
    expect(await screen.findByText('AI help is temporarily unavailable. Everything else in the app works as usual.')).toBeInTheDocument();
    expect(screen.getByLabelText('Your message')).toBeDisabled();
  });

  it('asks for consent first when it has not been given', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'NO_CONSENT', consentGiven: false });
    render(<DreamCoachPage />);
    expect(await screen.findByText('Allow AI to use my data')).toBeInTheDocument();
  });

  it('streams the reply, then shows the saved one as an AI suggestion with its reason, and lets it be rated', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    let finish: (value: AiChatTurn) => void = () => undefined;
    vi.mocked(streamAiMessage).mockImplementation((_token, _id, _message, _language, _focus, onText) => {
      onText('What would ');
      return new Promise((resolve) => { finish = resolve; });
    });
    vi.mocked(rateAiMessage).mockResolvedValue({ ...turn.reply, rating: 1 });
    render(<DreamCoachPage />);

    await userEvent.type(await screen.findByLabelText('Your message'), 'Open a bakery');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(await screen.findByText('What would')).toBeInTheDocument();
    expect(streamAiMessage).toHaveBeenCalledWith('token', null, 'Open a bakery', 'en', { page: '/dream-coach' }, expect.any(Function));

    finish(turn);
    expect(await screen.findByText('What would success look like?')).toBeInTheDocument();
    expect(screen.getByText('AI suggestion')).toBeInTheDocument();
    expect(screen.getByText('Based on your plan and your tasks')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Helpful' }));
    expect(rateAiMessage).toHaveBeenCalledWith('token', 2, 1);
    expect(await screen.findByRole('button', { name: 'Helpful', pressed: true })).toBeInTheDocument();
  });

  it('keeps the text when sending fails and shows a crisis reply as an alert', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(streamAiMessage).mockRejectedValueOnce(new Error('AI help is temporarily unavailable.'));
    render(<DreamCoachPage />);
    const box = await screen.findByLabelText('Your message');
    await userEvent.type(box, 'Hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(await screen.findByText('AI help is temporarily unavailable.')).toBeInTheDocument();
    expect(box).toHaveValue('Hello');

    vi.mocked(streamAiMessage).mockResolvedValueOnce({
      ...turn,
      participantMessage: { ...turn.participantMessage, content: 'Hello', safetyFlag: 'CRISIS' },
      reply: { id: 4, role: 'ASSISTANT', content: 'Please call 117', safetyFlag: 'CRISIS', createdAt: '2026-11-03T08:00:01Z' },
    });
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('Please call 117')).toBeInTheDocument();
  });

  it('clears the whole history after confirming, and suggests a fresh start when a chat gets long', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    const long = Array.from({ length: 20 }, (_, index) => ({
      id: index + 1,
      role: index % 2 === 0 ? 'USER' as const : 'ASSISTANT' as const,
      content: `Message ${index + 1}`,
      createdAt: '2026-11-03T08:00:00Z',
    }));
    vi.mocked(listAiConversations).mockResolvedValueOnce([{ id: 9, title: 'Old chat', updatedAt: '2026-11-03T08:00:00Z' }]);
    vi.mocked(getAiConversation).mockResolvedValue({ id: 9, title: 'Old chat', updatedAt: '2026-11-03T08:00:00Z', messages: long });
    vi.mocked(deleteAllAiConversations).mockResolvedValue(undefined);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<DreamCoachPage />);

    await userEvent.click(await screen.findByText('Old chat'));
    expect(await screen.findByText(/This conversation is getting long/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete all conversations' }));
    expect(confirm).toHaveBeenCalled();
    expect(deleteAllAiConversations).toHaveBeenCalledWith('token');
    expect(await screen.findByText('No conversations yet.')).toBeInTheDocument();
    confirm.mockRestore();
  });
  it('deletes one conversation after confirming, and keeps it when the person says no', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(listAiConversations).mockResolvedValueOnce([
      { id: 9, title: 'Old chat', updatedAt: '2026-11-03T08:00:00Z' },
      { id: 10, title: 'Bakery plan', updatedAt: '2026-11-04T08:00:00Z' },
    ]).mockResolvedValue([{ id: 10, title: 'Bakery plan', updatedAt: '2026-11-04T08:00:00Z' }]);
    vi.mocked(deleteAiConversation).mockResolvedValue(undefined);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValue(true);
    render(<DreamCoachPage />);

    const list = await screen.findByRole('list', { name: 'Conversations' });
    await userEvent.click(within(list).getByRole('button', { name: 'Delete conversation Old chat' }));
    expect(deleteAiConversation).not.toHaveBeenCalled();
    await userEvent.click(within(list).getByRole('button', { name: 'Delete conversation Old chat' }));
    expect(deleteAiConversation).toHaveBeenCalledWith('token', 9);
    await vi.waitFor(() => expect(within(list).queryByText('Old chat')).not.toBeInTheDocument());
    expect(within(list).getByText('Bakery plan')).toBeInTheDocument();
    confirm.mockRestore();
  });

  it('shows why a conversation could not be deleted', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(listAiConversations).mockResolvedValue([{ id: 9, title: 'Old chat', updatedAt: '2026-11-03T08:00:00Z' }]);
    vi.mocked(deleteAiConversation).mockRejectedValue(new Error('Conversation not found'));
    vi.mocked(deleteAllAiConversations).mockRejectedValue(new Error('Please try again later'));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<DreamCoachPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Delete conversation Old chat' }));
    expect(await screen.findByText('Conversation not found')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete all conversations' }));
    expect(await screen.findByText('Please try again later')).toBeInTheDocument();
    confirm.mockRestore();
  });

  it('shows why the Dream Coach could not load', async () => {
    vi.mocked(getAiStatus).mockRejectedValue(new Error('Server unavailable'));
    render(<DreamCoachPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });
});
