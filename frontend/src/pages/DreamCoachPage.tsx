import { useCallback, useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { deleteAiConversation, deleteAllAiConversations, getAiStatus, listAiConversations } from '../api/aiApi';
import { DreamCoachChat } from '../components/ai/DreamCoachChat';
import { quickActionsFor } from '../components/ai/aiFocus';
import { AiConsentCard } from '../components/common/AiConsentCard';
import { Button } from '../components/common/Button';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import { formatDate } from '../i18n/formatDate';
import { tr } from '../i18n/translate';
import type { AiConversation, AiStatus } from '../types/vision';
import { AiStatusNotice } from '../components/ai/AiStatusNotice';
import { PageSection } from './PageSection';

/**
 * V9: the Dream Coach with all past conversations (AI-FR-25). The same chat
 * opens from any page through the Dream Coach button (AI-FR-01).
 */
export function DreamCoachPage() {
  const { token } = useAuth();
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [conversations, setConversations] = useState<AiConversation[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [error, setError] = useState('');

  const loadList = useCallback(async () => {
    if (!token) {
      return;
    }
    setConversations(await listAiConversations(token));
  }, [token]);

  useEffect(() => {
    if (!token) {
      return;
    }
    Promise.all([getAiStatus(token), listAiConversations(token)])
      .then(([nextStatus, list]) => {
        setStatus(nextStatus);
        setConversations(list);
      })
      .catch((loadError: unknown) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load the Dream Coach.')));
  }, [token]);

  async function handleDelete(id: number) {
    if (!token || !window.confirm(tr('Delete this conversation? This cannot be undone.'))) {
      return;
    }
    try {
      await deleteAiConversation(token, id);
      if (id === activeId) {
        setActiveId(null);
      }
      await loadList();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? tr(deleteError.message) : tr('Unable to delete this conversation.'));
    }
  }

  async function handleDeleteAll() {
    if (!token || !window.confirm(tr('Delete all your Dream Coach conversations? This cannot be undone. Your plan and journals are not affected.'))) {
      return;
    }
    try {
      await deleteAllAiConversations(token);
      setActiveId(null);
      await loadList();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? tr(deleteError.message) : tr('Unable to delete your conversations.'));
    }
  }

  return (
    <PageSection title={tr('Dream Coach')} subtitle={tr('An AI assistant for your dreams, goals and next steps')}>
      {!status && !error && <Loading />}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {status && token && (
        <Stack spacing={2}>
          {status.reason === 'NO_CONSENT' && <AiConsentCard token={token} onChange={setStatus} />}
          <AiStatusNotice status={status} />
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '260px 1fr' }, alignItems: 'start' }}>
            <Card>
              <CardContent sx={{ p: 1, '&:last-child': { pb: 1 } }}>
                <Button variant="secondary" onClick={() => setActiveId(null)}>
                  <Plus size={14} aria-hidden="true" /> {tr('New conversation')}
                </Button>
                {conversations.length === 0 && (
                  <Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>{tr('No conversations yet.')}</Typography>
                )}
                <List dense aria-label={tr('Conversations')}>
                  {conversations.map((conversation) => (
                    <Box key={conversation.id} sx={{ display: 'flex', alignItems: 'center' }}>
                      <ListItemButton selected={conversation.id === activeId} onClick={() => setActiveId(conversation.id)}>
                        <ListItemText
                          primary={conversation.title}
                          secondary={formatDate(new Date(conversation.updatedAt))}
                          slotProps={{ primary: { noWrap: true } }}
                        />
                      </ListItemButton>
                      <IconButton size="small" aria-label={tr('Delete conversation {title}', { title: conversation.title })} onClick={() => void handleDelete(conversation.id)}>
                        <Trash2 size={14} />
                      </IconButton>
                    </Box>
                  ))}
                </List>
                {conversations.length > 0 && (
                  <Button variant="secondary" onClick={() => void handleDeleteAll()}>
                    <Trash2 size={14} aria-hidden="true" /> {tr('Delete all conversations')}
                  </Button>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardContent>
                <DreamCoachChat
                  token={token}
                  status={status}
                  onStatusChange={setStatus}
                  conversationId={activeId}
                  onConversationChange={setActiveId}
                  onSent={() => void loadList()}
                  focus={{ page: '/dream-coach' }}
                  quickActions={quickActionsFor('/dream-coach')}
                  onNewConversation={() => setActiveId(null)}
                />
              </CardContent>
            </Card>
          </Box>
        </Stack>
      )}
    </PageSection>
  );
}
