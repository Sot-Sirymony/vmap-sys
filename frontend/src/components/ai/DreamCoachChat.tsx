import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { Copy, ThumbsDown, ThumbsUp } from 'lucide-react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiConversation, getAiStatus, rateAiMessage, streamAiMessage } from '../../api/aiApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { AiChatTurn, AiFocus, AiMessage, AiStatus } from '../../types/vision';
import { Button } from '../common/Button';
import { basedOn } from './aiFocus';

const PENDING_ID = -1;
const STREAMING_ID = -2;
/** The backend sends Claude only the most recent 20 messages; past that, a new chat costs less and loses little. */
export const LONG_CHAT_MESSAGES = 20;

type Props = {
  token: string;
  status: AiStatus;
  onStatusChange: (status: AiStatus) => void;
  conversationId: number | null;
  onConversationChange: (id: number) => void;
  onSent?: (turn: AiChatTurn) => void;
  /** What is on screen. V15: a function is read when a message is sent, so the page assistant sends the form as it is then. */
  focus: AiFocus | (() => AiFocus);
  quickActions: string[];
  /** Starts a fresh conversation; offered when this one gets long. */
  onNewConversation?: () => void;
};

/**
 * V9 (AI-FR-01 to AI-FR-06, AI-NFR-01): one Dream Coach conversation. The
 * reply streams in as it is written; when it is done, the saved text replaces
 * it. Replies are labelled AI suggestions with the records they used, can be
 * copied, and can be rated.
 */
export function DreamCoachChat({
  token, status, onStatusChange, conversationId, onConversationChange, onSent, focus, quickActions, onNewConversation,
}: Props) {
  const { language } = useI18n();
  const [messages, setMessages] = useState<AiMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const endRef = useRef<HTMLDivElement>(null);
  // The conversation this component just created; no need to reload it.
  const createdHere = useRef<number | null>(null);

  useEffect(() => {
    setError('');
    if (conversationId === null) {
      setMessages([]);
      return;
    }
    if (conversationId === createdHere.current) {
      return;
    }
    getAiConversation(token, conversationId)
      .then((conversation) => setMessages(conversation.messages ?? []))
      .catch((loadError: unknown) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to open this conversation.')));
  }, [token, conversationId]);

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [messages]);

  async function send(text: string) {
    const message = text.trim();
    if (!message || sending) {
      return;
    }
    setSending(true);
    setError('');
    setDraft('');
    const now = new Date().toISOString();
    setMessages((current) => [
      ...current,
      { id: PENDING_ID, role: 'USER', content: message, createdAt: now },
      { id: STREAMING_ID, role: 'ASSISTANT', content: '', createdAt: now },
    ]);
    try {
      const turn = await streamAiMessage(token, conversationId, message, language, typeof focus === 'function' ? focus() : focus, (piece) => {
        setMessages((current) => current.map((item) => (item.id === STREAMING_ID ? { ...item, content: item.content + piece } : item)));
      });
      setMessages((current) => [...current.filter((item) => item.id >= 0), turn.participantMessage, turn.reply]);
      if (turn.conversationId !== conversationId) {
        createdHere.current = turn.conversationId;
        onConversationChange(turn.conversationId);
      }
      onSent?.(turn);
      onStatusChange({ ...status, messagesLeftToday: Math.max(0, status.messagesLeftToday - 1) });
    } catch (sendError) {
      setMessages((current) => current.filter((item) => item.id >= 0));
      setDraft(message);
      setError(sendError instanceof Error ? tr(sendError.message) : tr('AI help is temporarily unavailable.'));
      getAiStatus(token).then(onStatusChange).catch(() => undefined);
    } finally {
      setSending(false);
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void send(draft);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void send(draft);
    }
  }

  async function handleRate(message: AiMessage, rating: number) {
    try {
      const saved = await rateAiMessage(token, message.id, message.rating === rating ? 0 : rating);
      setMessages((current) => current.map((item) => (item.id === saved.id ? saved : item)));
    } catch (rateError) {
      setError(rateError instanceof Error ? tr(rateError.message) : tr('Unable to save this.'));
    }
  }

  const canSend = status.available;

  return (
    <Stack spacing={1.5}>
      <Box aria-live="polite" sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        {messages.length === 0 && (
          <Box>
            <Typography variant="body1" sx={{ mb: 1 }}>{tr('What would you like to work on?')}</Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {quickActions.map((action) => (
                <Chip key={action} label={action} onClick={canSend ? () => void send(action) : undefined} disabled={!canSend} />
              ))}
            </Box>
          </Box>
        )}
        {messages.map((message) => (
          <MessageBubble key={message.id} message={message} onRate={(rating) => void handleRate(message, rating)} />
        ))}
        <div ref={endRef} />
      </Box>
      {error && <Alert severity="error">{error}</Alert>}
      {onNewConversation && !sending && messages.filter((item) => item.id > 0).length >= LONG_CHAT_MESSAGES && (
        <Alert
          severity="info"
          action={<Button variant="secondary" onClick={onNewConversation}>{tr('New conversation')}</Button>}
        >
          {tr('This conversation is getting long. The Dream Coach only reads the most recent part, and long chats cost more. A new conversation is a fresh start.')}
        </Alert>
      )}
      {messages.length > 0 && canSend && !sending && (
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
          {quickActions.map((action) => (
            <Chip key={action} size="small" variant="outlined" label={action} onClick={() => void send(action)} />
          ))}
        </Box>
      )}
      <Box component="form" onSubmit={handleSubmit} sx={{ display: 'grid', gap: 1 }}>
        <TextField
          multiline
          minRows={2}
          maxRows={8}
          size="small"
          value={draft}
          disabled={!canSend}
          placeholder={canSend ? tr('Ask the Dream Coach…') : ''}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
          slotProps={{ htmlInput: { 'aria-label': tr('Your message'), maxLength: 4000 } }}
        />
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'space-between' }}>
          <Typography variant="caption" color="text.secondary">
            {canSend ? tr('{count} AI requests left today', { count: status.messagesLeftToday }) : ''}
          </Typography>
          <Button type="submit" disabled={!canSend || sending || !draft.trim()}>{sending ? tr('The Dream Coach is thinking…') : tr('Send')}</Button>
        </Box>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {tr('The Dream Coach can make mistakes. Check important details, and talk to your coach about big decisions. Nothing is saved to your plan unless you do it yourself.')}
      </Typography>
    </Stack>
  );
}

