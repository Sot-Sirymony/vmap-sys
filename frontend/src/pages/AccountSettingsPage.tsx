import { FormEvent, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { deleteAccount, exportAccountData, getReminderSettings, updateReminderSettings } from '../api/accountApi';
import { Button } from '../components/common/Button';
import { Input } from '../components/common/Input';
import { useAuth } from '../context/AuthContext';
import type { ReminderSettings } from '../types/vision';
import { localDateIso } from '../utils/localDate';
import { saveBlob } from '../utils/saveBlob';
import { useI18n, type Language } from '../i18n/I18nContext';
import { PageSection } from './PageSection';

const DELETE_WORD = 'DELETE';

/** FR-77 and NFR-09: reminders, taking your data with you, and leaving. */
export function AccountSettingsPage() {
  const { t, language, setLanguage } = useI18n();
  const { token, logout } = useAuth();
  const [reminders, setReminders] = useState<ReminderSettings | null>(null);
  const [reminderError, setReminderError] = useState('');
  const [exportError, setExportError] = useState('');
  const [exporting, setExporting] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmWord, setConfirmWord] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }
    getReminderSettings(token)
      .then(setReminders)
      .catch((error: unknown) => setReminderError(error instanceof Error ? error.message : t('Unable to load your reminder settings.')));
  }, [token]);

  async function handleReminderToggle(enabled: boolean) {
    if (!token) {
      return;
    }
    setReminderError('');
    try {
      setReminders(await updateReminderSettings(token, enabled, Intl.DateTimeFormat().resolvedOptions().timeZone));
    } catch (error) {
      setReminderError(error instanceof Error ? error.message : t('Unable to save your reminder settings.'));
    }
  }

  async function handleExport() {
    if (!token) {
      return;
    }
    setExporting(true);
    setExportError('');
    try {
      saveBlob(await exportAccountData(token), `vision-mapping-data-${localDateIso()}.json`);
    } catch (error) {
      setExportError(error instanceof Error ? error.message : t('Unable to export your data.'));
    } finally {
      setExporting(false);
    }
  }

  async function handleDelete(event: FormEvent) {
    event.preventDefault();
    if (!token || confirmWord !== DELETE_WORD || !password) {
      return;
    }
    setDeleting(true);
    setDeleteError('');
    try {
      await deleteAccount(token, password);
      logout();
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : t('Unable to delete your account.'));
      setDeleting(false);
    }
  }

  return (
    <PageSection title={t('Account')} subtitle={t('Reminders, your data, and your account')}>
      <Stack spacing={2} sx={{ maxWidth: 620 }}>
        <Card>
          <CardHeader title={t('Language')} subheader={t('Choose the language for this device. Pages not yet translated stay in English.')} />
          <CardContent>
            <ToggleButtonGroup
              exclusive
              size="small"
              aria-label={t('Language')}
              value={language}
              onChange={(_event, next: Language | null) => {
                if (next) {
                  setLanguage(next);
                }
              }}
            >
              <ToggleButton value="en" lang="en">English</ToggleButton>
              <ToggleButton value="km" lang="km">ខ្មែរ</ToggleButton>
            </ToggleButtonGroup>
          </CardContent>
        </Card>

        <Card>
          <CardHeader title={t('Daily reminder')} subheader={t('One email a day about due work, your weekly review, or a long quiet spell. Nothing is sent on a day with nothing to say.')} />
          <CardContent>
            <Stack spacing={1}>
              {reminderError && <Alert severity="error">{reminderError}</Alert>}
              {reminders && (
                <>
                  <FormControlLabel
                    control={<Switch checked={reminders.enabled} onChange={(event) => void handleReminderToggle(event.target.checked)} />}
                    label={t('Email me a daily reminder')}
                  />
                  {reminders.enabled && reminders.timeZone && (
                    <Typography variant="body2" color="text.secondary">
                      {t('Sent after {hour}:00, {zone} time.', { hour: reminders.sendHour, zone: reminders.timeZone })}
                    </Typography>
                  )}
                  {!reminders.emailConfigured && (
                    <Alert severity="warning">
                      {t('This server has no mail server set up, so no reminder emails can be sent yet, even with this turned on.')}
                    </Alert>
                  )}
                </>
              )}
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardHeader title={t('Export your data')} subheader={t('Download everything in your account as one JSON file, including archived records.')} />
          <CardContent>
            <Stack spacing={1}>
              {exportError && <Alert severity="error">{exportError}</Alert>}
              <Box>
                <Button variant="secondary" disabled={exporting} onClick={() => void handleExport()}>
                  {exporting ? t('Preparing…') : t('Download my data')}
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardHeader title={t('Delete your account')} subheader={t('This permanently erases your account and all your data. It cannot be undone, so export your data first.')} />
          <CardContent>
            <Box component="form" onSubmit={(event) => void handleDelete(event)} sx={{ display: 'grid', gap: 1.5 }}>
              <label>
                {t('Your password')}
                <Input type="password" autoComplete="current-password" aria-label={t('Your password')} value={password} onChange={(event) => setPassword(event.target.value)} required />
              </label>
              <label>
                {t('Type {word} to confirm', { word: DELETE_WORD })}
                <Input aria-label={t('Type {word} to confirm', { word: DELETE_WORD })} value={confirmWord} onChange={(event) => setConfirmWord(event.target.value)} required />
              </label>
              {deleteError && <Alert severity="error">{t(deleteError)}</Alert>}
              <Box>
                <Button type="submit" variant="danger" disabled={deleting || !password || confirmWord !== DELETE_WORD}>
                  {deleting ? t('Deleting…') : t('Permanently delete my account')}
                </Button>
              </Box>
            </Box>
          </CardContent>
        </Card>
      </Stack>
    </PageSection>
  );
}
