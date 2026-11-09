export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type WorkStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'WAITING' | 'BLOCKED' | 'COMPLETED' | 'PAUSED';
// FR-34.1: how a task affects the user's energy. Optional; absent reads as NEUTRAL.
export type EnergyDemand = 'CHARGE' | 'NEUTRAL' | 'DRAIN';

// FR-35: a synergy link from one goal's side. crossVisionArea marks a link
// whose goals sit in different Vision Areas — a cross-pollination candidate.
export type GoalSynergyLink = {
  id: number;
  goalId: number;
  relatedGoalId: number;
  relatedGoalCode: string;
  relatedGoalTitle: string;
  relatedGoalVisionAreaName: string;
  crossVisionArea: boolean;
  note?: string;
  createdAt: string;
};

export type GoalSynergyLinkRequest = {
  relatedGoalId: number;
  note?: string;
};

// FR-36: a captured lesson, aggregated read-only from a Review or an Obstacle.
export type InsightSource = 'REVIEW' | 'OBSTACLE';
export type InsightKind = 'LESSON_LEARNED' | 'ROOT_CAUSE' | 'CREATIVE_ALTERNATIVES';
export type Insight = {
  source: InsightSource;
  kind: InsightKind;
  sourceId: number;
  sourceTitle: string;
  content: string;
  date?: string;
};
export type LifecycleStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
export type DreamStatus = 'IDEA' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
export type DreamType = 'SHORT_TERM' | 'LONG_TERM' | 'LIFETIME';
export type PartnerStatus = 'TO_CONTACT' | 'CONTACTED' | 'AGREED' | 'ACTIVE' | 'WAITING' | 'DECLINED' | 'COMPLETED';
export type PartnerSupportType = 'MENTOR' | 'EXPERT' | 'ADVISOR' | 'COLLEAGUE' | 'FINANCIAL' | 'TECHNICAL' | 'EMOTIONAL' | 'OTHER';
// FR-15.2: the exchange basis a partner responds to.
export type OfferType = 'MONEY' | 'SHARED_VISION' | 'RECOGNITION' | 'EXPERIENCE' | 'OTHER';
// FR-49: a two-axis (pace x focus) work-style archetype.
export type WorkStyleArchetype = 'DRIVER' | 'CONNECTOR' | 'STEADIER' | 'PLANNER';
// FR-50.4: what drives the partner, distinct from OfferType (what the user offers them).
export type PartnerMotivator = 'FINANCIAL_GAIN' | 'AVOIDING_LOSS' | 'SHARED_VISION' | 'RECOGNITION' | 'OTHER';
export type CommunicationStatus = 'DRAFT' | 'SENT' | 'FOLLOWED_UP' | 'REPLIED' | 'CLOSED';
export type ReviewType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY';
export type ObstacleType = 'KNOWLEDGE' | 'SKILL' | 'TIME' | 'MONEY' | 'MOTIVATION' | 'PARTNER' | 'SYSTEM' | 'DECISION' | 'OTHER';
// FR-61.1: whether the other person actually agreed to the expectation held.
export type ExpectationAgreement = 'YES' | 'NO' | 'UNSURE';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ObstacleStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'ACCEPTED';
// FR-51: how a dream's or goal's target date relates to its children's dates.
// BOTTOM_UP (default) requires the parent date to be no earlier than the
// latest child date; TOP_DOWN_FIXED opts out for a real external deadline.
export type ScheduleMode = 'BOTTOM_UP' | 'TOP_DOWN_FIXED';

export type VisionArea = {
  id: number;
  code: string;
  name: string;
  description?: string;
  visionStatement?: string;
  priority: Priority;
  // Same pattern as Dream.letterRank (FR-57): optional single uppercase
  // letter, a label not a strict order — ties allowed.
  letterRank?: string | null;
  status: LifecycleStatus;
  // Display order among this user's vision areas, for drag-and-drop reorder.
  // Server-managed only — never sent in a request.
  sortOrder?: number | null;
  archived: boolean;
};

export type VisionAreaRequest = {
  name: string;
  description?: string;
  visionStatement?: string;
  priority: Priority;
  letterRank?: string | null;
  status: LifecycleStatus;
};

export type Dream = {
  id: number;
  code: string;
  visionAreaId: number;
  title: string;
  description?: string;
  whyImportant?: string;
  successDefinition?: string;
  dreamType: DreamType;
  priority: Priority;
  // FR-57 / BR-46: optional intra-area label, a single uppercase letter.
  // A rank label, not a strict order — ties within a Vision Area are fine.
  letterRank?: string | null;
  targetDate?: string;
  status: DreamStatus;
  moonshot: boolean;
  moonshotVision?: string;
  // FR-56 / BR-45: optional link to an image representing the fulfilled
  // dream. A URL only — no file upload/storage involved.
  imageUrl?: string | null;
  scheduleMode: ScheduleMode;
  // FR-51: computed — true only when scheduleMode is TOP_DOWN_FIXED and a
  // goal's target date now runs past this dream's fixed target date.
  scheduleOverrun: boolean;
  scheduleOverrunDetail?: string;
  // FR-55.2: Gate A of BR-44 — an eight-item decision-prudence checklist,
  // relevant only for a High/Critical-priority Moonshot dream. "Answered"
  // means non-null regardless of value; never scored (FR-55.4).
  decisionSkippedResearch?: boolean | null;
  decisionAssumedNoChange?: boolean | null;
  decisionTrustedUnverifiedClaim?: boolean | null;
  decisionJudgedByAppearance?: boolean | null;
  decisionUnderTimePressure?: boolean | null;
  decisionNoOutsideInput?: boolean | null;
  decisionChasedEasyReward?: boolean | null;
  decisionDismissedDisagreeingAdvice?: boolean | null;
  // FR-55.4: non-null once BR-44's gate has cleared; never re-fires after.
  decisionGateClearedAt?: string | null;
  // FR-69: the signed commitment that launches this dream. Server-managed,
  // set only through the commitment endpoint.
  commitmentSignedName?: string | null;
  commitmentStartDate?: string | null;
  /** When the dream was created (ISO instant). The timeline's last-resort start date. */
  createdAt?: string;
  commitmentFirstAction?: string | null;
  committedAt?: string | null;
  // Display order among this vision area's dreams, for drag-and-drop
  // reorder. Server-managed only — never sent in a request.
  sortOrder?: number | null;
  archived: boolean;
};