function MessageBubble({ message, onRate }: { message: AiMessage; onRate: (rating: number) => void }) {
  const [copied, setCopied] = useState(false);
  const mine = message.role === 'USER';
  if (!mine && message.safetyFlag === 'CRISIS') {
    return (
      <Alert severity="error" icon={false} sx={{ whiteSpace: 'pre-wrap' }}>
        {message.content}
      </Alert>
    );
  }
  const reason = mine ? null : basedOn(message.contextSources);
  const streaming = message.id === STREAMING_ID;

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Box sx={{ alignSelf: mine ? 'flex-end' : 'flex-start', maxWidth: '88%' }}>
      {!mine && <Chip size="small" label={tr('AI suggestion')} sx={{ mb: 0.5 }} />}
      <Box
        sx={{
          px: 1.5,
          py: 1,
          borderRadius: 2,
          bgcolor: mine ? 'primary.main' : 'action.hover',
          color: mine ? 'primary.contrastText' : 'text.primary',
          whiteSpace: 'pre-wrap',
          overflowWrap: 'anywhere',
        }}
      >
        <Typography variant="body2">{message.content || (streaming ? tr('The Dream Coach is thinking…') : '')}</Typography>
      </Box>
      {!mine && message.id > 0 && (
        <Box sx={{ display: 'flex', gap: 0.5, mt: 0.25, alignItems: 'center', flexWrap: 'wrap' }}>
          <IconButton size="small" aria-label={tr('Helpful')} aria-pressed={message.rating === 1} color={message.rating === 1 ? 'primary' : 'default'} onClick={() => onRate(1)}>
            <ThumbsUp size={14} />
          </IconButton>
          <IconButton size="small" aria-label={tr('Not helpful')} aria-pressed={message.rating === -1} color={message.rating === -1 ? 'primary' : 'default'} onClick={() => onRate(-1)}>
            <ThumbsDown size={14} />
          </IconButton>
          <IconButton size="small" aria-label={copied ? tr('Copied') : tr('Copy')} onClick={() => void handleCopy()}>
            <Copy size={14} />
          </IconButton>
          {reason && <Typography variant="caption" color="text.secondary">{reason}</Typography>}
        </Box>
      )}
    </Box>
  );
}
