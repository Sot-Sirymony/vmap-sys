import type { Priority, Severity, WorkStatus } from '../types/vision';

/**
 * Ordinal ranks for enums that must sort by meaning, not alphabetically:
 * sorting priority as text would put Critical before Low before Medium.
 */
const priorityRanks: Record<Priority, number> = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
  CRITICAL: 4,
};

const workStatusRanks: Record<WorkStatus, number> = {
  NOT_STARTED: 1,
  IN_PROGRESS: 2,
  WAITING: 3,
  BLOCKED: 4,
  PAUSED: 5,
  COMPLETED: 6,
};

export function priorityRank(priority: Priority) {
  return priorityRanks[priority];
}

export function workStatusRank(status: WorkStatus) {
  return workStatusRanks[status];
}

export function severityRank(severity: Severity) {
  return priorityRanks[severity];
}
