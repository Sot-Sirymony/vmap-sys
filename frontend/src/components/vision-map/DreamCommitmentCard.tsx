import { FormEvent, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import { commitToDream } from '../../api/dreamApi';
import type { Dream } from '../../types/vision';
import { localDateIso } from '../../utils/localDate';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { useI18n } from '../../i18n/I18nContext';

/**
 * FR-69: the signed, dated decision to start this dream. Its passion statement
 * is the dream's own "why it matters", so the two can never drift apart.
 */
export function DreamCommitmentCard({ dream, token, defaultName, onChanged }: {
  dream: Dream;
  token: string;
  defaultName: string;
  onChanged: () => Promise<void>;
}) {
  const { t } = useI18n();
  const [editing, setEditing] = useState(false);
  const [signedName, setSignedName] = useState(dream.commitmentSignedName ?? defaultName);
  const [startDate, setStartDate] = useState(dream.commitmentStartDate ?? localDateIso());
  const [firstAction, setFirstAction] = useState(dream.commitmentFirstAction ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const why = dream.whyImportant?.trim();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await commitToDream(token, dream.id, { signedName: signedName.trim(), startDate, firstAction: firstAction.trim() });
      await onChanged();
      setEditing(false);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t('Unable to save your commitment.'));
    } finally {
      setSaving(false);
    }
  }

  if (dream.committedAt && !editing) {
    return (
      <Card>
        <CardHeader
          title={t('Commitment')}
          subheader={t('Signed by {name} on {date}', { name: dream.commitmentSignedName ?? '', date: localDateIso(new Date(dream.committedAt)) })}
          action={<Button variant="secondary" onClick={() => setEditing(true)}>{t('Update')}</Button>}
        />
        <CardContent>
          {why && <Typography variant="body2">{t('Why: {text}', { text: why })}</Typography>}
          <Typography variant="body2">{t('Starts: {date}', { date: dream.commitmentStartDate ?? '' })}</Typography>
          <Typography variant="body2">{t('First action: {text}', { text: dream.commitmentFirstAction ?? '' })}</Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title={t('Commit to this dream')} subheader={t('Decide when you start and the first thing you will do')} />
      <CardContent>
        {why ? (
          <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 1.5 }}>
            <Typography variant="body2">{t('Why it matters: {text}', { text: why })}</Typography>
            <label>
              {t('First action')}
              <Input value={firstAction} onChange={(event) => setFirstAction(event.target.value)} required />
            </label>
            <label>
              {t('Start date')}
              <Input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} required />
            </label>
            <label>
              {t('Signed by')}
              <Input value={signedName} onChange={(event) => setSignedName(event.target.value)} required />
            </label>
            {error && <Alert severity="error">{error}</Alert>}
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button type="submit" disabled={saving}>{saving ? t('Saving…') : t('Sign commitment')}</Button>
              {editing && <Button variant="secondary" onClick={() => setEditing(false)}>{t('Cancel')}</Button>}
            </Box>
          </Box>
        ) : (
          <Typography variant="body2">{t('Edit this dream and write why it matters to you. Your commitment rests on that reason.')}</Typography>
        )}
      </CardContent>
    </Card>
  );
}
