import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiApi from '../api/aiApi';
import * as aiPlanApi from '../api/aiPlanApi';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as partnerApi from '../api/partnerApi';
import * as planShareApi from '../api/planShareApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aGoal, aStep, aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { DreamDetailPage } from './DreamDetailPage';

vi.mock('../api/aiApi');
vi.mock('../api/aiPlanApi');
vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/partnerApi');
vi.mock('../api/planShareApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');

const areas = [aVisionArea({ id: 1, name: 'Career' }), aVisionArea({ id: 2, name: 'Health' })];
const researcher = aDream({ id: 1, visionAreaId: 1, title: 'Become a researcher', whyImportant: 'Help my community' });
const marathon = aDream({ id: 2, visionAreaId: 2, title: 'Run a marathon', moonshot: true });

function renderMap(route: string) {
  return renderPage(<DreamDetailPage />, { route, path: route.startsWith('/dreams/') ? '/dreams/:dreamId' : '/vision-map' });
}

describe('DreamDetailPage (CLAUDE.md page 4: the Vision Map)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    answerEmptyLists([aiPlanApi, dreamApi, goalApi, partnerApi, planShareApi, stepApi, taskApi, visionAreaApi], ['listPartners']);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ available: false, reason: 'OFF', consentGiven: false, dailyLimit: 0, messagesLeftToday: 0 });
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue(areas);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([researcher, marathon]);
    vi.mocked(goalApi.listGoals).mockResolvedValue([aGoal({ id: 10, dreamId: 1, title: 'Prepare one concept note' })]);
    vi.mocked(stepApi.listSteps).mockResolvedValue([aStep({ id: 20, goalId: 10, title: 'Search literature' })]);
    vi.mocked(taskApi.listTasks).mockResolvedValue([aTask({ id: 30, stepId: 20, title: 'Search PubMed' })]);
  });

  it('shows every dream at a glance with its goal, step and task counts', async () => {
    renderMap('/vision-map');
    expect(await screen.findByText('Become a researcher')).toBeInTheDocument();
    expect(screen.getByText('1 goal(s) · 1 step(s) · 1 task(s)')).toBeInTheDocument();
    expect(screen.getByText('No goals yet — open to add the first')).toBeInTheDocument();
    expect(screen.getByLabelText('Moonshot')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Run a marathon'));
    expect(currentLocation()).toBe('/dreams/2');
  });

  it('opens one dream as a tree from area to tasks, with the plan tools beside it', async () => {
    renderMap('/dreams/1');
    expect(await screen.findByRole('tree', { name: 'Vision map for Become a researcher' })).toBeInTheDocument();
    const tree = within(screen.getByRole('tree'));
    expect(tree.getByText('Prepare one concept note')).toBeInTheDocument();
    expect(tree.getByText('Search literature')).toBeInTheDocument();
    expect(tree.getByText('Search PubMed')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'All dreams' })).toHaveAttribute('href', '/vision-map');
    expect(screen.getByText('Commit to this dream')).toBeInTheDocument();
    expect(screen.getByText('Share with a partner')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Build my plan' }));
    expect(currentLocation()).toBe('/dreams/1/build');
  });

  it('switches to another dream from the picker', async () => {
    renderMap('/dreams/1');
    await screen.findByRole('tree');
    await userEvent.click(screen.getByRole('combobox', { name: 'Dream' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Run a marathon' }));
    expect(currentLocation()).toBe('/dreams/2');
  });

  it('jumps to a dream in the chosen vision area when the open one is not in it', async () => {
    renderMap('/dreams/1?visionAreaId=2');
    await waitFor(() => expect(currentLocation()).toBe('/dreams/2?visionAreaId=2'));
  });

  it('says so when a vision area has no dreams, or a dream does not exist', async () => {
    const first = renderMap('/vision-map?visionAreaId=9');
    expect(await screen.findByText('No dreams in this vision area yet.')).toBeInTheDocument();
    first.unmount();
    renderMap('/dreams/99');
    expect(await screen.findByText('No dream selected.')).toBeInTheDocument();
  });

  it('invites a new participant to create the first dream', async () => {
    vi.mocked(dreamApi.listDreams).mockResolvedValue([]);
    renderMap('/vision-map');
    expect(await screen.findByText('No dreams on the map yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Create your first dream' }));
    expect(currentLocation()).toBe('/dreams?create=dream');
  });

  it('shows why the map could not load', async () => {
    vi.mocked(goalApi.listGoals).mockRejectedValue(new Error('Server is down.'));
    renderMap('/dreams/1');
    expect(await screen.findByText('Server is down.')).toBeInTheDocument();
  });

  it('narrows the tree by priority and status', async () => {
    renderMap('/dreams/1?status=COMPLETED');
    expect(await screen.findByRole('tree')).toBeInTheDocument();
    expect(within(screen.getByRole('tree')).queryByText('Search PubMed')).not.toBeInTheDocument();
  });
});
