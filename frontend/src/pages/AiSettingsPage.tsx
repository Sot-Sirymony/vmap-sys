import { type FormEvent, useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiSettings, removeAiApiKey, saveAiApiKey, testAiConnection, updateAiSettings } from '../api/aiSettingsApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import { formatDate } from '../i18n/formatDate';
import { tr } from '../i18n/translate';
import type { AiSettings } from '../types/vision';
import { PageSection } from './PageSection';

/**
 * V9 (AI-FR-24): admins set the Claude API key, model and monthly budget
 * without code or a restart. The key is sent once, stored encrypted, and only
 * its last four characters come back.
 */
export function AiSettingsPage() {
  const { token, user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [settings, setSettings] = useState<AiSettings | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!token || !isAdmin) {
      return;
    }
    try {
      setSettings(await getAiSettings(token));
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load AI settings.'));
    }
  }, [token, isAdmin]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!isAdmin) {
    return (
      <PageSection title={tr('AI settings')} subtitle={tr('Claude API key, model and budget')}>
        <Alert severity="info">{tr('Only an administrator can change AI settings.')}</Alert>
      </PageSection>
    );
  }

  return (
    <PageSection title={tr('AI settings')} subtitle={tr('Claude API key, model and budget')}>
      {error && <ErrorMessage message={error} />}
      {!settings && !error && <Loading />}
      {settings && token && (
        <Stack spacing={2}>
          <ApiKeyCard settings={settings} token={token} onChanged={setSettings} />
          <OptionsCard key={`${settings.model}-${settings.enabled}-${settings.monthlyBudgetUsd}-${settings.dailyMessageLimit}-${settings.helpLines}`} settings={settings} token={token} onChanged={setSettings} />
        </Stack>
      )}
    </PageSection>
  );
}

type CardProps = { settings: AiSettings; token: string; onChanged: (next: AiSettings) => void };

