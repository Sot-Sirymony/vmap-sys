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
import { getMyCohort, joinCohort, leaveCohort, setJournalSharing } from '../../api/cohortApi';
import { tr } from '../../i18n/translate';
import type { MyCohort } from '../../types/vision';
import { Button } from './Button';
import { Input } from './Input';

/** V9 (D-7, D-8): the participant's cohort, joining with a code, and whether the coach may read journals. */
export function CohortCard({ token }: { token: string | null }) {
  const [cohort, setCohort] = useState<MyCohort | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }
    getMyCohort(token)
      .then(setCohort)
      .catch((loadError: unknown) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your cohort.')));
  }, [token]);

  async function run(action: () => Promise<MyCohort>) {
    setBusy(true);
    setError('');
    try {
      setCohort(await action());
      setCode('');
    } catch (actionError) {
      setError(actionError instanceof Error ? tr(actionError.message) : tr('Unable to save this.'));
    } finally {
      setBusy(false);
    }
  }

  function handleJoin(event: FormEvent) {
    event.preventDefault();
    if (token && code.trim()) {
      void run(() => joinCohort(token, code.trim()));
    }
  }

  return (
    <Card>
      <CardHeader title={tr('Cohort and coach')} subheader={tr('Join a cohort with the code your coach or administrator gave you')} />
      <CardContent>
        <Stack spacing={1.5}>
          {error && <Alert severity="error">{error}</Alert>}
          {cohort && !cohort.cohortId && (
            <Box component="form" onSubmit={handleJoin} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-end' }}>
              <Box sx={{ flex: 1, minWidth: 180 }}>
                <label>
                  {tr('Join code')}
                  <Input aria-label={tr('Join code')} autoComplete="off" value={code} onChange={(event) => setCode(event.target.value)} />
                </label>
              </Box>
              <Button type="submit" disabled={busy || !code.trim()}>{tr('Join')}</Button>
            </Box>
          )}
          {cohort?.cohortId && token && (
            <>
              <Typography variant="body2">
                {cohort.coachName
                  ? tr('You are in {cohort}, coached by {coach}.', { cohort: cohort.cohortName ?? '', coach: cohort.coachName })
                  : tr('You are in {cohort}. No coach is assigned yet.', { cohort: cohort.cohortName ?? '' })}
              </Typography>
              <FormControlLabel
                control={(
                  <Switch
                    checked={cohort.shareJournalsWithCoach}
                    disabled={busy}
                    onChange={(event) => void run(() => setJournalSharing(token, event.target.checked))}
                  />
                )}
                label={tr('Share my fear and attitude journals with my coach')}
              />
              <Typography variant="caption" color="text.secondary">
                {tr('Your coach always sees your dreams, goals, steps, tasks and progress. Journals stay private unless this is on.')}
              </Typography>
              <Box>
                <Button type="button" variant="secondary" disabled={busy} onClick={() => void run(() => leaveCohort(token))}>
                  {tr('Leave cohort')}
                </Button>
              </Box>
            </>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