export type DreamRequest = {
  visionAreaId: number;
  title: string;
  description?: string;
  whyImportant?: string;
  successDefinition?: string;
  dreamType: DreamType;
  priority: Priority;
  letterRank?: string | null;
  targetDate?: string;
  status: DreamStatus;
  moonshot: boolean;
  moonshotVision?: string;
  imageUrl?: string | null;
  scheduleMode: ScheduleMode;
  decisionSkippedResearch?: boolean | null;
  decisionAssumedNoChange?: boolean | null;
  decisionTrustedUnverifiedClaim?: boolean | null;
  decisionJudgedByAppearance?: boolean | null;
  decisionUnderTimePressure?: boolean | null;
  decisionNoOutsideInput?: boolean | null;
  decisionChasedEasyReward?: boolean | null;
  decisionDismissedDisagreeingAdvice?: boolean | null;
};

export type Goal = {
  id: number;
  code: string;
  dreamId: number;
  title: string;
  description?: string;
  successCriteria?: string;
  priority: Priority;
  // Same pattern as Dream.letterRank (FR-57): optional single uppercase
  // letter, a label not a strict order — ties allowed.
  letterRank?: string | null;
  targetDate?: string;
  status: WorkStatus;
  progressPercent: number;
  moonshot: boolean;
  moonshotVision?: string;
  scheduleMode: ScheduleMode;
  // FR-51: computed — true only when scheduleMode is TOP_DOWN_FIXED and a
  // step's target date now runs past this goal's fixed target date.
  scheduleOverrun: boolean;
  scheduleOverrunDetail?: string;
  // Display order among this dream's goals, for the Vision Map tree's
  // drag-and-drop reorder. Server-managed only — never sent in a request.
  sortOrder?: number | null;
  archived: boolean;
};

export type GoalRequest = {
  dreamId: number;
  title: string;
  description?: string;
  successCriteria?: string;
  priority: Priority;
  letterRank?: string | null;
  targetDate?: string;
  status: WorkStatus;
  moonshot: boolean;
  moonshotVision?: string;
  scheduleMode: ScheduleMode;
};

export type VisionStep = {
  id: number;
  code: string;
  goalId: number;
  title: string;
  description?: string;
  owner: string;
  sequenceNumber: number;
  complex: boolean;
  priority: Priority;
  targetDate?: string;
  status: WorkStatus;
  progressPercent: number;
  archived: boolean;
  // v0.1 FR-05: the resources and people this step needs.
  resources?: string | null;
  // AI-FR-11: times it was given a new date while overdue.
  overdueReschedules?: number;
  // V11 Phase 4 (PB-FR-12).
  estimatedHours?: number | null;
  startsAfterStepId?: number | null;
};

export type VisionStepRequest = {
  goalId: number;
  title: string;
  description?: string;
  owner: string;
  sequenceNumber: number;
  complex: boolean;
  priority: Priority;
  targetDate?: string;
  status: WorkStatus;
  // Left out keeps what is saved; an empty string clears it.
  resources?: string | null;
  // V11 Phase 4: left out keeps what is saved; startsAfterStepId 0 clears it.
  estimatedHours?: number | null;
  startsAfterStepId?: number | null;
};

export type TaskItem = {
  id: number;
  code: string;
  stepId: number;
  title: string;
  description?: string;
  owner: string;
  priority: Priority;
  // Same pattern as Dream.letterRank (FR-57): optional single uppercase
  // letter, a label not a strict order — ties allowed.
  letterRank?: string | null;
  startDate?: string;
  dueDate: string;
  /** When the task was marked Completed (ISO instant), if it is. */
  completedAt?: string | null;
  status: WorkStatus;
  progressPercent: number;
  estimatedHours?: number;
  actualHours?: number;
  blockerReason?: string;
  nextAction?: string;
  energyDemand?: EnergyDemand;
  // Display order among this step's tasks, for the Vision Map tree's
  // drag-and-drop reorder. Server-managed only — never sent in a request.
  sortOrder?: number | null;
  // The participant's overall order on the Tasks page, across all steps
  // ("this first, that second"). Null until the first drag; sorts last.
  listOrder?: number | null;
  archived: boolean;
};

export type TaskItemRequest = {
  stepId: number;
  title: string;
  description?: string;
  owner: string;
  priority: Priority;
  letterRank?: string | null;
  startDate?: string;
  dueDate: string;
  status: WorkStatus;
  progressPercent: number;
  estimatedHours?: number;
  actualHours?: number;
  blockerReason?: string;
  nextAction?: string;
  energyDemand?: EnergyDemand;
};

export type Partner = {
  id: number;
  code: string;
  name: string;
  role?: string;
  organization?: string;
  email?: string;
  phone?: string;
  strength?: string;
  supportType: PartnerSupportType;
  offerType?: OfferType | null;
  relatedVisionAreaId?: number;
  relatedDreamId?: number;
  relatedGoalId?: number;
  relatedStepId?: number;
  relatedTaskId?: number;
  status: PartnerStatus;
  notes?: string;
  // FR-50.1: seven original integrity-and-reliability checks. Gate (BR-39)
  // fires only the first time a FINANCIAL/TECHNICAL partner moves to Active.
  flagDishonesty?: boolean | null;
  flagAnger?: boolean | null;
  flagPoorJudgment?: boolean | null;
  flagOutsizedReward?: boolean | null;
  flagFlatteryPressure?: boolean | null;
  flagGossip?: boolean | null;
  flagDisregardBoundaries?: boolean | null;
  riskOverrideNote?: string;
  primaryMotivator?: PartnerMotivator | null;
  // FR-50.2: non-null once the BR-39 gate has cleared; never re-fires after.
  vettedAt?: string | null;
  // FR-49.3: the user's own estimate, never a partner self-report.
  workStyleType?: WorkStyleArchetype | null;
  archived: boolean;
};

export type PartnerRequest = {
  name: string;
  role?: string;
  organization?: string;
  email?: string;
  phone?: string;
  strength?: string;
  supportType: PartnerSupportType;
  offerType?: OfferType;
  relatedVisionAreaId?: number;
  relatedDreamId?: number;
  relatedGoalId?: number;
  relatedStepId?: number;
  relatedTaskId?: number;
  status: PartnerStatus;
  notes?: string;
  flagDishonesty?: boolean | null;
  flagAnger?: boolean | null;
  flagPoorJudgment?: boolean | null;
  flagOutsizedReward?: boolean | null;
  flagFlatteryPressure?: boolean | null;
  flagGossip?: boolean | null;
  flagDisregardBoundaries?: boolean | null;
  riskOverrideNote?: string;
  primaryMotivator?: PartnerMotivator | null;
  workStyleType?: WorkStyleArchetype | null;
};

