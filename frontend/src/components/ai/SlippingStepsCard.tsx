import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { listSlippingSteps, rescueStep } from '../../api/aiPlanApi';
import { createStep, listSteps, updateStep } from '../../api/stepApi';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { SlippingStep, StepRescue } from '../../types/vision';
import { localDateIso } from '../../utils/localDate';
import { stepRequest } from '../../utils/entityRequests';
import { Button } from '../common/Button';

/**
 * V9 (AI-FR-11): steps that went overdue again after being given a new date.
 * The list needs no AI; "Get ideas" asks Claude for a smaller step, a new date
 * or a helper, each of which the participant may use or ignore. Hidden when
 * nothing is slipping.
 */
export function SlippingStepsCard({ token }: { token: string | null }) {
  const { user } = useAuth();
  const { language } = useI18n();
  const [steps, setSteps] = useState<SlippingStep[]>([]);
  const [ideas, setIdeas] = useState<Record<number, StepRescue>>({});
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    try {
      setSteps(await listSlippingSteps(token));
    } catch {
      setSteps([]);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!token || steps.length === 0) {
    return null;
  }

  async function act(stepId: number, action: () => Promise<void>) {
    setBusyId(stepId);
    setError('');
    setNotice('');
    try {
      await action();
    } catch (actionError) {
      setError(actionError instanceof Error ? tr(actionError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusyId(null);
    }
  }

  const getIdeas = (step: SlippingStep) => act(step.stepId, async () => {
    const answer = await rescueStep(token, step.stepId, language);
    setIdeas((current) => ({ ...current, [step.stepId]: answer }));
  });

  const applyDate = (step: SlippingStep, date: string) => act(step.stepId, async () => {
    const saved = (await listSteps(token)).find((item) => item.id === step.stepId);
    if (!saved) {
      return;
    }
    await updateStep(token, saved.id, { ...stepRequest(saved), targetDate: date });
    setNotice(tr('New date saved.'));
    await load();
  });

  const addSmallerStep = (step: SlippingStep, title: string, date: string | null | undefined) => act(step.stepId, async () => {
    const soon = new Date();
    soon.setDate(soon.getDate() + 3);
    await createStep(token, {
      goalId: step.goalId,
      title,
      owner: user?.fullName ?? 'Me',
      sequenceNumber: 0,
      complex: false,
      priority: 'HIGH',
      targetDate: date ?? localDateIso(soon),
      status: 'NOT_STARTED',
    });
    setNotice(tr('Smaller step added to your plan.'));
  });

  return (
    <Card sx={{ borderLeft: 4, borderColor: 'warning.main' }}>
      <CardHeader
        title={tr('Steps that keep slipping')}
        subheader={tr('These were given a new date and are overdue again. That is common; a smaller step often helps.')}
      />
      <CardContent>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          {notice && <Alert severity="success">{notice}</Alert>}
          {steps.map((step) => {
            const idea = ideas[step.stepId];
            return (
              <Box key={step.stepId}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{step.title}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {step.goalTitle} · {tr('Due {date}', { date: formatDate(new Date(`${step.targetDate}T00:00:00`)) })}
                  {' · '}{tr('Moved {count} time(s)', { count: step.overdueReschedules })}
                </Typography>
                {!idea && (
                  <Box sx={{ mt: 0.5 }}>
                    <Button variant="secondary" disabled={busyId !== null} onClick={() => void getIdeas(step)}>
                      {busyId === step.stepId ? tr('The Dream Coach is thinking…') : tr('Get ideas')}
                    </Button>
                  </Box>
                )}
                {idea && (
                  <Stack spacing={1} sx={{ mt: 1, pl: 1.5, borderLeft: 2, borderColor: 'divider' }}>
                    <Chip size="small" label={tr('AI suggestion')} sx={{ alignSelf: 'flex-start' }} />
                    {idea.reason && <Typography variant="body2" color="text.secondary">{idea.reason}</Typography>}
                    {idea.smallerStep && (
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                        <Typography variant="body2">{tr('Smaller step: {text}', { text: idea.smallerStep })}</Typography>
                        <Button variant="secondary" disabled={busyId !== null}
                          onClick={() => void addSmallerStep(step, idea.smallerStep as string, idea.newDate)}>
                          {tr('Add as a new step')}
                        </Button>
                      </Box>
                    )}
                    {idea.newDate && (
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                        <Typography variant="body2">
                          {tr('New date: {date}', { date: formatDate(new Date(`${idea.newDate}T00:00:00`), { year: true }) })}
                        </Typography>
                        <Button variant="secondary" disabled={busyId !== null} onClick={() => void applyDate(step, idea.newDate as string)}>
                          {tr('Use this date')}
                        </Button>
                      </Box>
                    )}
                    {idea.helper && (
                      <Typography variant="body2">
                        {tr('Who could help: {text}', { text: idea.helper })}{' '}
                        <Link component={RouterLink} to="/partners">{tr('Partners')}</Link>
                      </Typography>
                    )}
                  </Stack>
                )}
              </Box>
            );
          })}
        </Stack>
      </CardContent>
    </Card>
  );
}
