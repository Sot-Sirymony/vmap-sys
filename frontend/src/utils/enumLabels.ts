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
  PartnerMotivator,
  PartnerStatus,
  PartnerSupportType,
  Priority,
  ReviewType,
  ScheduleMode,
  Severity,
  WorkStatus,
} from '../types/vision';

export const priorityLabels: Record<Priority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

// FR-51: how a dream's or goal's target date relates to its children's.
export const scheduleModeLabels: Record<ScheduleMode, string> = {
  BOTTOM_UP: 'Bottom-up (default)',
  TOP_DOWN_FIXED: 'Fixed deadline (top-down)',
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

// FR-50.4: what drives the partner, as distinct from offerTypeLabels above
// (what the user offers them in return).
export const partnerMotivatorLabels: Record<PartnerMotivator, string> = {
  FINANCIAL_GAIN: 'Financial gain',
  AVOIDING_LOSS: 'Avoiding a loss',
  SHARED_VISION: 'Shared vision',
  RECOGNITION: 'Recognition',
  OTHER: 'Other',
};

// FR-50.1: seven original, plain-language integrity-and-reliability checks.
export const INTEGRITY_CHECKLIST_QUESTIONS = [
  { key: 'flagDishonesty', label: 'I have concerns about this person’s honesty or trustworthiness' },
  { key: 'flagAnger', label: 'I have seen a pattern of volatile anger from them' },
  { key: 'flagPoorJudgment', label: 'I have seen a pattern of poor judgment or repeated bad decisions' },
  { key: 'flagOutsizedReward', label: 'They are promising an unusually large reward for very little effort' },
  { key: 'flagFlatteryPressure', label: 'They rely on excessive flattery or high-pressure persuasion' },
  { key: 'flagGossip', label: 'They share other people’s private information inappropriately' },
  { key: 'flagDisregardBoundaries', label: 'They disregard agreements, rules, or boundaries' },
] as const;

// FR-55.2: Gate A of BR-44 — an eight-item, originally-worded decision-
// prudence checklist. Answering honestly that a trap applies is still
// "answered" (FR-55.4) — there is no right answer, only a completed one.
export const DECISION_CHECKLIST_QUESTIONS = [
  { key: 'decisionSkippedResearch', label: 'Have I actually researched this, not just gone with my first instinct?' },
  { key: 'decisionAssumedNoChange', label: 'Am I assuming today’s conditions will hold, rather than considering how things could change?' },
  { key: 'decisionTrustedUnverifiedClaim', label: 'Have I verified the key claims myself, rather than taking them at face value?' },
  { key: 'decisionJudgedByAppearance', label: 'Am I judging this by substance, not just how polished or confident it looks?' },
  { key: 'decisionUnderTimePressure', label: 'Am I deciding on my own timeline, rather than because someone is rushing me?' },
  { key: 'decisionNoOutsideInput', label: 'Have I talked this through with someone outside my own head?' },
  { key: 'decisionChasedEasyReward', label: 'Does the reward here match the effort, rather than looking too easy?' },
  { key: 'decisionDismissedDisagreeingAdvice', label: 'Am I still willing to hear advice that disagrees with what I want to do?' },
] as const;

export type DecisionChecklistKey = (typeof DECISION_CHECKLIST_QUESTIONS)[number]['key'];

/** FR-55.2: "answered" means every item has a value, regardless of what it is. */
export function isDecisionChecklistComplete(answers: Record<DecisionChecklistKey, boolean | null>): boolean {
  return DECISION_CHECKLIST_QUESTIONS.every((question) => answers[question.key] !== null);
}

// FR-55.2: null (not false) for every item — "answered" means non-null,
// regardless of which way it went. Shared by DreamsPage and VisionMapTree,
// the two places that edit a dream's decision checklist.
export const EMPTY_DECISION_ANSWERS: Record<DecisionChecklistKey, boolean | null> = {
  decisionSkippedResearch: null,
  decisionAssumedNoChange: null,
  decisionTrustedUnverifiedClaim: null,
  decisionJudgedByAppearance: null,
  decisionUnderTimePressure: null,
  decisionNoOutsideInput: null,
  decisionChasedEasyReward: null,
  decisionDismissedDisagreeingAdvice: null,
};

export type IntegrityFlagKey = (typeof INTEGRITY_CHECKLIST_QUESTIONS)[number]['key'];

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