// FR-49.2: dominant is null only when the user has never taken the
// assessment. secondary is null whenever both axes read decisively.
export type WorkStyleProfile = {
  dominant: WorkStyleArchetype | null;
  secondary: WorkStyleArchetype | null;
  paceFastScore: number | null;
  paceDeliberateScore: number | null;
  focusTaskScore: number | null;
  focusPeopleScore: number | null;
};

export type WorkStyleAssessmentRequest = {
  paceFastScore: number;
  paceDeliberateScore: number;
  focusTaskScore: number;
  focusPeopleScore: number;
};

// FR-15.1: the partner a step needs, written down before anyone is recruited.
export type IdealPartnerProfile = {
  id: number;
  stepId: number;
  requiredExperience?: string;
  characterTraits?: string;
  motivation?: string;
  offerInReturn?: string;
  archived: boolean;
};

export type IdealPartnerProfileRequest = Omit<IdealPartnerProfile, 'id' | 'archived'>;

export type CommunicationMessage = {
  id: number;
  partnerId?: number;
  relatedDreamId?: number;
  relatedGoalId?: number;
  relatedTaskId?: number;
  audience?: string;
  purpose?: string;
  subject?: string;
  hook?: string;
  problem?: string;
  request?: string;
  benefitToPartner?: string;
  wordPicture?: string;
  expectedOutcome?: string;
  // FR-52.1: four additive persuasion fields, all optional.
  objectionsAndAnswers?: string;
  socialProof?: string;
  valueComparison?: string;
  callToAction?: string;
  messageBody?: string;
  status: CommunicationStatus;
  followUpDate?: string;
  archived: boolean;
};

export type CommunicationMessageRequest = Omit<CommunicationMessage, 'id' | 'archived'>;

export type Review = {
  id: number;
  reviewType: ReviewType;
  reviewDate: string;
  // Same pattern as Dream.letterRank (FR-57): optional single uppercase
  // letter, a label not a strict order — ties allowed.
  letterRank?: string | null;
  relatedVisionAreaId?: number;
  relatedDreamId?: number;
  summary?: string;
  completedTasks?: string;
  delayedTasks?: string;
  blockedTasks?: string;
  lessonsLearned?: string;
  nextActions?: string;
  diligenceClearVision?: boolean | null;
  diligenceWorkedPlan?: boolean | null;
  diligenceUsedLeverage?: boolean | null;
  diligencePriorityFirst?: boolean | null;
  diligenceSmarterRoute?: boolean | null;
  // FR-53: widens the checklist above from five checks to ten.
  diligenceRightlyPlanned?: boolean | null;
  diligenceRightlyPerformed?: boolean | null;
  diligenceExpeditious?: boolean | null;
  diligenceEfficient?: boolean | null;
  diligenceQualityOutcome?: boolean | null;
  // FR-53: computed server-side — met-count / 10 * 100 once all ten are
  // answered, null while the checklist is skipped. Never sent in a request.
  diligenceScorePercent?: number | null;
  diligenceNote?: string;
  archived: boolean;
};

export type ReviewRequest = Omit<Review, 'id' | 'archived' | 'diligenceScorePercent'> & {
  // FR-59.3: transient — spawns a GratitudeEntry when non-blank, never
  // stored on the Review itself, so it's never part of the `Review` type.
  gratitudeNote?: string;
};

export type Obstacle = {
  id: number;
  relatedDreamId?: number;
  relatedGoalId?: number;
  relatedStepId?: number;
  relatedTaskId?: number;
  title: string;
  description?: string;
  obstacleType: ObstacleType;
  severity: Severity;
  // Same pattern as Dream.letterRank (FR-57): optional single uppercase
  // letter, a label not a strict order — ties allowed.
  letterRank?: string | null;
  solution?: string;
  rootCause?: string;
  creativeAlternatives?: string;
  // FR-54.1: a guided worksheet, offered only for PARTNER-type obstacles.
  // Diagnostic only (FR-54.4) — none of these gate a status transition.
  conflictIncident?: string;
  conflictCost?: string;
  conflictOtherPerspective?: string;
  conflictLesson?: string;
  // FR-54.2 / BR-43: never sent to Excel export.
  conflictPrivateNote?: string;
  conflictNextAction?: string;
  // FR-61.1: diagnostic only — see BR-50.
  conflictExpectation?: string;
  conflictExpectationAgreed?: ExpectationAgreement | null;
  // FR-61.2: non-null once released; never re-fires afterward. Set only via
  // the dedicated release-expectation action, never sent in a request.
  expectationReleasedAt?: string | null;
  // FR-62.1: completeness (not content) gates Resolved for PARTNER-type
  // obstacles — see BR-51.
  conflictNoCharacterAttacks?: boolean | null;
  conflictStayedOnIncident?: boolean | null;
  conflictNoThreatsOrSarcasm?: boolean | null;
  conflictDefinedWinWin?: boolean | null;
  // FR-60.1: diagnostic only — see BR-49.
  criticismOverstated?: string;
  criticismDelivery?: string;
  criticismSubstance?: string;
  requiredPartnerId?: number;
  status: ObstacleStatus;
  archived: boolean;
};

export type ObstacleRequest = Omit<Obstacle, 'id' | 'archived' | 'expectationReleasedAt'>;

export type DreamCommitmentRequest = {
  signedName: string;
  startDate: string;
  firstAction: string;
};

// FR-71: higher means the barrier holds the user back less.
export type ChainAssessment = {
  id: number;
  takenOn: string;
  settlingScore: number;
  fearScore: number;
  criticismScore: number;
  visionScore: number;
  knowledgeScore: number;
  resourcesScore: number;
  averageScore: number;
};

export type ChainScoreKey = 'settlingScore' | 'fearScore' | 'criticismScore' | 'visionScore' | 'knowledgeScore' | 'resourcesScore';

export type ChainAssessmentRequest = Omit<ChainAssessment, 'id' | 'averageScore'>;

