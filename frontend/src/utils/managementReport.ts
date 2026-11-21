import type {
  ManagementReport, ReportAttentionKind, ReportChangeKind, ReportComparison, ReportHealth, ReportHealthReason, ReportPeriodKind, ReportSection,
} from '../types/vision';
import type { ReportFilter } from '../api/reportApi';
import { tr } from '../i18n/translate';
import { formatDate } from '../i18n/formatDate';

/** V18 MR-FR-11: health labels, read with an icon, never colour alone (MR-FR-13). */
export const HEALTH_LABELS: Record<ReportHealth, string> = {
  RED: 'Off track',
  AMBER: 'At risk',
  DONE_OPEN_WORK: 'Done – open work',
  GREEN: 'On track',
  DONE: 'Done',
};

export const HEALTH_ORDER: readonly ReportHealth[] = ['RED', 'AMBER', 'DONE_OPEN_WORK', 'GREEN', 'DONE'];

/** MR-FR-12: what each "needs attention" item is. */
export const ATTENTION_LABELS: Record<ReportAttentionKind, string> = {
  CRITICAL_BLOCKER: 'Critical blocker',
  BLOCKED_TASK: 'Blocked task',
  OVERDUE_URGENT: 'Overdue, high priority',
  OVERDUE: 'Overdue',
  REOPEN_OR_FINISH: 'Reopen or finish',
  GAP_NEEDS_HELP: 'Gap with no help planned',
};

/** V19 RM-FR-06: the period presets, in the picker's order. */
export const PERIOD_LABELS: Record<Exclude<ReportPeriodKind, 'CUSTOM'>, string> = {
  THIS_WEEK: 'This week',
  LAST_WEEK: 'Last week',
  THIS_MONTH: 'This month',
  LAST_MONTH: 'Last month',
  THIS_QUARTER: 'This quarter',
  LAST_QUARTER: 'Last quarter',
};

/** A custom period covers at most this many days (the server checks it too). */
export const MAX_PERIOD_DAYS = 366;

/**
 * V19 RM-FR-06: why a custom period cannot be used, or '' when it can. Dates
 * are yyyy-MM-dd strings, so they compare in order as text.
 */
export function customPeriodProblem(from: string, to: string, today: string): string {
  if (!from || !to) return tr('Choose a from date and a to date.');
  if (from > to) return tr('The period must start on or before its end.');
  if (to > today) return tr('The period cannot end after today.');
  const days = Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1;
  if (days > MAX_PERIOD_DAYS) return tr('A custom period can cover at most 366 days.');
  return '';
}

/** A yyyy-MM-dd date as the app shows dates, with the year. */
export function reportDate(isoDate: string) {
  return formatDate(new Date(`${isoDate}T00:00:00`), { year: true });
}

/** "This month", or "Nov 1, 2026 to Nov 10, 2026" for a custom period. */
export function periodLabel(period: ManagementReport['period']) {
  return period.kind === 'CUSTOM'
    ? tr('{from} to {to}', { from: reportDate(period.from), to: reportDate(period.to) })
    : tr(PERIOD_LABELS[period.kind]);
}

/** V19 RM-FR-01: one health reason in words. */
export function healthReasonText(reason: ReportHealthReason): string {
  const value = reason.value ?? 0;
  switch (reason.code) {
    case 'COMPLETED': return tr('Completed');
    case 'COMPLETED_OPEN_WORK': return tr('Completed, but {count} task(s) are still open', { count: value });
    case 'GOAL_OVERDUE': return tr('Target date passed {count} day(s) ago', { count: value });
    case 'BLOCKED_TASKS': return tr('{count} blocked task(s)', { count: value });
    case 'CRITICAL_BLOCKER': return value > 1
      ? tr('Critical blocker: {title} and {count} more', { title: reason.title ?? '', count: value - 1 })
      : tr('Critical blocker: {title}', { title: reason.title ?? '' });
    case 'OVERDUE_WORK': return tr('{count} overdue task(s) or step(s)', { count: value });
    case 'OPEN_BLOCKERS': return tr('{count} open blocker(s)', { count: value });
    case 'BEHIND_SCHEDULE': return tr('Progress {progress}% with {elapsed}% of the time gone', { progress: value, elapsed: reason.elapsed ?? 0 });
    case 'ON_TRACK': return tr('No overdue work or open blockers');
    default: return String((reason as { code: string }).code);
  }
}

