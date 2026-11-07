import type { Dream, Goal, Obstacle, Partner, Review, TaskItem, VisionArea, VisionStep } from '../types/vision';

/**
 * COV-FR-09: valid records with sensible defaults. Pass only what a test is
 * about, for example `aTask({ status: 'BLOCKED', blockerReason: 'No budget' })`.
 */

export function aVisionArea(overrides: Partial<VisionArea> = {}): VisionArea {
  return { id: 1, code: 'VA-001', name: 'Career', priority: 'HIGH', status: 'ACTIVE', archived: false, ...overrides };
}

export function aDream(overrides: Partial<Dream> = {}): Dream {
  return {
    id: 1, code: 'D-001', visionAreaId: 1, title: 'Become a public health researcher', whyImportant: 'Help my community',
    successDefinition: 'One published study', dreamType: 'LONG_TERM', priority: 'HIGH', targetDate: '2028-06-30', status: 'ACTIVE',
    moonshot: false, scheduleMode: 'BOTTOM_UP', scheduleOverrun: false, archived: false, ...overrides,
  } as Dream;
}

export function aGoal(overrides: Partial<Goal> = {}): Goal {
  return {
    id: 1, code: 'G-001', dreamId: 1, title: 'Prepare one concept note', successCriteria: 'Mentor approves it', priority: 'HIGH',
    targetDate: '2027-06-30', status: 'IN_PROGRESS', progressPercent: 0, moonshot: false, scheduleMode: 'BOTTOM_UP',
    scheduleOverrun: false, archived: false, ...overrides,
  };
}

export function aStep(overrides: Partial<VisionStep> = {}): VisionStep {
  return {
    id: 1, code: 'S-001', goalId: 1, title: 'Search literature', owner: 'Me', sequenceNumber: 1, complex: false, priority: 'HIGH',
    targetDate: '2027-03-31', status: 'NOT_STARTED', progressPercent: 0, archived: false, ...overrides,
  };
}

export function aTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 1, code: 'T-001', stepId: 1, title: 'Search PubMed', owner: 'Me', priority: 'HIGH', dueDate: '2027-01-31',
    status: 'NOT_STARTED', progressPercent: 0, archived: false, ...overrides,
  };
}

export function aPartner(overrides: Partial<Partner> = {}): Partner {
  return {
    id: 1, code: 'P-001', name: 'Dr. Mentor', role: 'Research lead', supportType: 'MENTOR', status: 'TO_CONTACT', archived: false,
    ...overrides,
  };
}

export function anObstacle(overrides: Partial<Obstacle> = {}): Obstacle {
  return { id: 1, title: 'No time on weekdays', obstacleType: 'TIME', severity: 'HIGH', status: 'OPEN', archived: false, ...overrides } as Obstacle;
}

export function aReview(overrides: Partial<Review> = {}): Review {
  return { id: 1, reviewType: 'WEEKLY', reviewDate: '2026-11-01', summary: 'A steady week', archived: false, ...overrides };
}
