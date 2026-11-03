import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';
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
import { addCoachComment, getCoachParticipant, ratePitch } from '../api/cohortApi';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { StatusBadge } from '../components/common/StatusBadge';
import { useAuth } from '../context/AuthContext';
import { formatDate } from '../i18n/formatDate';
import { tr } from '../i18n/translate';
import type { CoachCommentTarget, CoachParticipantDetail } from '../types/vision';
import { fearLikelihoodLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

/** V9 (v0.1 FR-17; D-8): one participant, read-only, with the coach's comments. */
export function CoachParticipantPage() {
  const { token } = useAuth();
  const userId = Number(useParams().userId);
  const [detail, setDetail] = useState<CoachParticipantDetail | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    try {
      setDetail(await getCoachParticipant(token, userId));
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load this participant.'));
    }
  }, [token, userId]);

  useEffect(() => {
    void load();
  }, [load]);

  const name = detail?.summary.fullName ?? tr('Participant');

  return (
    <PageSection title={name} subtitle={detail ? `${detail.summary.cohortName} · ${detail.summary.email}` : tr('Read-only view for their coach')}>
      <Breadcrumbs crumbs={[{ label: tr('Coaching'), to: '/coach' }, { label: name }]} />
      {error && <ErrorMessage message={error} />}
      {!detail && !error && <Loading />}
      {detail && (
        <Stack spacing={2}>
          {detail.summary.atRisk && (
            <Alert severity="warning">{tr('No activity for {count} days. A short check-in may help.', { count: detail.summary.daysInactive })}</Alert>
          )}
          <Card>
            <CardHeader title={tr('Your objectives')} />
            <CardContent>
              <Stack spacing={0.5}>
                {detail.summary.metrics.metrics.map((metric) => (
                  <Typography key={metric.id} variant="body2">
                    {metric.met ? '✓ ' : '○ '}{tr(metric.label)}: {tr(metric.value)}
                  </Typography>
                ))}
              </Stack>
            </CardContent>
          </Card>
          <PlanCard detail={detail} />
          <JournalsCard detail={detail} />
          <PitchCard detail={detail} token={token} userId={userId} onRated={() => void load()} />
          <CommentsCard detail={detail} token={token} userId={userId} onAdded={() => void load()} />
        </Stack>
      )}
    </PageSection>
  );
}

