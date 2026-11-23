import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as accountApi from '../api/accountApi';
import * as savedReportApi from '../api/savedReportApi';
import * as dashboardApi from '../api/dashboardApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { setCurrentLanguage } from '../i18n/translate';
import { aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import type { DashboardSummary } from '../types/vision';
import { localDateIso } from '../utils/localDate';
import { DashboardPage } from './DashboardPage';

vi.mock('../api/accountApi');
vi.mock('../api/savedReportApi');
vi.mock('../api/dashboardApi');
vi.mock('../api/gratitudeEntryApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');

const noAttention = {
  blockedTasksWithoutPartner: [], complexStepsWithoutTasks: [], dreamsWithoutGoals: [], goalsWithoutSteps: [],
  inactiveMoonshotGoals: [], inactiveMoonshotDreams: [], starvedVisionAreas: [],
};

const empty: DashboardSummary = {
  totalVisionAreas: 0, activeDreams: 0, activeGoals: 0, activeTasks: 0, completedTasks: 0, overdueTasks: 0, blockedTasks: 0,
  averageProgress: 0, tasksDueThisWeek: 0, tasksDueInPeriod: 0, completedTasksInPeriod: 0, goalsByStatus: {}, dreamsByVisionArea: {},
  tasksByStatus: {}, tasksByPriority: {}, activeObstaclesByType: {}, partnersByStatus: {}, reviewCadence: {}, progressTrend: [],
  visionAreaProgress: [], priorityTasks: [], nextUpTasks: [], weeksWithDiligence: 0, moonshotGoals: 0, moonshotDreams: 0, attention: noAttention,
  energyBudget: { charge: 0, neutral: 0, drain: 0, net: 0 }, gratitude: { countThisWeek: 0, recent: [] },
};

const ethics = aTask({ id: 1, title: 'Submit ethics form', dueDate: '2020-01-10', priority: 'CRITICAL', status: 'BLOCKED', progressPercent: 20, blockerReason: 'Waiting for the faculty form' });
const pubmed = aTask({ id: 2, title: 'Search PubMed', dueDate: '2099-01-31', priority: 'HIGH', status: 'IN_PROGRESS', progressPercent: 60 });

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
  priorityTasks: [ethics, pubmed],
  nextUpTasks: [ethics, pubmed],
};

/** Reduced motion, so numbers show their final value at once. */
function preferReducedMotion() {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('prefers-reduced-motion'),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
}

const pressure = () => screen.getByRole('region', { name: 'Task pressure' });
const kpi = (label: string) => within(pressure()).getByText(label).closest('a, div') as HTMLElement;

