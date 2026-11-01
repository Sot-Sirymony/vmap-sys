import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { listGoals } from '../api/goalApi';
import { listSteps, updateStep } from '../api/stepApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import { useI18n } from '../i18n/I18nContext';
import type { Goal, VisionStep } from '../types/vision';
import { stepRequest } from '../utils/entityRequests';
import { PageSection } from './PageSection';

/** FR-64: dates older steps that predate the rule, one line each, without opening every step. */
export function UndatedStepsPage() {
  const { t } = useI18n();
  const { token } = useAuth();
  const [steps, setSteps] = useState<VisionStep[] | null>(null);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [dates, setDates] = useState<Record<number, string>>({});
  const [loadError, setLoadError] = useState('');
  const [saveError, setSaveError] = useState('');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setLoadError('');
    try {
      const [nextSteps, nextGoals] = await Promise.all([listSteps(token), listGoals(token)]);
      setSteps(nextSteps.filter((step) => !step.targetDate));
      setGoals(nextGoals);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : t('Unable to load your steps.'));
    }
  }, [token, t]);

  useEffect(() => {
    void load();
  }, [load]);

  async function save(step: VisionStep) {
    if (!token) {
      return;
    }
    setSaveError('');
    try {
      await updateStep(token, step.id, { ...stepRequest(step), targetDate: dates[step.id] });
      await load();
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : t('Unable to save this date.'));
    }
  }

  return (
    <PageSection title={t('Steps with no date')} subtitle={t('Give each step a date so it can appear on your Today page.')}>
      {loadError && <ErrorMessage message={loadError} onRetry={() => void load()} />}
      {!steps && !loadError && <Loading />}
      {steps && steps.length === 0 && <EmptyState>{t('Every step has a date.')}</EmptyState>}
      {steps && steps.length > 0 && (
        <Stack spacing={1.5}>
          {saveError && <Alert severity="error">{saveError}</Alert>}
          {steps.map((step) => (
            <Card key={step.id}>
              <CardContent sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5 }}>
                <Box sx={{ flex: '1 1 200px', minWidth: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{step.title}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {step.code} · {goals.find((goal) => goal.id === step.goalId)?.title}
                  </Typography>
                </Box>
                <Box sx={{ width: 170 }}>
                  <Input
                    type="date"
                    aria-label={t('Date for {title}', { title: step.title })}
                    value={dates[step.id] ?? ''}
                    onChange={(event) => setDates((current) => ({ ...current, [step.id]: event.target.value }))}
                  />
                </Box>
                <Button variant="secondary" disabled={!dates[step.id]} onClick={() => void save(step)}>{t('Save')}</Button>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}
    </PageSection>
  );
}
