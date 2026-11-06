import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { draftOutreach } from '../../api/aiDailyApi';
import { tr } from '../../i18n/translate';
import type { OutreachDraft } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';

export type NewContact = { partnerId: number; name: string; role: string };

/**
 * V11 (PB-FR-27): after adding the plan, a message draft for each new "To
 * contact" partner, from the existing outreach helper (AI-FR-17). The
 * participant copies and sends it themselves; the app never sends anything.
 */
export function NewContactsOutreach({ contacts, dreamId, dreamTitle, token, language }: {
  contacts: NewContact[];
  dreamId: number;
  dreamTitle: string;
  token: string;
  language: string;
}) {
  const [drafts, setDrafts] = useState<Record<number, OutreachDraft>>({});
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState('');

  async function draft(contact: NewContact) {
    setBusy(contact.partnerId);
    setError('');
    try {
      const message = await draftOutreach(token, {
        partnerId: contact.partnerId,
        relatedDreamId: dreamId,
        purpose: tr('Help with my plan: {dream}', { dream: dreamTitle }),
        request: tr('Advice or support as a {role}', { role: contact.role || contact.name }),
      }, language);
      setDrafts((current) => ({ ...current, [contact.partnerId]: message }));
    } catch (draftError) {
      setError(draftError instanceof Error ? tr(draftError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(null);
    }
  }

  if (contacts.length === 0) {
    return null;
  }
  return (
    <Stack spacing={1.5}>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr('Reach out to your new contacts')}</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {contacts.map((contact) => {
        const message = drafts[contact.partnerId];
        return (
          <Box key={contact.partnerId} sx={{ display: 'grid', gap: 1 }}>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{contact.name}</Typography>
              {!message && (
                <Button variant="secondary" disabled={busy !== null} onClick={() => void draft(contact)}>{tr('Draft a message')}</Button>
              )}
            </Box>
            {busy === contact.partnerId && <Loading />}
            {message && (
              <>
                <TextField size="small" label={tr('Subject')} value={message.subject ?? ''} slotProps={{ input: { readOnly: true } }} />
                <TextField size="small" label={tr('Message')} value={message.message ?? ''} multiline minRows={4}
                  slotProps={{ input: { readOnly: true } }} />
                <Box>
                  <Button variant="secondary" onClick={() => void navigator.clipboard?.writeText(
                    `${message.subject ?? ''}\n\n${message.message ?? ''}`.trim(),
                  )}>{tr('Copy')}</Button>
                </Box>
              </>
            )}
          </Box>
        );
      })}
      <Typography variant="caption" color="text.secondary">{tr('Copy the message and send it yourself. Nothing is sent from here.')}</Typography>
    </Stack>
  );
}
