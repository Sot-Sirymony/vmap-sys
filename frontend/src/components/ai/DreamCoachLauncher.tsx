import { useEffect, useState } from 'react';
import { Link as RouterLink, useLocation } from 'react-router';
import { Bot, X } from 'lucide-react';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import Fab from '@mui/material/Fab';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { deleteAiConversation, getAiStatus } from '../../api/aiApi';
import { useAuth } from '../../context/AuthContext';
import { tr } from '../../i18n/translate';
import type { AiStatus } from '../../types/vision';
import { AiConsentCard } from '../common/AiConsentCard';
import { Button } from '../common/Button';
import { AiStatusNotice } from './AiStatusNotice';
import { DreamCoachChat } from './DreamCoachChat';
import { assistantPageFor, focusFor, quickActionsFor } from './aiFocus';

/**
 * V9 (AI-FR-01, AI-FR-02): the Dream Coach on every screen. The button opens a
 * side panel that knows the page, dream or module on screen and offers three
 * quick questions for it. The conversation carries on as the participant moves
 * between pages. It also opens over a form dialog, so a reply can be copied
 * into the form. Hidden while AI is switched off for everyone.
 */
export function DreamCoachLauncher() {
  const { token } = useAuth();
  const { pathname } = useLocation();
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [open, setOpen] = useState(false);
  const [conversationId, setConversationId] = useState<number | null>(null);

  useEffect(() => {
    if (!token) {
      return;
    }
    getAiStatus(token).then(setStatus).catch(() => setStatus(null));
  }, [token, open]);

  async function handleClear() {
    if (!token || conversationId === null || !window.confirm(tr('Delete this conversation? This cannot be undone.'))) {
      return;
    }
    try {
      await deleteAiConversation(token, conversationId);
    } finally {
      setConversationId(null);
    }
  }

  // V11 Phase 7 (PB-FR-50): the Plan page and Build my plan have the plan assistant instead, so only one assistant is on screen.
  // V15 (AH-FR-11): the same on the five pages that have the page assistant.
  const planAssistantPage = pathname === '/plan' || /^\/dreams\/\d+\/build/.test(pathname) || assistantPageFor(pathname) !== null;
  if (!token || !status || status.reason === 'OFF' || pathname.startsWith('/dream-coach') || planAssistantPage) {
    return null;
  }

  return (
    <>
      <Fab
        color="primary"
        className="dream-coach-fab"
        aria-label={tr('Open the Dream Coach')}
        onClick={() => setOpen(true)}
        // Above dialogs, so the coach can be opened while a form is open: ask, copy a reply, paste it into the form.
        sx={{ position: 'fixed', right: { xs: 16, md: 24 }, bottom: { xs: 88, md: 24 }, zIndex: (theme) => theme.zIndex.modal + 1 }}
      >
        <Bot size={24} />
      </Fab>
      <Drawer
        anchor="right"
        open={open}
        onClose={() => setOpen(false)}
        // Over any open form dialog; while open it takes the keyboard focus, and closing it returns to the form untouched.
        sx={{ zIndex: (theme) => theme.zIndex.modal + 2 }}
        slotProps={{ paper: { sx: { width: { xs: '100%', sm: 420 }, p: 2 }, 'aria-label': tr('Dream Coach') } }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="h6" component="h2">{tr('Dream Coach')}</Typography>
          <IconButton aria-label={tr('Close')} onClick={() => setOpen(false)}><X size={18} /></IconButton>
        </Box>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
          <Button variant="secondary" onClick={() => setConversationId(null)}>{tr('New conversation')}</Button>
          {conversationId !== null && (
            <Button variant="secondary" onClick={() => void handleClear()}>{tr('Clear this chat')}</Button>
          )}
          <Link component={RouterLink} to="/dream-coach" onClick={() => setOpen(false)} variant="body2">{tr('All conversations')}</Link>
        </Box>
        {status.reason === 'NO_CONSENT' && <AiConsentCard token={token} onChange={setStatus} />}
        <AiStatusNotice status={status} />
        <Box sx={{ mt: 1 }}>
          <DreamCoachChat
            token={token}
            status={status}
            onStatusChange={setStatus}
            conversationId={conversationId}
            onConversationChange={setConversationId}
            focus={focusFor(pathname)}
            quickActions={quickActionsFor(pathname)}
            onNewConversation={() => setConversationId(null)}
          />
        </Box>
      </Drawer>
    </>
  );
}
