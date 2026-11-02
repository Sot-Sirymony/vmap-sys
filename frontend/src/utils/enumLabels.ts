import type {
  FearLikelihood,
  CommunicationStatus,
  DreamStatus,
  DreamType,
  EnergyDemand,
  GratitudeCategory,
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
import { tr } from '../i18n/translate';

export const priorityLabels: Record<Priority, string> = {
  LOW: tr('Low'),
  MEDIUM: tr('Medium'),
  HIGH: tr('High'),
  CRITICAL: tr('Critical'),
};

// FR-51: how a dream's or goal's target date relates to its children's.
export const scheduleModeLabels: Record<ScheduleMode, string> = {
  BOTTOM_UP: tr('Bottom-up (default)'),
  TOP_DOWN_FIXED: tr('Fixed deadline (top-down)'),
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
  CHARGE: tr('Charge'),
  NEUTRAL: tr('Neutral'),
  DRAIN: tr('Drain'),
};

export const severityLabels: Record<Severity, string> = {
  LOW: tr('Low'),
  MEDIUM: tr('Medium'),
  HIGH: tr('High'),
  CRITICAL: tr('Critical'),
};

export const workStatusLabels: Record<WorkStatus, string> = {
  NOT_STARTED: tr('Not Started'),
  IN_PROGRESS: tr('In Progress'),
  WAITING: tr('Waiting'),
  BLOCKED: tr('Blocked'),
  PAUSED: tr('Paused'),
  COMPLETED: tr('Completed'),
};

export const dreamStatusLabels: Record<DreamStatus, string> = {
  IDEA: tr('Idea'),
  ACTIVE: tr('Active'),
  PAUSED: tr('Paused'),
  COMPLETED: tr('Completed'),
  ARCHIVED: tr('Archived'),
};

export const dreamTypeLabels: Record<DreamType, string> = {
  SHORT_TERM: tr('Short Term'),
  LONG_TERM: tr('Long Term'),
  LIFETIME: tr('Lifetime'),
};

export const lifecycleStatusLabels: Record<LifecycleStatus, string> = {
  ACTIVE: tr('Active'),
  PAUSED: tr('Paused'),
  COMPLETED: tr('Completed'),
  ARCHIVED: tr('Archived'),
};

export const partnerStatusLabels: Record<PartnerStatus, string> = {
  TO_CONTACT: tr('To Contact'),
  CONTACTED: tr('Contacted'),
  AGREED: tr('Agreed'),
  ACTIVE: tr('Active'),
  WAITING: tr('Waiting'),
  DECLINED: tr('Declined'),
  COMPLETED: tr('Completed'),
};

export const fearLikelihoodLabels: Record<FearLikelihood, string> = {
  LOW: tr('Low'),
  MEDIUM: tr('Medium'),
  HIGH: tr('High'),
};

export const partnerSupportTypeLabels: Record<PartnerSupportType, string> = {
  MENTOR: tr('Mentor'),
  EXPERT: tr('Expert'),
  ADVISOR: tr('Advisor'),
  COLLEAGUE: tr('Colleague'),
  FINANCIAL: tr('Financial'),
  TECHNICAL: tr('Technical'),
  EMOTIONAL: tr('Emotional'),
  OTHER: tr('Other'),
};

export const offerTypeLabels: Record<OfferType, string> = {
  MONEY: tr('Money'),
  SHARED_VISION: tr('Shared Vision'),
  RECOGNITION: tr('Recognition'),
  EXPERIENCE: tr('Experience'),
  OTHER: tr('Other'),
};

// FR-50.4: what drives the partner, as distinct from offerTypeLabels above
// (what the user offers them in return).
export const partnerMotivatorLabels: Record<PartnerMotivator, string> = {
  FINANCIAL_GAIN: tr('Financial gain'),
  AVOIDING_LOSS: tr('Avoiding a loss'),
  SHARED_VISION: tr('Shared vision'),
  RECOGNITION: tr('Recognition'),
  OTHER: tr('Other'),
};

// FR-50.1: seven original, plain-language integrity-and-reliability checks.
export const INTEGRITY_CHECKLIST_QUESTIONS = [
  { key: 'flagDishonesty', label: tr('I have concerns about this person’s honesty or trustworthiness') },
  { key: 'flagAnger', label: tr('I have seen a pattern of volatile anger from them') },
  { key: 'flagPoorJudgment', label: tr('I have seen a pattern of poor judgment or repeated bad decisions') },
  { key: 'flagOutsizedReward', label: tr('They are promising an unusually large reward for very little effort') },
  { key: 'flagFlatteryPressure', label: tr('They rely on excessive flattery or high-pressure persuasion') },
  { key: 'flagGossip', label: tr('They share other people’s private information inappropriately') },
  { key: 'flagDisregardBoundaries', label: tr('They disregard agreements, rules, or boundaries') },
] as const;

// FR-55.2: Gate A of BR-44 — an eight-item, originally-worded decision-
// prudence checklist. Answering honestly that a trap applies is still
// "answered" (FR-55.4) — there is no right answer, only a completed one.
export const DECISION_CHECKLIST_QUESTIONS = [
  { key: 'decisionSkippedResearch', label: tr('Have I actually researched this, not just gone with my first instinct?') },
  { key: 'decisionAssumedNoChange', label: tr('Am I assuming today’s conditions will hold, rather than considering how things could change?') },
  { key: 'decisionTrustedUnverifiedClaim', label: tr('Have I verified the key claims myself, rather than taking them at face value?') },
  { key: 'decisionJudgedByAppearance', label: tr('Am I judging this by substance, not just how polished or confident it looks?') },
  { key: 'decisionUnderTimePressure', label: tr('Am I deciding on my own timeline, rather than because someone is rushing me?') },
  { key: 'decisionNoOutsideInput', label: tr('Have I talked this through with someone outside my own head?') },
  { key: 'decisionChasedEasyReward', label: tr('Does the reward here match the effort, rather than looking too easy?') },
  { key: 'decisionDismissedDisagreeingAdvice', label: tr('Am I still willing to hear advice that disagrees with what I want to do?') },
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

// FR-62.1: four original conduct checks. Answering "No" is still answered
// (FR-62.2) — only a blank answer blocks a PARTNER obstacle's Resolved
// transition (BR-51).
export const CONFLICT_CHECKLIST_QUESTIONS = [
  { key: 'conflictNoCharacterAttacks', label: tr('Avoided attacking character or name-calling') },
  { key: 'conflictStayedOnIncident', label: tr('Stayed on this specific incident, not past ones') },
  { key: 'conflictNoThreatsOrSarcasm', label: tr('Avoided threats, ultimatums, or sarcasm') },
  { key: 'conflictDefinedWinWin', label: tr('Named an outcome that works for both sides, not just one') },
] as const;

export type ConflictChecklistKey = (typeof CONFLICT_CHECKLIST_QUESTIONS)[number]['key'];

/** FR-62.2: "answered" means every item has a value, regardless of what it is. */
export function isConflictChecklistComplete(answers: Record<ConflictChecklistKey, boolean | null>): boolean {
  return CONFLICT_CHECKLIST_QUESTIONS.every((question) => answers[question.key] !== null);
}

export const EMPTY_CONFLICT_CHECKLIST_ANSWERS: Record<ConflictChecklistKey, boolean | null> = {
  conflictNoCharacterAttacks: null,
  conflictStayedOnIncident: null,
  conflictNoThreatsOrSarcasm: null,
  conflictDefinedWinWin: null,
};

export const communicationStatusLabels: Record<CommunicationStatus, string> = {
  DRAFT: tr('Draft'),
  SENT: tr('Sent'),
  FOLLOWED_UP: tr('Followed Up'),
  REPLIED: tr('Replied'),
  CLOSED: tr('Closed'),
};

export const reviewTypeLabels: Record<ReviewType, string> = {
  DAILY: tr('Daily'),
  WEEKLY: tr('Weekly'),
  MONTHLY: tr('Monthly'),
  QUARTERLY: tr('Quarterly'),
};

// FR-59.1: what kind of thing a gratitude entry is about.
export const gratitudeCategoryLabels: Record<GratitudeCategory, string> = {
  GIFT: tr('Gift'),
  HEALTH: tr('Health'),
  PERSON: tr('Person'),
  OTHER: tr('Other'),
};

export const obstacleTypeLabels: Record<ObstacleType, string> = {
  KNOWLEDGE: tr('Knowledge'),
  SKILL: tr('Skill'),
  TIME: tr('Time'),
  MONEY: tr('Money'),
  MOTIVATION: tr('Motivation'),
  PARTNER: tr('Partner'),
  SYSTEM: tr('System'),
  DECISION: tr('Decision'),
  OTHER: tr('Other'),
};

export const obstacleStatusLabels: Record<ObstacleStatus, string> = {
  OPEN: tr('Open'),
  IN_PROGRESS: tr('In Progress'),
  RESOLVED: tr('Resolved'),
  ACCEPTED: tr('Accepted'),
};

// FR-38: in-app issue reporting.
export const reportTypeLabels: Record<ReportType, string> = {
  BUG: tr('Bug'),
  IMPROVEMENT: tr('Improvement'),
  QUESTION: tr('Question'),
  OTHER: tr('Other'),
};

export const issueReportStatusLabels: Record<IssueReportStatus, string> = {
  OPEN: tr('Open'),
  IN_REVIEW: tr('In Review'),
  PLANNED: tr('Planned'),
  IN_PROGRESS: tr('In Progress'),
  RESOLVED: tr('Resolved'),
  CLOSED: tr('Closed'),
  WONT_FIX: "Won't Fix",
};
