import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import {
  archiveInnerBlocker,
  changeInnerBlockerStatus,
  listInnerBlockers,
  makeInnerBlockerHabit,
  restoreInnerBlocker,
} from '../../api/innerBlockerApi';
import { tr } from '../../i18n/translate';
import type { InnerBlocker } from '../../types/vision';
import { localDateIso } from '../../utils/localDate';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Loading } from '../common/Loading';
import { InnerBlockerCard } from './InnerBlockerCard';
import { InnerBlockerForm, type InnerBlockerDraft } from './InnerBlockerForm';

/** BR-34-3: at most this many active blockers. */
export const MAX_ACTIVE_BLOCKERS = 10;
/** The habit name limit (FR-68). */
const HABIT_NAME_MAX = 120;

/** D-34-6: the practice as a habit name, cut at a word boundary to fit. */
export function habitNameFrom(practice: string): string {
  const text = practice.trim().replace(/\s+/g, ' ');
  if (text.length <= HABIT_NAME_MAX) {
    return text;
  }
  const cut = text.slice(0, HABIT_NAME_MAX);
  const space = cut.lastIndexOf(' ');
  return (space > 40 ? cut.slice(0, space) : cut).replace(/[\s,.;:]+$/, '');
}

type Filter = 'ACTIVE' | 'RESOLVED' | 'ARCHIVED';

/** §2.1: example ideas shown under the form, never prefilled (D-34-13). */
const EXAMPLE_IDEAS = [
  'Starting many things, finishing few',
  'Waiting until I feel ready',
  'Saying yes to everything except my plan',
];

type Props = {
  token: string;
  /** Stage 3: this dream's active blockers plus those for all dreams, added for this dream (D-34-8). */
  dreamId?: number;
  /** The Assessment page: the dreams the form can link to; also turns on the filters (§2.2). */
  dreams?: { id: number; title: string }[];
  /** D-34-5: a chain's prefill to open the form with. */
  prefill?: InnerBlockerDraft | null;
  onPrefillUsed?: () => void;
  /** Tells the parent how many active blockers are shown (stage 3 summary). */
  onCountChange?: (count: number) => void;
};

type StopAsk = { blocker: InnerBlocker; action: 'RESOLVE' | 'ARCHIVE' };

/**
 * V34 (§2.1, §2.2): the participant's inner blockers with add, edit, make it
 * a daily habit, resolve, archive and restore. Used in Build my plan's Inner
 * blockers stage and on the Assessment page.
 */
