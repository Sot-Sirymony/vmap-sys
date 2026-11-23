import { useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import Box from '@mui/material/Box';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import MuiButton from '@mui/material/Button';
import Select from '@mui/material/Select';
import Skeleton from '@mui/material/Skeleton';
import { keyframes } from '@mui/material/styles';
import { ArrowDown, ArrowUp, Rocket } from 'lucide-react';
import { getDashboardSummary } from '../api/dashboardApi';
import { updateTaskStatus } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterSelect, optionsFromEntities } from '../components/common/FilterSelect';
import { ReminderSettingsCard } from '../components/common/ReminderSettingsCard';
import { StatusChip } from '../components/common/StatusChip';
import { AttentionPanel } from '../components/dashboard/AttentionPanel';
import { ConfettiBurst, rememberCompletedGoals, shouldCelebrate } from '../components/dashboard/Celebration';
import { ChartCard } from '../components/dashboard/ChartCard';
import { ProgressTrendChart } from '../components/dashboard/charts';
import { EnergyBudgetCard } from '../components/dashboard/EnergyBudgetCard';
import { GettingStarted } from '../components/dashboard/GettingStarted';
import { GratitudeCard } from '../components/dashboard/GratitudeCard';
import { InsightTabs } from '../components/dashboard/InsightTabs';
import { KpiCard } from '../components/dashboard/KpiCard';
import { MobileCollapse } from '../components/dashboard/MobileCollapse';
import { NextUpList, type NextUpTask } from '../components/dashboard/NextUpList';
import { ProgressMetricsCard } from '../components/dashboard/ProgressMetricsCard';
import { ProgressRing } from '../components/dashboard/ProgressRing';
import { RankedProgressList, type RankedProgressItem } from '../components/dashboard/RankedProgressList';
import { StackedStatusBar, WORK_STATUS_ORDER } from '../components/dashboard/StackedStatusBar';
import { cardSurface, tabular } from '../components/dashboard/surface';
import { ZoneHeader } from '../components/dashboard/ZoneHeader';
import { ReportReminderNotice } from '../components/report/ReportReminderNotice';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useReducedMotion } from '../hooks/useReducedMotion';
import { useUrlFilter } from '../hooks/useUrlFilter';
import { formatDate } from '../i18n/formatDate';
import { tr } from '../i18n/translate';
import type { DashboardSummary as DashboardSummaryData, TaskItem, VisionArea } from '../types/vision';
import { formatDelta, formatNumber, formatPercent } from '../utils/format';
import { PageSection } from './PageSection';

type DashboardPeriod = 'month' | 'quarter' | 'year';

/** BRD 25 §8 — zones fade and rise in once, 200ms, staggered; off under reduced motion. */
const fadeRise = keyframes`
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: none; }
`;

const SLOW_LOAD_MS = 10_000;

const PERIOD_OPTIONS = [
  { value: 'month', label: tr('This month') },
  { value: 'quarter', label: tr('This quarter') },
  { value: 'year', label: tr('This year') },
];

function isPeriod(value: string): value is DashboardPeriod {
  return value === 'month' || value === 'quarter' || value === 'year';
}

