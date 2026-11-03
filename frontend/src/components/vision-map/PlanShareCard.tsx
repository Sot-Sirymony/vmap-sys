import { FormEvent, useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { listPartners } from '../../api/partnerApi';
import { createPlanShare, listPlanShares, revokePlanShare, shareLink } from '../../api/planShareApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { Dream, Partner, PlanShare } from '../../types/vision';
import { Button } from '../common/Button';

const EXPIRY_OPTIONS = [30, 90, 365];

/**
 * V9 (v0.1 FR-10): share this dream's plan with a partner or mentor through a
 * read-only link. The link is shown once, because the server keeps only its
 * hash; a lost link is stopped and replaced.
 */
export function PlanShareCard({ dream, token }: { dream: Dream; token: string }) {
  const [shares, setShares] = useState<PlanShare[]>([]);
  const [partners, setPartners] = useState<Partner[]>([]);
  const [open, setOpen] = useState(false);
  const [partnerId, setPartnerId] = useState('');
  const [message, setMessage] = useState('');
  const [expiresInDays, setExpiresInDays] = useState(90);
  const [newLink, setNewLink] = useState('');
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setShares(await listPlanShares(token, dream.id));
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your share links.'));
    }
  }, [token, dream.id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (open && partners.length === 0) {
      listPartners(token, 0, 100).then((page) => setPartners(page.content)).catch(() => setPartners([]));
    }
  }, [open, token, partners.length]);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setCopied(false);
    try {
      const created = await createPlanShare(token, {
        dreamId: dream.id,
        partnerId: partnerId ? Number(partnerId) : null,
        message: message.trim(),
        expiresInDays,
      });
      setNewLink(created.token ? shareLink(created.token) : '');
      setMessage('');
      setPartnerId('');
      setOpen(false);
      await load();
    } catch (createError) {
      setError(createError instanceof Error ? tr(createError.message) : tr('Unable to create the link.'));
    } finally {
      setBusy(false);
    }
  }

  async function handleRevoke(id: number) {
    setError('');
    try {
      await revokePlanShare(token, id);
      await load();
    } catch (revokeError) {
      setError(revokeError instanceof Error ? tr(revokeError.message) : tr('Unable to stop the link.'));
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(newLink);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Card>
      <CardHeader
        title={tr('Share with a partner')}
        subheader={tr('A read-only link to this dream\'s goals and steps. Your journals and other dreams stay private.')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          {error && <Alert severity="error">{error}</Alert>}
          {newLink && (
            <Alert severity="success">
              <Typography variant="body2">{tr('Copy this link now. It is shown only once.')}</Typography>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mt: 1 }}>
                <TextField
                  size="small"
                  value={newLink}
                  sx={{ flex: '1 1 260px' }}
                  slotProps={{ htmlInput: { readOnly: true, 'aria-label': tr('Share link') } }}
                  onFocus={(event) => event.target.select()}
                />
                <Button variant="secondary" onClick={() => void handleCopy()}>{copied ? tr('Copied') : tr('Copy link')}</Button>
              </Box>
            </Alert>
          )}
          {!open && (
            <Box><Button variant="secondary" onClick={() => { setOpen(true); setNewLink(''); }}>{tr('Create a share link')}</Button></Box>
          )}
          {open && (
            <Box component="form" onSubmit={(event) => void handleCreate(event)} sx={{ display: 'grid', gap: 1 }}>
              <Select
                size="small"
                displayEmpty
                value={partnerId}
                onChange={(event) => setPartnerId(event.target.value)}
                SelectDisplayProps={{ 'aria-label': tr('Who is it for?') }}
              >
                <MenuItem value="">{tr('No partner named')}</MenuItem>
                {partners.map((partner) => <MenuItem key={partner.id} value={String(partner.id)}>{partner.name}</MenuItem>)}
              </Select>
              <TextField
                multiline
                minRows={2}
                size="small"
                label={tr('What you are asking them for')}
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                slotProps={{ htmlInput: { maxLength: 1000 } }}
              />
              <Select
                size="small"
                value={expiresInDays}
                onChange={(event) => setExpiresInDays(Number(event.target.value))}
                SelectDisplayProps={{ 'aria-label': tr('Link ends after') }}
              >
                {EXPIRY_OPTIONS.map((days) => (
                  <MenuItem key={days} value={days}>{tr('Ends after {days} days', { days })}</MenuItem>
                ))}
              </Select>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button type="submit" disabled={busy}>{busy ? tr('Saving…') : tr('Create link')}</Button>
                <Button variant="secondary" onClick={() => setOpen(false)}>{tr('Cancel')}</Button>
              </Box>
            </Box>
          )}
          {shares.map((share) => (
            <Box key={share.id} sx={{ borderLeft: 3, borderColor: share.open ? 'primary.main' : 'divider', pl: 1.5 }}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{share.partnerName ?? tr('No partner named')}</Typography>
                <Chip size="small" label={share.open ? tr('Open') : share.revokedAt ? tr('Stopped') : tr('Ended')} />
              </Box>
              <Typography variant="caption" color="text.secondary">
                {tr('Made {date}', { date: formatDate(new Date(share.createdAt)) })}
                {share.expiresAt ? ` · ${tr('Ends {date}', { date: formatDate(new Date(share.expiresAt)) })}` : ''}
                {` · ${tr('Opened {count} times', { count: share.viewCount })}`}
              </Typography>
              {share.message && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{share.message}</Typography>}
              {share.open && (
                <Box sx={{ mt: 0.5 }}><Button variant="secondary" onClick={() => void handleRevoke(share.id)}>{tr('Stop sharing')}</Button></Box>
              )}
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}
