import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { savePlanCapacity } from '../../api/aiPlanApi';
import { getReviewRhythm } from '../../api/reviewApi';
import { formatDate, formatMonth } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { PlanCapacity, PlanDraftContent, ReviewRhythmChoice, Weekday } from '../../types/vision';
import { Button } from '../common/Button';
import { type DraftUpdate, defaultRhythm, overloadedWeeks, spreadDates } from './draftModel';

const day = (value: string) => formatDate(new Date(`${value}T00:00:00`), { year: true });

const WEEKDAYS: { id: Weekday; label: string }[] = [
  { id: 'MON', label: 'Monday' }, { id: 'TUE', label: 'Tuesday' }, { id: 'WED', label: 'Wednesday' }, { id: 'THU', label: 'Thursday' },
  { id: 'FRI', label: 'Friday' }, { id: 'SAT', label: 'Saturday' }, { id: 'SUN', label: 'Sunday' },
];

type Props = {
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  dreamId: number;
  dreamDate?: string | null;
  token: string;
  capacity: PlanCapacity | null;
  onCapacityChange: (capacity: PlanCapacity) => void;
  today?: Date;
};

/**
 * V31 v1 step 3 (BRD 31 stage 8, DC-FR-46 to DC-FR-49): the weekly load with
 * Spread the dates (PB-FR-16), a month-by-month preview of the new work, and
 * the review rhythm. The rhythm starts from the dream's saved one, or from the
 * suggestion for the plan's length; it is kept in the draft and saved with the
 * plan. The coach never moves a date by itself.
 */
