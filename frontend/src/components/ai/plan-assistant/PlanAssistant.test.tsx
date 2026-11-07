import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus, listAiConversations } from '../../../api/aiApi';
import { applyAssistantChange, checkAssistantSection, proposeAssistantChanges, recordPlanOutcome } from '../../../api/aiPlanApi';
import type { AiStatus, AssistantSection, SectionCheck } from '../../../types/vision';
import { type AssistantTopic, HelpWithSectionButton, PlanAssistantProvider } from './PlanAssistant';

vi.mock('../../../api/aiApi', () => ({
  getAiStatus: vi.fn(),
  setAiConsent: vi.fn(),
  listAiConversations: vi.fn(),
  getAiConversation: vi.fn(),
  streamAiMessage: vi.fn(),
  rateAiMessage: vi.fn(),
}));
vi.mock('../../../api/aiPlanApi', () => ({
  checkAssistantSection: vi.fn(),
  proposeAssistantChanges: vi.fn(),
  applyAssistantChange: vi.fn(),
  recordPlanOutcome: vi.fn(),
  saveClarifiedField: vi.fn(),
  ratePlanAnswer: vi.fn(),
}));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 5 };
const off: AiStatus = { ...ready, available: false, reason: 'OFF' };
const check = (section: AssistantSection, canPropose = true): SectionCheck => ({
  surface: 'BUILDER',
  section,
  canPropose,
  issues: section === 'STEPS'
    ? [{ kind: 'MISSING_OWNER', itemType: 'STEP', itemRef: 's1', itemTitle: 'Gather papers', problem: 'This step has no owner.', fix: 'Make it yours for now.' }]
    : [],
});

function Page({ current, surface = 'BUILDER', onDraftChange = vi.fn(), onPlanChanged = vi.fn() }: {
  current: AssistantTopic;
  surface?: 'PLAN' | 'BUILDER';
  onDraftChange?: () => void;
  onPlanChanged?: () => void;
}) {
  return (
    <MemoryRouter initialEntries={['/dreams/5/build']}>
      <PlanAssistantProvider token="token" surface={surface} dreamId={5} current={current}
        onDraftChange={onDraftChange} onPlanChanged={onPlanChanged}>
        <HelpWithSectionButton section="STEPS" />
        <HelpWithSectionButton section="GOALS" />
      </PlanAssistantProvider>
    </MemoryRouter>
  );
}

