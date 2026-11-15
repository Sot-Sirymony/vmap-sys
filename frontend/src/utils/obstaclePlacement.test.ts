import { describe, expect, it } from 'vitest';
import type { Dream, Goal, Obstacle, TaskItem, VisionStep } from '../types/vision';
import { canTurnIntoStep, groupStepsForPicker, needsPlacing, placementOf, placementText, stepContext } from './obstaclePlacement';

const obstacle = (overrides: Partial<Obstacle>): Obstacle => ({
  id: 1, title: 'No budget', obstacleType: 'MONEY', severity: 'HIGH', status: 'OPEN', archived: false, ...overrides,
} as Obstacle);

const dreams = [{ id: 1, title: 'Publish a study' }, { id: 2, title: 'Open a shop' }] as Dream[];
const goals = [
  { id: 10, dreamId: 1, title: 'Collect data' },
  { id: 20, dreamId: 2, title: 'Find a place' },
] as Goal[];
const steps = [
  { id: 100, goalId: 10, title: 'Run the survey', sequenceNumber: 2, archived: false },
  { id: 101, goalId: 10, title: 'Write questions', sequenceNumber: 1, archived: false },
  { id: 200, goalId: 20, title: 'Visit three shops', sequenceNumber: 1, archived: false },
  { id: 300, goalId: 99, title: 'Orphan step', sequenceNumber: 1, archived: false },
  { id: 400, goalId: 10, title: 'Old step', sequenceNumber: 3, archived: true },
] as VisionStep[];
const tasks = [{ id: 7, stepId: 100, title: 'Print the forms' }] as TaskItem[];

describe('obstacle placement helpers (V17 BL-FR-01 to BL-FR-05, BL-FR-20)', () => {
  it('reads placement from the server, or from the ids for older cached rows', () => {
    expect(placementOf(obstacle({ placement: 'STEP', relatedTaskId: 7 }))).toBe('STEP');
    expect(placementOf(obstacle({ relatedTaskId: 7 }))).toBe('TASK');
    expect(placementOf(obstacle({ relatedStepId: 100 }))).toBe('STEP');
    expect(placementOf(obstacle({ relatedDreamId: 1 }))).toBe('NONE');
  });

  it('lists only active, non-exempt obstacles off the steps, or on an archived step', () => {
    expect(needsPlacing(obstacle({ relatedDreamId: 1 }))).toBe(true);
    expect(needsPlacing(obstacle({ relatedStepId: 100, anchorArchived: true }))).toBe(true);
    expect(needsPlacing(obstacle({ relatedStepId: 100 }))).toBe(false);
    expect(needsPlacing(obstacle({ placementExempt: true }))).toBe(false);
    expect(needsPlacing(obstacle({ archived: true }))).toBe(false);
    expect(needsPlacing(obstacle({ status: 'RESOLVED' }))).toBe(false);
    expect(needsPlacing(obstacle({ status: 'ACCEPTED' }))).toBe(false);
  });

  it('says where an obstacle sits', () => {
    expect(placementText(obstacle({ relatedTaskId: 7 }), steps, tasks)).toBe('Task: Print the forms');
    expect(placementText(obstacle({ relatedTaskId: 8 }), steps, tasks)).toBe('On a task');
    expect(placementText(obstacle({ relatedStepId: 100 }), steps, tasks)).toBe('Step: Run the survey');
    expect(placementText(obstacle({ relatedStepId: 999 }), steps, tasks)).toBe('On a step');
    expect(placementText(obstacle({ placementExempt: true }), steps, tasks)).toBe('Partner conflict');
    expect(placementText(obstacle({}), steps, tasks)).toBe('Not on a step yet');
  });

  it('groups live steps under "Dream · Goal", the preferred dream first, steps in order', () => {
    const groups = groupStepsForPicker(steps, goals, dreams, 2);
    expect(groups.map((group) => group.label)).toEqual(['Open a shop · Find a place', 'Other steps', 'Publish a study · Collect data']);
    const survey = groups.find((group) => group.key === '10');
    expect(survey?.steps.map((step) => step.title)).toEqual(['Write questions', 'Run the survey']);
    // Without a preferred dream, groups are alphabetical.
    expect(groupStepsForPicker(steps, goals, dreams).map((group) => group.label)[0]).toBe('Open a shop · Find a place');
  });

  it('shows the goal and dream of the chosen step', () => {
    expect(stepContext(100, steps, goals, dreams)).toBe('Goal: Collect data · Dream: Publish a study');
    expect(stepContext(100, steps, goals, [])).toBe('Goal: Collect data');
    expect(stepContext(300, steps, goals, dreams)).toBe('');
    expect(stepContext(undefined, steps, goals, dreams)).toBe('');
  });

  it('turns only open, non-exempt obstacles into a step', () => {
    expect(canTurnIntoStep(obstacle({}))).toBe(true);
    expect(canTurnIntoStep(obstacle({ status: 'IN_PROGRESS' }))).toBe(true);
    expect(canTurnIntoStep(obstacle({ placementExempt: true }))).toBe(false);
    expect(canTurnIntoStep(obstacle({ archived: true }))).toBe(false);
    expect(canTurnIntoStep(obstacle({ status: 'RESOLVED' }))).toBe(false);
    expect(canTurnIntoStep(obstacle({ status: 'ACCEPTED' }))).toBe(false);
  });
});
