import { FormEvent, useCallback, useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { useNavigate } from 'react-router';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import FormControl from '@mui/material/FormControl';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { listAttitudeCheckIns } from '../api/dailyPlanApi';
import { archiveFearEntry, createFearEntry, listFearEntries } from '../api/fearEntryApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import type { AttitudeCheckIn, FearEntry, FearLikelihood } from '../types/vision';
import { fearLikelihoodLabels } from '../utils/enumLabels';
import { localDateIso } from '../utils/localDate';
import { useI18n } from '../i18n/I18nContext';
import { PageSection } from './PageSection';

/**
 * FR-65: the dated logs. Fears are written here and attitude history is read
 * here. Criticism keeps its existing home on the Obstacles page, so it is
 * linked rather than logged twice.
 */
export function JournalPage() {
  const { t } = useI18n();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [fears, setFears] = useState<FearEntry[] | null>(null);
  const [attitudes, setAttitudes] = useState<AttitudeCheckIn[]>([]);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [fear, setFear] = useState('');
  const [worstCase, setWorstCase] = useState('');
  const [likelihood, setLikelihood] = useState<FearLikelihood>('MEDIUM');
  const [firstTest, setFirstTest] = useState('');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setError('');
    try {
      const [nextFears, nextAttitudes] = await Promise.all([listFearEntries(token), listAttitudeCheckIns(token)]);
      setFears(nextFears);
      setAttitudes(nextAttitudes);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t('Unable to load your journal.'));
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  const ready = Boolean(fear.trim() && worstCase.trim() && firstTest.trim());

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token || !ready) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      await createFearEntry(token, { fear: fear.trim(), worstCase: worstCase.trim(), likelihood, firstTest: firstTest.trim() });
      setFear('');
      setWorstCase('');
      setLikelihood('MEDIUM');
      setFirstTest('');
      await load();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t('Unable to save this fear.'));
    } finally {
      setSaving(false);
    }
  }

  async function handleArchive(id: number) {
    if (!token) {
      return;
    }
    try {
      await archiveFearEntry(token, id);
      await load();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : t('Unable to remove this.'));
    }
  }

  return (
    <PageSection
      title={t('Journal')}
      subtitle={t('Private, dated logs of your fears and attitude')}
      actions={<Button variant="secondary" onClick={() => navigate('/obstacles')}>{t('Criticism log')}</Button>}
    >
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}
      {fears === null && !error && <Loading />}
      {fears !== null && (
        <Stack spacing={2}>
          <Card>
            <CardHeader title={t('Write down a fear')} subheader={t('Name it, size it, then pick a small test that would challenge it')} />
            <CardContent>
              <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 1.5 }}>
                <label>
                  {t('What are you afraid of?')}
                  <Input value={fear} onChange={(event) => setFear(event.target.value)} required />
                </label>
                <label>
                  {t('What is the worst that could happen?')}
                  <Textarea value={worstCase} onChange={(event) => setWorstCase(event.target.value)} required />
                </label>
                <label>
                  {t('How likely is that?')}
                  <FormControl fullWidth size="small">
                    <Select
                      SelectDisplayProps={{ 'aria-label': t('How likely is that?') }}
                      value={likelihood}
                      onChange={(event) => setLikelihood(event.target.value as FearLikelihood)}
                    >
                      {(Object.keys(fearLikelihoodLabels) as FearLikelihood[]).map((value) => (
                        <MenuItem value={value} key={value}>{t(fearLikelihoodLabels[value])}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </label>
                <label>
                  {t('What is a low-risk first test?')}
                  <Textarea value={firstTest} onChange={(event) => setFirstTest(event.target.value)} required />
                </label>
                <Box>
                  <Button type="submit" disabled={saving || !ready}>{saving ? t('Saving…') : t('Save fear')}</Button>
                </Box>
              </Box>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title={t('Fear log')} />
            <CardContent>
              {fears.length === 0 ? (
                <EmptyState>{t('No fears written down yet.')}</EmptyState>
              ) : (
                <Stack spacing={2}>
                  {fears.map((entry) => (
                    <Box key={entry.id} sx={{ display: 'flex', gap: 1 }}>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{entry.fear}</Typography>
                        <Typography variant="caption" color="text.secondary" component="div">
                          {t('{date} · {level} likelihood', { date: localDateIso(new Date(entry.createdAt)), level: t(fearLikelihoodLabels[entry.likelihood]) })}
                        </Typography>
                        <Typography variant="body2">{t('Worst case: {text}', { text: entry.worstCase })}</Typography>
                        <Typography variant="body2">{t('First test: {text}', { text: entry.firstTest })}</Typography>
                      </Box>
                      <IconButton size="small" aria-label={t('Remove {name}', { name: entry.fear })} onClick={() => void handleArchive(entry.id)}>
                        <X size={14} />
                      </IconButton>
                    </Box>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader title={t('Attitude history')} subheader={t('Your last 30 check-ins, from the Today page')} />
            <CardContent>
              {attitudes.length === 0 ? (
                <EmptyState>{t('No check-ins yet.')}</EmptyState>
              ) : (
                <Stack spacing={0.5}>
                  {attitudes.map((checkIn) => (
                    <Typography variant="body2" key={checkIn.id}>
                      <strong>{checkIn.checkInDate}</strong> · {t('{score} of 5', { score: checkIn.score })}{checkIn.note ? ` · ${checkIn.note}` : ''}
                    </Typography>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Stack>
      )}
    </PageSection>
  );
}