export type ChainAssessmentSummary = {
  assessments: ChainAssessment[];
  improvementPercent?: number | null;
  retakeSuggested: boolean;
};

// FR-72: the fifteen modules.
export type ModuleStage = 'BREAK_CHAINS' | 'BOOST' | 'LAUNCH';
export type ModuleStatus = 'LOCKED' | 'AVAILABLE' | 'COMPLETED';
export type ModuleField = { key: string; label: string; type: string };
export type ModuleRow = Record<string, string>;

export type LearningModuleSummary = {
  number: number;
  position: number;
  stage: ModuleStage;
  title: string;
  summary: string;
  status: ModuleStatus;
  completedAt?: string | null;
};

export type LearningModuleDetail = LearningModuleSummary & {
  guidance: string;
  exerciseInstructions: string;
  linkPath?: string | null;
  fields: ModuleField[];
  rows: ModuleRow[];
  requirementMessage?: string | null;
  // V9 (v0.1 FR-11): the coach's rating of the saved exercise.
  coachRating?: number | null;
  coachFeedback?: string | null;
  coachName?: string | null;
  coachRatedAt?: string | null;
};

// FR-78: an admin's view of one module in one language.
export type ModuleContent = {
  number: number;
  position: number;
  language: string;
  translated: boolean;
  title: string;
  summary: string;
  guidance: string;
  exerciseInstructions: string;
  fields: ModuleField[];
};

export type ModuleContentRequest = {
  title: string;
  summary: string;
  guidance: string;
  exerciseInstructions: string;
  fieldLabels?: Record<string, string>;
};

// FR-73: one dream's progress through the guided plan.
export type PlanStatus = {
  dreamId: number;
  stages: { key: string; label: string; done: boolean; detail: string }[];
  planReady: boolean;
};

// FR-74: a result reached on the way to a dream.
export type Milestone = {
  id: number;
  dreamId: number;
  title: string;
  achievedOn: string;
  note?: string | null;
};

export type MilestoneRequest = {
  dreamId: number;
  title: string;
  achievedOn: string;
  note?: string;
};

// FR-75: one objective, measured for this user.
export type ProgressMetric = { id: string; label: string; value: string; target: string; met: boolean };
export type ProgressMetrics = { metrics: ProgressMetric[] };

// FR-77: emailConfigured is false when the server cannot send mail at all.
/** V9: a cohort as an admin manages it. */
export type Cohort = {
  id: number;
  name: string;
  joinCode: string;
  coachId?: number | null;
  coachName?: string | null;
  coachEmail?: string | null;
  startDate?: string | null;
  archived: boolean;
  memberCount: number;
  // V9 (AI-FR-24): AI for this cohort's members.
  aiEnabled: boolean;
};

export type CohortMember = {
  userId: number;
  fullName: string;
  email: string;
};

/** The participant's own cohort; cohortId is null when they have not joined one. */
export type MyCohort = {
  cohortId?: number | null;
  cohortName?: string | null;
  coachName?: string | null;
  startDate?: string | null;
  shareJournalsWithCoach: boolean;
};

export type CoachCommentTarget = 'GENERAL' | 'DREAM' | 'GOAL' | 'STEP';

export type CoachComment = {
  id: number;
  coachName?: string | null;
  targetType: CoachCommentTarget;
  targetId?: number | null;
  targetTitle?: string | null;
  body: string;
  createdAt: string;
  readAt?: string | null;
};

export type CoachParticipant = {
  userId: number;
  fullName: string;
  email: string;
  cohortId: number;
  cohortName: string;
  lastActiveAt: string;
  daysInactive: number;
  atRisk: boolean;
  metrics: ProgressMetrics;
  // AI-FR-19: why they may need attention, each with its reason.
  riskReasons: string[];
  // AI-NFR-03: AI summaries and drafts about them need their own AI consent.
  aiConsent: boolean;
};

/** Journal lists are null unless the participant shares them (D-8). */
export type CoachParticipantDetail = {
  summary: CoachParticipant;
  dreams: Dream[];
  goals: Goal[];
  steps: VisionStep[];
  tasks: TaskItem[];
  journalsShared: boolean;
  fearEntries?: FearEntry[] | null;
  attitudeCheckIns?: AttitudeCheckIn[] | null;
  comments: CoachComment[];
  pitch?: PitchReview | null;
};

// V9 (v0.1 FR-11): a participant's saved pitch (module 11) and the coach's rating.
export type PitchReview = {
  rows: ModuleRow[];
  coachRating?: number | null;
  coachFeedback?: string | null;
  coachName?: string | null;
  coachRatedAt?: string | null;
};

// V9 (v0.1 FR-10): a read-only link to one dream's plan. token is only set
// in the answer that creates it; the server keeps only its hash.
export type PlanShare = {
  id: number;
  dreamId: number;
  dreamTitle: string;
  partnerId?: number | null;
  partnerName?: string | null;
  message?: string | null;
  createdAt: string;
  expiresAt?: string | null;
  revokedAt?: string | null;
  open: boolean;
  lastViewedAt?: string | null;
  viewCount: number;
  token?: string | null;
};

// V9 (v0.1 FR-10): what a partner sees through a share link.
export type SharedPlan = {
  ownerFirstName: string;
  partnerName?: string | null;
  message?: string | null;
  dreamTitle: string;
  dreamDescription?: string | null;
  successDefinition?: string | null;
  targetDate?: string | null;
  progressPercent: number;
  expiresAt?: string | null;
  goals: {
    title: string;
    status: WorkStatus;
    targetDate?: string | null;
    progressPercent: number;
    steps: { title: string; status: WorkStatus; targetDate?: string | null }[];
  }[];
};

/** FR-20: one interface string an admin changed. sourceText is the English the app uses as its key. */
export type UiTextOverride = {
  id: number;
  language: 'en' | 'km';
  sourceText: string;
  text: string;
  updatedAt: string;
};

export type ReminderSettings = {
  enabled: boolean;
  timeZone?: string | null;
  emailConfigured: boolean;
  sendHour: number;
  /** The chosen address, or null to use the login email. */
  reminderEmail?: string | null;
  /** Where reminders actually go. */
  sendsTo: string;
};

// FR-65: the fear log.
export type FearLikelihood = 'LOW' | 'MEDIUM' | 'HIGH';

export type FearEntry = {
  id: number;
  fear: string;
  worstCase: string;
  likelihood: FearLikelihood;
  firstTest: string;
  relatedDreamId?: number | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
};