function PlanCard({ detail }: { detail: CoachParticipantDetail }) {
  return (
    <Card>
      <CardHeader title={tr('Plan')} subheader={tr('Dreams, goals and steps, read-only')} />
      <CardContent>
        {detail.dreams.length === 0 && <Typography variant="body2">{tr('No dreams yet.')}</Typography>}
        <Stack spacing={2}>
          {detail.dreams.map((dream) => (
            <Box key={dream.id}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{dream.title}</Typography>
                <StatusBadge status={dream.status} />
              </Box>
              {detail.goals.filter((goal) => goal.dreamId === dream.id).map((goal) => (
                <Box key={goal.id} sx={{ pl: 2, mt: 1 }}>
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{goal.title}</Typography>
                    <StatusBadge status={goal.status} />
                  </Box>
                  {detail.steps.filter((step) => step.goalId === goal.id).map((step) => {
                    const tasks = detail.tasks.filter((task) => task.stepId === step.id);
                    return (
                      <Typography key={step.id} variant="body2" sx={{ pl: 2 }}>
                        {step.title} · {step.owner ?? '—'} · {step.targetDate ?? tr('No date')} · {tr(step.status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()))}
                        {tasks.length > 0 ? ` · ${tr('{done} of {total} tasks done', { done: tasks.filter((task) => task.status === 'COMPLETED').length, total: tasks.length })}` : ''}
                      </Typography>
                    );
                  })}
                </Box>
              ))}
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}

function JournalsCard({ detail }: { detail: CoachParticipantDetail }) {
  return (
    <Card>
      <CardHeader title={tr('Journals')} />
      <CardContent>
        {!detail.journalsShared && (
          <Typography variant="body2" color="text.secondary">
            {tr('Journals are private. The participant can choose to share them with you in their Account settings.')}
          </Typography>
        )}
        {detail.journalsShared && (
          <Stack spacing={1.5}>
            <Typography variant="subtitle2">{tr('Fear log')}</Typography>
            {(detail.fearEntries ?? []).length === 0 && <Typography variant="body2">{tr('No fears written down yet.')}</Typography>}
            {(detail.fearEntries ?? []).map((entry) => (
              <Typography key={entry.id} variant="body2">
                {entry.fear} · {tr(fearLikelihoodLabels[entry.likelihood])} · {tr('First test: {text}', { text: entry.firstTest })}
              </Typography>
            ))}
            <Typography variant="subtitle2">{tr('Attitude history')}</Typography>
            {(detail.attitudeCheckIns ?? []).length === 0 && <Typography variant="body2">{tr('No check-ins yet.')}</Typography>}
            {(detail.attitudeCheckIns ?? []).slice(-14).reverse().map((checkIn) => (
              <Typography key={checkIn.id} variant="body2">
                {checkIn.checkInDate} · {tr('{score} of 5', { score: checkIn.score })}{checkIn.note ? ` · ${checkIn.note}` : ''}
              </Typography>
            ))}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

type PitchCardProps = { detail: CoachParticipantDetail; token: string | null; userId: number; onRated: () => void };

/** V9 (v0.1 FR-11): the participant's saved pitch and the coach's rating of it. */
function PitchCard({ detail, token, userId, onRated }: PitchCardProps) {
  const pitch = detail.pitch;
  const [rating, setRating] = useState(pitch?.coachRating ?? 3);
  const [feedback, setFeedback] = useState(pitch?.coachFeedback ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      await ratePitch(token, userId, rating, feedback);
      onRated();
    } catch (rateError) {
      setError(rateError instanceof Error ? tr(rateError.message) : tr('Unable to save this rating.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('Pitch')} subheader={tr('From module 11, Make a Clear Ask')} />
      <CardContent>
        {!pitch && <Typography variant="body2" color="text.secondary">{tr('No pitch saved yet.')}</Typography>}
        {pitch && (
          <Stack spacing={1.5}>
            {pitch.rows.map((row, index) => (
              <Box key={index} sx={{ borderLeft: 3, borderColor: 'divider', pl: 1.5 }}>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{row.pitch}</Typography>
                {row.practice && <Typography variant="caption" color="text.secondary">{tr('Practised: {text}', { text: row.practice })}</Typography>}
                {row.rating && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{tr('Their own rating: {score} of 5', { score: row.rating })}</Typography>}
              </Box>
            ))}
            {pitch.coachRating != null && (
              <Alert severity="info">
                {tr('You rated it {score} of 5', { score: pitch.coachRating })}
                {pitch.coachRatedAt ? ` · ${formatDate(new Date(pitch.coachRatedAt))}` : ''}
              </Alert>
            )}
            <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 1 }}>
              <Select size="small" value={rating} onChange={(event) => setRating(Number(event.target.value))} SelectDisplayProps={{ 'aria-label': tr('Your rating') }}>
                {[1, 2, 3, 4, 5].map((score) => <MenuItem key={score} value={score}>{tr('{score} of 5', { score })}</MenuItem>)}
              </Select>
              <TextField
                multiline
                minRows={2}
                size="small"
                label={tr('Feedback on clarity, benefit and the ask')}
                value={feedback}
                onChange={(event) => setFeedback(event.target.value)}
                slotProps={{ htmlInput: { maxLength: 2000 } }}
              />
              {error && <Alert severity="error">{error}</Alert>}
              <Box><Button type="submit" disabled={busy}>{pitch.coachRating != null ? tr('Update rating') : tr('Save rating')}</Button></Box>
            </Box>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

type CommentsCardProps = { detail: CoachParticipantDetail; token: string | null; userId: number; onAdded: () => void };

function CommentsCard({ detail, token, userId, onAdded }: CommentsCardProps) {
  const [target, setTarget] = useState('GENERAL');
  const [body, setBody] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const targets = useMemo(() => [
    { value: 'GENERAL', label: tr('General') },
    ...detail.dreams.map((dream) => ({ value: `DREAM:${dream.id}`, label: `${tr('Dream')}: ${dream.title}` })),
    ...detail.goals.map((goal) => ({ value: `GOAL:${goal.id}`, label: `${tr('Goal')}: ${goal.title}` })),
    ...detail.steps.map((step) => ({ value: `STEP:${step.id}`, label: `${tr('Step')}: ${step.title}` })),
  ], [detail]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token || !body.trim()) {
      return;
    }
    const [type, id] = target.split(':');
    setBusy(true);
    setError('');
    try {
      await addCoachComment(token, userId, type as CoachCommentTarget, id ? Number(id) : null, body);
      setBody('');
      onAdded();
    } catch (sendError) {
      setError(sendError instanceof Error ? tr(sendError.message) : tr('Unable to send this comment.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('Comments')} subheader={tr('The participant sees these on their Today page')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 1 }}>
            <Select size="small" value={target} onChange={(event) => setTarget(event.target.value)} SelectDisplayProps={{ 'aria-label': tr('About') }}>
              {targets.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
            </Select>
            <TextField
              multiline
              minRows={2}
              size="small"
              value={body}
              onChange={(event) => setBody(event.target.value)}
              slotProps={{ htmlInput: { 'aria-label': tr('Comment'), maxLength: 2000 } }}
            />
            {error && <Alert severity="error">{error}</Alert>}
            <Box><Button type="submit" disabled={busy || !body.trim()}>{tr('Send comment')}</Button></Box>
          </Box>
          {detail.comments.map((comment) => (
            <Box key={comment.id} sx={{ borderLeft: 3, borderColor: 'divider', pl: 1.5 }}>
              <Typography variant="caption" color="text.secondary">
                {formatDate(new Date(comment.createdAt))}{comment.targetTitle ? ` · ${comment.targetTitle}` : ''}
              </Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{comment.body}</Typography>
              <Chip size="small" sx={{ mt: 0.5 }} label={comment.readAt ? tr('Read') : tr('Not read yet')} />
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}