function ApiKeyCard({ settings, token, onChanged }: CardProps) {
  const [apiKey, setApiKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function run(action: () => Promise<AiSettings>, fallback: string) {
    setBusy(true);
    setError('');
    try {
      onChanged(await action());
      return true;
    } catch (actionError) {
      setError(actionError instanceof Error ? tr(actionError.message) : tr(fallback));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function handleSave(event: FormEvent) {
    event.preventDefault();
    if (await run(() => saveAiApiKey(token, apiKey.trim()), 'Unable to save the key.')) {
      setApiKey('');
    }
  }

  function handleRemove() {
    if (window.confirm(tr('Remove the saved API key? AI features stop until a new key is added.'))) {
      void run(() => removeAiApiKey(token), 'Unable to remove the key.');
    }
  }

  const sourceLabel = settings.keySource === 'DATABASE'
    ? tr('Saved here')
    : settings.keySource === 'ENVIRONMENT' ? tr('From the server setting ANTHROPIC_API_KEY') : tr('No key yet');

  return (
    <Card>
      <CardHeader
        title={tr('Claude API key')}
        subheader={tr('Stored encrypted on the server. It is never shown again, only its last four characters.')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" color={settings.keySource === 'NONE' ? 'default' : 'primary'} label={sourceLabel} />
            {settings.apiKeyLast4 && <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>sk-ant-…{settings.apiKeyLast4}</Typography>}
            {settings.keySource === 'DATABASE' && settings.apiKeyUpdatedAt && (
              <Typography variant="caption" color="text.secondary">
                {tr('Saved {date}', { date: formatDate(new Date(settings.apiKeyUpdatedAt), { year: true }) })}
                {settings.apiKeyUpdatedBy ? ` · ${settings.apiKeyUpdatedBy}` : ''}
              </Typography>
            )}
          </Box>
          {!settings.keyReadable && (
            <Alert severity="warning">
              {tr('The saved key can no longer be read, probably because the server\'s encryption key changed. Enter it again.')}
            </Alert>
          )}
          <Box component="form" onSubmit={(event) => void handleSave(event)} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
            <TextField
              size="small"
              type="password"
              autoComplete="off"
              placeholder="sk-ant-…"
              value={apiKey}
              onChange={(event) => setApiKey(event.target.value)}
              sx={{ flex: '1 1 280px' }}
              slotProps={{ htmlInput: { 'aria-label': tr('New API key'), maxLength: 300 } }}
            />
            <Button type="submit" disabled={busy || apiKey.trim().length < 20}>
              {settings.keySource === 'DATABASE' ? tr('Replace key') : tr('Save key')}
            </Button>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button variant="secondary" disabled={busy || settings.keySource === 'NONE'} onClick={() => void run(() => testAiConnection(token), 'Unable to test the connection.')}>
              {busy ? tr('Checking…') : tr('Test connection')}
            </Button>
            {settings.keySource === 'DATABASE' && (
              <Button variant="secondary" disabled={busy} onClick={handleRemove}>{tr('Remove key')}</Button>
            )}
          </Box>
          {settings.lastTestAt && (
            <Alert severity={settings.lastTestOk ? 'success' : 'error'}>
              {tr(settings.lastTestMessage ?? '')}
              {` · ${formatDate(new Date(settings.lastTestAt))}`}
            </Alert>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          <Typography variant="caption" color="text.secondary">
            {tr('Create a key in the Claude Console at console.anthropic.com, under API keys. The test only looks up the model, so it costs nothing.')}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function OptionsCard({ settings, token, onChanged }: CardProps) {
  const [enabled, setEnabled] = useState(settings.enabled);
  const [model, setModel] = useState(settings.model);
  const [budget, setBudget] = useState(String(settings.monthlyBudgetUsd));
  const [dailyLimit, setDailyLimit] = useState(String(settings.dailyMessageLimit));
  const [helpLines, setHelpLines] = useState(settings.helpLines);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setSaved(false);
    try {
      onChanged(await updateAiSettings(token, {
        enabled,
        model,
        monthlyBudgetUsd: Number(budget),
        dailyMessageLimit: Number(dailyLimit),
        helpLines: helpLines.trim(),
      }));
      setSaved(true);
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to save AI settings.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('AI features')} subheader={tr('AI stays off for everyone until you turn it on here.')} />
      <CardContent>
        <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 2, maxWidth: 480 }}>
          <FormControlLabel
            control={<Switch checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />}
            label={tr('Turn on AI features')}
          />
          <label>
            {tr('Model')}
            <Select fullWidth size="small" value={model} onChange={(event) => setModel(event.target.value)} SelectDisplayProps={{ 'aria-label': tr('Model') }}>
              {settings.models.map((option) => <MenuItem key={option.id} value={option.id}>{option.label}</MenuItem>)}
            </Select>
          </label>
          <TextField
            size="small"
            type="number"
            label={tr('Monthly budget (US dollars)')}
            helperText={tr('AI pauses for everyone when this is reached.')}
            value={budget}
            onChange={(event) => setBudget(event.target.value)}
            slotProps={{ htmlInput: { min: 0, max: 1000, step: '0.01' } }}
          />
          <Typography variant="body2">
            {tr('Spent this month: ${spent} of ${budget} ({count} requests)', {
              spent: Number(settings.spendThisMonthUsd).toFixed(2),
              budget: Number(settings.monthlyBudgetUsd).toFixed(2),
              count: settings.requestsThisMonth,
            })}
          </Typography>
          {Number(settings.monthlyBudgetUsd) > 0 && Number(settings.spendThisMonthUsd) >= Number(settings.monthlyBudgetUsd) * 0.8 && (
            <Alert severity="warning">{tr('AI spend has passed 80% of this month\'s budget.')}</Alert>
          )}
          <TextField
            size="small"
            type="number"
            label={tr('Dream Coach messages per person per day')}
            helperText={tr('Keeps one person from using the whole budget.')}
            value={dailyLimit}
            onChange={(event) => setDailyLimit(event.target.value)}
            slotProps={{ htmlInput: { min: 1, max: 500 } }}
          />
          <TextField
            multiline
            minRows={3}
            size="small"
            label={tr('Help lines shown in a crisis')}
            helperText={tr('Shown to anyone whose message suggests they may be in crisis. Your safeguarding lead should check these numbers.')}
            value={helpLines}
            onChange={(event) => setHelpLines(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          {error && <Alert severity="error">{error}</Alert>}
          {saved && <Alert severity="success">{tr('Saved.')}</Alert>}
          <Box><Button type="submit" disabled={busy || budget === '' || dailyLimit === '' || !helpLines.trim()}>{busy ? tr('Saving…') : tr('Save settings')}</Button></Box>
        </Box>
      </CardContent>
    </Card>
  );
}