function isoDay(year: number, month1: number, day: number): string {
  return `${year}-${String(month1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

// Calendar-aligned window for the two time-based figures. All bounds are
// inclusive and computed from the browser's local date.
function periodRange(period: DashboardPeriod): { from: string; to: string } {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth(); // 0-11
  if (period === 'year') {
    return { from: isoDay(year, 1, 1), to: isoDay(year, 12, 31) };
  }
  if (period === 'quarter') {
    const startMonth = Math.floor(month / 3) * 3; // 0, 3, 6, 9
    const lastDay = new Date(year, startMonth + 3, 0).getDate();
    return { from: isoDay(year, startMonth + 1, 1), to: isoDay(year, startMonth + 3, lastDay) };
  }
  const lastDay = new Date(year, month + 1, 0).getDate();
  return { from: isoDay(year, month + 1, 1), to: isoDay(year, month + 1, lastDay) };
}

/** "Good morning, Sokha" by the user's own clock (BRD 25 §3 header). */
function greeting(fullName: string | undefined, now: Date = new Date()): string {
  const name = fullName?.trim().split(/\s+/)[0];
  const hour = now.getHours();
  let text = tr('Good evening');
  if (hour < 12) {
    text = tr('Good morning');
  } else if (hour < 18) {
    text = tr('Good afternoon');
  }
  return name ? `${text}, ${name}` : text;
}

function toNextUp(task: TaskItem): NextUpTask {
  return {
    id: task.id,
    title: task.title,
    context: task.code,
    priority: task.priority,
    dueDate: task.dueDate,
    status: task.status,
    blockerReason: task.blockerReason,
  };
}

/**
 * BRD 25 — the redesigned Dashboard. Three zones in a fixed reading order
 * (D-25-2): Today (what to do next, how am I doing), Plan health (areas,
 * goals and tasks), Insights (trend, obstacles, partners, reviews, and the
 * account cards). One request feeds it all, as before.
 */
export function DashboardPage() {
  const { token, user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [summary, setSummary] = useState<DashboardSummaryData | null>(null);
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [slow, setSlow] = useState(false);
  const [celebration, setCelebration] = useState(0);
  // In the URL, so a scoped dashboard can be bookmarked and shared (DA-FR-16).
  const [filterVisionAreaId, setFilterVisionAreaId] = useUrlFilter('visionAreaId');
  const [periodValue, setPeriodValue] = useUrlFilter('period');
  const period = isPeriod(periodValue) ? periodValue : 'month';
  const periodLabel = PERIOD_OPTIONS.find((option) => option.value === period)?.label.toLowerCase() ?? tr('this month');
  const { from, to } = periodRange(period);
  // Drill-down links carry the dashboard's vision-area scope, so the target page
  // shows the same rows the widget counted.
  const scopeSuffix = filterVisionAreaId ? `&visionAreaId=${filterVisionAreaId}` : '';
  const scoped = (base: string) => (filterVisionAreaId ? `${base}${base.includes('?') ? '&' : '?'}visionAreaId=${filterVisionAreaId}` : base);

  function reloadSummary(showSpinner: boolean) {
    if (!token) {
      return;
    }
    if (showSpinner) {
      setLoading(true);
    }
    getDashboardSummary(token, filterVisionAreaId, from, to)
      .then((summaryData) => {
        setSummary(summaryData);
        setError('');
      })
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : tr('Unable to load dashboard.')))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    reloadSummary(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, filterVisionAreaId, from, to]);

  // The dropdown always offers every area, even while the dashboard is scoped.
  useEffect(() => {
    if (!token) {
      return;
    }
    void listVisionAreas(token).then(setVisionAreas).catch(() => setVisionAreas([]));
  }, [token]);

  // BRD 25 §8: after 10 s of loading, say so calmly instead of leaving skeletons unexplained.
  useEffect(() => {
    if (!loading) {
      setSlow(false);
      return undefined;
    }
    const timer = window.setTimeout(() => setSlow(true), SLOW_LOAD_MS);
    return () => window.clearTimeout(timer);
  }, [loading]);

  // DA-FR-14: celebrate once when more goals are complete than at the last visit.
  // Only for the whole plan; a scoped view counts a different set of goals.
  useEffect(() => {
    if (!summary || filterVisionAreaId) {
      return;
    }
    const completed = summary.goalsByStatus?.COMPLETED ?? 0;
    const previous = rememberCompletedGoals(String(user?.id ?? 'me'), completed);
    if (shouldCelebrate(previous, completed)) {
      setCelebration((count) => count + 1);
      showToast(tr('A goal is complete. One step closer to your dream.'));
    }
    // showToast is stable; the summary is what changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [summary, filterVisionAreaId, user?.id]);

  async function completeTask(task: NextUpTask) {
    if (!token) {
      return;
    }
    try {
      await updateTaskStatus(token, Number(task.id), 'COMPLETED');
      showToast(tr('Task completed: {title}', { title: task.title }));
      reloadSummary(false);
    } catch (completeError) {
      showToast(completeError instanceof Error ? completeError.message : tr('Could not complete the task.'));
    }
  }

  const hasArea = (summary?.totalVisionAreas ?? 0) > 0;
  const hasDream = (summary?.activeDreams ?? 0) > 0;
  const hasGoal = (summary?.activeGoals ?? 0) > 0;
  const isFirstRun = !loading && !error && summary !== null && !hasArea;
  const showOnboardingBanner = !loading && !error && summary !== null && hasArea && (!hasDream || !hasGoal);
  const cardState = loading && !summary ? 'loading' : 'ready';

  return (
    <PageSection
      title={greeting(user?.fullName)}
      subtitle={`${formatDate(new Date(), { year: true })} · ${tr('{n} tasks due this week', { n: formatNumber(summary?.tasksDueThisWeek ?? 0) })}`}
      actions={
        <>
          <FilterSelect
            label={tr('Vision Area')}
            value={filterVisionAreaId}
            onChange={setFilterVisionAreaId}
            options={optionsFromEntities(visionAreas, (area) => area.name)}
            allLabel={tr('All areas')}
          />
          <label>
            {tr('Period')}
            <FormControl size="small" sx={{ minWidth: 140 }}>
              <Select SelectDisplayProps={{ 'aria-label': 'Period' }} value={period} onChange={(event) => setPeriodValue(event.target.value)}>
                {PERIOD_OPTIONS.map((option) => (
                  <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </label>
          <MuiButton variant="contained" onClick={() => navigate('/tasks?new=true')} sx={{ whiteSpace: 'nowrap' }}>
            {tr('New Task')}
          </MuiButton>
        </>
      }
    >
      {error && (
        <Box sx={{ display: 'grid', gap: 1.5, justifyItems: 'start' }}>
          <ErrorMessage message={error} />
          <MuiButton variant="outlined" size="small" onClick={() => reloadSummary(true)}>{tr('Try again')}</MuiButton>
        </Box>
      )}
      {slow && (
        <Box role="status" sx={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>{tr('Still loading…')}</Box>
      )}
      {isFirstRun && <GettingStarted hasArea={hasArea} hasDream={hasDream} hasGoal={hasGoal} />}
      {!isFirstRun && !error && (
        <DashboardZones
          summary={summary}
          cardState={cardState}
          visionAreas={visionAreas}
          periodLabel={periodLabel}
          dueLink={`/tasks?dueFrom=${from}&dueTo=${to}${scopeSuffix}`}
          scopeSuffix={scopeSuffix}
          scoped={scoped}
          onComplete={completeTask}
          celebration={celebration}
          before={
            <>
              {showOnboardingBanner && <GettingStarted hasArea={hasArea} hasDream={hasDream} hasGoal={hasGoal} />}
              <ReportReminderNotice token={token} />
            </>
          }
          accountCards={
            <>
              <EnergyBudgetCard budget={summary?.energyBudget} />
              <GratitudeCard token={token} gratitude={summary?.gratitude} onChanged={() => reloadSummary(false)} />
              <ProgressMetricsCard token={token} />
              <ReminderSettingsCard token={token} />
            </>
          }
          attention={<AttentionPanel attention={summary?.attention} visionAreaId={filterVisionAreaId} />}
        />
      )}
    </PageSection>
  );
}

type ZonesProps = {
  summary: DashboardSummaryData | null;
  cardState: 'loading' | 'ready';
  visionAreas: VisionArea[];
  periodLabel: string;
  dueLink: string;
  scopeSuffix: string;
  scoped: (base: string) => string;
  onComplete: (task: NextUpTask) => void;
  /** Increments to play the goal-completed burst (DA-FR-14). */
  celebration: number;
  before: ReactNode;
  attention: ReactNode;
  accountCards: ReactNode;
};

const grid = {
  display: 'grid',
  gap: { xs: '12px', md: '16px', lg: '24px' },
  gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(8, minmax(0, 1fr))', lg: 'repeat(12, minmax(0, 1fr))' },
  alignItems: 'start',
} as const;

const span = (md: number | string, lg: number | string) => ({
  gridColumn: { xs: '1 / -1', md: typeof md === 'number' ? `span ${md}` : md, lg: typeof lg === 'number' ? `span ${lg}` : lg },
});

function DashboardZones({ summary, cardState, visionAreas, periodLabel, dueLink, scopeSuffix, scoped, onComplete, celebration, before, attention, accountCards }: ZonesProps) {
  const reduced = useReducedMotion();
  const loadingCards = cardState === 'loading';
  // Staggered entry per zone; nothing moves under reduced motion.
  const enter = (index: number) => (reduced ? {} : { animation: `${fadeRise} 200ms var(--motion-ease) both`, animationDelay: `${index * 40}ms` });
  const tasksByPriority = summary?.tasksByPriority ?? {};
  const nextUp = (summary?.nextUpTasks ?? summary?.priorityTasks ?? []).map(toNextUp);

  // Delta over the trend window: the last weekly point against the first.
  const trend = (summary?.progressTrend ?? []).map((point) => ({
    label: formatDate(new Date(`${point.weekEnd}T00:00:00`)),
    progress: Number(point.progress),
  }));
  const delta = trend.length >= 2 ? trend[trend.length - 1].progress - trend[0].progress : null;

  // Areas: colour by their position among the user's areas (first five get a
  // chart slot, the rest "Other"), so an area keeps its colour across cards.
  const areaIndex = new Map([...visionAreas].sort((a, b) => a.id - b.id).map((area, index) => [area.name, { area, index }]));
  const areaColor = (name: string) => {
    const found = areaIndex.get(name);
    return found && found.index < 5 ? `var(--chart-${found.index + 1})` : 'var(--chart-other)';
  };
  const dreamsByArea = summary?.dreamsByVisionArea ?? {};
  const dreamCount = (name: string) => {
    const count = dreamsByArea[name] ?? 0;
    return count === 1 ? tr('1 dream') : tr('{n} dreams', { n: formatNumber(count) });
  };
  const areaItems: RankedProgressItem[] = (summary?.visionAreaProgress ?? []).map((entry) => {
    const area = areaIndex.get(entry.name)?.area;
    return {
      id: entry.name,
      label: entry.name,
      value: Number(entry.progress),
      caption: dreamCount(entry.name),
      color: areaColor(entry.name),
      to: area ? `/dreams?visionAreaId=${area.id}` : undefined,
    };
  });

  const goalsByStatus = summary?.goalsByStatus ?? {};
  const tasksByStatus = summary?.tasksByStatus ?? {};
  const statusTotal = WORK_STATUS_ORDER.reduce((sum, status) => sum + (goalsByStatus[status] ?? 0) + (tasksByStatus[status] ?? 0), 0);

  const blockedWithoutPartner = summary?.attention?.blockedTasksWithoutPartner.length ?? 0;
  const DeltaIcon = delta !== null && delta < 0 ? ArrowDown : ArrowUp;

  return (
    <Box sx={{ display: 'grid', gap: { xs: 2, md: 4 } }}>
      {before}

      {/* ---------------- Today ---------------- */}
      <Box component="section" aria-labelledby="zone-today" sx={{ display: 'grid', gap: 2, ...enter(0) }}>
        <ZoneHeader id="zone-today" label={tr('Today')} />
        <Box sx={grid}>
          <Box sx={{ ...span(5, 8), order: { xs: 2, md: 0 } }}>
            <ChartCard title={tr('Next up')} caption={tr('Overdue first, then due today, then by priority')} state={cardState}>
              <NextUpList tasks={nextUp} onComplete={onComplete} emptyMessage={tr('Nothing open right now. Add a task to a goal to see it here.')} />
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', mt: 1.5, pt: 1.5, borderTop: '1px solid var(--divider)' }}>
                <Box component="span" sx={{ ...tabular, fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>
                  {tr('{critical} Critical · {high} High open', { critical: formatNumber(tasksByPriority.CRITICAL ?? 0), high: formatNumber(tasksByPriority.HIGH ?? 0) })}
                </Box>
                <Box component={Link} to={scoped('/tasks')} sx={{ color: 'var(--primary)', fontSize: '0.8125rem', fontWeight: 500, textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>
                  {tr('View all tasks')}
                </Box>
              </Box>
            </ChartCard>
          </Box>

          <Box component="section" aria-label={tr('Overall progress')} sx={{ ...span(3, 4), ...cardSurface, order: { xs: 1, md: 0 } }}>
            <Box component="h3" sx={{ m: 0, mb: 2, fontSize: '1rem', lineHeight: 1.5, fontWeight: 600, display: { xs: 'none', sm: 'block' } }}>{tr('Overall progress')}</Box>
            <Box sx={{ display: 'flex', flexDirection: { xs: 'row', sm: 'column' }, alignItems: 'center', textAlign: { xs: 'left', sm: 'center' }, gap: { xs: 2, sm: 1.5 } }}>
              {loadingCards && <Skeleton variant="circular" width={120} height={120} aria-hidden="true" />}
              <Box sx={{ display: loadingCards ? 'none' : { xs: 'block', sm: 'none' } }}><ProgressRing value={Number(summary?.averageProgress ?? 0)} label={tr('Overall progress')} size={72} thickness={12} /></Box>
              <Box sx={{ display: loadingCards ? 'none' : { xs: 'none', sm: 'block' } }}><ProgressRing value={Number(summary?.averageProgress ?? 0)} label={tr('Overall progress')} size={140} /></Box>
              <Box sx={{ display: 'grid', gap: 0.5, justifyItems: { xs: 'start', sm: 'center' } }}>
                {delta !== null && (
                  <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, fontSize: '0.8125rem', fontWeight: 600, color: delta < 0 ? 'var(--st-declined-fg)' : 'var(--st-done-fg)' }}>
                    <DeltaIcon size={14} strokeWidth={2} aria-hidden="true" />
                    {formatDelta(delta)}
                    <Box component="span" sx={{ fontWeight: 400, color: 'var(--text-secondary)' }}>{tr('over 12 weeks')}</Box>
                  </Box>
                )}
                <Box component="span" sx={{ fontSize: 'var(--font-caption)', color: 'var(--text-secondary)', maxWidth: '32ch' }}>
                  {tr('{areas} areas · {dreams} dreams · {goals} goals · {tasks} open tasks', {
                    areas: formatNumber(summary?.totalVisionAreas ?? 0),
                    dreams: formatNumber(summary?.activeDreams ?? 0),
                    goals: formatNumber(summary?.activeGoals ?? 0),
                    tasks: formatNumber(summary?.activeTasks ?? 0),
                  })}
                </Box>
                {(summary?.moonshotGoals ?? 0) + (summary?.moonshotDreams ?? 0) > 0 && (
                  <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, fontSize: 'var(--font-caption)', fontWeight: 600, color: 'var(--moonshot-fg)' }}>
                    <Rocket size={14} aria-hidden="true" />
                    {tr('{goals} moonshot goals · {dreams} moonshot dreams', { goals: formatNumber(summary?.moonshotGoals ?? 0), dreams: formatNumber(summary?.moonshotDreams ?? 0) })}
                  </Box>
                )}
              </Box>
            </Box>
            <ConfettiBurst play={celebration} />
          </Box>

          {loadingCards && (
            <Box aria-hidden="true" sx={{ ...span('1 / -1', '1 / -1'), order: { xs: 3, md: 0 }, display: 'grid', gap: { xs: 1, md: 2, lg: 3 }, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
              {[0, 1, 2].map((index) => (
                <Box key={index} sx={{ ...cardSurface, display: 'grid', gap: 1.25 }}>
                  <Skeleton variant="rounded" height={12} width="50%" />
                  <Skeleton variant="rounded" height={36} width="35%" />
                </Box>
              ))}
            </Box>
          )}
          <Box component="section" aria-label={tr('Task pressure')} sx={{ ...span('1 / -1', '1 / -1'), order: { xs: 3, md: 0 }, display: loadingCards ? 'none' : 'grid', gap: { xs: 1, md: 2, lg: 3 }, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
            <KpiCard
              label={tr('Due {period}', { period: periodLabel })}
              value={summary?.tasksDueInPeriod ?? 0}
              caption={tr('{n} completed {period}', { n: formatNumber(summary?.completedTasksInPeriod ?? 0), period: periodLabel })}
              to={dueLink}
            />
            <KpiCard label={tr('Overdue Tasks')} value={summary?.overdueTasks ?? 0} clearWhenZero to={scoped('/tasks?overdue=true')} />
            <KpiCard
              label={tr('Blocked Tasks')}
              value={summary?.blockedTasks ?? 0}
              clearWhenZero
              caption={blockedWithoutPartner > 0 ? tr('{n} need a partner', { n: formatNumber(blockedWithoutPartner) }) : undefined}
              to={scoped('/tasks?status=BLOCKED')}
            />
          </Box>
        </Box>
      </Box>

      {/* ---------------- Plan health ---------------- */}
      <Box component="section" aria-labelledby="zone-health" sx={{ display: 'grid', gap: 2, ...enter(1) }}>
        <ZoneHeader id="zone-health" label={tr('Plan health')} />
        <Box sx={grid}>
          <Box sx={span('1 / -1', 7)}>
            <ChartCard
              title={tr('Vision Area progress')}
              caption={tr('Average goal progress per area — lowest first is what needs attention')}
              state={areaItems.length === 0 && cardState === 'ready' ? 'empty' : cardState}
              emptyMessage={tr('No vision areas yet.')}
              table={
                <table className="data-table">
                  <thead><tr><th>{tr('Vision Area')}</th><th>{tr('Progress')}</th><th>{tr('Dreams')}</th></tr></thead>
                  <tbody>
                    {areaItems.map((item) => (
                      <tr key={item.id}><td>{item.label}</td><td>{formatPercent(item.value)}</td><td>{formatNumber(dreamsByArea[item.label] ?? 0)}</td></tr>
                    ))}
                  </tbody>
                </table>
              }
            >
              <RankedProgressList items={areaItems} label={tr('Vision Area progress')} />
            </ChartCard>
          </Box>
          <Box sx={span('1 / -1', 5)}>
            <MobileCollapse label={tr('Show goals and tasks by status')}>
            <ChartCard
              title={tr('Goals and tasks by status')}
              caption={tr('Completed sits on the right, so the bar fills towards green')}
              state={statusTotal === 0 && cardState === 'ready' ? 'empty' : cardState}
              emptyMessage={tr('No goals or tasks yet.')}
              table={
                <table className="data-table">
                  <thead><tr><th>{tr('Status')}</th><th>{tr('Goals')}</th><th>{tr('Tasks')}</th></tr></thead>
                  <tbody>
                    {WORK_STATUS_ORDER.map((status) => (
                      <tr key={status}>
                        <td><StatusChip status={status} /></td>
                        <td><Link to={scoped(`/goals?status=${status}`)}>{formatNumber(goalsByStatus[status] ?? 0)}</Link></td>
                        <td><Link to={scoped(`/tasks?status=${status}`)}>{formatNumber(tasksByStatus[status] ?? 0)}</Link></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              }
            >
              <Box sx={{ display: 'grid', gap: 2.5 }}>
                <StackedStatusBar label={tr('Goals')} counts={goalsByStatus} />
                <StackedStatusBar label={tr('Tasks')} counts={tasksByStatus} />
              </Box>
              {Object.keys(dreamsByArea).length > 0 && (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', columnGap: 2, rowGap: 0.75, mt: 2.5, pt: 1.75, borderTop: '1px solid var(--divider)', fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>
                  <span>{tr('Dreams by vision area')}</span>
                  {Object.entries(dreamsByArea).map(([name, count]) => {
                    const area = areaIndex.get(name)?.area;
                    const content = (
                      <>
                        <Box component="span" sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: areaColor(name) }} />
                        {name} <Box component="b" sx={{ ...tabular, color: 'var(--text-primary)', fontWeight: 600 }}>{formatNumber(count)}</Box>
                      </>
                    );
                    return area ? (
                      <Box key={name} component={Link} to={`/dreams?visionAreaId=${area.id}`} sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, color: 'inherit', textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>{content}</Box>
                    ) : (
                      <Box key={name} component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>{content}</Box>
                    );
                  })}
                </Box>
              )}
            </ChartCard>
            </MobileCollapse>
          </Box>
        </Box>
      </Box>

      {/* ---------------- Insights ---------------- */}
      <Box component="section" aria-labelledby="zone-insights" sx={{ display: 'grid', gap: 2, ...enter(2) }}>
        <ZoneHeader id="zone-insights" label={tr('Insights')} />
        {attention}
        <Box sx={grid}>
          <Box sx={{ ...span('1 / -1', 7), order: { xs: 2, lg: 0 } }}>
            <MobileCollapse label={tr('Show the progress trend')}>
            <ChartCard
              title={tr('Progress trend')}
              caption={tr('Portfolio-wide average task progress over the last 12 weeks')}
              state={trend.length === 0 && cardState === 'ready' ? 'empty' : cardState}
              emptyMessage={tr('No progress logged yet — update a task\'s progress to start the trend.')}
              table={
                <table className="data-table">
                  <thead><tr><th>{tr('Week ending')}</th><th>{tr('Progress')}</th></tr></thead>
                  <tbody>{trend.map((point) => <tr key={point.label}><td>{point.label}</td><td>{formatPercent(point.progress)}</td></tr>)}</tbody>
                </table>
              }
            >
              <Box sx={{ width: '100%', height: 220 }} role="img" aria-label={trend.length >= 2 ? tr('Progress went from {from} to {to} over 12 weeks', { from: formatPercent(trend[0].progress), to: formatPercent(trend[trend.length - 1].progress) }) : tr('Progress trend')}>
                <ProgressTrendChart data={trend} />
              </Box>
            </ChartCard>
            </MobileCollapse>
          </Box>
          <Box sx={{ ...span('1 / -1', 5), order: { xs: 1, lg: 0 } }}>
            <InsightTabs
              obstaclesByType={summary?.activeObstaclesByType ?? {}}
              blockedTasks={summary?.blockedTasks ?? 0}
              partnersByStatus={summary?.partnersByStatus ?? {}}
              reviewCadence={summary?.reviewCadence ?? {}}
              weeksWithDiligence={summary?.weeksWithDiligence ?? 0}
              scope={scopeSuffix}
            />
          </Box>
        </Box>
        <Box sx={{ display: 'grid', gap: { xs: '12px', md: '16px', lg: '24px' }, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(2, minmax(0, 1fr))' }, alignItems: 'start' }}>
          {accountCards}
        </Box>
      </Box>
    </Box>
  );
}