describe('DashboardPage (BRD 25)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    preferReducedMotion();
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue(known);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1, name: 'Career' }), aVisionArea({ id: 2, name: 'Health' })]);
    // The account cards load on their own; they are tested with their components.
    vi.mocked(accountApi.getProgressMetrics).mockReturnValue(new Promise(() => undefined));
    vi.mocked(accountApi.getReminderSettings).mockReturnValue(new Promise(() => undefined));
    vi.mocked(savedReportApi.getReportReminder).mockReturnValue(new Promise(() => undefined));
  });
  afterEach(() => {
    setCurrentLanguage('en');
    vi.unstubAllGlobals();
  });

  it('greets the person and reads Today → Plan health → Insights (D-25-2)', async () => {
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('heading', { level: 1, name: /^Good (morning|afternoon|evening), Test$/ })).toBeInTheDocument();
    const zones = screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent);
    expect(zones).toEqual(['Today', 'Plan health', 'Insights']);
  });

  it('shows the task pressure figures, each linked to the list it counts (D-25-3)', async () => {
    renderPage(<DashboardPage />);
    await screen.findByText('Overdue Tasks');
    expect(within(kpi('Due this month')).getByTestId('kpi-number')).toHaveTextContent('8');
    expect(within(pressure()).getByText('7 completed this month')).toBeInTheDocument();
    expect(within(kpi('Overdue Tasks')).getByTestId('kpi-number')).toHaveTextContent('2');
    expect(within(pressure()).getByText('Overdue Tasks').closest('a')).toHaveAttribute('href', '/tasks?overdue=true');
    expect(within(pressure()).getByText('Blocked Tasks').closest('a')).toHaveAttribute('href', '/tasks?status=BLOCKED');
  });

  it('turns a zero into "All clear" (DA-FR-05)', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, overdueTasks: 0, blockedTasks: 0 });
    renderPage(<DashboardPage />);
    await screen.findByText('Overdue Tasks');
    expect(within(pressure()).getAllByText('All clear')).toHaveLength(2);
  });

  it('shows overall progress as a ring, with the portfolio counts', async () => {
    renderPage(<DashboardPage />);
    expect((await screen.findAllByRole('img', { name: 'Overall progress: 42%' })).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('3 areas · 4 dreams · 6 goals · 12 open tasks')).toBeInTheDocument();
  });

  it('shows the change in progress over the trend window', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({
      ...known,
      progressTrend: [{ weekEnd: '2026-10-31', progress: 30 }, { weekEnd: '2026-11-07', progress: 42 }],
    });
    renderPage(<DashboardPage />);
    expect(await screen.findByText('+12 pts')).toBeInTheDocument();
    expect(screen.queryByText(/No progress logged yet/)).not.toBeInTheDocument();
  });

  it('asks for this calendar month by default, and links the due figure to the same window', async () => {
    renderPage(<DashboardPage />);
    await screen.findByText('Overdue Tasks');
    const [, scope, from = '', to = ''] = vi.mocked(dashboardApi.getDashboardSummary).mock.calls[0];
    expect(scope).toBe('');
    expect(from).toMatch(/^\d{4}-\d{2}-01$/);
    expect(to.slice(0, 7)).toBe(from.slice(0, 7));
    expect(within(pressure()).getByText('Due this month').closest('a')).toHaveAttribute('href', `/tasks?dueFrom=${from}&dueTo=${to}`);
  });

  it('lists what to do next: overdue first, with the blocker shown (DA-FR-02)', async () => {
    renderPage(<DashboardPage />);
    const card = await screen.findByRole('region', { name: 'Next up' });
    const rows = within(card).getAllByRole('listitem');
    expect(within(rows[0]).getByText('Submit ethics form')).toBeInTheDocument();
    expect(within(rows[0]).getByText(/days late$/)).toBeInTheDocument();
    expect(within(rows[0]).getByText('Blocked')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Waiting for the faculty form')).toBeInTheDocument();
    expect(within(card).getByText('1 Critical · 4 High open')).toBeInTheDocument();
  });

  it('falls back to the priority list when the server sends no next-up list', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, nextUpTasks: undefined });
    renderPage(<DashboardPage />);
    const card = await screen.findByRole('region', { name: 'Next up' });
    expect(within(card).getByText('Search PubMed')).toBeInTheDocument();
  });

  it('completes a task from Next up and refreshes the figures', async () => {
    vi.mocked(taskApi.updateTaskStatus).mockResolvedValue({ ...pubmed, status: 'COMPLETED' });
    renderPage(<DashboardPage />);
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Mark Search PubMed as done' }));
    expect(taskApi.updateTaskStatus).toHaveBeenCalledWith('token', 2, 'COMPLETED');
    expect(await screen.findByText('Task completed: Search PubMed')).toBeInTheDocument();
    await waitFor(() => expect(dashboardApi.getDashboardSummary).toHaveBeenCalledTimes(2));
  });

  it('says why a task could not be completed', async () => {
    vi.mocked(taskApi.updateTaskStatus).mockRejectedValue(new Error('Say what changed before leaving Blocked.'));
    renderPage(<DashboardPage />);
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Mark Submit ethics form as done' }));
    expect(await screen.findByText('Say what changed before leaving Blocked.')).toBeInTheDocument();
  });

  it('ranks vision areas lowest first and links each to its dreams', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({
      ...known,
      visionAreaProgress: [{ name: 'Career', progress: 55 }, { name: 'Health', progress: 20 }],
    });
    renderPage(<DashboardPage />);
    const list = await screen.findByRole('list', { name: 'Vision Area progress' });
    const rows = within(list).getAllByRole('link');
    expect(rows[0]).toHaveTextContent('Health');
    expect(rows[0]).toHaveAttribute('href', '/dreams?visionAreaId=2');
    expect(rows[1]).toHaveTextContent('Career');
    expect(within(list).getByText('3 dreams')).toBeInTheDocument();
  });

  it('shows goals and tasks as stacked status bars, with a table that links each count', async () => {
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('img', { name: /^Goals: 6\. Not started 2, In progress 4/ })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /^Tasks: 6\./ })).toBeInTheDocument();
    const card = screen.getByRole('region', { name: 'Goals and tasks by status' });
    await userEvent.click(within(card).getByRole('button', { name: 'View as table' }));
    expect(within(card).getByRole('link', { name: '4' })).toHaveAttribute('href', '/goals?status=IN_PROGRESS');
  });

  it('shows dreams by vision area as linked counts', async () => {
    renderPage(<DashboardPage />);
    await screen.findByText('Dreams by vision area');
    await waitFor(() => expect(screen.getByRole('link', { name: 'Career 3' })).toHaveAttribute('href', '/dreams?visionAreaId=1'));
  });

  it('names the obstacle types, largest first, in the Insights card', async () => {
    renderPage(<DashboardPage />);
    const obstacles = await screen.findByRole('list', { name: 'Top obstacles' });
    expect(within(obstacles).getByRole('link', { name: 'Time' })).toHaveAttribute('href', '/obstacles?type=TIME');
    expect(screen.getByRole('link', { name: /1 blocked task/ })).toHaveAttribute('href', '/tasks?status=BLOCKED');
  });

  it('shows the partner pipeline with links to each status', async () => {
    renderPage(<DashboardPage />);
    await userEvent.click(await screen.findByRole('tab', { name: 'Partners' }));
    expect(screen.getByRole('link', { name: 'To Contact (2)' })).toHaveAttribute('href', '/partners?status=TO_CONTACT');
    expect(screen.getByRole('link', { name: 'Active (1)' })).toHaveAttribute('href', '/partners?status=ACTIVE');
    expect(within(screen.getByRole('list', { name: 'Partner pipeline' })).getAllByRole('listitem')).toHaveLength(4);
  });

  it('moves between Insights tabs with the arrow keys', async () => {
    renderPage(<DashboardPage />);
    const first = await screen.findByRole('tab', { name: 'Obstacles' });
    first.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Partners' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Partners' })).toHaveFocus();
  });

  it('shows the review habit: streak, heatmap and diligence', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, reviewCadence: { [localDateIso()]: 2 } });
    renderPage(<DashboardPage />);
    await userEvent.click(await screen.findByRole('tab', { name: 'Reviews' }));
    expect(screen.getByText('week review streak')).toBeInTheDocument();
    expect(screen.getByText('3 of the last 12 weeks include a diligence checkup.')).toBeInTheDocument();
    expect(screen.getByText('Less')).toBeInTheDocument();
  });

  it('says plainly when a card has nothing to show yet', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...empty, totalVisionAreas: 1, activeDreams: 1, activeGoals: 1 });
    renderPage(<DashboardPage />);
    expect(await screen.findByText('Nothing open right now. Add a task to a goal to see it here.')).toBeInTheDocument();
    expect(screen.getByText('No vision areas yet.')).toBeInTheDocument();
    expect(screen.getByText('No goals or tasks yet.')).toBeInTheDocument();
    expect(screen.getByText(/No progress logged yet/)).toBeInTheDocument();
    expect(screen.getByText('No open obstacles.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Partners' }));
    expect(screen.getByText('No partners yet.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Reviews' }));
    expect(screen.getByText('No daily or weekly reviews logged yet.')).toBeInTheDocument();
  });

  it('shows only the getting-started steps on a brand new account', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue(empty);
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create a vision area' })).toHaveAttribute('href', '/vision-areas?create=area');
    expect(screen.queryByText('Overdue Tasks')).not.toBeInTheDocument();
    expect(screen.queryByText('Next up')).not.toBeInTheDocument();
  });

  it('keeps the getting-started steps above the figures until the first dream has goals', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, activeGoals: 0 });
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('heading', { name: 'Get started' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the Vision Map' })).toHaveAttribute('href', '/vision-map');
    expect(screen.getByText('Overdue Tasks')).toBeInTheDocument();
  });

  it('scopes the dashboard to one vision area, and carries that scope into the links', async () => {
    renderPage(<DashboardPage />, { route: '/?visionAreaId=1' });
    await screen.findByText('Overdue Tasks');
    expect(dashboardApi.getDashboardSummary).toHaveBeenCalledWith('token', '1', expect.any(String), expect.any(String));
    expect(within(pressure()).getByText('Due this month').closest('a')?.getAttribute('href')).toMatch(/&visionAreaId=1$/);
    expect(within(pressure()).getByText('Overdue Tasks').closest('a')).toHaveAttribute('href', '/tasks?overdue=true&visionAreaId=1');
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

  it('opens the task board to add a task', async () => {
    renderPage(<DashboardPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New Task' }));
    expect(currentLocation()).toBe('/tasks?new=true');
  });

  it('shows why the dashboard could not load, with a way to try again', async () => {
    vi.mocked(dashboardApi.getDashboardSummary).mockRejectedValueOnce(new Error('Server unavailable'));
    renderPage(<DashboardPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Get started' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Overdue Tasks')).toBeInTheDocument();
  });

  it('celebrates once when a goal has been completed since the last visit (DA-FR-14)', async () => {
    window.localStorage.setItem('vms.v25.completedGoals.1', '1');
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, goalsByStatus: { ...known.goalsByStatus, COMPLETED: 2 } });
    renderPage(<DashboardPage />);
    expect(await screen.findByText('A goal is complete. One step closer to your dream.')).toBeInTheDocument();
    expect(window.localStorage.getItem('vms.v25.completedGoals.1')).toBe('2');
  });

  it('does not celebrate on a first visit or in a scoped view', async () => {
    window.localStorage.removeItem('vms.v25.completedGoals.1');
    vi.mocked(dashboardApi.getDashboardSummary).mockResolvedValue({ ...known, goalsByStatus: { COMPLETED: 4 } });
    const { unmount } = renderPage(<DashboardPage />);
    await screen.findByText('Overdue Tasks');
    expect(screen.queryByText('A goal is complete. One step closer to your dream.')).not.toBeInTheDocument();
    unmount();

    window.localStorage.setItem('vms.v25.completedGoals.1', '1');
    renderPage(<DashboardPage />, { route: '/?visionAreaId=1' });
    await screen.findByText('Overdue Tasks');
    expect(screen.queryByText('A goal is complete. One step closer to your dream.')).not.toBeInTheDocument();
    window.localStorage.removeItem('vms.v25.completedGoals.1');
  });

  it('keeps the layout while loading, and says so calmly after ten seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(dashboardApi.getDashboardSummary).mockReturnValue(new Promise(() => undefined));
    renderPage(<DashboardPage />);
    expect(await screen.findByRole('region', { name: 'Next up' })).toHaveAttribute('aria-busy', 'true');
    expect(screen.queryByText('Still loading…')).not.toBeInTheDocument();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(await screen.findByText('Still loading…')).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('shows the dashboard in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<DashboardPage />);
    expect(await screen.findByText('កិច្ចការហួសកំណត់')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'ថ្ងៃនេះ' })).toBeInTheDocument();
  });
});
