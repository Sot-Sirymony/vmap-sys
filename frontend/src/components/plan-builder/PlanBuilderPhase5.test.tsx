import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { draftOutreach } from '../../api/aiDailyApi';
import { suggestHelpersAndRisks } from '../../api/aiPlanApi';
import type { Dream, PlanDraftContent } from '../../types/vision';
import { HelpersStage } from './HelpersStage';
import { NewContactsOutreach } from './NewContactsOutreach';
import { type DraftUpdate, emptyGoal, helperFromSuggestion, removeHelper, riskFromSuggestion } from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({ suggestHelpersAndRisks: vi.fn(), ratePlanAnswer: vi.fn() }));
vi.mock('../../api/aiDailyApi', () => ({ draftOutreach: vi.fn() }));

const dream = { id: 5, title: 'Own a home', archived: false } as Dream;

function Harness({ initial }: { initial: PlanDraftContent }) {
  const [content, setContent] = useState(initial);
  const change = (update: DraftUpdate) => setContent((current) => (typeof update === 'function' ? update(current) : update));
  return (
    <MemoryRouter>
      <HelpersStage dream={dream} content={content} onChange={change} token="t" language="en" aiReady />
      <output data-testid="draft">{JSON.stringify(content)}</output>
    </MemoryRouter>
  );
}

const draft = () => JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as PlanDraftContent;

describe('Build my plan, phase 5', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(suggestHelpersAndRisks).mockReset();
  });

  it('links a matching partner by default, ties risks to the helper that fits, and uses fears only when ticked', async () => {
    vi.mocked(suggestHelpersAndRisks).mockResolvedValue({
      requestId: 3,
      helpers: [{ role: 'Mortgage adviser', supportType: 'FINANCIAL', forKey: 'g1', reason: 'Loans',
        matches: [{ partnerId: 11, name: 'Sokha Chan', role: 'Mortgage adviser', supportType: 'FINANCIAL', status: 'ACTIVE' }] }],
      risks: [{ title: 'Deposit takes longer', obstacleType: 'MONEY', severity: 'HIGH', helperType: 'FINANCIAL',
        helperLabel: 'Financial partner or sponsor', fromFears: true, forKey: 'g1' }],
      resources: [{ kind: 'BOOK', name: 'Home buying basics', checkBeforeUse: true }],
    });
    const goal = { ...emptyGoal(), key: 'g1', title: 'Save a deposit' };
    render(<Harness initial={{ goals: [goal] }} />);

    await userEvent.click(screen.getByRole('checkbox', { name: 'Also use my fear journal for this' }));
    await userEvent.click(screen.getByRole('button', { name: 'Suggest helpers and risks' }));
    expect(suggestHelpersAndRisks).toHaveBeenCalledWith('t', 5, 'en', {
      items: [{ key: 'g1', kind: 'GOAL', title: 'Save a deposit' }], useFears: true, ask: { answers: [] },
    });

    expect(await screen.findByDisplayValue('Deposit takes longer')).toBeInTheDocument();
    expect(screen.getByText('From your fear journal')).toBeInTheDocument();
    expect(screen.getByText('Who fits this kind of gap: Financial partner or sponsor')).toBeInTheDocument();
    expect(screen.getByText('Check before use')).toBeInTheDocument();
    const [helper] = draft().helpers ?? [];
    expect(helper).toMatchObject({ partnerId: 11, partnerName: 'Sokha Chan', linkKey: 'g1', requestId: 3 });
    expect(draft().risks?.[0]).toMatchObject({ helperKey: helper.key, linkKey: 'g1', fromFears: true });

    // Choosing a new contact instead clears the link and asks for a name.
    await userEvent.click(screen.getByRole('combobox', { name: 'Who' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Add as a new contact to reach' }));
    expect(draft().helpers?.[0]).toMatchObject({ partnerId: null, edited: true });
    expect(screen.getByRole('textbox', { name: 'Name, or how you will find them' })).toBeInTheDocument();
  });

  it('works by hand without AI suggestions', async () => {
    render(<Harness initial={{ goals: [{ ...emptyGoal(), title: 'Save' }] }} />);
    await userEvent.click(screen.getByRole('button', { name: 'Add a risk' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Risk' }), 'Prices go up');
    expect(draft().risks?.[0]).toMatchObject({ title: 'Prices go up', obstacleType: 'OTHER', severity: 'MEDIUM' });
    expect(suggestHelpersAndRisks).not.toHaveBeenCalled();
  });

  it('forgets a removed helper on the risks that named it', () => {
    const helper = helperFromSuggestion({ role: 'Adviser', supportType: 'ADVISOR', matches: [] }, 1);
    const risk = riskFromSuggestion({ title: 'Unsure', obstacleType: 'DECISION', severity: 'LOW', helperType: 'ADVISOR', fromFears: false },
      [helper], 1);
    expect(helper.partnerId).toBeNull();
    expect(risk.helperKey).toBe(helper.key);
    const next = removeHelper({ goals: [], helpers: [helper], risks: [risk] }, helper.key);
    expect(next.helpers).toEqual([]);
    expect(next.risks?.[0].helperKey).toBeNull();
  });

  it('drafts a message for a new contact, to copy and send yourself (PB-FR-27)', async () => {
    vi.mocked(draftOutreach).mockResolvedValue({ subject: 'Could you advise me?', message: 'Dear adviser, ...' });
    render(<NewContactsOutreach contacts={[{ partnerId: 41, name: 'Property lawyer', role: 'Property lawyer' }]}
      dreamId={5} dreamTitle="Own a home" token="t" language="en" />);

    await userEvent.click(screen.getByRole('button', { name: 'Draft a message' }));
    expect(draftOutreach).toHaveBeenCalledWith('t', expect.objectContaining({ partnerId: 41, relatedDreamId: 5 }), 'en');
    expect(await screen.findByDisplayValue('Could you advise me?')).toBeInTheDocument();
    expect(screen.getByText('Copy the message and send it yourself. Nothing is sent from here.')).toBeInTheDocument();
  });
});