export type FearEntryRequest = Pick<FearEntry, 'fear' | 'worstCase' | 'likelihood' | 'firstTest'> & {
  relatedDreamId?: number;
};

// FR-65: one attitude score per day.
export type AttitudeCheckIn = {
  id: number;
  checkInDate: string;
  score: number;
  note?: string | null;
};

export type AttitudeCheckInRequest = {
  score: number;
  note?: string;
};

// FR-68: a discipline with its streak, as of the date it was asked for.
export type Habit = {
  id: number;
  name: string;
  checkedOnDate: boolean;
  currentStreak: number;
  archived: boolean;
};

// FR-67: everything the Today page shows.
export type DailyPlanItem = {
  kind: 'STEP' | 'TASK';
  id: number;
  code: string;
  title: string;
  owner: string;
  priority: Priority;
  dueDate: string;
  status: WorkStatus;
  overdue: boolean;
  dreamId: number;
  dreamTitle: string;
};

export type DailyPlan = {
  date: string;
  items: DailyPlanItem[];
  attitude?: AttitudeCheckIn | null;
  habits: Habit[];
  weeklyReviewDue: boolean;
  lastWeeklyReviewDate?: string | null;
  undatedStepCount: number;
};

// FR-59.1: what kind of thing a gratitude entry is about.
export type GratitudeCategory = 'GIFT' | 'HEALTH' | 'PERSON' | 'OTHER';

export type GratitudeEntry = {
  id: number;
  category: GratitudeCategory;
  description: string;
  relatedDreamId?: number;
  relatedGoalId?: number;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
};

export type GratitudeEntryRequest = Omit<GratitudeEntry, 'id' | 'archived' | 'createdAt' | 'updatedAt'>;

// FR-38: in-app issue & improvement reporting.
export type ReportType = 'BUG' | 'IMPROVEMENT' | 'QUESTION' | 'OTHER';
export type IssueReportStatus =
  | 'OPEN'
  | 'IN_REVIEW'
  | 'PLANNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'CLOSED'
  | 'WONT_FIX';

export type IssueReport = {
  id: number;
  code: string;
  reporterId: number;
  reporterName: string;
  reporterEmail: string;
  reportType: ReportType;
  title: string;
  description?: string;
  severity?: Severity;
  contextRoute?: string;
  appVersion?: string;
  status: IssueReportStatus;
  resolutionNote?: string;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
};

// What the reporter submits; contextRoute/appVersion are auto-captured (FR-38.2).
export type IssueReportRequest = {
  reportType: ReportType;
  title: string;
  description?: string;
  severity?: Severity;
  contextRoute?: string;
  appVersion?: string;
};

// FR-38.4: an admin moving a report along its lifecycle, with a resolution note.
export type IssueReportStatusUpdateRequest = {
  status: IssueReportStatus;
  resolutionNote?: string;
};

export type ProgressLog = {
  id: number;
  relatedTaskId: number;
  progressPercentBefore: number;
  progressPercentAfter: number;
  note?: string;
  loggedAt: string;
  archived: boolean;
};

