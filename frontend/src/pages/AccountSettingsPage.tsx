import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { deleteAccount, exportAccountData } from '../api/accountApi';
import { Button } from '../components/common/Button';
import { Input } from '../components/common/Input';
import { AiConsentCard } from '../components/common/AiConsentCard';
import { CohortCard } from '../components/common/CohortCard';
import { ReminderSettingsCard } from '../components/common/ReminderSettingsCard';
import { isStepUpCancelled, useStepUp } from '../components/step-up/useStepUp';
import { useAuth } from '../context/AuthContext';
import { localDateIso } from '../utils/localDate';
import { saveBlob } from '../utils/saveBlob';
import { useI18n, type Language } from '../i18n/I18nContext';
import { PageSection } from './PageSection';

const DELETE_WORD = 'DELETE';

/** V20 SV-FR-35: any case, spaces around it ignored (phones type "Delete" or add a space). */
export function isDeleteWord(value: string) {
  return value.trim().toUpperCase() === DELETE_WORD;
}

/** FR-77 and NFR-09: reminders, taking your data with you, and leaving. */
export function AccountSettingsPage() {
  const { t, language, setLanguage } = useI18n();
  const { token, logout, user } = useAuth();
  const navigate = useNavigate();
  const [exportError, setExportError] = useState('');
  const [exporting, setExporting] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmWord, setConfirmWord] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);
  const stepUp = useStepUp(token);

  async function handleExport() {
    if (!token) {
      return;
    }
    setExporting(true);
    setExportError('');
    try {
      // V20 SV-FR-03: everything at once, so "Verify it's you" first.
      saveBlob(await stepUp.protect('EXPORT_DATA', (code) => exportAccountData(token, code)), `vision-mapping-data-${localDateIso()}.json`);
    } catch (error) {
      if (isStepUpCancelled(error)) return;
      setExportError(error instanceof Error ? t(error.message) : t('Unable to export your data.'));
    } finally {
      setExporting(false);
    }
  }

  async function handleDelete(event: FormEvent) {
    event.preventDefault();
    if (!token || !isDeleteWord(confirmWord) || !password) {
      return;
    }
    setDeleting(true);
    setDeleteError('');
    try {
      // V20 SV-FR-02: always a fresh code, even right after another verification.
      await stepUp.protect('DELETE_ACCOUNT', (code) => deleteAccount(token, password, code));
      logout();
    } catch (error) {
      if (isStepUpCancelled(error)) {
        setDeleting(false);
        return;
      }
      setDeleteError(error instanceof Error ? error.message : t('Unable to delete your account.'));
      setDeleting(false);
    }
  }

  return (
    <PageSection title={t('Account')} subtitle={t('Reminders, your data, and your account')}>
      <Stack spacing={2} sx={{ maxWidth: 620 }}>
        <Card>
          <CardHeader title={t('Language')} subheader={t('Choose the language for this device. The page reloads to apply it.')} />
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
            {user?.role === 'ADMIN' && (
              <Box sx={{ mt: 2 }}>
                <Button variant="secondary" onClick={() => navigate('/settings/interface-text')}>{t('Edit interface text')}</Button>
              </Box>
            )}
          </CardContent>
        </Card>

        <CohortCard token={token} />

        <AiConsentCard token={token} />

        <ReminderSettingsCard token={token} />

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
              <Typography variant="caption" color="text.secondary">{t("We'll email you a code to confirm it's you.")}</Typography>
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
              {confirmWord && !isDeleteWord(confirmWord) && (
                <Typography variant="caption" color="text.secondary" role="status">
                  {t('Type {word} to enable the button.', { word: DELETE_WORD })}
                </Typography>
              )}
              {deleteError && <Alert severity="error">{t(deleteError)}</Alert>}
              <Box>
                <Button type="submit" variant="danger" disabled={deleting || !password || !isDeleteWord(confirmWord)}>
                  {deleting ? t('Deleting…') : t('Permanently delete my account')}
                </Button>
              </Box>
              <Typography variant="caption" color="text.secondary">{t("We'll email you a code to confirm it's you.")}</Typography>
            </Box>
          </CardContent>
        </Card>
      </Stack>
      {stepUp.dialog}
    </PageSection>
  );
}
