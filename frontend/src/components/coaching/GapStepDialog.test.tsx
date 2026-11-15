import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GapStepDialog } from './GapStepDialog';
import type { Goal, Obstacle, VisionStep } from '../../types/vision';

const createGapStep = vi.fn();
const convertObstacleToStep = vi.fn();
vi.mock('../../api/stepApi', () => ({ createGapStep: (...args: unknown[]) => createGapStep(...args) }));
vi.mock('../../api/obstacleApi', () => ({ convertObstacleToStep: (...args: unknown[]) => convertObstacleToStep(...args) }));

const step = {
  id: 12, code: 'S-012', goalId: 4, title: 'Analyse the survey', owner: 'Me', sequenceNumber: 2, complex: false,
  priority: 'HIGH', status: 'NOT_STARTED', progressPercent: 0, archived: false, targetDate: '2099-03-10',
  resources: 'no statistics skills', gapKind: 'SKILL',
} as VisionStep;

const goals = [{ id: 4, dreamId: 2, title: 'Publish the study', archived: false }] as Goal[];

describe('Turn this gap into a step (V17 BL-FR-19, BL-FR-20)', () => {
  beforeEach(() => {
    createGapStep.mockReset().mockResolvedValue({ step, partner: null, obstacle: null });
    convertObstacleToStep.mockReset().mockResolvedValue({ step, partner: null, obstacle: null });
  });

  it('pre-fills a find-help step and a partner from a step gap', async () => {
    const onSaved = vi.fn();
    render(<GapStepDialog token="t" source={{ kind: 'step', step }} goals={goals} ownerName="Dara" onClose={vi.fn()} onSaved={onSaved} />);

    expect(screen.getByDisplayValue('Find a technical expert or trainer for no statistics skills')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2099-03-09')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Also add a partner to contact' })).toBeChecked();

    await userEvent.click(screen.getByRole('button', { name: 'Save step' }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(createGapStep).toHaveBeenCalledWith('t', 12, expect.objectContaining({
      title: 'Find a technical expert or trainer for no statistics skills', owner: 'Dara', gapKind: 'SKILL',
      createPartner: true, partnerSupportType: 'TECHNICAL', goalId: undefined,
    }));
  });

  it('asks for a goal when the obstacle is not on a step yet', async () => {
    const obstacle = { id: 7, title: 'No budget for a translator', obstacleType: 'MONEY', status: 'OPEN', archived: false,
      relatedDreamId: 2 } as Obstacle;
    render(<GapStepDialog token="t" source={{ kind: 'obstacle', obstacle }} goals={goals} ownerName="Dara" onClose={vi.fn()} onSaved={vi.fn()} />);

    expect(screen.getByText('The blocker will sit on the new step and move to In Progress.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save step' }));
    await waitFor(() => expect(convertObstacleToStep).toHaveBeenCalledWith('t', 7, expect.objectContaining({
      goalId: 4, title: 'Find a financial partner or sponsor for No budget for a translator', createPartner: true,
    })));
  });
});