export function InnerBlockerList({ token, dreamId, dreams, prefill, onPrefillUsed, onCountChange }: Props) {
  const stageMode = dreamId !== undefined;
  const [filter, setFilter] = useState<Filter>('ACTIVE');
  const [blockers, setBlockers] = useState<InnerBlocker[] | null>(null);
  const [activeCount, setActiveCount] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState<InnerBlockerDraft | null>(null);
  const [habitFor, setHabitFor] = useState<{ id: number; name: string } | null>(null);
  const [stopAsk, setStopAsk] = useState<StopAsk | null>(null);
  const [busy, setBusy] = useState(false);
  const today = localDateIso();

  const load = useCallback(async () => {
    setError('');
    try {
      const shown = await listInnerBlockers(token, {
        status: filter === 'ARCHIVED' ? undefined : filter,
        archived: filter === 'ARCHIVED',
        dreamId,
        includeAllDreams: stageMode,
        date: today,
      });
      setBlockers(shown);
      const active = filter === 'ACTIVE' && !stageMode
        ? shown.length
        : (await listInnerBlockers(token, { status: 'ACTIVE' })).length;
      setActiveCount(active);
      if (stageMode) {
        onCountChange?.(shown.length);
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your inner blockers.'));
    }
  }, [token, filter, dreamId, stageMode, today, onCountChange]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (prefill) {
      setEditing(prefill);
      onPrefillUsed?.();
    }
  }, [prefill, onPrefillUsed]);

  const full = activeCount >= MAX_ACTIVE_BLOCKERS;

  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true);
    setError('');
    try {
      await action();
      setNotice(done);
      await load();
    } catch (actionError) {
      setError(actionError instanceof Error ? tr(actionError.message) : tr('Something went wrong. Please try again.'));
    } finally {
      setBusy(false);
    }
  }

  function resolveOrArchive(blocker: InnerBlocker, action: StopAsk['action']) {
    if (blocker.habitId && !blocker.habitArchived) {
      setStopAsk({ blocker, action });
      return;
    }
    void finish(blocker, action, false);
  }

  function finish(blocker: InnerBlocker, action: StopAsk['action'], stopHabit: boolean) {
    setStopAsk(null);
    return action === 'RESOLVE'
      ? run(() => changeInnerBlockerStatus(token, blocker.id, 'RESOLVED', stopHabit), tr('Marked as no longer holding you back.'))
      : run(() => archiveInnerBlocker(token, blocker.id, stopHabit), tr('Blocker archived.'));
  }

  function actionsFor(blocker: InnerBlocker) {
    if (blocker.archived) {
      return <Button variant="secondary" disabled={busy} onClick={() => void run(() => restoreInnerBlocker(token, blocker.id), tr('Blocker restored.'))}>{tr('Restore')}</Button>;
    }
    const canMakeHabit = blocker.status === 'ACTIVE' && (!blocker.habitId || blocker.habitArchived);
    return (
      <>
        <Button variant="secondary" disabled={busy} onClick={() => setEditing(blocker)}>{tr('Edit')}</Button>
        {canMakeHabit && (
          <Button variant="secondary" disabled={busy} onClick={() => setHabitFor({ id: blocker.id, name: habitNameFrom(blocker.practice) })}>
            {tr('Make it a daily habit')}
          </Button>
        )}
        {blocker.status === 'ACTIVE'
          ? <Button variant="secondary" disabled={busy} onClick={() => resolveOrArchive(blocker, 'RESOLVE')}>{tr('No longer holds me back')}</Button>
          : <Button variant="secondary" disabled={busy}
            onClick={() => void run(() => changeInnerBlockerStatus(token, blocker.id, 'ACTIVE', false), tr('Marked as active again.'))}>
            {tr('Active again')}
          </Button>}
        <Button variant="text" disabled={busy} onClick={() => resolveOrArchive(blocker, 'ARCHIVE')}>{tr('Archive')}</Button>
      </>
    );
  }

  return (
    <Stack spacing={1.5}>
      {!stageMode && (
        <ToggleButtonGroup exclusive size="small" value={filter} aria-label={tr('Show inner blockers')}
          onChange={(_event, value: Filter | null) => value && setFilter(value)} sx={{ flexWrap: 'wrap' }}>
          <ToggleButton value="ACTIVE">{tr('Active')}</ToggleButton>
          <ToggleButton value="RESOLVED">{tr('No longer holds me back')}</ToggleButton>
          <ToggleButton value="ARCHIVED">{tr('Archived')}</ToggleButton>
        </ToggleButtonGroup>
      )}
      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success" role="status" onClose={() => setNotice('')}>{notice}</Alert>}
      {blockers === null && !error && <Loading />}
      {blockers !== null && blockers.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          {filter === 'ACTIVE' ? tr('No inner blockers yet.') : tr('Nothing here.')}
        </Typography>
      )}
      {blockers?.map((blocker) => (
        editing?.id === blocker.id ? (
          <Box key={blocker.id} sx={{ p: 1.5, border: 1, borderColor: 'primary.main', borderRadius: 'var(--style-radius-control)' }}>
            <InnerBlockerForm token={token} initial={editing} dreams={dreams} fixedDreamId={dreamId}
              onSaved={() => { setEditing(null); void run(async () => undefined, tr('Blocker saved.')); }}
              onCancel={() => setEditing(null)} />
          </Box>
        ) : (
          <InnerBlockerCard key={blocker.id} blocker={blocker} showDream={!stageMode || blocker.dreamId === null || blocker.dreamId === undefined}
            actions={actionsFor(blocker)} />
        )
      ))}
      {filter === 'ACTIVE' && (editing && editing.id === undefined ? (
        <Box sx={{ p: 1.5, border: 1, borderColor: 'primary.main', borderRadius: 'var(--style-radius-control)', display: 'grid', gap: 1 }}>
          <InnerBlockerForm token={token} initial={editing} dreams={dreams} fixedDreamId={dreamId}
            onSaved={() => { setEditing(null); void run(async () => undefined, tr('Blocker saved.')); }}
            onCancel={() => setEditing(null)} />
          <Typography variant="caption" color="text.secondary">
            {tr('Ideas: {ideas}', { ideas: EXAMPLE_IDEAS.map((idea) => tr(idea)).join(' · ') })}
          </Typography>
        </Box>
      ) : (
        <Box>
          <Button variant="secondary" disabled={full || busy} onClick={() => setEditing({})}>{tr('Add your own blocker')}</Button>
          {full && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
              {tr('You have 10 active blockers. Resolve or archive one first.')}
            </Typography>
          )}
        </Box>
      ))}

      {habitFor && (
        <Dialog open onClose={() => setHabitFor(null)} maxWidth="xs" fullWidth>
          <DialogTitle>{tr('Make it a daily habit')}</DialogTitle>
          <DialogContent>
            <DialogContentText sx={{ mb: 1 }}>{tr('The habit shows on your Today page so you can tick it each day. You can change the name.')}</DialogContentText>
            <label>
              {tr('Habit name')}
              <Input value={habitFor.name} maxLength={HABIT_NAME_MAX} autoFocus
                onChange={(event) => setHabitFor({ ...habitFor, name: event.target.value })} />
            </label>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button disabled={busy || !habitFor.name.trim()}
              onClick={() => { const target = habitFor; setHabitFor(null); void run(() => makeInnerBlockerHabit(token, target.id, today, target.name.trim()), tr('Added to your daily habits.')); }}>
              {tr('Add habit')}
            </Button>
            <Button variant="secondary" onClick={() => setHabitFor(null)}>{tr('Cancel')}</Button>
          </DialogActions>
        </Dialog>
      )}

      {stopAsk && (
        <Dialog open onClose={() => setStopAsk(null)} maxWidth="xs" fullWidth>
          <DialogTitle>{tr('Also stop the habit?')}</DialogTitle>
          <DialogContent>
            <DialogContentText>
              {tr('"{name}" is a daily habit made from this practice. You can keep it going or stop it too.', { name: stopAsk.blocker.habitName ?? '' })}
            </DialogContentText>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2, flexWrap: 'wrap', gap: 1 }}>
            <Button onClick={() => void finish(stopAsk.blocker, stopAsk.action, false)}>{tr('Keep the habit')}</Button>
            <Button variant="secondary" onClick={() => void finish(stopAsk.blocker, stopAsk.action, true)}>{tr('Stop the habit too')}</Button>
            <Button variant="text" onClick={() => setStopAsk(null)}>{tr('Cancel')}</Button>
          </DialogActions>
        </Dialog>
      )}
    </Stack>
  );
}