export function ScheduleStage({ content, onChange, dreamId, dreamDate, token, capacity, onCapacityChange, today = new Date() }: Props) {
  const rhythm = content.reviewRhythm;
  const [loaded, setLoaded] = useState(rhythm != null);

  useEffect(() => {
    if (loaded) {
      return undefined;
    }
    let cancelled = false;
    getReviewRhythm(token, dreamId)
      .then((saved) => {
        if (!cancelled) {
          const start: ReviewRhythmChoice = saved
            ? { daily: saved.daily, weeklyDay: saved.weeklyDay ?? null, monthlyDay: saved.monthlyDay ?? null, quarterly: saved.quarterly }
            : defaultRhythm(content, dreamDate, today);
          onChange((draft) => (draft.reviewRhythm ? draft : { ...draft, reviewRhythm: start }));
          setLoaded(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          onChange((draft) => (draft.reviewRhythm ? draft : { ...draft, reviewRhythm: defaultRhythm(draft, dreamDate, today) }));
          setLoaded(true);
        }
      });
    return () => {
      cancelled = true;
    };
    // The rhythm is filled in once, when the stage first opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loaded, token, dreamId]);

  const setRhythm = (patch: Partial<ReviewRhythmChoice>) =>
    onChange((draft) => ({ ...draft, reviewRhythm: { ...(draft.reviewRhythm ?? defaultRhythm(draft, dreamDate, today)), ...patch } }));

  return (
    <Stack spacing={3}>
      <Box component="section" aria-label={tr('Your week')}>
        <Typography variant="subtitle1" component="h3" sx={{ mb: 1 }}>{tr('Your week')}</Typography>
        <CapacityCheck content={content} onChange={onChange} capacity={capacity} onCapacityChange={onCapacityChange} token={token} />
      </Box>

      <Box component="section" aria-label={tr('When the new work falls')}>
        <Typography variant="subtitle1" component="h3" sx={{ mb: 1 }}>{tr('When the new work falls')}</Typography>
        <MonthPreview content={content} />
      </Box>

      <Box component="section" aria-label={tr('Review rhythm')}>
        <Typography variant="subtitle1" component="h3" sx={{ mb: 0.5 }}>{tr('Review rhythm')}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {tr('How often you will look back at this dream. Due reviews show on the Reviews page, and in your reminder email if reminders are on.')}
        </Typography>
        {rhythm && (
          <Stack spacing={1}>
            <FormControlLabel control={<Checkbox checked={rhythm.daily} onChange={(event) => setRhythm({ daily: event.target.checked })} />}
              label={tr('A two-minute check-in every day')} />
            <TextField select size="small" label={tr('Weekly review on')} value={rhythm.weeklyDay ?? ''} sx={{ maxWidth: 260 }}
              onChange={(event) => setRhythm({ weeklyDay: (event.target.value || null) as Weekday | null })}>
              <MenuItem value="">{tr('No weekly review')}</MenuItem>
              {WEEKDAYS.map((item) => <MenuItem key={item.id} value={item.id}>{tr(item.label)}</MenuItem>)}
            </TextField>
            <TextField select size="small" label={tr('Monthly review on day')} value={rhythm.monthlyDay ?? ''} sx={{ maxWidth: 260 }}
              onChange={(event) => setRhythm({ monthlyDay: event.target.value === '' ? null : Number(event.target.value) })}>
              <MenuItem value="">{tr('No monthly review')}</MenuItem>
              {Array.from({ length: 28 }, (_, index) => index + 1).map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}
            </TextField>
            <FormControlLabel control={<Checkbox checked={rhythm.quarterly} onChange={(event) => setRhythm({ quarterly: event.target.checked })} />}
              label={tr('A review every quarter')} />
            <Box>
              <Button variant="secondary" onClick={() => setRhythm(defaultRhythm(content, dreamDate, today))}>{tr('Use the suggested rhythm')}</Button>
            </Box>
          </Stack>
        )}
      </Box>
    </Stack>
  );
}

/** DC-FR-47: how many new goals, steps and tasks fall due in each month. */
export function monthCounts(content: PlanDraftContent): { month: string; count: number }[] {
  const counts = new Map<string, number>();
  const add = (date?: string | null) => {
    if (date) {
      counts.set(date.slice(0, 7), (counts.get(date.slice(0, 7)) ?? 0) + 1);
    }
  };
  for (const goal of content.goals) {
    if (goal.existingId == null) {
      add(goal.targetDate);
    }
    for (const step of goal.steps) {
      if (step.existingId == null) {
        add(step.targetDate);
      }
      step.tasks.forEach((task) => add(task.dueDate));
    }
  }
  return [...counts.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([month, count]) => ({ month, count }));
}

function MonthPreview({ content }: { content: PlanDraftContent }) {
  const months = monthCounts(content);
  const most = Math.max(1, ...months.map((item) => item.count));
  if (months.length === 0) {
    return <Typography variant="body2" color="text.secondary">{tr('Nothing new has a date yet.')}</Typography>;
  }
  return (
    <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 0.5 }}>
      {months.map((item) => (
        <Box component="li" key={item.month} sx={{ display: 'grid', gridTemplateColumns: '7rem 1fr 3rem', gap: 1, alignItems: 'center' }}>
          <Typography variant="body2">{formatMonth(new Date(`${item.month}-01T00:00:00`))}</Typography>
          <Box sx={{ height: 10, borderRadius: 'var(--style-radius-control)', bgcolor: 'primary.main', width: `${(item.count / most) * 100}%`, minWidth: 6 }} />
          <Typography variant="body2" color="text.secondary">{item.count}</Typography>
        </Box>
      ))}
    </Box>
  );
}

/** PB-FR-16: hours a week, and weeks where the draft adds more work than that. */
export function CapacityCheck({ content, onChange, capacity, onCapacityChange, token }: {
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  capacity: PlanCapacity | null;
  onCapacityChange: (capacity: PlanCapacity) => void;
  token: string;
}) {
  const [hours, setHours] = useState(capacity?.hoursPerWeek != null ? String(capacity.hoursPerWeek) : '');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const planned = capacity?.planned ?? [];
  const perWeek = capacity?.hoursPerWeek ?? null;
  const weeks = overloadedWeeks(content, planned, perWeek);

  async function save() {
    const value = hours.trim() === '' ? null : Number(hours);
    if (value === (perWeek ?? null) || (value != null && (Number.isNaN(value) || value < 0.5 || value > 100))) {
      return;
    }
    setError('');
    try {
      onCapacityChange(await savePlanCapacity(token, value));
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to save your hours.'));
    }
  }

  function spread() {
    if (!perWeek) {
      return;
    }
    const result = spreadDates(content, planned, perWeek);
    onChange(result.content);
    setNotice(result.moved > 0
      ? tr('Moved {count} item(s) a week or more later.', { count: result.moved })
      : tr('Nothing could move without going past its step or goal date. Change the dates or hours yourself.'));
  }

  return (
    <Box sx={{ display: 'grid', gap: 1 }}>
      <TextField size="small" type="number" label={tr('Hours a week you have for your plans')} value={hours} sx={{ maxWidth: 320 }}
        slotProps={{ htmlInput: { min: 0.5, max: 100, step: 0.5 } }} helperText={tr('Optional. Used to flag weeks with too much work.')}
        onChange={(event) => setHours(event.target.value)} onBlur={() => void save()} />
      {error && <Alert severity="error">{error}</Alert>}
      {weeks.length > 0 && (
        <Alert severity="warning" action={<Button variant="secondary" onClick={spread}>{tr('Spread the dates')}</Button>}>
          <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
            {tr('These weeks have more work than your {hours} hours:', { hours: perWeek ?? 0 })}
          </Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {weeks.map((week) => (
              <Typography component="li" variant="body2" key={week.weekStart}>
                {tr('Week of {date}: {total} h ({draft} h new in this draft)', {
                  date: day(week.weekStart), total: Math.round(week.total * 10) / 10, draft: Math.round(week.draft * 10) / 10,
                })}
              </Typography>
            ))}
          </Box>
        </Alert>
      )}
      {notice && <Alert severity="info" onClose={() => setNotice('')}>{notice}</Alert>}
    </Box>
  );
}

