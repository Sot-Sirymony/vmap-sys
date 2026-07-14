import { priorityColors as priorityPalette, statusColors } from '../theme';
import type {
  CommunicationStatus,
  DreamStatus,
  DreamType,
  LifecycleStatus,
  ObstacleStatus,
  ObstacleType,
  PartnerStatus,
  PartnerSupportType,
  Priority,
  ReviewType,
  Severity,
  WorkStatus,
} from '../types/vision';

export const priorityLabels: Record<Priority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

// Drawn from the theme's priority palette so the dashboard chart and
// PriorityBadge can never disagree. Still an escalation ramp (priority is
// ordinal, so color reads as rising urgency rather than four unrelated hues) —
// see theme.ts for why each step darkens as well as warms.
export const priorityColors: Record<Priority, string> = {
  LOW: priorityPalette.LOW,
  MEDIUM: priorityPalette.MEDIUM,
  HIGH: priorityPalette.HIGH,
  CRITICAL: priorityPalette.CRITICAL,
};

// Severity shares its levels with Priority but is its own enum on the backend —
// kept separate so a change to one can't silently redefine the other.
export const severityLabels: Record<Severity, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

export const workStatusLabels: Record<WorkStatus, string> = {
  NOT_STARTED: 'Not Started',
  IN_PROGRESS: 'In Progress',
  WAITING: 'Waiting',
  BLOCKED: 'Blocked',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
};

// Drawn from the theme's status palette so the dashboard chart and StatusBadge
// can never disagree about what, say, "Completed" green is. Still matches the
// semantic scheme used for Excel export conditional formatting (completed =
// green, blocked = orange). See theme.ts for why WAITING is purple, not amber.
export const workStatusColors: Record<WorkStatus, string> = {
  NOT_STARTED: statusColors.NOT_STARTED,
  IN_PROGRESS: statusColors.IN_PROGRESS,
  WAITING: statusColors.WAITING,
  BLOCKED: statusColors.BLOCKED,
  PAUSED: statusColors.PAUSED,
  COMPLETED: statusColors.COMPLETED,
};

export const dreamStatusLabels: Record<DreamStatus, string> = {
  IDEA: 'Idea',
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
};

export const dreamTypeLabels: Record<DreamType, string> = {
  SHORT_TERM: 'Short Term',
  LONG_TERM: 'Long Term',
  LIFETIME: 'Lifetime',
};

export const lifecycleStatusLabels: Record<LifecycleStatus, string> = {
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
};

export const partnerStatusLabels: Record<PartnerStatus, string> = {
  TO_CONTACT: 'To Contact',
  CONTACTED: 'Contacted',
  ACTIVE: 'Active',
  WAITING: 'Waiting',
  DECLINED: 'Declined',
  COMPLETED: 'Completed',
};

export const partnerSupportTypeLabels: Record<PartnerSupportType, string> = {
  MENTOR: 'Mentor',
  EXPERT: 'Expert',
  ADVISOR: 'Advisor',
  COLLEAGUE: 'Colleague',
  FINANCIAL: 'Financial',
  TECHNICAL: 'Technical',
  EMOTIONAL: 'Emotional',
  OTHER: 'Other',
};

export const communicationStatusLabels: Record<CommunicationStatus, string> = {
  DRAFT: 'Draft',
  SENT: 'Sent',
  FOLLOWED_UP: 'Followed Up',
  REPLIED: 'Replied',
  CLOSED: 'Closed',
};

export const reviewTypeLabels: Record<ReviewType, string> = {
  DAILY: 'Daily',
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
};

export const obstacleTypeLabels: Record<ObstacleType, string> = {
  KNOWLEDGE: 'Knowledge',
  SKILL: 'Skill',
  TIME: 'Time',
  MONEY: 'Money',
  MOTIVATION: 'Motivation',
  PARTNER: 'Partner',
  SYSTEM: 'System',
  DECISION: 'Decision',
  OTHER: 'Other',
};

export const obstacleStatusLabels: Record<ObstacleStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  ACCEPTED: 'Accepted',
};
