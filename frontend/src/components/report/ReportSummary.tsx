import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import {
  ArrowDown, ArrowUp, CircleCheck, CircleDashed, CircleX, ListChecks, Minus, TriangleAlert, type LucideIcon,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import type { ReportFilter } from '../../api/reportApi';
import { statusColors } from '../../theme';
import type { ManagementReport, ObstacleType, Priority, ReportHealth, ReportHealthReason } from '../../types/vision';
import {
  ATTENTION_LABELS, CHANGE_LABELS, HEALTH_LABELS, REMOVED_REASON_LABELS, attentionLink, blockedTasksLink, changeValue, changeWords,
  goalChangeText, healthReasonText, overdueTasksLink, periodLabel, reportDate, signed, unplacedLink,
} from '../../utils/managementReport';
import type { ReportComparison } from '../../types/vision';
import { DataTable, type DataTableColumn } from '../common/DataTable';
import { ProgressBar } from '../common/ProgressBar';
import { Button } from '../common/Button';
import { BlockersChart, GoalProgressChart, PriorityChart, ProgressTrendCard, ReportChartCard, TasksByStatusChart } from './ReportCharts';
import { surface } from '../../design/surface';
import { obstacleTypeLabels, priorityLabels } from '../../utils/enumLabels';
import { tr } from '../../i18n/translate';

const HEALTH_ICONS: Record<ReportHealth, { icon: LucideIcon; color: string }> = {
  RED: { icon: CircleX, color: statusColors.DECLINED },
  AMBER: { icon: TriangleAlert, color: statusColors.BLOCKED },
  DONE_OPEN_WORK: { icon: CircleDashed, color: statusColors.WAITING },
  GREEN: { icon: CircleCheck, color: statusColors.COMPLETED },
  DONE: { icon: ListChecks, color: statusColors.PAUSED },
};

const HEALTH_RANK: Record<ReportHealth, number> = { RED: 0, AMBER: 1, DONE_OPEN_WORK: 2, GREEN: 3, DONE: 4 };

/** MR-FR-11, MR-NFR-07: health as an icon and a text label, never colour alone. */
export function HealthBadge({ health }: Readonly<{ health: ReportHealth }>) {
  const { icon: Icon, color } = HEALTH_ICONS[health];
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, whiteSpace: 'nowrap' }}>
      <Icon size={16} color={color} aria-hidden="true" />
      <span>{tr(HEALTH_LABELS[health])}</span>
    </Box>
  );
}

/**
 * V19 RM-FR-01: the badge with the rules that set it, in a tooltip and as a
 * short line under it, so the reason can be read without hovering.
 */
function HealthWithReasons({ health, reasons }: Readonly<{ health: ReportHealth; reasons: ReportHealthReason[] }>) {
  const words = reasons.map(healthReasonText);
  return (
    <Box sx={{ display: 'grid', gap: 0.25 }}>
      <Tooltip title={words.join(' · ')}>
        <Box component="span" sx={{ width: 'fit-content' }}><HealthBadge health={health} /></Box>
      </Tooltip>
      {words.length > 0 && (
        <Typography variant="caption" color="text.secondary">{words.join(' · ')}</Typography>
      )}
    </Box>
  );
}

/**
 * V19 RM-FR-07 to RM-FR-09: what happened in the period, next to the as-of
 * figures, with a note when a figure cannot cover a past period.
 */
