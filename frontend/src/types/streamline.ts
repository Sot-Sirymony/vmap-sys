// V27 (BRD 27): the Streamline review — question, delete, simplify, speed up, automate.

export type StreamlineStatus = 'OPEN' | 'COMPLETED' | 'ABANDONED';
export type StageState = 'PENDING' | 'DONE' | 'NOT_NEEDED';
export type RequirementSource = 'SELF' | 'PARTNER' | 'MENTOR' | 'COACH' | 'ORGANISATION' | 'NOT_SURE';
export type StreamlineVerdict = 'NEEDED' | 'NOT_SURE' | 'NOT_NEEDED';
export type StreamlineDecision = 'KEEP' | 'DELETE' | 'MERGE';
export type AutomationChoice = 'REPEAT' | 'REMIND' | 'DELEGATE' | 'MANUAL';
export type SpeedLever = 'REMOVE_WAIT' | 'UNBLOCK_FIRST' | 'PARALLEL' | 'BATCH' | 'SHORTEN_LOOP' | 'CLOSER_DATE';
export type RepeatCadence = 'DAILY' | 'WEEKLY' | 'MONTHLY';

export type StreamlineStage = { number: number; state: StageState; reason: string | null; unlocked: boolean };

export type StreamlineItem = {
  id: number;
  kind: 'STEP' | 'TASK';
  stepId: number | null;
  taskId: number | null;
  parentStepId: number | null;
  code: string;
  title: string;
  status: string;
  archived: boolean;
  estimatedHours: number | null;
  requirementOwner: string | null;
  requirementSource: RequirementSource | null;
  partnerId: number | null;
  whyNeeded: string | null;
  verdict: StreamlineVerdict | null;
  decision: StreamlineDecision | null;
  mergeIntoItemId: number | null;
  keepReason: string | null;
  simplified: boolean;
  simplifyNote: string | null;
  speedLevers: SpeedLever[];
  automation: AutomationChoice | null;
  delegatePartnerId: number | null;
  archivedWithParent: boolean;
  archivedAt: string | null;
  restoredAt: string | null;
  cycleTimeDays: number | null;
  waitingDays: number | null;
  blockedDays: number | null;
  repeatCandidate: boolean;
  repeatRuleId: number | null;
  /** V32 D-32-9: how often a "Remind me" item reminds; day is the weekday (1 = Monday) or the day of the month. */
  remindCadence?: RepeatCadence | null;
  remindDay?: number | null;
};

export type StreamlineSummary = {
  deleted: number;
  kept: number;
  merged: number;
  addedBack: number;
  stepsBefore: number;
  stepsAfter: number;
  tasksBefore: number;
  tasksAfter: number;
  hoursBefore: number;
  hoursAfter: number;
  cycleBefore: number | null;
  cycleTarget: number | null;
  cycleNow: number | null;
  repeating: number;
  reminders: number;
  delegated: number;
  nothingBroughtBack: boolean;
};

export type StreamlineReview = {
  id: number;
  code: string;
  anchorType: 'GOAL' | 'STEP';
  goalId: number | null;
  stepId: number | null;
  anchorCode: string;
  anchorTitle: string;
  status: StreamlineStatus;
  currentStage: number;
  stages: StreamlineStage[];
  baselineCycleDays: number | null;
  targetCycleDays: number | null;
  currentCycleDays: number | null;
  waitingDays: number;
  blockedDays: number;
  startedAt: string;
  completedAt: string | null;
  items: StreamlineItem[];
  summary: StreamlineSummary;
};

export type StreamlineListItem = {
  id: number;
  code: string;
  anchorType: 'GOAL' | 'STEP';
  goalId: number | null;
  stepId: number | null;
  anchorTitle: string;
  status: StreamlineStatus;
  currentStage: number;
  startedAt: string;
  completedAt: string | null;
  deleted: number;
  addedBack: number;
  stepsBefore: number;
  stepsAfter: number;
  tasksBefore: number;
  tasksAfter: number;
};

export type StreamlineCandidate = {
  anchorType: 'GOAL' | 'STEP';
  anchorId: number;
  code: string;
  title: string;
  reason: 'MANY_STEPS' | 'OVERDUE' | 'MANY_TASKS' | 'REPEATED_WORK' | 'CYCLE_TIME_DOUBLED';
  detail: string;
};

export type StreamlineItemUpdate = Partial<{
  requirementOwner: string;
  requirementSource: RequirementSource;
  partnerId: number;
  whyNeeded: string;
  verdict: StreamlineVerdict;
  keep: boolean;
  keepReason: string;
  speedLevers: SpeedLever[];
  automation: AutomationChoice;
  delegatePartnerId: number;
  remindCadence: RepeatCadence;
  remindDay: number;
}>;