/** The URL key the Tasks page filters by for this scope, so a tile's link counts exactly the same rows. */
function scopeParams(filter: ReportFilter): string {
  if (filter.dreamId) return `&dreamId=${filter.dreamId}`;
  if (filter.areaId) return `&visionAreaId=${filter.areaId}`;
  return '';
}

export function overdueTasksLink(filter: ReportFilter) {
  return `/tasks?overdue=true${scopeParams(filter)}`;
}

export function blockedTasksLink(filter: ReportFilter) {
  return `/tasks?status=BLOCKED${scopeParams(filter)}`;
}

export function unplacedLink(filter: ReportFilter) {
  return `/obstacles?placement=unplaced${filter.dreamId ? `&dreamId=${filter.dreamId}` : ''}`;
}

/** Where a "needs attention" item lives. */
export function attentionLink(item: ManagementReport['attention'][number]) {
  if (item.itemType === 'OBSTACLE') return `/obstacles?goalId=${item.goalId}`;
  if (item.itemType === 'STEP') return `/steps?goalId=${item.goalId}`;
  // V19 RM-FR-02: a Completed goal with open work lists its tasks, to finish them or reopen the goal.
  if (item.itemType === 'GOAL') return `/tasks?goalId=${item.goalId}`;
  return `/tasks?stepId=${item.stepId}`;
}

/** MR-FR-13: "12 of 40 tasks are completed". */
export function completedSummary(report: ManagementReport) {
  return { completed: report.headline.completedTasks, total: report.headline.totalTasks };
}

