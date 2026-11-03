import { type FormEvent, useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import {
  addCohortMember,
  createCohort,
  listCohortMembers,
  listCohorts,
  newJoinCode,
  removeCohortMember,
  setCohortArchived,
  updateCohort,
  type CohortInput,
} from '../api/cohortApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import { tr } from '../i18n/translate';
import type { Cohort, CohortMember } from '../types/vision';
import { PageSection } from './PageSection';

const EMPTY: CohortInput = { name: '', coachEmail: '', startDate: null };

/** V9 (v0.1 FR-01, FR-20): admins create cohorts, choose coaches, and manage members. */
export function CohortsPage() {
  const { token, user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [cohorts, setCohorts] = useState<Cohort[] | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!token || !isAdmin) {
      return;
    }
    try {
      setCohorts(await listCohorts(token));
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load cohorts.'));
    }
  }, [token, isAdmin]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!isAdmin) {
    return (
      <PageSection title={tr('Cohorts')} subtitle={tr('Groups of participants and their coaches')}>
        <Alert severity="info">{tr('Only an administrator can manage cohorts.')}</Alert>
      </PageSection>
    );
  }

  function replace(updated: Cohort) {
    setCohorts((current) => (current ?? []).map((cohort) => (cohort.id === updated.id ? updated : cohort)));
  }

  return (
    <PageSection title={tr('Cohorts')} subtitle={tr('Groups of participants and their coaches')}>
      <Stack spacing={2}>
        <Card>
          <CardHeader title={tr('New cohort')} subheader={tr('Naming a coach gives that account the coach role. They must register first.')} />
          <CardContent>
            <CohortForm token={token} submitLabel={tr('Create cohort')} onSaved={() => void load()} />
          </CardContent>
        </Card>
        {error && <ErrorMessage message={error} />}
        {!cohorts && !error && <Loading />}
        {cohorts?.map((cohort) => <CohortRow key={cohort.id} token={token} cohort={cohort} onChanged={replace} onReload={() => void load()} />)}
      </Stack>
    </PageSection>
  );
}

type CohortFormProps = { token: string | null; cohort?: Cohort; submitLabel: string; onSaved: (cohort: Cohort) => void };

function CohortForm({ token, cohort, submitLabel, onSaved }: CohortFormProps) {
  const [input, setInput] = useState<CohortInput>(cohort
    ? { name: cohort.name, coachEmail: cohort.coachEmail ?? '', startDate: cohort.startDate ?? null }
    : EMPTY);
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
      const saved = cohort ? await updateCohort(token, cohort.id, input) : await createCohort(token, input);
      if (!cohort) {
        setInput(EMPTY);
      }
      onSaved(saved);
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to save this cohort.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: '2fr 2fr 1fr auto' }, alignItems: 'end' }}>
      <label>
        {tr('Name')}
        <Input aria-label={tr('Cohort name')} value={input.name} onChange={(event) => setInput({ ...input, name: event.target.value })} required />
      </label>
      <label>
        {tr('Coach email')}
        <Input type="email" aria-label={tr('Coach email')} value={input.coachEmail} onChange={(event) => setInput({ ...input, coachEmail: event.target.value })} />
      </label>
      <label>
        {tr('Start date')}
        <Input type="date" aria-label={tr('Start date')} value={input.startDate ?? ''} onChange={(event) => setInput({ ...input, startDate: event.target.value || null })} />
      </label>
      <Button type="submit" disabled={busy || !input.name.trim()}>{submitLabel}</Button>
      {error && <Box sx={{ gridColumn: '1 / -1' }}><Alert severity="error">{error}</Alert></Box>}
    </Box>
  );
}

type CohortRowProps = { token: string | null; cohort: Cohort; onChanged: (cohort: Cohort) => void; onReload: () => void };