export type StreamlineSimplify = Partial<{
  title: string;
  description: string;
  owner: string;
  estimatedHours: number;
  markSimple: boolean;
  note: string;
}>;

export type StreamlineSuggestion = {
  itemId: number;
  code: string;
  title: string;
  action: 'QUESTION' | 'VERDICT' | 'ANSWER' | 'DELETE' | 'MERGE' | 'KEEP' | 'SIMPLIFY' | 'LEVER' | 'REPEAT' | 'DELEGATE' | 'REMIND';
  detail: string;
  reason: string;
  targetItemId: number | null;
  verdict: StreamlineVerdict | null;
  lever: SpeedLever | null;
  cadence: RepeatCadence | null;
  /** V30 D-30-7: a stage 1 draft (action ANSWER). The owner is a role, never a person's name. */
  requirementSource?: RequirementSource | null;
  requirementOwner?: string | null;
  whyNeeded?: string | null;
  /** V30 D-30-8: a SIMPLIFY suggestion's new title and smaller scope. */
  newTitle?: string | null;
  newDescription?: string | null;
};

/** V30 D-30-4: a goal or step the AI suggests streamlining. */
export type StreamlinePick = { anchorType: 'GOAL' | 'STEP'; anchorId: number; code: string; title: string; reason: string };
export type StreamlinePicks = { requestId: number | null; candidates: StreamlinePick[] };

/** V30 D-30-9: advice on a completed review. */
export type StreamlineAdvice = { requestId: number | null; points: { text: string; itemCode: string | null }[] };

/** V32 D-32-1: savedAt is set when the cards were saved earlier, and null for a fresh answer. */
export type StreamlineSuggestions = {
  requestId: number | null;
  stage: number;
  suggestions: StreamlineSuggestion[];
  savedAt?: string | null;
  /** V32 D-32-7: drafted by Draft all stages before the earlier stages were finished. */
  draftedBeforeChanges?: boolean;
};

/** V32 D-32-7: the stages drafted in one request. */
export type StreamlineDraftAll = { requestId: number | null; stages: StreamlineSuggestions[] };

/** V32 (D-32-7, D-32-8): saved drafts for the current and later stages; preview stages cannot be applied yet. */
export type StreamlineDrafts = {
  stages: { stage: number; preview: boolean; suggestions: StreamlineSuggestions }[];
  canDraftAll: boolean;
  unavailableReason: string | null;
};

/** V32 D-32-4: one action of a batch, in the shape of the single actions. */
export type StreamlineBatchAction =
  | { type: 'UPDATE'; itemId: number; update: StreamlineItemUpdate }
  | { type: 'DELETE'; itemId: number }
  | { type: 'MERGE'; itemId: number; targetItemId: number }
  | { type: 'SIMPLIFY'; itemId: number; simplify: StreamlineSimplify }
  | { type: 'REPEAT'; itemId: number; cadence: RepeatCadence };

/** V32 D-32-4: batchId is null when nothing was applied. */
export type StreamlineBatchResult = {
  batchId: number | null;
  applied: number;
  total: number;
  failedAtPosition: number | null;
  error: string | null;
  review: StreamlineReview;
};

/** V32 SF-FR-07: one batch in Recent changes. */
export type StreamlineBatchView = {
  id: number;
  stage: number;
  applied: number;
  total: number;
  failedAtPosition: number | null;
  createdAt: string;
  undoneAt: string | null;
  canUndo: boolean;
  changes: { code: string; action: StreamlineBatchAction['type'] }[];
};

/** V32 D-32-5, BR-32-6: skipped items were changed after the batch and left as they are. */
export type StreamlineUndoResult = { undone: number; skipped: { code: string; reason: string }[]; review: StreamlineReview };

export type StreamlineDashboard = {
  openReviews: number;
  cutThisQuarter: number;
  addedBackThisQuarter: number;
  medianCycleDays: number | null;
  cycleTrend: { weekEnd: string; medianDays: number | null; tasks: number }[];
  /** V32 D-32-11: this week's Streamline suggestion, or null. */
  nudge?: StreamlineCandidate | null;
};

export type StreamlineRepeatRule = {
  id: number;
  stepId: number;
  sourceTaskId: number | null;
  lastTaskId: number | null;
  title: string;
  cadence: RepeatCadence;
  nextDue: string;
  active: boolean;
};