function PeriodCard({ report }: Readonly<{ report: ManagementReport }>) {
  const { period, periodFigures: figures } = report;
  const items = [
    { label: tr('Tasks completed'), value: figures.tasksCompleted },
    { label: tr('Progress gained'), value: tr('+{points} points', { points: figures.progressGained }) },
    { label: tr('Blockers opened'), value: figures.blockersOpened },
    { label: tr('Blockers closed'), value: figures.blockersClosed },
    { label: tr('Tasks that became overdue'), value: figures.tasksBecameOverdue },
  ];
  return (
    <Card component="section" aria-label={tr('This period')}>
      <CardHeader
        title={tr('This period: {period}', { period: periodLabel(period) })}
        titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }}
        subheader={tr('{from} to {to}', { from: reportDate(period.from), to: reportDate(period.to) })}
      />
      <CardContent sx={{ pt: 0, display: 'grid', gap: 1.5 }}>
        <Box component="dl" sx={{ m: 0, display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(5, 1fr)' } }}>
          {items.map((item) => (
            <Box key={item.label}>
              <Typography component="dt" variant="caption" color="text.secondary">{item.label}</Typography>
              <Typography component="dd" variant="h6" sx={{ m: 0 }}>{item.value}</Typography>
            </Box>
          ))}
        </Box>
        {period.statusAsOfToday && (
          <Alert severity="info">
            {tr('Status figures are as of today; period figures cover {from} to {to}.', { from: reportDate(period.from), to: reportDate(period.to) })}
          </Alert>
        )}
        {period.closedBeforeRecording && (
          <Typography variant="caption" color="text.secondary">
            {tr('This period starts before the app recorded when blockers were closed, so older ones count on the day they were last changed.')}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

/** RM-FR-35: the What changed card shows this many lines; the rest are counted. */
export const MAX_CHANGES_SHOWN = 15;

/**
 * RM-FR-33, RM-NFR-09: a headline change as an arrow and signed text, with
 * the change in words for screen readers ("2 fewer overdue tasks").
 */
function ChangeLine({ value, words }: Readonly<{ value: number; words: string }>) {
  const Icon = value > 0 ? ArrowUp : value < 0 ? ArrowDown : Minus;
  return (
    <Typography variant="caption" color="text.secondary" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25 }}>
      <Icon size={12} aria-hidden="true" />
      <span aria-hidden="true">{signed(value)}</span>
      <Box component="span" sx={visuallyHidden}>{words}</Box>
    </Typography>
  );
}

// Pixel strings, not numbers: in MUI's sx, width: 1 means 100%, which made these labels as wide as their parent.
const visuallyHidden = {
  position: 'absolute', width: '1px', height: '1px', p: 0, m: '-1px', overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap', border: 0,
} as const;

/**
 * RM-FR-35, RM-FR-36: what changed since the compared report, most important
 * first, up to 15 lines and "and N more" opening the Details tab. Goals no
 * longer in the report are listed with why, when known (RM-FR-34).
 */
function WhatChangedCard({ comparison, onShowDetails }: Readonly<{ comparison: ReportComparison; onShowDetails?: () => void }>) {
  const shown = comparison.changes.slice(0, MAX_CHANGES_SHOWN);
  const more = comparison.totalChanges - shown.length;
  return (
    <Card component="section" aria-label={tr('What changed')}>
      <CardHeader title={tr('What changed')} titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }}
        subheader={tr('Since {name} ({code}), frozen {date}', { name: comparison.name, code: comparison.code, date: reportDate(comparison.asOf) })} />
      <CardContent sx={{ pt: 0, display: 'grid', gap: 1.5 }}>
        {shown.length === 0 ? (
          <Typography variant="body2">{tr('Nothing changed since the compared report.')}</Typography>
        ) : (
          <Box component="ol" sx={{ m: 0, pl: 3, display: 'grid', gap: 1 }}>
            {shown.map((change) => {
              const before = changeValue(change.kind, change.before);
              const after = changeValue(change.kind, change.after);
              const values = before && after ? `${before} → ${after}` : before || after;
              return (
                <li key={`${change.kind}-${change.itemType}-${change.itemId}`}>
                  <Typography variant="body2" component="span" sx={{ fontWeight: 600 }}>{tr(CHANGE_LABELS[change.kind])}: </Typography>
                  <Typography variant="body2" component="span">
                    {change.title}{change.renamed ? ` ${tr('(renamed)')}` : ''}{values ? ` · ${values}` : ''}
                  </Typography>
                  {change.context && <Typography variant="body2" component="span" color="text.secondary"> · {change.context}</Typography>}
                </li>
              );
            })}
          </Box>
        )}
        {more > 0 && (
          <Box>
            {onShowDetails ? (
              <Button variant="secondary" onClick={onShowDetails}>{tr('and {count} more: see the Details tab', { count: more })}</Button>
            ) : (
              <Typography variant="body2" color="text.secondary">{tr('and {count} more', { count: more })}</Typography>
            )}
          </Box>
        )}
        {comparison.removed.length > 0 && (
          <Box>
            <Typography variant="subtitle2" component="h4">{tr('No longer in this report')}</Typography>
            <Box component="ul" sx={{ m: 0, pl: 3 }}>
              {comparison.removed.map((goal) => (
                <li key={goal.goalId}>
                  <Typography variant="body2" component="span">{goal.title}</Typography>
                  {goal.reason && (
                    <Typography variant="body2" component="span" color="text.secondary"> · {tr(REMOVED_REASON_LABELS[goal.reason])}</Typography>
                  )}
                </li>
              ))}
            </Box>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

type GoalRow = ManagementReport['goals'][number] & { id: number };
type ReportGoal = ManagementReport['goals'][number];
type AttentionItem = ManagementReport['attention'][number];

/** Tinted pill and bar colours per health, from the status tokens (text colours meet 4.5:1 on their tint). */
const HEALTH_TONE: Record<ReportHealth, { bg: string; fg: string; fill: string }> = {
  RED: { bg: 'var(--st-declined-bg)', fg: 'var(--st-declined-fg)', fill: 'var(--st-declined)' },
  AMBER: { bg: 'var(--st-blocked-bg)', fg: 'var(--st-blocked-fg)', fill: 'var(--st-blocked)' },
  DONE_OPEN_WORK: { bg: 'var(--st-waiting-bg)', fg: 'var(--st-waiting-fg)', fill: 'var(--st-waiting)' },
  GREEN: { bg: 'var(--st-done-bg)', fg: 'var(--st-done-fg)', fill: 'var(--st-done)' },
  DONE: { bg: 'var(--st-moving-bg)', fg: 'var(--st-moving-fg)', fill: 'var(--st-moving)' },
};

const PRIORITY_ORDER: readonly Priority[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const PRIORITY_FILL: Record<Priority, string> = {
  CRITICAL: 'var(--st-declined)', HIGH: 'var(--st-declined)', MEDIUM: 'var(--st-blocked)', LOW: 'var(--primary)',
};

/** The prototype's status pill: label in words, tinted by health. */
function HealthPill({ health }: Readonly<{ health: ReportHealth }>) {
  const tone = HEALTH_TONE[health];
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', px: 1.25, py: 0.25, borderRadius: 'var(--radius-pill)',
      fontSize: 'var(--font-caption)', fontWeight: 600, whiteSpace: 'nowrap', bgcolor: tone.bg, color: tone.fg }}>
      {tr(HEALTH_LABELS[health])}
    </Box>
  );
}

const valueColor = { red: 'var(--st-declined-fg)', amber: 'var(--st-blocked-fg)', green: 'var(--st-done-fg)' } as const;

/**
 * One KPI tile (prototype "tile"): label, big value, a sub line, and for tiles
 * that open a filtered list, a "View … →" hint; the whole tile is the link.
 */
function SummaryTile({ label, value, valueLabel, sub, tone, to, hint, extra }: Readonly<{
  label: string; value: ReactNode; valueLabel?: string; sub?: ReactNode; tone?: keyof typeof valueColor; to?: string; hint?: string; extra?: ReactNode;
}>) {
  const body = (
    <>
      <Typography component="span" sx={{ fontSize: 'var(--font-meta)', color: 'var(--text-secondary)', fontWeight: 500 }}>{label}</Typography>
      <Typography component="span" sx={{ fontSize: '1.75rem', fontWeight: 700, lineHeight: 1.2, fontVariantNumeric: 'tabular-nums', color: tone ? valueColor[tone] : 'inherit' }}>
        {valueLabel ? <><span aria-hidden="true">{value}</span><Box component="span" sx={visuallyHidden}>{valueLabel}</Box></> : value}
      </Typography>
      {sub && <Typography component="span" sx={{ fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>{sub}</Typography>}
      {extra}
      {to && hint && (
        <Typography component="span" sx={{ fontSize: 'var(--font-caption)', color: 'var(--primary)', fontWeight: 600, mt: 'auto' }}>
          {hint} <span aria-hidden="true">→</span>
        </Typography>
      )}
    </>
  );
  const tileSx = { ...surface('card', { interactive: Boolean(to) }), display: 'flex', flexDirection: 'column', gap: 0.75, color: 'inherit', textDecoration: 'none' };
  return to ? <Box component={Link} to={to} sx={tileSx}>{body}</Box> : <Box sx={tileSx}>{body}</Box>;
}

/** A labelled bar row (prototype "bar-row"); the colour repeats what the text already says. */
function BarRow({ label, value, percent, fill }: Readonly<{ label: ReactNode; value: ReactNode; percent: number; fill: string }>) {
  return (
    <Box sx={{ display: 'grid', gap: 0.5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, fontSize: 'var(--font-meta)' }}>
        <span>{label}</span><Box component="span" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>{value}</Box>
      </Box>
      <Box sx={{ height: 10, bgcolor: 'var(--surface-sunken)', borderRadius: 'var(--radius-pill)', overflow: 'hidden' }}>
        <Box sx={{ height: '100%', width: `${Math.max(0, Math.min(100, percent))}%`, bgcolor: fill, borderRadius: 'var(--radius-pill)' }} />
      </Box>
    </Box>
  );
}

/** Prototype chart 1: progress by goal, coloured by health, with health in words too. */
function GoalProgressBars({ goals }: Readonly<{ goals: ReportGoal[] }>) {
  const sorted = [...goals].sort((a, b) => Number(b.progress) - Number(a.progress));
  const high = goals.filter((goal) => Number(goal.progress) >= 70).length;
  const lowest = sorted[sorted.length - 1];
  const summary = lowest
    ? tr('{high} of {total} goal(s) are above 70%; the lowest is {title} at {percent}%.', {
      high, total: goals.length, title: lowest.goalTitle, percent: Math.round(Number(lowest.progress)) })
    : tr('No goals in this scope.');
  return (
    <ReportChartCard title={tr('Progress by goal')} summary={summary} height="auto"
      headers={[tr('Goal'), tr('Progress'), tr('Health')]}
      rows={sorted.map((goal) => ({ label: goal.goalTitle, values: [`${Math.round(Number(goal.progress))}%`, tr(HEALTH_LABELS[goal.health])] }))}>
      <Box sx={{ display: 'grid', gap: 1.25 }}>
        {sorted.map((goal) => (
          <BarRow key={goal.goalId} label={goal.goalTitle} percent={Number(goal.progress)} fill={HEALTH_TONE[goal.health].fill}
            value={`${Math.round(Number(goal.progress))}% · ${tr(HEALTH_LABELS[goal.health])}`} />
        ))}
      </Box>
    </ReportChartCard>
  );
}

/** Prototype chart 2: open tasks by priority, on track and overdue. */
function PriorityBars({ report }: Readonly<{ report: ManagementReport }>) {
  const rows = PRIORITY_ORDER
    .map((priority) => report.openTasksByPriority.find((row) => row.priority === priority))
    .filter((row): row is ManagementReport['openTasksByPriority'][number] => Boolean(row) && (row!.onTrack + row!.overdue) > 0);
  const max = Math.max(1, ...rows.map((row) => row.onTrack + row.overdue));
  const overdue = rows.reduce((sum, row) => sum + row.overdue, 0);
  const urgentOverdue = rows.filter((row) => row.priority === 'HIGH' || row.priority === 'CRITICAL').reduce((sum, row) => sum + row.overdue, 0);
  const summary = overdue === 0
    ? tr('No open task is overdue.')
    : tr('{urgent} of {overdue} overdue task(s) are high or critical priority.', { urgent: urgentOverdue, overdue });
  return (
    <ReportChartCard title={tr('Open tasks by priority')} summary={summary} height="auto"
      headers={[tr('Priority'), tr('On track'), tr('Overdue')]}
      rows={rows.map((row) => ({ label: tr(priorityLabels[row.priority]), values: [row.onTrack, row.overdue] }))}>
      <Box sx={{ display: 'grid', gap: 1.25 }}>
        {rows.length === 0 && <Typography variant="body2">{tr('No open tasks.')}</Typography>}
        {rows.map((row) => (
          <BarRow key={row.priority} percent={((row.onTrack + row.overdue) / max) * 100} fill={PRIORITY_FILL[row.priority]}
            value={row.onTrack + row.overdue}
            label={tr('{priority} priority ({onTrack} on track, {overdue} overdue)', {
              priority: tr(priorityLabels[row.priority]), onTrack: row.onTrack, overdue: row.overdue })} />
        ))}
      </Box>
    </ReportChartCard>
  );
}

const listItemSx = {
  display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1.5, py: 1.5,
  borderBottom: '1px solid var(--divider)', '&:last-of-type': { borderBottom: 'none', pb: 0 },
} as const;

/** Prototype "Goal health": each goal with why, and its health as a pill (MR-FR-11, RM-FR-01). */
function GoalHealthList({ goals }: Readonly<{ goals: ReportGoal[] }>) {
  const sorted = [...goals].sort((a, b) => HEALTH_RANK[a.health] - HEALTH_RANK[b.health]);
  return (
    <Box component="section" aria-label={tr('Goal health')} sx={surface('card')}>
      <Typography component="h3" sx={{ fontSize: '1rem', fontWeight: 600, mb: 1 }}>{tr('Goal health')}</Typography>
      {sorted.length === 0 && <Typography variant="body2">{tr('No goals in this scope.')}</Typography>}
      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {sorted.map((goal) => {
          const reasons = (goal.reasons ?? []).map(healthReasonText).join(' · ');
          return (
            <Box component="li" key={goal.goalId} sx={listItemSx} data-testid={`goal-health-${goal.goalId}`}>
              <Box sx={{ minWidth: 0 }}>
                <Typography component="p" sx={{ fontWeight: 600, fontSize: 'var(--font-body)', m: 0 }}>{goal.goalTitle}</Typography>
                {reasons && <Typography component="p" sx={{ fontSize: 'var(--font-meta)', color: 'var(--text-secondary)', m: 0 }}>{reasons}</Typography>}
              </Box>
              <HealthPill health={goal.health} />
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

/** The action word for a "needs attention" item, by where it is fixed. */
function fixWord(item: AttentionItem): string {
  switch (item.itemType) {
    case 'OBSTACLE': return tr('Resolve');
    case 'STEP': return tr('Open step');
    case 'GOAL': return tr('Open goal');
    default: return tr('Open task');
  }
}

/** Prototype "Needs attention (up to 10)": what, why, and a link to fix it (MR-FR-12). */
function NeedsAttentionList({ items }: Readonly<{ items: AttentionItem[] }>) {
  return (
    <Box component="section" aria-label={tr('Needs attention')} sx={surface('card')}>
      <Typography component="h3" sx={{ fontSize: '1rem', fontWeight: 600, mb: 1 }}>{tr('Needs attention (up to 10)')}</Typography>
      {items.length === 0 && <Typography variant="body2">{tr('Nothing needs attention right now.')}</Typography>}
      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {items.map((item) => (
          <Box component="li" key={`${item.itemType}-${item.itemId}-${item.kind}`} sx={listItemSx}>
            <Box sx={{ minWidth: 0 }}>
              <Typography component="p" sx={{ fontWeight: 600, fontSize: 'var(--font-body)', m: 0 }}>{item.title}</Typography>
              <Typography component="p" sx={{ fontSize: 'var(--font-meta)', color: 'var(--text-secondary)', m: 0 }}>
                {tr(ATTENTION_LABELS[item.kind])}{item.context ? ` · ${item.context}` : ''}
              </Typography>
            </Box>
            <Box component={Link} to={attentionLink(item)} sx={{ fontSize: 'var(--font-meta)', fontWeight: 600, color: 'var(--primary)', textDecoration: 'none', whiteSpace: 'nowrap', '&:hover': { textDecoration: 'underline' } }}>
              {fixWord(item)}<Box component="span" sx={visuallyHidden}>: {item.title}</Box> <span aria-hidden="true">→</span>
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

/**
 * V18 MR-FR-07 to MR-FR-13, laid out as the Optimize Report-management
 * prototype (docs/Optimize Repot-management/summary.html): header, six tiles,
 * progress by goal and open tasks by priority (each with Show as table), then
 * goal health beside needs attention. V19's period figures and What changed
 * follow, and the other charts and the full goal table are under More detail.
 * With a comparison, each tile shows its change (RM-FR-33).
 */
export function ReportSummary({ report, filter, onShowDetails, comparisonHint, shared = false }: Readonly<{
  report: ManagementReport;
  filter: ReportFilter;
  onShowDetails?: () => void;
  comparisonHint?: string;
  /**
   * V19 RM-FR-47: the reviewer's read-only view through a share link: no links into the plan, no "needs attention"
   * list, and no goal health when it was not shared.
   */
  shared?: boolean;
}>) {
  const [moreOpen, setMoreOpen] = useState(false);
  const showGoals = !shared || report.goals.length > 0;
  const { headline } = report;
  const comparison = report.comparison ?? null;
  const goalRows: GoalRow[] = report.goals.map((goal) => ({ ...goal, id: goal.goalId }));
  const change = (value: number, words: string) => (comparison ? <ChangeLine value={value} words={words} /> : undefined);
  const health = headline.goalsByHealth;
  const activeGoals = report.goals.filter((goal) => goal.health !== 'DONE').length;
  const scopeName = report.scope.kind === 'ALL' ? tr('All dreams') : report.scope.label ?? '';
  const dueBy = new Date(`${report.asOf}T00:00:00`);
  dueBy.setDate(dueBy.getDate() + 14);
  const blockerKinds = Object.entries(report.blockersByKind)
    .filter(([, count]) => (count ?? 0) > 0)
    .sort((a, b) => (b[1] ?? 0) - (a[1] ?? 0))
    .slice(0, 2)
    .map(([kind, count]) => `${count} ${tr(obstacleTypeLabels[kind as ObstacleType]).toLowerCase()}`)
    .join(', ');
  const columns: DataTableColumn<GoalRow>[] = [
    ...(report.scope.kind === 'DREAM' ? [] : [{
      key: 'dream', label: tr('Dream'), sortValue: (row: GoalRow) => row.dreamTitle, render: (row: GoalRow) => row.dreamTitle,
    }]),
    { key: 'goal', label: tr('Goal'), sortValue: (row) => row.goalTitle, render: (row) => row.goalTitle },
    {
      key: 'health',
      label: tr('Health'),
      sortValue: (row) => HEALTH_RANK[row.health],
      render: (row) => <HealthWithReasons health={row.health} reasons={row.reasons ?? []} />,
    },
    {
      key: 'progress',
      label: tr('Progress'),
      sortValue: (row) => Number(row.progress),
      render: (row) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 120 }}>
          <Box sx={{ flex: 1 }}><ProgressBar value={Number(row.progress)} /></Box>
          <span>{Math.round(Number(row.progress))}%</span>
        </Box>
      ),
    },
    { key: 'target', label: tr('Target date'), sortValue: (row) => row.targetDate ?? '9999', render: (row) => row.targetDate ?? '—' },
    { key: 'overdue', label: tr('Overdue tasks'), sortValue: (row) => row.overdueTasks, render: (row) => row.overdueTasks },
    { key: 'blocked', label: tr('Blocked items'), sortValue: (row) => row.blockedItems, render: (row) => row.blockedItems },
    { key: 'next', label: tr('Next action'), render: (row) => row.nextAction ?? '—' },
    ...(comparison ? [{
      key: 'change',
      label: tr('Change'),
      render: (row: GoalRow) => goalChangeText(comparison.goals.find((goal) => goal.goalId === row.goalId), row) || '—',
    }] : []),
  ];

  return (
    <Stack spacing={3}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 1.5 }}>
        <Box>
          <Typography component="h2" sx={{ fontSize: '1.25rem', fontWeight: 600, m: 0 }}>{tr('Summary')}</Typography>
          <Typography component="p" sx={{ fontSize: 'var(--font-body-sm)', color: 'var(--text-secondary)', m: 0 }}>
            {tr('Scope:')} <strong>{scopeName}</strong> • {tr('Generated {date}', { date: reportDate(report.asOf) })}
          </Typography>
        </Box>
      </Box>

      <Box component="section" aria-label={tr('Key metrics')} sx={{ display: 'grid', gap: 1.75, gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))' }}>
        <SummaryTile label={tr('Overall progress')} value={`${headline.overallProgress}%`}
          sub={tr('Across {count} active goal(s)', { count: activeGoals })}
          extra={comparison && change(comparison.headline.overallProgress, changeWords('progress', comparison.headline.overallProgress, ''))} />
        <SummaryTile label={tr('Goals by health')}
          value={`${health.GREEN ?? 0} / ${health.AMBER ?? 0} / ${health.RED ?? 0}`}
          valueLabel={tr('{onTrack} on track, {atRisk} at risk, {offTrack} off track', { onTrack: health.GREEN ?? 0, atRisk: health.AMBER ?? 0, offTrack: health.RED ?? 0 })}
          sub={<span aria-hidden="true">{tr('On track • At risk • Off track')}</span>} />
        <SummaryTile label={tr('Overdue tasks')} value={headline.overdueTasks} tone={headline.overdueTasks > 0 ? 'red' : undefined}
          to={!shared && headline.overdueTasks > 0 ? overdueTasksLink(filter) : undefined} hint={tr('View overdue tasks')}
          extra={comparison && change(comparison.headline.overdueTasks, changeWords('count', comparison.headline.overdueTasks, tr('overdue tasks')))} />
        <SummaryTile label={tr('Blocked tasks')} value={headline.blockedTasks} tone={headline.blockedTasks > 0 ? 'amber' : undefined}
          to={!shared && headline.blockedTasks > 0 ? blockedTasksLink(filter) : undefined} hint={tr('View blocked tasks')}
          extra={comparison && change(comparison.headline.blockedTasks, changeWords('count', comparison.headline.blockedTasks, tr('blocked tasks')))} />
        <SummaryTile label={tr('Open blockers')} value={headline.openBlockers} sub={blockerKinds || tr('None open')}
          extra={comparison && change(comparison.headline.openBlockers, changeWords('count', comparison.headline.openBlockers, tr('open blockers')))} />
        <SummaryTile label={tr('Due in next 14 days')} value={headline.dueSoonTasks} tone={headline.dueSoonTasks > 0 ? 'green' : undefined}
          sub={tr('Due by {date}', { date: reportDate(dueBy.toISOString().slice(0, 10)) })}
          extra={comparison && change(comparison.headline.dueSoonTasks, changeWords('count', comparison.headline.dueSoonTasks, tr('tasks due soon')))} />
      </Box>

      {!shared && report.unplacedObstacles > 0 && (
        <Alert severity="warning" action={<Link to={unplacedLink(filter)}>{tr('Show them')}</Link>}>
          {tr('{count} blocker(s) are not on a step yet, so they are not counted on any goal.', { count: report.unplacedObstacles })}
        </Alert>
      )}

      <Box component="section" aria-label={tr('Progress and task charts')} sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(min(320px, 100%), 1fr))' }}>
        {showGoals && <GoalProgressBars goals={report.goals} />}
        <PriorityBars report={report} />
      </Box>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(min(340px, 100%), 1fr))' }}>
        {showGoals && <GoalHealthList goals={report.goals} />}
        {!shared && <NeedsAttentionList items={report.attention} />}
      </Box>

      <PeriodCard report={report} />
      {comparison && <WhatChangedCard comparison={comparison} onShowDetails={onShowDetails} />}
      {!comparison && comparisonHint && <Alert severity="info">{comparisonHint}</Alert>}

      <Box>
        <Button variant="secondary" aria-expanded={moreOpen} aria-controls="report-more-detail" onClick={() => setMoreOpen((open) => !open)}>
          {moreOpen ? tr('Hide more detail') : tr('More detail: all charts and the goal table')}
        </Button>
        {moreOpen && (
          <Stack id="report-more-detail" spacing={2} sx={{ mt: 2 }}>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
              <TasksByStatusChart report={report} />
              <BlockersChart report={report} />
              {showGoals && <GoalProgressChart report={report} />}
              <PriorityChart report={report} />
              <Box sx={{ gridColumn: '1 / -1' }}><ProgressTrendCard report={report} /></Box>
            </Box>
            {showGoals && (
              <Card component="section" aria-label={tr('Goal details')}>
                <CardHeader title={tr('Goal details')} titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }}
                  subheader={tr('Off track: overdue, or a blocked task or critical blocker. At risk: overdue work, an open blocker, or more than 20 points behind schedule. Done – open work: completed, but tasks are still open.')} />
                <CardContent sx={{ pt: 0 }}>
                  <DataTable rows={goalRows} columns={columns} emptyMessage={tr('No goals in this scope.')} defaultSortKey="health" />
                </CardContent>
              </Card>
            )}
          </Stack>
        )}
      </Box>
    </Stack>
  );
}
