import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../api/aiApi';
import { dreamIntake, suggestVisionStatement } from '../api/aiPlanApi';
import { createDream } from '../api/dreamApi';
import { createVisionArea, listVisionAreas, updateVisionArea } from '../api/visionAreaApi';
import type { AiStatus, Dream, VisionArea } from '../types/vision';
import { DreamCoachStartPage } from './DreamCoachStartPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { fullName: 'Ana' } }) }));
vi.mock('../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../api/aiPlanApi', () => ({ dreamIntake: vi.fn(), suggestVisionStatement: vi.fn(), ratePlanAnswer: vi.fn() }));
vi.mock('../api/dreamApi', () => ({ createDream: vi.fn() }));
vi.mock('../api/visionAreaApi', () => ({ listVisionAreas: vi.fn(), createVisionArea: vi.fn(), updateVisionArea: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const career = { id: 3, name: 'Career', visionStatement: '', priority: 'HIGH', status: 'ACTIVE', archived: false } as unknown as VisionArea;

function Landing() {
  const location = useLocation();
  return <p>{`Builder ${location.pathname} ${JSON.stringify(location.state)}`}</p>;
}

function renderPage(path = '/plan/new') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/plan/new" element={<DreamCoachStartPage />} />
        <Route path="/dreams/:dreamId/build" element={<Landing />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DreamCoachStartPage (V31 v1 step 1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(createDream).mockResolvedValue({ id: 9 } as Dream);
  });

  it('words the vision statement, asks about the dream, restates it and saves it only when confirmed', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(listVisionAreas).mockResolvedValue([career]);
    vi.mocked(suggestVisionStatement).mockResolvedValue({ statement: 'My research changes how clinics use data.', requestId: 4 });
    vi.mocked(updateVisionArea).mockResolvedValue(career);
    vi.mocked(dreamIntake)
      .mockResolvedValueOnce({ done: false, question: 'By when would you like that?', field: 'TARGET_DATE', requestId: 5 })
      .mockResolvedValueOnce({ done: true, title: 'Become an AI-assisted researcher', whyImportant: null,
        successDefinition: 'My mentor accepts my concept note', targetDate: '2027-06-30', requestId: 6 });
    renderPage('/plan/new?areaId=3');

    expect(await screen.findByRole('combobox', { name: 'Vision area' })).toHaveTextContent('Career');
    await userEvent.type(screen.getByRole('textbox', { name: /In one sentence/ }), 'I want my research to change how clinics use data');
    await userEvent.click(screen.getByRole('button', { name: 'Word it for me' }));
    expect(await screen.findByDisplayValue('My research changes how clinics use data.')).toBeInTheDocument();
    expect(suggestVisionStatement).toHaveBeenCalledWith('token', 'en',
      { areaName: 'Career', sentence: 'I want my research to change how clinics use data' });
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    await userEvent.type(screen.getByRole('textbox', { name: /Your dream, in your own words/ }), 'Get better at research with AI');
    await userEvent.click(screen.getByRole('button', { name: 'Help me make it clear' }));
    expect(await screen.findByText('By when would you like that?')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Your answer'), '2027-06-30');
    await userEvent.click(screen.getByRole('button', { name: 'Answer' }));

    expect(await screen.findByDisplayValue('Become an AI-assisted researcher')).toBeInTheDocument();
    expect(dreamIntake).toHaveBeenLastCalledWith('token', 'en', { areaName: 'Career', words: 'Get better at research with AI',
      answers: [{ question: 'By when would you like that?', answer: '2027-06-30', field: 'TARGET_DATE' }], suggestAnyway: false });
    // Nothing is saved before the participant confirms.
    expect(createDream).not.toHaveBeenCalled();
    await userEvent.type(screen.getByRole('textbox', { name: 'Why it matters' }), 'Better care');
    await userEvent.click(screen.getByRole('button', { name: 'This is right, save it and plan' }));

    expect(await screen.findByText('Builder /dreams/9/build {"fromCoach":true}')).toBeInTheDocument();
    expect(updateVisionArea).toHaveBeenCalledWith('token', 3, expect.objectContaining({
      name: 'Career', visionStatement: 'My research changes how clinics use data.' }));
    expect(createDream).toHaveBeenCalledWith('token', expect.objectContaining({
      visionAreaId: 3, title: 'Become an AI-assisted researcher', description: 'Get better at research with AI', whyImportant: 'Better care',
      successDefinition: 'My mentor accepts my concept note', targetDate: '2027-06-30', status: 'IDEA', priority: 'MEDIUM' }));
  });

  it('works without AI: a starter area, the details filled in by hand, and the new area saved first', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'NO_CONSENT' });
    vi.mocked(listVisionAreas).mockResolvedValue([]);
    vi.mocked(createVisionArea).mockResolvedValue({ ...career, id: 7, name: 'Health' });
    renderPage();

    expect(await screen.findByText(/AI suggestions are off/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Health' }));
    expect(screen.queryByRole('button', { name: 'Word it for me' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(screen.queryByRole('button', { name: 'Help me make it clear' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: /Your dream, in your own words/ }), 'Run a half marathon');
    await userEvent.click(screen.getByRole('button', { name: 'Fill in the details myself' }));
    expect(screen.getByRole('textbox', { name: 'Dream' })).toHaveValue('Run a half marathon');
    await userEvent.click(screen.getByRole('button', { name: 'This is right, save it and plan' }));

    expect(await screen.findByText(/Builder \/dreams\/9\/build/)).toBeInTheDocument();
    expect(createVisionArea).toHaveBeenCalledWith('token', { name: 'Health', visionStatement: undefined, priority: 'MEDIUM', status: 'ACTIVE' });
    expect(createDream).toHaveBeenCalledWith('token', expect.objectContaining({ visionAreaId: 7, title: 'Run a half marathon', status: 'IDEA' }));
  });

  it('keeps the area made by a failed attempt, so trying again makes no second one', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'NO_CONSENT' });
    vi.mocked(listVisionAreas).mockResolvedValue([]);
    vi.mocked(createVisionArea).mockResolvedValue({ ...career, id: 7, name: 'Health' });
    vi.mocked(createDream).mockRejectedValueOnce(new Error('Title is required.')).mockResolvedValueOnce({ id: 9 } as Dream);
    renderPage();

    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), 'Health');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.type(screen.getByRole('textbox', { name: /Your dream, in your own words/ }), 'Sleep eight hours');
    await userEvent.click(screen.getByRole('button', { name: 'Fill in the details myself' }));
    await userEvent.click(screen.getByRole('button', { name: 'This is right, save it and plan' }));
    expect(await screen.findByText('Title is required.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'This is right, save it and plan' }));

    expect(await screen.findByText(/Builder \/dreams\/9\/build/)).toBeInTheDocument();
    expect(createVisionArea).toHaveBeenCalledTimes(1);
  });
});