/** "2 off track · 1 at risk · 3 on track · 1 done", leaving out zeros. */
export function healthParts(report: ManagementReport): { health: ReportHealth; count: number }[] {
  return HEALTH_ORDER.map((health) => ({ health, count: report.headline.goalsByHealth[health] ?? 0 })).filter((part) => part.count > 0);
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const PRIORITY_KEYS = new Set(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);

/**
 * V19 RM-FR-06, RM-FR-10: the report's scope, period and filters from the
 * URL. Unknown values are dropped; this month, no filters and completed goals
 * included are the defaults and are left out, so the object stays minimal.
 */
export function filterFromSearch(params: URLSearchParams): ReportFilter {
  const filter: ReportFilter = {};
  const dreamId = Number(params.get('dreamId'));
  const areaId = Number(params.get('areaId'));
  if (dreamId > 0) filter.dreamId = dreamId;
  else if (areaId > 0) filter.areaId = areaId;
  const kind = params.get('period');
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  if (kind === 'CUSTOM' && ISO_DATE.test(from) && ISO_DATE.test(to)) Object.assign(filter, { period: 'CUSTOM', from, to });
  else if (kind && kind !== 'THIS_MONTH' && kind in PERIOD_LABELS) filter.period = kind as ReportPeriodKind;
  const priorities = (params.get('priority') ?? '').split(',').filter((value) => PRIORITY_KEYS.has(value)) as ReportFilter['priorities'];
  if (priorities?.length) filter.priorities = priorities;
  const owner = params.get('owner')?.trim();
  if (owner) filter.owner = owner;
  const healths = (params.get('health') ?? '').split(',').filter((value) => value in HEALTH_LABELS) as ReportHealth[];
  if (healths.length) filter.healths = healths;
  if (params.get('completed') === '0') filter.includeCompleted = false;
  const compareTo = Number(params.get('compareTo'));
  if (compareTo > 0) filter.compareTo = compareTo;
  return filter;
}

/** The URL keys for a filter, '' for each key to remove. */
export function filterToSearch(filter: ReportFilter): Record<string, string> {
  const custom = filter.period === 'CUSTOM';
  return {
    dreamId: filter.dreamId ? String(filter.dreamId) : '',
    areaId: !filter.dreamId && filter.areaId ? String(filter.areaId) : '',
    period: filter.period && filter.period !== 'THIS_MONTH' ? filter.period : '',
    from: custom ? filter.from ?? '' : '',
    to: custom ? filter.to ?? '' : '',
    priority: filter.priorities?.join(',') ?? '',
    owner: filter.owner ?? '',
    health: filter.healths?.join(',') ?? '',
    completed: filter.includeCompleted === false ? '0' : '',
    compareTo: filter.compareTo ? String(filter.compareTo) : '',
  };
}

// ---------- V19 phase 3: comparison and sections ----------

/** RM-FR-35: the kind of one What changed line. */
export const CHANGE_LABELS: Record<ReportChangeKind, string> = {
  GOAL_WORSE: 'Health got worse',
  DUE_LATER: 'Date moved later',
  NEW_BLOCKER: 'New blocker',
  BLOCKER_CLOSED: 'Blocker no longer open',
  GOAL_BETTER: 'Health got better',
  TASK_COMPLETED: 'Task completed',
};

/** RM-FR-34: why a goal left the report. */
export const REMOVED_REASON_LABELS: Record<NonNullable<ReportComparison['removed'][number]['reason']>, string> = {
  COMPLETED_ARCHIVED: 'Completed and archived',
  ARCHIVED: 'Archived',
  MOVED: 'Moved to another dream',
};

/** RM-FR-39: the file sections, in the order they are offered. */
export const SECTION_LABELS: Record<ReportSection, string> = {
  COMMENTARY: 'Commentary',
  SUMMARY: 'Summary',
  GOAL_HEALTH: 'Goal health',
  DETAILS: 'Details / Report rows',
  BLOCKERS: 'Blockers',
  CHANGES: 'What changed',
  NEXT_ACTIONS: 'Next actions',
  SUPPORT: 'Decisions and support',
};

export const ALL_SECTIONS = Object.keys(SECTION_LABELS) as ReportSection[];

/** RM-FR-33: "+12", "−2" (a true minus sign) or "0", to at most one decimal place. */
export function signed(value: number) {
  const rounded = Math.round(value * 10) / 10;
  if (rounded > 0) return `+${rounded}`;
  if (rounded < 0) return `−${Math.abs(rounded)}`;
  return '0';
}

/**
 * RM-FR-33, RM-NFR-09: a headline change in words for screen readers. For
 * progress and completed tasks up is good; for overdue, blocked and blockers
 * fewer is good, so the words say "fewer" or "more" rather than up or down.
 */
export function changeWords(kind: 'progress' | 'count', value: number, noun: string) {
  const amount = Math.abs(Math.round(value * 10) / 10);
  if (amount === 0) return tr('no change since the compared report');
  if (kind === 'progress') {
    return value > 0 ? tr('up {count} points', { count: amount }) : tr('down {count} points', { count: amount });
  }
  return value > 0 ? tr('{count} more {noun}', { count: amount, noun }) : tr('{count} fewer {noun}', { count: amount, noun });
}

/** RM-FR-35: a change's before or after value in words: a health, a date or a severity. */
export function changeValue(kind: ReportChangeKind, value?: string | null) {
  if (!value) return '';
  if (kind === 'GOAL_WORSE' || kind === 'GOAL_BETTER') return tr(HEALTH_LABELS[value as ReportHealth] ?? value);
  if (kind === 'DUE_LATER') return reportDate(value);
  if (kind === 'NEW_BLOCKER') return tr(value.charAt(0) + value.slice(1).toLowerCase());
  return value;
}

/**
 * RM-FR-34: one goal's change: "At risk → On track · +12 points · Target 10
 * Jan → 24 Jan · (renamed)", "New", or '' when nothing changed.
 */
export function goalChangeText(change: ReportComparison['goals'][number] | undefined, row: ManagementReport['goals'][number]) {
  if (!change) return '';
  if (change.isNew) return tr('New');
  const parts: string[] = [];
  if (change.healthBefore && change.healthBefore !== row.health) {
    parts.push(`${tr(HEALTH_LABELS[change.healthBefore])} → ${tr(HEALTH_LABELS[row.health])}`);
  }
  if (change.progressChange) parts.push(tr('{points} points', { points: signed(Number(change.progressChange)) }));
  if (change.targetBefore) {
    parts.push(tr('Target {from} → {to}', { from: reportDate(change.targetBefore), to: row.targetDate ? reportDate(row.targetDate) : '—' }));
  }
  if (change.previousTitle) parts.push(tr('(renamed)'));
  return parts.join(' · ');
}
