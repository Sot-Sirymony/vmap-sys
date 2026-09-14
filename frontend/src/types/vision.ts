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
export type PartnerStatus = 'TO_CONTACT' | 'CONTACTED' | 'ACTIVE' | 'WAITING' | 'DECLINED' | 'COMPLETED';
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
  status: LifecycleStatus;
  archived: boolean;
};

export type VisionAreaRequest = {
  name: string;
  description?: string;
  visionStatement?: string;
  priority: Priority;
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
  archived: boolean;
};

export type GoalRequest = {
  dreamId: number;
  title: string;
  description?: string;
  successCriteria?: string;
  priority: Priority;
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
  sequenceNumber: number;
  complex: boolean;
  priority: Priority;
  targetDate?: string;
  status: WorkStatus;
  progressPercent: number;
  archived: boolean;
};

export type VisionStepRequest = {
  goalId: number;
  title: string;
  description?: string;
  sequenceNumber: number;
  complex: boolean;
  priority: Priority;
  targetDate?: string;
  status: WorkStatus;
};

export type TaskItem = {
  id: number;
  code: string;
  stepId: number;
  title: string;
  description?: string;
  owner: string;
  priority: Priority;
  startDate?: string;
  dueDate: string;
  status: WorkStatus;
  progressPercent: number;
  estimatedHours?: number;
  actualHours?: number;
  blockerReason?: string;
  nextAction?: string;
  energyDemand?: EnergyDemand;
  archived: boolean;
};

export type TaskItemRequest = {
  stepId: number;
  title: string;
  description?: string;
  owner: string;
  priority: Priority;
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

export type ReviewRequest = Omit<Review, 'id' | 'archived' | 'diligenceScorePercent'>;

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
  requiredPartnerId?: number;
  status: ObstacleStatus;
  archived: boolean;
};

export type ObstacleRequest = Omit<Obstacle, 'id' | 'archived' | 'expectationReleasedAt'>;

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
