import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { listCoachParticipants } from '../api/cohortApi';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import { tr } from '../i18n/translate';
import type { CoachParticipant } from '../types/vision';
import { SafetyAlertsCard } from '../components/common/SafetyAlertsCard';
import { PageSection } from './PageSection';

/** V9 (v0.1 FR-16, FR-17): the participants of the cohorts this coach leads, quietest first. */
export function CoachPage() {
  const { token, user } = useAuth();
  const isCoach = user?.role === 'COACH' || user?.role === 'ADMIN';
  const [participants, setParticipants] = useState<CoachParticipant[] | null>(null);
  const [error, setError] = useState('');
  const [cohortId, setCohortId] = useState('');
  const [atRiskOnly, setAtRiskOnly] = useState(false);

  useEffect(() => {
    if (!token || !isCoach) {
      return;
    }
    listCoachParticipants(token)
      .then(setParticipants)
      .catch((loadError: unknown) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your participants.')));
  }, [token, isCoach]);

  const cohorts = useMemo(() => {
    const byId = new Map((participants ?? []).map((participant) => [participant.cohortId, participant.cohortName]));
    return [...byId.entries()];
  }, [participants]);

  const shown = useMemo(() => (participants ?? [])
    .filter((participant) => !cohortId || String(participant.cohortId) === cohortId)
    .filter((participant) => !atRiskOnly || participant.atRisk)
    .sort((a, b) => b.daysInactive - a.daysInactive), [participants, cohortId, atRiskOnly]);

  if (!isCoach) {
    return (
      <PageSection title={tr('Coaching')} subtitle={tr('Your cohort at a glance')}>
        <Alert severity="info">{tr('Only a coach can open the coach workspace.')}</Alert>
      </PageSection>
    );
  }

  const atRiskCount = (participants ?? []).filter((participant) => participant.atRisk).length;

  return (
    <PageSection title={tr('Coaching')} subtitle={tr('Your cohort at a glance, quietest first')}>
      <Stack spacing={2}>
        <SafetyAlertsCard token={token} />
        {error && <ErrorMessage message={error} />}
        {!participants && !error && <Loading />}
        {participants && participants.length === 0 && (
          <EmptyState>{tr('No participants yet. Ask an administrator to make you the coach of a cohort, then share its join code.')}</EmptyState>
        )}
        {participants && participants.length > 0 && (
          <>
            {atRiskCount > 0 && (
              <Alert severity="warning">{tr('{count} participant(s) have done nothing for 7 days or more.', { count: atRiskCount })}</Alert>
            )}
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
              {cohorts.length > 1 && (
                <Select
                  size="small"
                  displayEmpty
                  value={cohortId}
                  onChange={(event) => setCohortId(event.target.value)}
                  SelectDisplayProps={{ 'aria-label': tr('Cohort') }}
                >
                  <MenuItem value="">{tr('All cohorts')}</MenuItem>
                  {cohorts.map(([id, name]) => <MenuItem key={id} value={String(id)}>{name}</MenuItem>)}
                </Select>
              )}
              <FormControlLabel
                control={<Switch checked={atRiskOnly} onChange={(event) => setAtRiskOnly(event.target.checked)} />}
                label={tr('At risk only')}
              />
            </Box>
            {shown.map((participant) => {
              const met = participant.metrics.metrics.filter((metric) => metric.met).length;
              return (
                <Card key={participant.userId} variant="outlined">
                  <CardContent>
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Box sx={{ minWidth: 0 }}>
                        <Link component={RouterLink} to={`/coach/participants/${participant.userId}`} sx={{ fontWeight: 600 }}>
                          {participant.fullName}
                        </Link>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflowWrap: 'anywhere' }}>
                          {participant.cohortName} · {participant.email}
                        </Typography>
                      </Box>
                      {participant.atRisk && <Chip size="small" color="warning" label={tr('At risk')} />}
                    </Box>
                    <Typography variant="body2" sx={{ mt: 1 }}>
                      {participant.daysInactive === 0
                        ? tr('Active today')
                        : tr('Last active {count} day(s) ago', { count: participant.daysInactive })}
                      {' · '}
                      {tr('{met} of {total} objectives reached', { met, total: participant.metrics.metrics.length })}
                    </Typography>
                  </CardContent>
                </Card>
              );
            })}
            {shown.length === 0 && <EmptyState>{tr('No participants match these filters.')}</EmptyState>}
          </>
        )}
      </Stack>
    </PageSection>
  );
}
