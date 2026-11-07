import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { recordPlanOutcome, suggestDreams, suggestVisionAreas } from '../../api/aiPlanApi';
import { createDream } from '../../api/dreamApi';
import { createVisionArea } from '../../api/visionAreaApi';
import type { AiStatus, VisionArea } from '../../types/vision';
import { AiDreamAssistant, AiVisionAreaAssistant } from './AiStartAssistant';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({
  suggestVisionAreas: vi.fn(), suggestDreams: vi.fn(), recordPlanOutcome: vi.fn(), ratePlanAnswer: vi.fn(),
}));
vi.mock('../../api/dreamApi', () => ({ createDream: vi.fn() }));
vi.mock('../../api/visionAreaApi', () => ({ createVisionArea: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const area = { id: 3, name: 'Career', archived: false } as VisionArea;
const renderInRouter = (ui: React.ReactElement) => render(<MemoryRouter>{ui}</MemoryRouter>);

describe('AiVisionAreaAssistant', () => {
  beforeEach(() => vi.clearAllMocks());

  it('adds only the areas the participant keeps, with their edits, and counts what was kept', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(recordPlanOutcome).mockResolvedValue(undefined);
    vi.mocked(suggestVisionAreas).mockResolvedValue({ requestId: 8, areas: [
      { name: 'Health', priority: 'HIGH', visionStatement: 'I feel strong.' },
      { name: 'Finance', priority: 'MEDIUM' },
    ] });
    const onDataChange = vi.fn().mockResolvedValue(undefined);
    render(<AiVisionAreaAssistant token="token" onDataChange={onDataChange} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Suggest vision areas' }));
    expect(await screen.findByDisplayValue('Health')).toBeInTheDocument();
    expect(createVisionArea).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Add Finance' }));
    const name = screen.getByDisplayValue('Health');
    await userEvent.clear(name);
    await userEvent.type(name, 'Health and fitness');
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 selected vision area(s)' }));

    expect(createVisionArea).toHaveBeenCalledTimes(1);
    expect(createVisionArea).toHaveBeenCalledWith('token', expect.objectContaining({
      name: 'Health and fitness', visionStatement: 'I feel strong.', priority: 'HIGH', status: 'ACTIVE',
    }));
    expect(onDataChange).toHaveBeenCalled();
    expect(await screen.findByText('1 vision area(s) added.')).toBeInTheDocument();
    // One kept, and it was edited.
    expect(recordPlanOutcome).toHaveBeenCalledWith('token', 8, 0, 1);
  });

  it('shows nothing while AI is off for everyone', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'OFF' });
    const { container } = render(<AiVisionAreaAssistant token="token" onDataChange={vi.fn()} />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});

describe('AiDreamAssistant', () => {
  beforeEach(() => vi.clearAllMocks());

  it('adds kept dreams to the chosen area as ideas', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestDreams).mockResolvedValue({ dreams: [
      { title: 'Lead a research team', dreamType: 'LONG_TERM', priority: 'HIGH', moonshot: false,
        whyImportant: 'Grow others', successDefinition: 'A team of five', targetDate: '2028-01-01' },
    ] });
    renderInRouter(<AiDreamAssistant token="token" visionAreas={[area]} onDataChange={vi.fn().mockResolvedValue(undefined)} />);

    expect(await screen.findByRole('button', { name: 'Suggest dreams' })).toBeDisabled();
    await userEvent.click(screen.getByRole('combobox', { name: 'Vision area to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Career' }));
    await userEvent.click(screen.getByRole('button', { name: 'Suggest dreams' }));
    expect(suggestDreams).toHaveBeenCalledWith('token', 3, 'en', false);
    expect(await screen.findByDisplayValue('Lead a research team')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add 1 selected dream(s)' }));
    expect(createDream).toHaveBeenCalledWith('token', expect.objectContaining({
      visionAreaId: 3, title: 'Lead a research team', whyImportant: 'Grow others', status: 'IDEA', targetDate: '2028-01-01',
    }));
    expect(await screen.findByText('1 dream(s) added as ideas.')).toBeInTheDocument();
  });

  it('offers to continue into Build my plan for each dream just added (PB-FR-06)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestDreams).mockResolvedValue({ dreams: [
      { title: 'Lead a research team', dreamType: 'LONG_TERM', priority: 'HIGH', moonshot: false },
    ] });
    vi.mocked(createDream).mockResolvedValue({ id: 41, title: 'Lead a research team' } as Awaited<ReturnType<typeof createDream>>);
    renderInRouter(<AiDreamAssistant token="token" visionAreas={[area]} initialVisionAreaId="3" onDataChange={vi.fn().mockResolvedValue(undefined)} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Suggest dreams' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add 1 selected dream(s)' }));
    const link = await screen.findByRole('link', { name: 'Build my plan for Lead a research team' });
    expect(link).toHaveAttribute('href', '/dreams/41/build');
  });

  it('shows a clarifying question instead of guessing', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestDreams).mockResolvedValue({ dreams: [], clarifyingQuestion: 'What kind of work do you love?' });
    renderInRouter(<AiDreamAssistant token="token" visionAreas={[area]} initialVisionAreaId="3" onDataChange={vi.fn()} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Suggest a moonshot dream' }));
    expect(suggestDreams).toHaveBeenCalledWith('token', 3, 'en', true);
    expect(await screen.findByText('What kind of work do you love?')).toBeInTheDocument();
  });
});
