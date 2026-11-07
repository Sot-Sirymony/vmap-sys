import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Dream, PlanDraftContent } from '../../types/vision';
import { GoalsStage } from './GoalsStage';
import { type DraftUpdate, emptyGoal } from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({ suggestGoals: vi.fn(), ratePlanAnswer: vi.fn() }));

const dream = { id: 5, title: 'Own a home', archived: false } as Dream;

function Harness({ initial }: { initial: PlanDraftContent }) {
  const [content, setContent] = useState(initial);
  const change = (update: DraftUpdate) => setContent((current) => (typeof update === 'function' ? update(current) : update));
  return (
    <MemoryRouter>
      <GoalsStage dream={dream} content={content} onChange={change} token="t" language="en" aiReady={false} />
      <output data-testid="draft">{JSON.stringify(content)}</output>
    </MemoryRouter>
  );
}

const titles = () => (JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as PlanDraftContent).goals.map((goal) => goal.title);

describe('Build my plan, phase 8: arranging before adding (PB-FR-22)', () => {
  it('moves, merges and splits new goals from each goal\'s menu, offering only what fits', async () => {
    render(<Harness initial={{ goals: [{ ...emptyGoal(), title: 'Save' }, { ...emptyGoal(), title: 'Invest' }] }} />);

    await userEvent.click(screen.getByRole('button', { name: 'Arrange Save' }));
    expect(screen.queryByRole('menuitem', { name: 'Move up' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Move down' }));
    expect(titles()).toEqual(['Invest', 'Save']);

    await userEvent.click(screen.getByRole('button', { name: 'Arrange Invest' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Merge with the next one' }));
    expect(titles()).toEqual(['Invest and Save']);

    await userEvent.click(screen.getByRole('button', { name: 'Arrange Invest and Save' }));
    expect(screen.queryByRole('menuitem', { name: 'Merge with the next one' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Split into two' }));
    expect(titles()).toEqual(['Invest and Save', '']);
  });
});
