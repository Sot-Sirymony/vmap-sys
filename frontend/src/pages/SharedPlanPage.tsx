import { useEffect, useMemo, useState } from 'react';
import { ChartGantt, List as ListIcon } from 'lucide-react';
import { useParams } from 'react-router';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { getSharedPlan } from '../api/planShareApi';
import { BrandMark } from '../components/common/BrandMark';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { ProgressBar } from '../components/common/ProgressBar';
import { StatusBadge } from '../components/common/StatusBadge';
import { TimelineView } from '../components/vision-map/VisionMapTimeline';
import { formatDate } from '../i18n/formatDate';
import { tr } from '../i18n/translate';
import type { Dream, Goal, SharedPlan, VisionStep } from '../types/vision';
import { buildDreamTimeline, defaultExpanded } from '../utils/timeline';
import { workStatusLabels } from '../utils/enumLabels';

/** A date-only value is a calendar day, so it is read in local time, not UTC. */
function localDay(isoDate: string) {
  return new Date(`${isoDate}T00:00:00`);
}

/**
 * V16 (TL-FR-48): the shared plan as timeline records. A share carries no ids,
 * codes or start dates, so ids are made up here and every start is worked out;
 * nothing beyond what the list already shows is revealed.
 */
function sharedPlanTimeline(plan: SharedPlan) {
  const dream = {
    id: 1, code: '', visionAreaId: 0, title: plan.dreamTitle, targetDate: plan.targetDate ?? undefined, status: 'ACTIVE',
    priority: 'MEDIUM', scheduleOverrun: false, archived: false, commitmentStartDate: null,
  } as unknown as Dream;
  const goals: Goal[] = [];
  const steps: VisionStep[] = [];
  plan.goals.forEach((goal, goalIndex) => {
    const goalId = goalIndex + 1;
    goals.push({
      id: goalId, code: '', dreamId: 1, title: goal.title, targetDate: goal.targetDate ?? undefined, status: goal.status,
      priority: 'MEDIUM', progressPercent: goal.progressPercent, sortOrder: goalIndex, scheduleOverrun: false, archived: false,
    } as unknown as Goal);
    goal.steps.forEach((step, stepIndex) => {
      steps.push({
        id: goalId * 1000 + stepIndex, code: '', goalId, title: step.title, targetDate: step.targetDate ?? undefined, status: step.status,
        sequenceNumber: stepIndex + 1, priority: 'MEDIUM', progressPercent: step.status === 'COMPLETED' ? 100 : 0, owner: '', archived: false,
      } as unknown as VisionStep);
    });
  });
  return buildDreamTimeline(dream, goals, steps, []);
}

/**
 * V9 (v0.1 FR-10): what a partner or mentor sees through a share link, without
 * an account. Read-only: one dream, its goals and steps, and the owner's ask.
 */
export function SharedPlanPage() {
  const shareToken = useParams().token ?? '';
  const [plan, setPlan] = useState<SharedPlan | null>(null);
  const [error, setError] = useState('');
  const [view, setView] = useState<'list' | 'timeline'>('list');
  const timeline = useMemo(() => (plan ? sharedPlanTimeline(plan) : null), [plan]);

  useEffect(() => {
    getSharedPlan(shareToken)
      .then(setPlan)
      .catch(() => setError(tr('This link has ended or was stopped.')));
  }, [shareToken]);

  return (
    <Box component="main" sx={{ minHeight: '100vh', bgcolor: 'background.default', py: 4 }}>
      <Container maxWidth="md">
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
          <BrandMark size={36} variant="color" />
          <Typography variant="h6" component="p">Vision Map</Typography>
        </Box>
        {error && <ErrorMessage message={error} />}
        {!plan && !error && <Loading />}
        {plan && (
          <Stack spacing={2}>
            <Box>
              <Typography variant="overline" color="text.secondary">
                {tr('{name} shared this plan with you', { name: plan.ownerFirstName })}
              </Typography>
              <Typography variant="h4" component="h1">{plan.dreamTitle}</Typography>
              {plan.dreamDescription && <Typography variant="body1" sx={{ mt: 1 }}>{plan.dreamDescription}</Typography>}
            </Box>
            {plan.message && (
              <Card>
                <CardHeader title={tr('How you can help')} subheader={plan.partnerName ?? undefined} />
                <CardContent>
                  <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>{plan.message}</Typography>
                </CardContent>
              </Card>
            )}
            <Card>
              <CardContent>
                <Stack spacing={1}>
                  {plan.successDefinition && (
                    <Typography variant="body2">{tr('Success looks like: {text}', { text: plan.successDefinition })}</Typography>
                  )}
                  {plan.targetDate && (
                    <Typography variant="body2">{tr('Target date: {date}', { date: formatDate(localDay(plan.targetDate)) })}</Typography>
                  )}
                  <Typography variant="body2">{tr('Progress: {percent}%', { percent: Math.round(plan.progressPercent) })}</Typography>
                  <ProgressBar value={plan.progressPercent} />
                </Stack>
              </CardContent>
            </Card>
            <Card>
              <CardHeader
                title={tr('Goals and steps')}
                action={plan.goals.length > 0 && (
                  <ToggleButtonGroup
                    size="small"
                    exclusive
                    value={view}
                    onChange={(_, next: 'list' | 'timeline' | null) => next && setView(next)}
                    aria-label={tr('How to show the plan')}
                  >
                    <ToggleButton value="list"><ListIcon size={16} aria-hidden="true" style={{ marginRight: 6 }} />{tr('List')}</ToggleButton>
                    <ToggleButton value="timeline"><ChartGantt size={16} aria-hidden="true" style={{ marginRight: 6 }} />{tr('Timeline')}</ToggleButton>
                  </ToggleButtonGroup>
                )}
              />
              <CardContent>
                {view === 'timeline' && timeline && (
                  <TimelineView
                    timeline={timeline}
                    label={tr('Timeline of {title}', { title: plan.dreamTitle })}
                    initialExpanded={new Set([...defaultExpanded(timeline.rows), ...timeline.rows.filter((row) => row.kind === 'step').map((row) => row.key)])}
                    onOpen={() => undefined}
                    readOnly
                  />
                )}
                {view === 'list' && plan.goals.length === 0 && <Typography variant="body2">{tr('No goals yet.')}</Typography>}
                {view === 'list' && <Stack spacing={2}>
                  {plan.goals.map((goal, goalIndex) => (
                    <Box key={goalIndex}>
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                        <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600 }}>{goal.title}</Typography>
                        <StatusBadge status={goal.status} />
                        {goal.targetDate && <Typography variant="caption" color="text.secondary">{formatDate(localDay(goal.targetDate))}</Typography>}
                      </Box>
                      <Box sx={{ my: 0.5 }}><ProgressBar value={goal.progressPercent} /></Box>
                      <Box component="ol" sx={{ pl: 3, my: 0.5 }}>
                        {goal.steps.map((step, stepIndex) => (
                          <li key={stepIndex}>
                            <Typography variant="body2">
                              {step.title}
                              {step.targetDate ? ` · ${formatDate(localDay(step.targetDate))}` : ''}
                              {` · ${workStatusLabels[step.status]}`}
                            </Typography>
                          </li>
                        ))}
                      </Box>
                    </Box>
                  ))}
                </Stack>}
              </CardContent>
            </Card>
            {plan.expiresAt && (
              <Typography variant="caption" color="text.secondary">
                {tr('This link ends on {date}.', { date: formatDate(new Date(plan.expiresAt)) })}
              </Typography>
            )}
          </Stack>
        )}
      </Container>
    </Box>
  );
}