function CohortRow({ token, cohort, onChanged, onReload }: CohortRowProps) {
  const [editing, setEditing] = useState(false);
  const [members, setMembers] = useState<CohortMember[] | null>(null);
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (actionError) {
      setError(actionError instanceof Error ? tr(actionError.message) : tr('Unable to save this cohort.'));
    } finally {
      setBusy(false);
    }
  }

  const showMembers = () => run(async () => {
    if (token) {
      setMembers(await listCohortMembers(token, cohort.id));
    }
  });

  function handleAdd(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      if (token && email.trim()) {
        await addCohortMember(token, cohort.id, email.trim());
        setEmail('');
        setMembers(await listCohortMembers(token, cohort.id));
        onReload();
      }
    });
  }

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{cohort.name}</Typography>
              <Typography variant="body2" color="text.secondary">
                {cohort.coachName ? tr('Coach: {name}', { name: cohort.coachName }) : tr('No coach yet')}
                {cohort.startDate ? ` · ${tr('Starts: {date}', { date: cohort.startDate })}` : ''}
                {` · ${tr('{count} member(s)', { count: cohort.memberCount })}`}
              </Typography>
            </Box>
            {cohort.archived && <Chip size="small" label={tr('Archived')} />}
          </Box>
          <FormControlLabel
            control={(
              <Switch
                checked={cohort.aiEnabled}
                disabled={busy}
                onChange={(event) => void run(async () => {
                  if (token) {
                    onChanged(await updateCohort(token, cohort.id, {
                      name: cohort.name, coachEmail: cohort.coachEmail ?? '', startDate: cohort.startDate ?? null, aiEnabled: event.target.checked,
                    }));
                  }
                })}
              />
            )}
            label={cohort.aiEnabled ? tr('AI help is on for this cohort') : tr('AI help is off for this cohort')}
          />
          <Typography variant="body2">
            {tr('Join code')}: <strong style={{ letterSpacing: '0.1em' }}>{cohort.joinCode}</strong>
          </Typography>
          {error && <Alert severity="error">{error}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button type="button" variant="secondary" disabled={busy} onClick={() => setEditing(!editing)}>{tr('Edit')}</Button>
            <Button type="button" variant="secondary" disabled={busy} onClick={() => void (members ? setMembers(null) : showMembers())}>
              {members ? tr('Hide members') : tr('Members')}
            </Button>
            <Button type="button" variant="secondary" disabled={busy} onClick={() => void run(async () => { if (token) onChanged(await newJoinCode(token, cohort.id)); })}>
              {tr('New join code')}
            </Button>
            <Button type="button" variant="secondary" disabled={busy} onClick={() => void run(async () => { if (token) onChanged(await setCohortArchived(token, cohort.id, !cohort.archived)); })}>
              {cohort.archived ? tr('Restore') : tr('Archive')}
            </Button>
          </Box>
          {editing && (
            <CohortForm token={token} cohort={cohort} submitLabel={tr('Save changes')} onSaved={(saved) => { onChanged(saved); setEditing(false); }} />
          )}
          {members && (
            <Stack spacing={1}>
              {members.length === 0 && <Typography variant="body2">{tr('No members yet.')}</Typography>}
              {members.map((member) => (
                <Box key={member.userId} sx={{ display: 'flex', gap: 1, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
                  <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{member.fullName} · {member.email}</Typography>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={busy}
                    aria-label={tr('Remove {name}', { name: member.fullName })}
                    onClick={() => void run(async () => {
                      if (token) {
                        await removeCohortMember(token, cohort.id, member.userId);
                        setMembers(await listCohortMembers(token, cohort.id));
                        onReload();
                      }
                    })}
                  >
                    {tr('Remove')}
                  </Button>
                </Box>
              ))}
              <Box component="form" onSubmit={handleAdd} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-end' }}>
                <Box sx={{ flex: 1, minWidth: 200 }}>
                  <label>
                    {tr('Add by email')}
                    <Input type="email" aria-label={tr('Add by email')} value={email} onChange={(event) => setEmail(event.target.value)} />
                  </label>
                </Box>
                <Button type="submit" disabled={busy || !email.trim()}>{tr('Add')}</Button>
              </Box>
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
