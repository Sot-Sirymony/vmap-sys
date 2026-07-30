import type {
  CommunicationStatus,
  DreamStatus,
  DreamType,
  EnergyDemand,
  LifecycleStatus,
  IssueReportStatus,
  ObstacleStatus,
  ObstacleType,
  OfferType,
  ReportType,
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

// FR-39.3 removed the `priorityColors` and `workStatusColors` re-exports that
// used to live in this file. They were static snapshots of the theme palettes,
// taken once at module load — which was fine until high contrast made the hues
// depend on the active mode and contrast setting. A frozen copy would have gone
// on quietly serving the old values, so the palettes are now reached only through
// `statusColor()` / `priorityColor()` in theme.ts, which take that state as
// arguments. This file keeps the *labels*, which genuinely are constant.
// Severity shares its levels with Priority but is its own enum on the backend —
// kept separate so a change to one can't silently redefine the other.
// FR-34.1: energy demand. The order (CHARGE → NEUTRAL → DRAIN) reads as a
// scale from energising to depleting, matching how the budget nets them.
export const energyDemandLabels: Record<EnergyDemand, string> = {
  CHARGE: 'Charge',
  NEUTRAL: 'Neutral',
  DRAIN: 'Drain',
};

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

export const offerTypeLabels: Record<OfferType, string> = {
  MONEY: 'Money',
  SHARED_VISION: 'Shared Vision',
  RECOGNITION: 'Recognition',
  EXPERIENCE: 'Experience',
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

// FR-38: in-app issue reporting.
export const reportTypeLabels: Record<ReportType, string> = {
  BUG: 'Bug',
  IMPROVEMENT: 'Improvement',
  QUESTION: 'Question',
  OTHER: 'Other',
};

export const issueReportStatusLabels: Record<IssueReportStatus, string> = {
  OPEN: 'Open',
  IN_REVIEW: 'In Review',
  PLANNED: 'Planned',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  WONT_FIX: "Won't Fix",
};
