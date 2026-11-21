import { type FormEvent, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { getReminderSettings, sendTestReminder, updateReminderSettings } from '../../api/accountApi';
import type { ReminderSettings, ReportReminderCadence } from '../../types/vision';
import { useI18n } from '../../i18n/I18nContext';
import { Button } from './Button';
import { Input } from './Input';
import { Select } from './Select';

/** 1 = Monday to 7 = Sunday, as the server stores them (RM-FR-49). */
const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;

/**
 * FR-77: the daily reminder switch and where it is sent. Shown on the
 * Dashboard and in Account settings, so both read and save the same settings.
 */
export function ReminderSettingsCard({ token }: { token: string | null }) {
  const { t } = useI18n();
  const [reminders, setReminders] = useState<ReminderSettings | null>(null);
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  function show(settings: ReminderSettings) {
    setReminders(settings);
    setEmail(settings.reminderEmail ?? '');
  }

  useEffect(() => {
    if (!token) {
      return;
    }
    getReminderSettings(token)
      .then(show)
      .catch((loadError: unknown) => setError(loadError instanceof Error ? loadError.message : t('Unable to load your reminder settings.')));
  }, [token]);

  async function save(enabled: boolean, reminderEmail: string, savedNotice: string,
    report?: { reportReminder: ReportReminderCadence; reportReminderDay: number | null }) {
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    setNotice('');
    try {
      show(await updateReminderSettings(token, enabled, browserTimeZone(), reminderEmail.trim(), report));
      setNotice(savedNotice);
    } catch (saveError) {
      setError(saveError instanceof Error ? t(saveError.message) : t('Unable to save your reminder settings.'));
    } finally {
      setBusy(false);
    }
  }

  function handleEmailSubmit(event: FormEvent) {
    event.preventDefault();
    if (reminders) {
      void save(reminders.enabled, email, t('Reminder email saved.'));
    }
  }

  async function handleTest() {
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const settings = await sendTestReminder(token);
      setNotice(t('Test email sent to {email}. Check your inbox and spam folder.', { email: settings.sendsTo }));
    } catch (testError) {
      setError(testError instanceof Error ? t(testError.message) : t('Unable to send a test email.'));
    } finally {
      setBusy(false);
    }
  }

  const unsavedEmail = reminders !== null && email.trim() !== (reminders.reminderEmail ?? '');

  /** RM-FR-49: a weekly reminder starts on Friday until another day is chosen. */
  function saveReport(cadence: ReportReminderCadence, day: number | null) {
    if (reminders) {
      void save(reminders.enabled, reminders.reminderEmail ?? '', t('Report reminder saved.'),
        { reportReminder: cadence, reportReminderDay: cadence === 'WEEKLY' ? day ?? 5 : null });
    }
  }

  return (
    <Card>
      <CardHeader
        title={t('Daily reminder')}
        subheader={t('One email a day about due work, your weekly review, or a long quiet spell. Nothing is sent on a day with nothing to say.')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          {error && <Alert severity="error">{error}</Alert>}
          {notice && <Alert severity="success">{notice}</Alert>}
          {reminders && (
            <>
              <FormControlLabel
                control={(
                  <Switch
                    checked={reminders.enabled}
                    disabled={busy}
                    onChange={(event) => void save(event.target.checked, reminders.reminderEmail ?? '', '')}
                  />
                )}
                label={t('Email me a daily reminder')}
              />
              <Box component="form" onSubmit={handleEmailSubmit} sx={{ display: 'grid', gap: 1 }}>
                <label>
                  {t('Send reminders to')}
                  <Input
                    type="email"
                    autoComplete="email"
                    aria-label={t('Send reminders to')}
                    placeholder={reminders.sendsTo}
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                  />
                </label>
                <Typography variant="caption" color="text.secondary">
                  {t('Leave blank to use your login email. Reminders now go to {email}.', { email: reminders.sendsTo })}
                </Typography>
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  <Button type="submit" disabled={busy || !unsavedEmail}>{t('Save email')}</Button>
                  <Button type="button" variant="secondary" disabled={busy || unsavedEmail || !reminders.emailConfigured} onClick={() => void handleTest()}>
                    {t('Send test email')}
                  </Button>
                </Box>
              </Box>
              {reminders.enabled && reminders.timeZone && (
                <Typography variant="body2" color="text.secondary">
                  {t('Sent after {hour}:00, {zone} time.', { hour: reminders.sendHour, zone: reminders.timeZone })}
                </Typography>
              )}
              <Box sx={{ display: 'grid', gap: 1, pt: 1 }}>
                <Typography variant="subtitle2" component="h3">{t('Report reminder')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('A notice on the Dashboard and Reports page when it is time to prepare a report, and an email that day when mail is set up.')}
                </Typography>
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  <Select aria-label={t('Remind me to prepare a report')} disabled={busy} value={reminders.reportReminder ?? 'NONE'}
                    onChange={(event) => saveReport(event.target.value as ReportReminderCadence, reminders.reportReminderDay ?? null)}>
                    <option value="NONE">{t('No report reminder')}</option>
                    <option value="WEEKLY">{t('Weekly')}</option>
                    <option value="MONTHLY">{t('Monthly, on the last working day')}</option>
                  </Select>
                  {reminders.reportReminder === 'WEEKLY' && (
                    <Select aria-label={t('Weekday')} disabled={busy} value={reminders.reportReminderDay ?? 5}
                      onChange={(event) => saveReport('WEEKLY', Number(event.target.value))}>
                      {WEEKDAYS.map((day, index) => <option key={day} value={index + 1}>{t(day)}</option>)}
                    </Select>
                  )}
                </Box>
              </Box>
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
  );
}
