import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as accountApi from '../api/accountApi';
import * as dashboardApi from '../api/dashboardApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { setCurrentLanguage } from '../i18n/translate';
import { aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import type { DashboardSummary } from '../types/vision';
import { DashboardPage } from './DashboardPage';

vi.mock('../api/accountApi');
vi.mock('../api/dashboardApi');
vi.mock('../api/gratitudeEntryApi');
vi.mock('../api/visionAreaApi');

const noAttention = {
  blockedTasksWithoutPartner: [], complexStepsWithoutTasks: [], dreamsWithoutGoals: [], goalsWithoutSteps: [],
  inactiveMoonshotGoals: [], inactiveMoonshotDreams: [], starvedVisionAreas: [],
};

const empty: DashboardSummary = {
  totalVisionAreas: 0, activeDreams: 0, activeGoals: 0, activeTasks: 0, completedTasks: 0, overdueTasks: 0, blockedTasks: 0,
  averageProgress: 0, tasksDueThisWeek: 0, tasksDueInPeriod: 0, completedTasksInPeriod: 0, goalsByStatus: {}, dreamsByVisionArea: {},
  tasksByStatus: {}, tasksByPriority: {}, activeObstaclesByType: {}, partnersByStatus: {}, reviewCadence: {}, progressTrend: [],
  visionAreaProgress: [], priorityTasks: [], weeksWithDiligence: 0, moonshotGoals: 0, moonshotDreams: 0, attention: noAttention,
  energyBudget: { charge: 0, neutral: 0, drain: 0, net: 0 }, gratitude: { countThisWeek: 0, recent: [] },
};

const known: DashboardSummary = {
  ...empty,
  totalVisionAreas: 3, activeDreams: 4, activeGoals: 6, activeTasks: 12, completedTasks: 9, overdueTasks: 2, blockedTasks: 1,
  averageProgress: 41.6, tasksDueThisWeek: 5, tasksDueInPeriod: 8, completedTasksInPeriod: 7,
  goalsByStatus: { IN_PROGRESS: 4, NOT_STARTED: 2 },
  dreamsByVisionArea: { Career: 3, Health: 1 },
  tasksByStatus: { BLOCKED: 1, IN_PROGRESS: 5 },
  tasksByPriority: { HIGH: 4, CRITICAL: 1 },
  activeObstaclesByType: { TIME: 2 },
  partnersByStatus: { TO_CONTACT: 2, ACTIVE: 1 },
  weeksWithDiligence: 3,
  priorityTasks: [
    aTask({ id: 1, title: 'Submit ethics form', dueDate: '2020-01-10', priority: 'CRITICAL', status: 'BLOCKED', progressPercent: 20 }),
    aTask({ id: 2, title: 'Search PubMed', dueDate: '2099-01-31', priority: 'HIGH', status: 'IN_PROGRESS', progressPercent: 60 }),
  ],
};

/** The card around a title, for checking what that tile or chart shows. */
const card = (title: string) => screen.getByText(title).closest('a, .MuiCard-root, [class*="MuiPaper"]') as HTMLElement;

describe('DashboardPage (CLAUDE.md page 1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue(known);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1, name: 'Career' }), aVisionArea({ id: 2, name: 'Health' })]);
    // The account cards load on their own; they are tested with their components.
    vi.mocked(accountApi.getProgressMetrics).mockReturnValue(new Promise(() => undefined));
    vi.mocked(accountApi.getReminderSettings).mockReturnValue(new Promise(() => undefined));
  });
  afterEach(() => setCurrentLanguage('en'));

  it('shows every summary figure from the server (vision areas, dreams, goals, tasks, overdue, blocked, progress, due)', async () => {
    renderPage(<DashboardPage />);
    expect(await screen.findByText('Overdue Tasks')).toBeInTheDocument();
    expect(within(card('Vision Areas')).getByText('3')).toBeInTheDocument();
    expect(within(card('Active Dreams')).getByText('4')).toBeInTheDocument();
    expect(within(card('Active Goals')).getByText('6')).toBeInTheDocument();
    expect(within(card('Open Tasks')).getByText('12')).toBeInTheDocument();
    expect(within(card('Overdue Tasks')).getByText('2')).toBeInTheDocument();
    expect(within(card('Blocked Tasks')).getByText('1')).toBeInTheDocument();
    expect(within(card('Average Progress')).getByText('42%')).toBeInTheDocument();
    expect(within(card('Completed this month')).getByText('7')).toBeInTheDocument();
    expect(within(card('Due this month')).getByText('8')).toBeInTheDocument();
  });

  it('asks for this calendar month by default, and links the due tile to the same window', async () => {
    renderPage(<DashboardPage />);
    await screen.findByText('Overdue Tasks');
    const [, scope, from = '', to = ''] = vi.mocked(dashboardApi.getDashboardSummary).mock.calls[0];
    expect(scope).toBe('');
    expect(from).toMatch(/^\d{4}-\d{2}-01$/);
    expect(to.slice(0, 7)).toBe(from.slice(0, 7));
    expect(screen.getByText('Due this month').closest('a')).toHaveAttribute('href', `/tasks?dueFrom=${from}&dueTo=${to}`);
  });

  it('lists the top priority tasks, with an overdue due date marked', async () => {
    renderPage(<DashboardPage />);
    const table = await screen.findByRole('region', { name: 'Priority tasks' });
    const [, first, second] = within(table).getAllByRole('row');
    expect(within(first).getByText('Submit ethics form')).toBeInTheDocument();
    expect(within(first).getByText('CRITICAL')).toBeInTheDocument();
    expect(within(first).getByText('BLOCKED')).toBeInTheDocument();
    expect(getComputedStyle(within(first).getByText('2020-01-10')).fontWeight).toBe('600');
    expect(getComputedStyle(within(second).getByText('2099-01-31')).fontWeight).not.toBe('600');
  });

  it('shows dreams by vision area and goals by status', async () => {
    renderPage(<DashboardPage />);
    await screen.findByText('Dreams by vision area', undefined, { timeout: 10000 });
    // The charts arrive as their own lazily loaded chunk, which can take a while on a busy runner.
    await waitFor(() => expect(within(card('Dreams by vision area')).getByText('Career')).toBeInTheDocument(), { timeout: 10000 });
    expect(within(card('Dreams by vision area')).getByText('Health')).toBeInTheDocument();
    await waitFor(() => expect(within(card('Goals by status')).getByText('In Progress')).toBeInTheDocument(), { timeout: 10000 });
    expect(within(card('Goals by status')).getByText('Not Started')).toBeInTheDocument();
  });

  it('shows the partner pipeline with links to each stage', async () => {
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('link', { name: 'To Contact (2)' })).toHaveAttribute('href', '/partners?status=TO_CONTACT');
    expect(screen.getByRole('link', { name: 'Active (1)' })).toHaveAttribute('href', '/partners?status=ACTIVE');
  });

  it('shows the review cadence once reviews are logged', async () => {
    const today = new Date();
    const iso = new Date(today.getTime() - today.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, reviewCadence: { [iso]: 2 } });
    renderPage(<DashboardPage />);
    expect(await screen.findByText('3 of the last 12 weeks include a diligence checkup.')).toBeInTheDocument();
    expect(screen.getByText('Less')).toBeInTheDocument();
  });

  it('says plainly when a widget has nothing to show yet', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...empty, totalVisionAreas: 1, activeDreams: 1, activeGoals: 1 });
    renderPage(<DashboardPage />);
    expect(await screen.findByText('No open priority tasks.')).toBeInTheDocument();
    expect(screen.getByText('No partners yet.')).toBeInTheDocument();
    expect(screen.getByText('No daily or weekly reviews logged yet.')).toBeInTheDocument();
    expect(screen.getByText(/No progress logged yet/)).toBeInTheDocument();
    expect(screen.getByText('No vision areas yet.')).toBeInTheDocument();
  });

  it('shows only the getting-started steps on a brand new account', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue(empty);
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create a vision area' })).toHaveAttribute('href', '/vision-areas?create=area');
    expect(screen.queryByText('Overdue Tasks')).not.toBeInTheDocument();
    expect(screen.queryByText('Priority tasks')).not.toBeInTheDocument();
  });

  it('keeps the getting-started steps above the figures until the first dream has goals', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, activeGoals: 0 });
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeInTheDocument();
    // Areas and dreams exist, so the next step is breaking the dream down.
    expect(screen.getByRole('link', { name: 'Open the Vision Map' })).toHaveAttribute('href', '/vision-map');
    expect(screen.getByText('Overdue Tasks')).toBeInTheDocument();
  });

  it('scopes the dashboard to one vision area, and carries that scope into the links', async () => {
    renderPage(<DashboardPage />, { route: '/?visionAreaId=1' });
    await screen.findByText('Overdue Tasks');
    expect(dashboardApi.getDashboardSummary).toHaveBeenCalledWith('token', '1', expect.any(String), expect.any(String));
    expect(screen.getByText('Due this month').closest('a')?.getAttribute('href')).toMatch(/&visionAreaId=1$/);
  });

  it('changes the period to this year', async () => {
    renderPage(<DashboardPage />);
    await screen.findByText('Overdue Tasks');
    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(await screen.findByRole('option', { name: 'This year' }));
    const year = new Date().getFullYear();
    await waitFor(() => expect(dashboardApi.getDashboardSummary).toHaveBeenLastCalledWith('token', '', `${year}-01-01`, `${year}-12-31`));
    expect(await screen.findByText('Due this year')).toBeInTheDocument();
    expect(currentLocation()).toBe('/?period=year');
  });

  it('shows the progress trend and each vision area’s progress once there is data', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({
      ...known,
      progressTrend: [{ weekEnd: '2026-10-31', progress: 30 }, { weekEnd: '2026-11-07', progress: 42 }],
      visionAreaProgress: [{ name: 'Career', progress: 55 }, { name: 'Health', progress: 20 }],
    });
    renderPage(<DashboardPage />);
    await screen.findByText('Progress trend');
    expect(screen.queryByText(/No progress logged yet/)).not.toBeInTheDocument();
    expect(screen.queryByText('No vision areas yet.')).not.toBeInTheDocument();
  });

  it('names the obstacle types and priorities in the charts', async () => {
    renderPage(<DashboardPage />);
    await screen.findByText('Top obstacles', undefined, { timeout: 10000 });
    await waitFor(() => expect(within(card('Top obstacles')).getByText('Time')).toBeInTheDocument(), { timeout: 10000 });
    await waitFor(() => expect(within(card('Tasks by status')).getByText('Blocked')).toBeInTheDocument(), { timeout: 10000 });
  });

  it('opens the task board to add a task', async () => {
    renderPage(<DashboardPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New Task' }));
    expect(currentLocation()).toBe('/tasks?new=true');
  });

  it('shows why the dashboard could not load', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<DashboardPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Get started' })).not.toBeInTheDocument();
  });

  it('shows the dashboard in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('region', { name: 'កិច្ចការអាទិភាព' })).toBeInTheDocument();
    expect(screen.getByText('កិច្ចការហួសកំណត់')).toBeInTheDocument();
  });
});