export type ExcelImportSummary = {
  createdRecords: number;
  skippedRecords: number;
  rowsBySheet: Record<string, number>;
  validationErrors: string[];
  // Path of the automatic snapshot saved before the import ran (BRD C-7);
  // null when the import aborted before starting.
  backupFile?: string | null;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type ArchiveImpact = {
  dreams: number;
  goals: number;
  steps: number;
  tasks: number;
};

export type DashboardTrendPoint = {
  weekEnd: string;
  progress: number;
};

export type DashboardAreaProgress = {
  name: string;
  progress: number;
};

export type DashboardSummary = {
  totalVisionAreas: number;
  activeDreams: number;
  activeGoals: number;
  activeTasks: number;
  completedTasks: number;
  overdueTasks: number;
  blockedTasks: number;
  averageProgress: number;
  tasksDueThisWeek: number;
  tasksDueInPeriod: number;
  completedTasksInPeriod: number;
  goalsByStatus: Record<string, number>;
  dreamsByVisionArea: Record<string, number>;
  tasksByStatus: Record<string, number>;
  tasksByPriority: Record<string, number>;
  activeObstaclesByType: Record<string, number>;
  partnersByStatus: Record<string, number>;
  reviewCadence: Record<string, number>;
  progressTrend: DashboardTrendPoint[];
  visionAreaProgress: DashboardAreaProgress[];
  priorityTasks: TaskItem[];
  weeksWithDiligence: number;
  moonshotGoals: number;
  moonshotDreams: number;
  attention: DashboardAttention;
  energyBudget: DashboardEnergyBudget;
  gratitude: DashboardGratitude;
};

/** FR-59.2: a rolling 7-day count plus the 2-3 most recent entries. */
export type DashboardGratitude = {
  countThisWeek: number;
  recent: GratitudeEntry[];
};

/** FR-34.2: this week's tasks by energy demand, CHARGE and DRAIN netted into `net`. */
export type DashboardEnergyBudget = {
  charge: number;
  neutral: number;
  drain: number;
  net: number;
};

/** Places the map has stopped being a map — see DashboardSummaryResponse.Attention. */
export type DashboardAttention = {
  blockedTasksWithoutPartner: TaskItem[];
  complexStepsWithoutTasks: VisionStep[];
  dreamsWithoutGoals: Dream[];
  goalsWithoutSteps: Goal[];
  inactiveMoonshotGoals: Goal[];
  inactiveMoonshotDreams: Dream[];
  // FR-37.1: areas with an active goal but no recent progress while others moved.
  starvedVisionAreas: VisionArea[];
};

// V9 (AI-FR-24): admin AI settings. The API key itself is never sent to the browser.
export type AiKeySource = 'DATABASE' | 'ENVIRONMENT' | 'NONE';

export type AiSettings = {
  enabled: boolean;
  model: string;
  models: { id: string; label: string }[];
  monthlyBudgetUsd: number;
  keySource: AiKeySource;
  keyReadable: boolean;
  apiKeyLast4?: string | null;
  apiKeyUpdatedAt?: string | null;
  apiKeyUpdatedBy?: string | null;
  lastTestAt?: string | null;
  lastTestOk?: boolean | null;
  lastTestMessage?: string | null;
  dailyMessageLimit: number;
  helpLines: string;
  spendThisMonthUsd: number;
  requestsThisMonth: number;
  aiReminders: boolean;
  coachingGuidelines?: string | null;
  // V11 (PB-FR-39): null when plan building uses the main model.
  planModel?: string | null;
};

// V9 Phase 3: the Dream Coach.
export type AiStatusReason = 'OK' | 'OFF' | 'BUDGET_REACHED' | 'NO_CONSENT' | 'DAILY_LIMIT';

export type AiStatus = {
  available: boolean;
  reason: AiStatusReason;
  consentGiven: boolean;
  consentAt?: string | null;
  dailyLimit: number;
  messagesLeftToday: number;
};

export type AiMessage = {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  safetyFlag?: 'CRISIS' | null;
  rating?: number | null;
  createdAt: string;
  // AI-FR-05: the records a reply was based on, such as plan or fears.
  contextSources?: string[];
};

// AI-FR-01: what is on screen, so the Dream Coach picks the right context.
export type AiFocus = {
  page: string;
  dreamId?: number | null;
  moduleNumber?: number | null;
  // V11 Phase 7 (PB-FR-42): set when the message goes to the plan assistant for one section.
  surface?: AssistantSurface | null;
  section?: AssistantSection | null;
  itemRef?: string | null;
};

// V11 Phase 7 (PB-FR-41 to PB-FR-50): the plan assistant on the Plan page (PLAN) and Build my plan (BUILDER).
export type AssistantSurface = 'PLAN' | 'BUILDER';

export type AssistantSection =
  | 'WHOLE' | 'STATEMENT' | 'GOALS' | 'STEPS' | 'DATES' | 'RESOURCES' | 'PEOPLE' | 'MILESTONES'
  | 'CLARIFY' | 'TASKS' | 'HELPERS' | 'REVIEW';

export type SectionIssue = {
  kind: string;
  itemType: string;
  itemRef: string;
  itemTitle?: string | null;
  problem: string;
  fix?: string | null;
};

export type SectionCheck = { surface: AssistantSurface; section: AssistantSection; issues: SectionIssue[]; canPropose: boolean };

export type AssistantChangeKind = 'EDIT' | 'ADD_STEP' | 'ADD_TASK' | 'ADD_RISK';
export type AssistantField = 'TITLE' | 'SUCCESS' | 'WHY' | 'DATE' | 'OWNER';

/** One proposed change card; the participant edits it, then applies or dismisses it. */
export type AssistantChange = {
  kind: AssistantChangeKind;
  itemType: string;
  itemRef?: string | null;
  itemTitle?: string | null;
  field?: AssistantField | null;
  value?: string | null;
  parentRef?: string | null;
  parentType?: string | null;
  parentTitle?: string | null;
  title?: string | null;
  owner?: string | null;
  date?: string | null;
  obstacleType?: string | null;
  severity?: string | null;
  solution?: string | null;
  reason?: string | null;
};

export type AssistantProposals = { changes: AssistantChange[]; note?: string | null; requestId?: number | null };

export type AssistantApplied = { itemType: string; id: number };

export type AiConversation = {
  id: number;
  title: string;
  updatedAt: string;
  messages?: AiMessage[] | null;
  // V11 (PB-FR-50): set for a plan assistant conversation.
  dreamId?: number | null;
  section?: AssistantSection | null;
};

export type AiChatTurn = {
  conversationId: number;
  title: string;
  participantMessage: AiMessage;
  reply: AiMessage;
};

// AI-NFR-09: who may need a check-in, never what they wrote.
export type AiSafetyAlert = {
  id: number;
  participantId: number;
  participantName: string;
  cohortName?: string | null;
  createdAt: string;
  acknowledgedAt?: string | null;
};

// V9 Phase 5 (AI-FR-07 to AI-FR-11): suggestions only; nothing is saved until the participant adds it.
export type VisionAreaSuggestion = {
  name: string;
  description?: string | null;
  visionStatement?: string | null;
  priority: Priority;
  reason?: string | null;
};

// V11 (PB-FR-34): requestId is the AI answer to rate and report outcomes against.
export type VisionAreaSuggestions = { areas: VisionAreaSuggestion[]; requestId?: number | null };

export type DreamSuggestion = {
  title: string;
  description?: string | null;
  whyImportant?: string | null;
  successDefinition?: string | null;
  dreamType: DreamType;
  priority: Priority;
  targetDate?: string | null;
  moonshot: boolean;
  reason?: string | null;
};

export type DreamSuggestions = { dreams: DreamSuggestion[]; clarifyingQuestion?: string | null; requestId?: number | null };

export type GoalSuggestion = {
  title: string;
  description?: string | null;
  successCriteria?: string | null;
  targetDate?: string | null;
  priority: Priority;
  moonshot: boolean;
  reason?: string | null;
};

export type GoalSuggestions = { goals: GoalSuggestion[]; clarifyingQuestion?: string | null; requestId?: number | null };

export type StepSuggestion = {
  title: string;
  description?: string | null;
  owner: string;
  targetDate?: string | null;
  resources?: string | null;
  people?: string | null;
  reason?: string | null;
  // V11 Phase 4 (PB-FR-12): startsAfter is the 1-based position of an earlier step in the same answer.
  priority?: Priority;
  complex?: boolean;
  estimatedHours?: number | null;
  startsAfter?: number | null;
};

// V11 Phase 4 (PB-FR-14): the task builder.
export type TaskSuggestion = {
  title: string;
  owner: string;
  dueDate?: string | null;
  priority: Priority;
  estimatedHours?: number | null;
  reason?: string | null;
};

export type TaskSuggestions = { tasks: TaskSuggestion[]; clarifyingQuestion?: string | null; requestId?: number | null };

export type StepSuggestions = { steps: StepSuggestion[]; clarifyingQuestion?: string | null; requestId?: number | null };

export type PlanIssue = {
  kind: string;
  itemType: 'GOAL' | 'STEP';
  itemId: number;
  itemTitle: string;
  problem: string;
  fix?: string | null;
  fromAi: boolean;
};

export type PlanCheck = { issues: PlanIssue[]; aiChecked: boolean; requestId?: number | null };

// V11 Phase 1 (PB-FR-31, PB-FR-32, PB-FR-36).
export type PlanAddResult = {
  goalIds: number[];
  stepIds: number[];
  taskIds?: number[];
  // V11 Phase 5: partners and obstacles created (Undo archives them) and existing partners given a link (Undo leaves them).
  partnerIds?: number[];
  obstacleIds?: number[];
  linkedPartnerIds?: number[];
};

// V11 Phase 6 (PB-FR-16, PB-FR-28, PB-FR-29): the draft check, its fixes and weekly capacity.
export type DraftFixAction = 'SET_OWNER_ME' | 'SET_DATE' | 'GO_TO_STEPS' | 'GO_TO_TASKS' | 'FIX_WITH_AI';

export type DraftIssue = {
  kind: string;
  itemType: 'GOAL' | 'STEP' | 'TASK';
  itemKey: string;
  parentKey?: string | null;
  itemTitle?: string | null;
  problem: string;
  fix?: string | null;
  fixAction?: DraftFixAction | null;
  fixValue?: string | null;
  fromAi: boolean;
};

export type DraftCheck = { issues: DraftIssue[]; aiChecked: boolean; requestId?: number | null };

export type DraftFix = {
  itemKey: string;
  kind: string;
  title?: string | null;
  targetDate?: string | null;
  newStepTitle?: string | null;
  newStepOwner?: string | null;
  newStepTargetDate?: string | null;
  note?: string | null;
  requestId?: number | null;
};

export type WeekLoad = { weekStart: string; hours: number };

export type PlanCapacity = { hoursPerWeek?: number | null; planned: WeekLoad[] };

// V11 Phase 5 (PB-FR-23 to PB-FR-26, PB-FR-40): helpers, risks and resources for the plan being built.
export type PartnerMatch = { partnerId: number; name: string; role?: string | null; supportType?: string | null; status: string };

export type HelperSuggestion = { role: string; supportType: string; forKey?: string | null; reason?: string | null; matches: PartnerMatch[] };

export type RiskSuggestion = {
  title: string;
  description?: string | null;
  obstacleType: string;
  severity: string;
  solution?: string | null;
  forKey?: string | null;
  helperType: string;
  helperLabel?: string | null;
  fromFears: boolean;
};

export type HelpersAndRisks = {
  helpers: HelperSuggestion[];
  risks: RiskSuggestion[];
  resources: ResourceIdea[];
  requestId?: number | null;
};

/** A helper in the draft: partnerId links one of the participant's partners; without it a new To Contact partner is added. */
export type DraftHelper = {
  key: string;
  role: string;
  supportType: string;
  name?: string | null;
  partnerId?: number | null;
  // Shown in the guide only.
  partnerName?: string | null;
  linkKey?: string | null;
  requestId?: number | null;
  edited?: boolean;
  // The partners that matched, kept in the browser for this session only (the server ignores it).
  matches?: PartnerMatch[];
};

export type DraftRisk = {
  key: string;
  title: string;
  description?: string | null;
  obstacleType: string;
  severity: string;
  solution?: string | null;
  linkKey?: string | null;
  helperKey?: string | null;
  fromFears?: boolean;
  requestId?: number | null;
  edited?: boolean;
};

// V11 Phase 2 (PB-FR-30): a Build my plan draft. An item with existingId stands for a goal or step already in the
// plan; it is never changed, only given new children. Everything else is new and is saved when the plan is added.
export type PlanStage = 'CLARIFY' | 'GOALS' | 'STEPS' | 'TASKS' | 'HELPERS' | 'REVIEW';

export type DraftTask = {
  key: string;
  title: string;
  owner: string;
  dueDate?: string | null;
  priority: Priority;
  estimatedHours?: number | null;
  // V11 Phase 4 (PB-FR-14).
  requestId?: number | null;
  edited?: boolean;
  locked?: boolean;
};

export type DraftStep = {
  key: string;
  existingId?: number | null;
  title: string;
  description?: string | null;
  owner: string;
  targetDate?: string | null;
  priority: Priority;
  complex: boolean;
  resources?: string | null;
  requestId?: number | null;
  edited: boolean;
  locked?: boolean;
  tasks: DraftTask[];
  // V11 Phase 4 (PB-FR-12): effort, and the key of the earlier step in this goal it starts after.
  estimatedHours?: number | null;
  startsAfterKey?: string | null;
};

export type DraftGoal = {
  key: string;
  existingId?: number | null;
  title: string;
  description?: string | null;
  successCriteria?: string | null;
  targetDate?: string | null;
  priority: Priority;
  moonshot: boolean;
  requestId?: number | null;
  edited: boolean;
  locked?: boolean;
  steps: DraftStep[];
};

// V11 Phase 3 (PB-FR-08, PB-FR-09): clarifying answers given in this session; sent with later suggestions.
export type PlanClarification = { question: string; answer: string };

export type PlanDraftContent = {
  goals: DraftGoal[];
  clarifications?: PlanClarification[];
  helpers?: DraftHelper[];
  risks?: DraftRisk[];
};

export type RefineAction = 'FEWER' | 'MORE' | 'SMALLER' | 'BOLDER' | 'EARLIER' | 'LATER' | 'ANOTHER';

/** V11 Phase 3: answers, suggest anyway, and refining the last set (PB-FR-19 to PB-FR-21). */
export type PlanAsk = {
  answers?: PlanClarification[];
  suggestAnyway?: boolean;
  refine?: RefineAction;
  note?: string;
  keep?: string[];
  replace?: string[];
};

export type ClarifyField = 'WHY_IMPORTANT' | 'SUCCESS_DEFINITION' | 'TARGET_DATE';

export type ClarifyQuestion = { question?: string | null; field?: ClarifyField | null; done: boolean; requestId?: number | null };

export type PlanDraft = {
  dreamId: number;
  stage: PlanStage;
  content: PlanDraftContent;
  updatedAt: string;
  expiresAt: string;
  // V11 (PB-NFR-03): AI requests this session has used, and its limit.
  aiRequestsUsed?: number;
  aiRequestLimit?: number;
};

export type PlanFeatureMetrics = {
  feature: string;
  requests: number;
  suggestedItems: number;
  addedItems: number;
  editedItems: number;
  acceptanceRate?: number | null;
  editRate?: number | null;
  ratings: number;
  thumbsUpRate?: number | null;
  costUsd: number;
  averageCostUsd: number;
  // V11 Phase 8 (PB-FR-36): this feature's requests and cost per plan finished in the period.
  requestsPerFinishedPlan?: number | null;
  costPerFinishedPlan?: number | null;
};

export type PlanMetrics = {
  days: number;
  features: PlanFeatureMetrics[];
  // V11 (PB-FR-36): plans added from Build my plan in the period.
  finishedPlans?: number;
  requestsPerFinishedPlan?: number | null;
  costPerFinishedPlan?: number | null;
};

export type SlippingStep = {
  stepId: number;
  title: string;
  goalId: number;
  goalTitle: string;
  targetDate: string;
  overdueReschedules: number;
};

export type StepRescue = {
  stepId: number;
  smallerStep?: string | null;
  newDate?: string | null;
  helper?: string | null;
  reason?: string | null;
};

// V9 Phase 6 (AI-FR-12 to AI-FR-17): daily-use suggestions.
export type NextStepPick = {
  itemType: 'STEP' | 'TASK';
  itemId: number;
  title: string;
  dueDate?: string | null;
  reason?: string | null;
  minutes?: number | null;
};

export type NextStep = { picks: NextStepPick[]; note?: string | null };

export type WeeklyReviewDraft = {
  summary?: string | null;
  completedTasks?: string | null;
  delayedTasks?: string | null;
  blockedTasks?: string | null;
  lessonsLearned?: string | null;
  nextActions?: string | null;
};

export type PitchAudience = 'PARTNER' | 'BUYER' | 'SPONSOR';

export type PitchFeedback = {
  audience: PitchAudience;
  reaction?: string | null;
  clarity: number;
  benefit: number;
  ask: number;
  improvedPitch?: string | null;
  tip?: string | null;
};

export type OutreachDraft = { subject?: string | null; message?: string | null };

export type ResourceIdea = { kind: string; name: string; why?: string | null; checkBeforeUse: boolean; url?: string | null };

// V9 Phase 7: AI help in module exercises (drafts never save or unlock anything).
export type ModuleHelpMode = 'START' | 'DRAFT' | 'FEEDBACK';

export type ModuleHelp = {
  mode: ModuleHelpMode;
  questions: string[];
  example?: string | null;
  rows: ModuleRow[];
  feedback?: string | null;
  improvements: string[];
};

export type AssessmentExplanation = {
  explanation?: string | null;
  focusModule?: number | null;
  focusTitle?: string | null;
  reason?: string | null;
};

// V9 Phase 8 (AI-FR-18 to AI-FR-21): the coach co-pilot.
export type AiParticipantSummary = {
  id: number;
  weekStart: string;
  generatedAt: string;
  progress?: string | null;
  mood?: string | null;
  risks?: string | null;
  suggestedAction?: string | null;
  riskSignals: string[];
  accurate?: boolean | null;
  ratingNote?: string | null;
};

export type AiCohortSummary = {
  id: number;
  weekStart: string;
  generatedAt: string;
  overview?: string | null;
  commonBlockers?: string | null;
  strugglingModules?: string | null;
  suggestedActions?: string | null;
  accurate?: boolean | null;
  ratingNote?: string | null;
};

// V9 Phase 9: admin AI.
export type ApprovedResource = {
  id: number;
  kind: string;
  name: string;
  url?: string | null;
  description?: string | null;
  active: boolean;
};

export type ModuleContentDraft = {
  title?: string | null;
  summary?: string | null;
  guidance?: string | null;
  exerciseInstructions?: string | null;
  examples?: string | null;
  quiz: { question: string; answer?: string | null }[];
};

// V8.1 FR-79 to FR-84: the Strengths and Gaps profile.
export type StrengthKind = 'STRENGTH' | 'WEAKNESS';
export type Relevance = 'HIGH' | 'MEDIUM' | 'LOW';
export type GapCoverMethod = 'PARTNER' | 'DELEGATE' | 'RESOURCE' | 'LEARN' | 'ACCEPT';
export type SelfAssessmentSource = 'MANUAL' | 'WORK_STYLE' | 'SIX_CHAIN';

export type SelfAssessmentItem = {
  id: number;
  dreamId: number;
  kind: StrengthKind;
  description: string;
  evidence: string;
  relevance: Relevance;
  source: SelfAssessmentSource;
  outsideBy?: string | null;
  outsideView?: string | null;
  outsideAgrees?: boolean | null;
  outsideMismatch: boolean;
  coverMethod?: GapCoverMethod | null;
  coverNote?: string | null;
  linkedPartnerId?: number | null;
  linkedStepId?: number | null;
  linkedModuleNumber?: number | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
};

export type SelfAssessmentItemRequest = {
  dreamId: number;
  kind: StrengthKind;
  description: string;
  evidence: string;
  relevance: Relevance;
  source?: SelfAssessmentSource;
  outsideBy?: string;
  outsideView?: string;
  outsideAgrees?: boolean | null;
};

export type GapCoverRequest = {
  method: GapCoverMethod;
  note?: string;
  partnerName?: string;
  partnerSupportType?: PartnerSupportType;
  goalId?: number;
  targetDate?: string;
};

export type GapCoverRemoval = {
  linkedItemRemoved: boolean;
  note?: string | null;
};

export type StrengthsProfileChange = {
  type: 'ADDED' | 'REMOVED' | 'CHANGED';
  kind: StrengthKind;
  description: string;
  relevanceBefore?: Relevance | null;
  relevanceAfter?: Relevance | null;
  coverBefore?: GapCoverMethod | null;
  coverAfter?: GapCoverMethod | null;
};

export type StrengthsProfile = {
  dreamId: number;
  dreamTitle: string;
  whatIBring: SelfAssessmentItem[];
  whatILack: SelfAssessmentItem[];
  recordedOnly: SelfAssessmentItem[];
  complete: boolean;
  uncoveredGaps: number;
  learnInUse: number;
  learnLimit: number;
  lastSavedOn?: string | null;
  comparedWith?: string | null;
  retakeSuggested: boolean;
  changes: StrengthsProfileChange[];
};

/** Diagnostics: one request that failed in the browser. status 0 = server not reached. */
export type ClientErrorLogRequest = {
  page: string;
  method: string;
  path: string;
  status: number;
  message?: string;
  reference?: string;
  appVersion?: string;
};

export type ClientErrorLogEntry = {
  id: number;
  occurredAt: string;
  /** Set only in the admin view. */
  userId: number | null;
  page: string;
  method: string;
  path: string;
  status: number;
  message: string | null;
  reference: string | null;
  appVersion: string | null;
};