describe('Plan assistant (V11 Phase 7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAiConversations).mockResolvedValue([]);
    vi.mocked(checkAssistantSection).mockImplementation(async (_token, _dream, topic) => check(topic.section));
    vi.mocked(recordPlanOutcome).mockResolvedValue(undefined);
  });

  it('opens for one section with its help, plan check and three quick actions (PB-FR-42 to PB-FR-44)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    render(<Page current={{ section: 'GOALS' }} />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with Steps' }));

    const panel = await screen.findByRole('dialog');
    expect(within(panel).getByText('Helping with: Steps')).toBeInTheDocument();
    expect(within(panel).getByText('For each goal, the actions that get it done, in order.')).toBeInTheDocument();
    expect(await within(panel).findByText(/This step has no owner\./)).toBeInTheDocument();
    expect(await within(panel).findByText('What must happen first?', { selector: '.MuiChip-label' })).toBeInTheDocument();
    expect(within(panel).getByText('Is this step too big?')).toBeInTheDocument();
    expect(checkAssistantSection).toHaveBeenCalledWith('token', 5, { surface: 'BUILDER', section: 'STEPS', itemRef: null });
    expect(listAiConversations).toHaveBeenCalledWith('token', { dreamId: 5, section: 'STEPS' });
  });

  it('follows the page to a new section unless the topic is kept (PB-FR-43)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    const { rerender } = render(<Page current={{ section: 'GOALS' }} />);
    await userEvent.click(screen.getByRole('button', { name: 'Open the plan assistant' }));
    expect(await screen.findByText('Helping with: Goals')).toBeInTheDocument();

    rerender(<Page current={{ section: 'STEPS' }} />);
    expect(await screen.findByText('Helping with: Steps')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Keep this topic when I move to another section' }));
    rerender(<Page current={{ section: 'TASKS' }} />);
    expect(screen.getByText('Helping with: Steps')).toBeInTheDocument();
  });

  it('still helps without AI: program help and plan check, no chat or cards (PB-FR-49)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(off);
    render(<Page current={{ section: 'STEPS' }} />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with Steps' }));

    const panel = await screen.findByRole('dialog');
    expect(await within(panel).findByText(/This step has no owner\./)).toBeInTheDocument();
    expect(within(panel).getByText('What steps are required?')).toBeInTheDocument();
    expect(within(panel).getByText('AI help is not available right now. The help and plan check above still work.')).toBeInTheDocument();
    expect(within(panel).queryByRole('button', { name: 'Suggest changes' })).not.toBeInTheDocument();
    expect(within(panel).queryByLabelText('Your message')).not.toBeInTheDocument();
    expect(proposeAssistantChanges).not.toHaveBeenCalled();
  });

  it('applies a card to the draft only when chosen, and dismissing changes nothing (PB-FR-46, PB-FR-48)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(proposeAssistantChanges).mockResolvedValue({
      requestId: 77,
      note: 'Two small fixes.',
      changes: [
        { kind: 'EDIT', itemType: 'STEP', itemRef: 's1', itemTitle: 'Do credit stuff', field: 'TITLE', value: 'Request a credit report' },
        { kind: 'ADD_STEP', itemType: 'STEP', parentRef: 'g1', parentTitle: 'Fix credit', title: 'Pay off the card', owner: 'Me', date: '2027-01-10' },
      ],
    });
    const onDraftChange = vi.fn();
    render(<Page current={{ section: 'STEPS' }} onDraftChange={onDraftChange} />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with Steps' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Suggest changes' }));

    const rename = await screen.findByRole('region', { name: 'Change the title' });
    await userEvent.click(within(screen.getByRole('region', { name: 'Add a step' })).getByRole('button', { name: 'Dismiss' }));
    expect(onDraftChange).not.toHaveBeenCalled();

    const field = within(rename).getByLabelText('Change the title');
    await userEvent.clear(field);
    await userEvent.type(field, 'Order my credit report');
    await userEvent.click(within(rename).getByRole('button', { name: 'Apply to draft' }));

    expect(onDraftChange).toHaveBeenCalledWith(expect.objectContaining({ itemRef: 's1', value: 'Order my credit report' }));
    expect(recordPlanOutcome).toHaveBeenCalledWith('token', 77, 0, 1);
    expect(await screen.findByText('Applied to your draft.')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Change the title' })).not.toBeInTheDocument();
  });

  it('saves a card on the Plan page through the server, then reloads the plan', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(proposeAssistantChanges).mockResolvedValue({
      requestId: 9,
      changes: [{ kind: 'EDIT', itemType: 'STEP', itemRef: 'step:3', itemTitle: 'Old', field: 'OWNER', value: 'Me' }],
    });
    vi.mocked(applyAssistantChange).mockResolvedValue({ itemType: 'STEP', id: 3 });
    const onPlanChanged = vi.fn();
    render(<Page current={{ section: 'WHOLE' }} surface="PLAN" onPlanChanged={onPlanChanged} />);
    await userEvent.click(screen.getByRole('button', { name: 'Help with Steps' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Suggest changes' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save to my plan' }));

    expect(applyAssistantChange).toHaveBeenCalledWith('token', 5, expect.objectContaining({ itemRef: 'step:3', value: 'Me' }), 9, false);
    expect(onPlanChanged).toHaveBeenCalled();
    expect(await screen.findByText('Saved to your plan.')).toBeInTheDocument();
  });
});
