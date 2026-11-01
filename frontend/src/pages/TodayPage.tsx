import { FormEvent, useCallback, useEffect, useState } from 'react';
import { Flame, X } from 'lucide-react';
import { Link, useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Checkbox from '@mui/material/Checkbox';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { getDailyPlan, saveAttitudeCheckIn } from '../api/dailyPlanApi';
import { archiveHabit, createHabit, setHabitChecked } from '../api/habitApi';
import { updateStepStatus } from '../api/stepApi';
import { updateTaskStatus } from '../api/taskApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { TintedChip } from '../components/common/TintedChip';
import { useAuth } from '../context/AuthContext';
import { statusColors } from '../theme';
import type { DailyPlan, DailyPlanItem } from '../types/vision';
import { localDateIso } from '../utils/localDate';
import { priorityLabels } from '../utils/enumLabels';
import { useI18n } from '../i18n/I18nContext';
import { PageSection } from './PageSection';

const MAX_HABITS = 3;
const ATTITUDE_SCORES = [1, 2, 3, 4, 5];

/**
 * FR-67: the one page for the day, built to take under five minutes on a phone
 * (NFR-01). Attitude, what is due, and habits all save the moment they change.
 */
export function TodayPage() {
  const { t, language } = useI18n();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [date] = useState(() => localDateIso());
  const [plan, setPlan] = useState<DailyPlan | null>(null);
  const [loadError, setLoadError] = useState('');
  const [actionError, setActionError] = useState('');
  const [attitudeNote, setAttitudeNote] = useState('');
  const [habitName, setHabitName] = useState('');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setLoadError('');
    try {
      const nextPlan = await getDailyPlan(token, date);
      setPlan(nextPlan);
      setAttitudeNote(nextPlan.attitude?.note ?? '');
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : t('Unable to load today.'));
    }
  }, [token, date]);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(action: () => Promise<unknown>) {
    setActionError('');
    try {
      await action();
      await load();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : t('Unable to save this.'));
    }
  }

  function complete(item: DailyPlanItem) {
    if (!token) {
      return;
    }
    void run(() => (item.kind === 'STEP'
      ? updateStepStatus(token, item.id, 'COMPLETED')
      : updateTaskStatus(token, item.id, 'COMPLETED')));
  }

  function saveAttitude(score: number) {
    if (token) {
      void run(() => saveAttitudeCheckIn(token, date, { score, note: attitudeNote.trim() || undefined }));
    }
  }

  function handleAddHabit(event: FormEvent) {
    event.preventDefault();
    if (token && habitName.trim()) {
      void run(async () => {
        await createHabit(token, date, habitName.trim());
        setHabitName('');
      });
    }
  }

  const subtitle = new Date(`${date}T00:00:00`).toLocaleDateString(language === 'km' ? 'km-KH' : undefined, { weekday: 'long', month: 'long', day: 'numeric' });

  return (
    <PageSection title={t('Today')} subtitle={subtitle}>
      {loadError && <ErrorMessage message={loadError} onRetry={() => void load()} />}
      {!plan && !loadError && <Loading />}
      {plan && (
        <Stack spacing={2}>
          {actionError && <Alert severity="error">{actionError}</Alert>}
          {plan.weeklyReviewDue && (
            <Alert severity="info" action={<Button variant="secondary" onClick={() => navigate('/reviews')}>{t('Start review')}</Button>}>
              {plan.lastWeeklyReviewDate
                ? t('Your weekly review is due. The last one was {date}.', { date: plan.lastWeeklyReviewDate })
                : t("Your weekly review is due: steps done, blockers, and next week's priorities.")}
            </Alert>
          )}

          <Card>
            <CardHeader title={t('How is your attitude today?')} subheader={t('1 is low, 5 is high. Tap a number to save.')} />
            <CardContent>
              <Stack spacing={1.5}>
                <ToggleButtonGroup
                  exclusive
                  size="small"
                  aria-label={t('Attitude score')}
                  value={plan.attitude?.score ?? null}
                  onChange={(_event, score: number | null) => {
                    if (score !== null) {
                      saveAttitude(score);
                    }
                  }}
                >
                  {ATTITUDE_SCORES.map((score) => (
                    <ToggleButton value={score} key={score} sx={{ minWidth: 44 }}>{score}</ToggleButton>
                  ))}
                </ToggleButtonGroup>
                <Input
                  value={attitudeNote}
                  onChange={(event) => setAttitudeNote(event.target.value)}
                  onBlur={() => {
                    if (plan.attitude && attitudeNote.trim() !== (plan.attitude.note ?? '')) {
                      saveAttitude(plan.attitude.score);
                    }
                  }}
                  placeholder={t('Optional note')}
                  aria-label={t('Attitude note')}
                />
              </Stack>
            </CardContent>
          </Card>

          <Card>
            <CardHeader title={t('Priorities')} subheader={t('Steps and tasks due today or overdue, most important first')} />
            <CardContent>
              {plan.items.length === 0 ? (
                <EmptyState>{t('Nothing is due today.')}</EmptyState>
              ) : (
                <Stack spacing={1}>
                  {plan.items.map((item) => (
                    <Box key={`${item.kind}-${item.id}`} sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                      <Checkbox
                        size="small"
                        checked={false}
                        onChange={() => complete(item)}
                        slotProps={{ input: { 'aria-label': t('Complete {title}', { title: item.title }) } }}
                      />
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.title}</Typography>
                        <Typography variant="caption" color="text.secondary" component="div">
                          {item.kind === 'STEP' ? t('Step') : t('Task')} {item.code} · {item.owner} ·{' '}
                          <Link to={`/dreams/${item.dreamId}`}>{item.dreamTitle}</Link>
                        </Typography>
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1, mt: 0.5 }}>
                          <PriorityBadge priority={t(priorityLabels[item.priority])} />
                          {item.overdue && <TintedChip label={t('Overdue since {date}', { date: item.dueDate })} hue={statusColors.DECLINED} />}
                        </Box>
                      </Box>
                    </Box>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader title={t('Habits')} subheader={t('Up to {count} disciplines you want to keep every day', { count: MAX_HABITS })} />
            <CardContent>
              <Stack spacing={1}>
                {plan.habits.map((habit) => (
                  <Box key={habit.id} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Checkbox
                      size="small"
                      checked={habit.checkedOnDate}
                      onChange={(event) => {
                        if (token) {
                          void run(() => setHabitChecked(token, habit.id, date, event.target.checked));
                        }
                      }}
                      slotProps={{ input: { 'aria-label': habit.name } }}
                    />
                    <Typography variant="body2" sx={{ flex: 1 }}>{habit.name}</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Flame size={14} aria-hidden />
                      <Typography variant="caption">
                        {t(habit.currentStreak === 1 ? '{count} day' : '{count} days', { count: habit.currentStreak })}
                      </Typography>
                    </Box>
                    <IconButton
                      size="small"
                      aria-label={t('Archive {name}', { name: habit.name })}
                      onClick={() => {
                        if (token) {
                          void run(() => archiveHabit(token, habit.id));
                        }
                      }}
                    >
                      <X size={14} />
                    </IconButton>
                  </Box>
                ))}
                {plan.habits.length === 0 && <EmptyState>{t('No habits yet. Add the first one below.')}</EmptyState>}
                {plan.habits.length < MAX_HABITS && (
                  <Box component="form" onSubmit={handleAddHabit} sx={{ display: 'flex', gap: 1 }}>
                    <Box sx={{ flex: 1 }}>
                      <Input
                        value={habitName}
                        onChange={(event) => setHabitName(event.target.value)}
                        placeholder={t('New habit')}
                        aria-label={t('New habit')}
                      />
                    </Box>
                    <Button type="submit" variant="secondary" disabled={!habitName.trim()}>{t('Add')}</Button>
                  </Box>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Stack>
      )}
    </PageSection>
  );
}
