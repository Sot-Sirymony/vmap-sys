export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type WorkStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'WAITING' | 'BLOCKED' | 'COMPLETED' | 'PAUSED';
export type LifecycleStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
export type DreamStatus = 'IDEA' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
export type DreamType = 'SHORT_TERM' | 'LONG_TERM' | 'LIFETIME';
export type PartnerStatus = 'TO_CONTACT' | 'CONTACTED' | 'ACTIVE' | 'WAITING' | 'DECLINED' | 'COMPLETED';
export type PartnerSupportType = 'MENTOR' | 'EXPERT' | 'ADVISOR' | 'COLLEAGUE' | 'FINANCIAL' | 'TECHNICAL' | 'EMOTIONAL' | 'OTHER';
export type CommunicationStatus = 'DRAFT' | 'SENT' | 'FOLLOWED_UP' | 'REPLIED' | 'CLOSED';
export type ReviewType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY';
export type ObstacleType = 'KNOWLEDGE' | 'SKILL' | 'TIME' | 'MONEY' | 'MOTIVATION' | 'PARTNER' | 'SYSTEM' | 'DECISION' | 'OTHER';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ObstacleStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'ACCEPTED';

export type VisionArea = {
  id: number;
  code: string;
  name: string;
  description?: string;
  priority: Priority;
  status: LifecycleStatus;
  archived: boolean;
};

export type VisionAreaRequest = {
  name: string;
  description?: string;
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
  targetDate?: string;
  status: DreamStatus;
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
  targetDate?: string;
  status: DreamStatus;
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
  relatedVisionAreaId?: number;
  relatedDreamId?: number;
  relatedGoalId?: number;
  relatedStepId?: number;
  relatedTaskId?: number;
  status: PartnerStatus;
  notes?: string;
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
  relatedVisionAreaId?: number;
  relatedDreamId?: number;
  relatedGoalId?: number;
  relatedStepId?: number;
  relatedTaskId?: number;
  status: PartnerStatus;
  notes?: string;
};

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
  diligenceNote?: string;
  archived: boolean;
};

export type ReviewRequest = Omit<Review, 'id' | 'archived'>;

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
  requiredPartnerId?: number;
  status: ObstacleStatus;
  archived: boolean;
};

export type ObstacleRequest = Omit<Obstacle, 'id' | 'archived'>;

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
  attention: DashboardAttention;
};

/** Places the map has stopped being a map — see DashboardSummaryResponse.Attention. */
export type DashboardAttention = {
  blockedTasksWithoutPartner: TaskItem[];
  complexStepsWithoutTasks: VisionStep[];
  dreamsWithoutGoals: Dream[];
  goalsWithoutSteps: Goal[];
};
