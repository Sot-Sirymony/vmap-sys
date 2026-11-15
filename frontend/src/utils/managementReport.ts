import type { ManagementReport, ReportAttentionKind, ReportHealth } from '../types/vision';
import type { ReportFilter } from '../api/reportApi';

/** V18 MR-FR-11: health labels, read with an icon, never colour alone (MR-FR-13). */
export const HEALTH_LABELS: Record<ReportHealth, string> = {
  RED: 'Off track',
  AMBER: 'At risk',
  GREEN: 'On track',
  DONE: 'Done',
};

export const HEALTH_ORDER: readonly ReportHealth[] = ['RED', 'AMBER', 'GREEN', 'DONE'];

/** MR-FR-12: what each "needs attention" item is. */
export const ATTENTION_LABELS: Record<ReportAttentionKind, string> = {
  CRITICAL_BLOCKER: 'Critical blocker',
  BLOCKED_TASK: 'Blocked task',
  OVERDUE_URGENT: 'Overdue, high priority',
  OVERDUE: 'Overdue',
  GAP_NEEDS_HELP: 'Gap with no help planned',
};

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
