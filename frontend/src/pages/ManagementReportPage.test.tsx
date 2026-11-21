import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as reportApi from '../api/reportApi';
import * as savedReportApi from '../api/savedReportApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { currentLocation, renderPage } from '../test/render';
import type { Dream, ManagementReport, VisionArea } from '../types/vision';
import { ManagementReportPage } from './ManagementReportPage';

vi.mock('../api/reportApi');
vi.mock('../api/savedReportApi');
vi.mock('../api/dreamApi');
vi.mock('../api/visionAreaApi');

const report = (overrides: Partial<ManagementReport> = {}): ManagementReport => ({
  scope: { kind: 'ALL', areaId: null, dreamId: null, label: null },
  asOf: '2026-11-16',
  period: { kind: 'THIS_MONTH', from: '2026-11-01', to: '2026-11-16', statusAsOfToday: false, closedBeforeRecording: false },
  periodFigures: { tasksCompleted: 9, progressGained: 12.4, blockersOpened: 1, blockersClosed: 2, tasksBecameOverdue: 3 },
  headline: {
    overallProgress: 41.5,
    goalsByHealth: { RED: 1, AMBER: 1, DONE_OPEN_WORK: 0, GREEN: 0, DONE: 1 },
    totalTasks: 40,
    completedTasks: 12,
    overdueTasks: 5,
    blockedTasks: 2,
    openBlockers: 3,
    dueSoonTasks: 4,
  },
  tasksByStatus: { NOT_STARTED: 10, IN_PROGRESS: 14, WAITING: 2, BLOCKED: 2, COMPLETED: 12, PAUSED: 0 },
  openTasksByPriority: [
    { priority: 'LOW', onTrack: 4, overdue: 0 },
    { priority: 'MEDIUM', onTrack: 10, overdue: 1 },
    { priority: 'HIGH', onTrack: 8, overdue: 3 },
    { priority: 'CRITICAL', onTrack: 1, overdue: 1 },
  ],
  blockersByKind: { MONEY: 2, KNOWLEDGE: 1 },
  unplacedObstacles: 2,
  goals: [
    { goalId: 1, goalCode: 'G-001', goalTitle: 'Collect the data', dreamId: 7, dreamTitle: 'Publish a study', health: 'RED',
      progress: 20, targetDate: '2026-11-01', overdueTasks: 3, blockedItems: 2, nextAction: 'Call the hall manager',
      reasons: [{ code: 'GOAL_OVERDUE', value: 15 }, { code: 'CRITICAL_BLOCKER', value: 1, title: 'No budget' }] },
    { goalId: 2, goalCode: 'G-002', goalTitle: 'Write the paper', dreamId: 7, dreamTitle: 'Publish a study', health: 'AMBER',
      progress: 45, targetDate: '2027-02-01', overdueTasks: 1, blockedItems: 1, nextAction: null,
      reasons: [{ code: 'BEHIND_SCHEDULE', value: 45, elapsed: 70 }] },
    { goalId: 3, goalCode: 'G-003', goalTitle: 'Pick a topic', dreamId: 7, dreamTitle: 'Publish a study', health: 'DONE',
      progress: 100, targetDate: '2026-09-01', overdueTasks: 0, blockedItems: 0, nextAction: null,
      reasons: [{ code: 'COMPLETED' }] },
  ],
  attention: [
    { kind: 'CRITICAL_BLOCKER', itemType: 'OBSTACLE', itemId: 9, title: 'No budget', context: 'Collect the data › Run the survey',
      stepId: 4, goalId: 1 },
    { kind: 'BLOCKED_TASK', itemType: 'TASK', itemId: 11, title: 'Print forms', context: 'Collect the data › Run the survey',
      stepId: 4, goalId: 1 },
  ],
  filters: { priorities: [], owner: null, healths: [], includeCompleted: true },
  owners: ['Dara', 'Sok'],
  trend: [
    { weekEnd: '2026-11-02', progress: 20 },
    { weekEnd: '2026-11-09', progress: 33.4 },
    { weekEnd: '2026-11-16', progress: 41.5 },
  ],
  ...overrides,
});

const areas = [{ id: 3, name: 'Career', archived: false }] as VisionArea[];
const dreams = [{ id: 7, title: 'Publish a study', archived: false }, { id: 8, title: 'Old dream', archived: true }] as Dream[];

describe('ManagementReportPage (V18 MR-FR-01 to MR-FR-13)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(savedReportApi.listSavedReports).mockResolvedValue([]);
    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report());
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue(areas);
    vi.mocked(dreamApi.listDreams).mockResolvedValue(dreams);
  });

  it('opens on the Summary tab for all dreams, with the scope and date (MRC-01)', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    expect(await screen.findByText(/All dreams · This month · as of/)).toBeInTheDocument();
    expect(reportApi.getManagementReport).toHaveBeenCalledWith('token', {});
    expect(screen.getByRole('tab', { name: 'Summary' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('41.5%')).toBeInTheDocument();
    // Tiles link to exactly the rows they count.
    expect(screen.getByRole('link', { name: /Overdue tasks/ })).toHaveAttribute('href', '/tasks?overdue=true');
    expect(screen.getByRole('link', { name: /Blocked tasks/ })).toHaveAttribute('href', '/tasks?status=BLOCKED');
    expect(screen.getByText('2 blocker(s) are not on a step yet, so they are not counted on any goal.')).toBeInTheDocument();
  });

  it('summarises each chart in words and offers the same numbers as a table (MRC-03, MR-FR-13)', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    const status = await screen.findByRole('region', { name: 'Tasks by status' });
    expect(within(status).getByText('12 of 40 tasks are completed.')).toBeInTheDocument();
    await userEvent.click(within(status).getByRole('button', { name: 'Show as table' }));
    const completedRow = within(status).getByRole('cell', { name: 'Completed' }).closest('tr') as HTMLElement;
    expect(within(completedRow).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['Completed', '12']);
    await userEvent.click(within(status).getByRole('button', { name: 'Show as chart' }));
    expect(within(status).queryByRole('table')).not.toBeInTheDocument();

    const priority = screen.getByRole('region', { name: 'Open tasks by priority' });
    expect(within(priority).getByText('28 open task(s), 13 of them high or critical.')).toBeInTheDocument();
    await userEvent.click(within(priority).getByRole('button', { name: 'Show as table' }));
    const highRow = within(priority).getByRole('cell', { name: 'High' }).closest('tr') as HTMLElement;
    expect(within(highRow).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['High', '8', '3']);

    const blockers = screen.getByRole('region', { name: 'Blocked items' });
    expect(within(blockers).getByText('2 blocked task(s) and 3 open blocker(s) on steps or tasks.')).toBeInTheDocument();
    await userEvent.click(within(blockers).getByRole('button', { name: 'Show as table' }));
    expect(within(blockers).getByRole('cell', { name: 'Money' })).toBeInTheDocument();

    const progress = screen.getByRole('region', { name: 'Progress by goal' });
    await userEvent.click(within(progress).getByRole('button', { name: 'Show as table' }));
    expect(within(progress).getByRole('cell', { name: '1. Collect the data' })).toBeInTheDocument();
  });

  it('shows the 12-week trend in words and as a table, or says there is no history yet (MR-FR-14)', async () => {
    const { unmount } = renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    const trend = await screen.findByRole('region', { name: 'Progress over the last 12 weeks' });
    expect(within(trend).getByText('Average task progress went from 20% to 42% over 3 week(s).')).toBeInTheDocument();
    await userEvent.click(within(trend).getByRole('button', { name: 'Show as table' }));
    const row = within(trend).getByRole('cell', { name: '2026-11-09' }).closest('tr') as HTMLElement;
    expect(within(row).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['2026-11-09', '33%']);
    unmount();

    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report({ trend: [] }));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    const empty = await screen.findByRole('region', { name: 'Progress over the last 12 weeks' });
    expect(within(empty).getByText('No progress history yet. It builds up as tasks move.')).toBeInTheDocument();
  });

  it('rates each goal with a label, not colour alone, and lists what needs attention (MRC-04, MR-FR-12)', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    const health = await screen.findByRole('region', { name: 'Goal health' });
    const redRow = within(health).getByText('Collect the data').closest('tr') as HTMLElement;
    expect(within(redRow).getByText('Off track')).toBeInTheDocument();
    expect(within(redRow).getByText('Call the hall manager')).toBeInTheDocument();
    expect(within(health).getByText('At risk')).toBeInTheDocument();
    expect(within(health).getByText('Done')).toBeInTheDocument();

    const attention = screen.getByRole('region', { name: 'Needs attention' });
    expect(within(attention).getByRole('link', { name: 'No budget' })).toHaveAttribute('href', '/obstacles?goalId=1');
    expect(within(attention).getByRole('link', { name: 'Print forms' })).toHaveAttribute('href', '/tasks?stepId=4');
  });

  it('reports on one dream or one vision area, kept in the URL (MRC-02)', async () => {
    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report({
      scope: { kind: 'DREAM', dreamId: 7, label: 'Publish a study' },
    }));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?dreamId=7', path: '/vision-map/report' });
    expect(await screen.findByText(/Publish a study · This month · as of/)).toBeInTheDocument();
    expect(reportApi.getManagementReport).toHaveBeenCalledWith('token', { dreamId: 7 });
    // One dream: the goal health table has no Dream column, and tiles link within the dream.
    const health = screen.getByRole('region', { name: 'Goal health' });
    expect(within(health).queryByRole('columnheader', { name: /Dream/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Overdue tasks/ })).toHaveAttribute('href', '/tasks?overdue=true&dreamId=7');

    await userEvent.click(screen.getByRole('combobox', { name: 'Report on' }));
    await userEvent.click(screen.getByRole('option', { name: 'One vision area' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { areaId: 3 }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Report on' }));
    await userEvent.click(screen.getByRole('option', { name: 'All dreams' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', {}));
  });

  it('explains what to add when there is nothing to report, and shows the Export tab (MR-FR-05)', async () => {
    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report({ goals: [], attention: [], unplacedObstacles: 0 }));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    expect(await screen.findByText('Nothing to report yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Export' }));
    expect(screen.getByRole('button', { name: 'Export to Excel' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Export to PowerPoint' })).toBeDisabled();
  });

  it('rmFr01: says why each goal has its health, in words under the label', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    const health = await screen.findByRole('region', { name: 'Goal health' });
    const redRow = within(health).getByText('Collect the data').closest('tr') as HTMLElement;
    expect(within(redRow).getByText('Target date passed 15 day(s) ago · Critical blocker: No budget')).toBeInTheDocument();
    const amberRow = within(health).getByText('Write the paper').closest('tr') as HTMLElement;
    expect(within(amberRow).getByText('Progress 45% with 70% of the time gone')).toBeInTheDocument();
  });

  it('rmFr02: a completed goal with open tasks is "Done – open work" and needs finishing or reopening', async () => {
    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report({
      goals: [{ goalId: 5, goalCode: 'G-005', goalTitle: 'Build the team', dreamId: 7, dreamTitle: 'Publish a study',
        health: 'DONE_OPEN_WORK', progress: 100, targetDate: null, overdueTasks: 0, blockedItems: 0, nextAction: 'Hire',
        reasons: [{ code: 'COMPLETED_OPEN_WORK', value: 2 }] }],
      attention: [{ kind: 'REOPEN_OR_FINISH', itemType: 'GOAL', itemId: 5, title: 'Build the team', context: 'Publish a study',
        stepId: null, goalId: 5 }],
    }));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    const health = await screen.findByRole('region', { name: 'Goal health' });
    expect(within(health).getByText('Done – open work')).toBeInTheDocument();
    expect(within(health).getByText('Completed, but 2 task(s) are still open')).toBeInTheDocument();
    const attention = screen.getByRole('region', { name: 'Needs attention' });
    expect(within(attention).getByText(/Reopen or finish/)).toBeInTheDocument();
    expect(within(attention).getByRole('link', { name: 'Build the team' })).toHaveAttribute('href', '/tasks?goalId=5');
  });

  it('rmFr08: shows what happened in the period, and notes when status figures are today\'s', async () => {
    const { unmount } = renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    const period = await screen.findByRole('region', { name: 'This period' });
    expect(within(period).getByText('This period: This month')).toBeInTheDocument();
    expect(within(period).getByText('Tasks completed').nextSibling).toHaveTextContent('9');
    expect(within(period).getByText('+12.4 points')).toBeInTheDocument();
    expect(within(period).getByText('Blockers closed').nextSibling).toHaveTextContent('2');
    expect(within(period).queryByText(/Status figures are as of today/)).not.toBeInTheDocument();
    unmount();

    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report({
      period: { kind: 'LAST_MONTH', from: '2026-10-01', to: '2026-10-31', statusAsOfToday: true, closedBeforeRecording: true },
    }));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?period=LAST_MONTH', path: '/vision-map/report' });
    const past = await screen.findByRole('region', { name: 'This period' });
    expect(within(past).getByText(/Status figures are as of today/)).toBeInTheDocument();
    expect(within(past).getByText(/before the app recorded when blockers were closed/)).toBeInTheDocument();
    expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { period: 'LAST_MONTH' });
    expect(screen.getByText(/All dreams · Last month · as of/)).toBeInTheDocument();
  });

  it('rmFr06: picks a preset at once, and a custom period only when its dates are valid', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?dreamId=7', path: '/vision-map/report' });
    await screen.findByRole('region', { name: 'This period' });
    expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { dreamId: 7 });

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(screen.getByRole('option', { name: 'This quarter' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { dreamId: 7, period: 'THIS_QUARTER' }));

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(screen.getByRole('option', { name: 'Custom dates' }));
    const calls = vi.mocked(reportApi.getManagementReport).mock.calls.length;
    await userEvent.type(screen.getByLabelText('From'), '2026-11-10');
    await userEvent.type(screen.getByLabelText('To'), '2026-11-01');
    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(screen.getByText('The period must start on or before its end.')).toBeInTheDocument();
    expect(reportApi.getManagementReport).toHaveBeenCalledTimes(calls);

    await userEvent.clear(screen.getByLabelText('To'));
    await userEvent.type(screen.getByLabelText('To'), '2026-11-12');
    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token',
      { dreamId: 7, period: 'CUSTOM', from: '2026-11-10', to: '2026-11-12' }));

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(screen.getByRole('option', { name: 'This month' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { dreamId: 7 }));
  });

  it('rmFr10: filters by priority, owner, health and completed goals in the URL, and resets them', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    await screen.findByRole('region', { name: 'This period' });
    await userEvent.click(screen.getByRole('combobox', { name: 'Priority' }));
    await userEvent.click(screen.getByRole('option', { name: 'Critical' }));
    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { priorities: ['CRITICAL'] }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Owner' }));
    await userEvent.click(screen.getByRole('option', { name: 'Sok' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { priorities: ['CRITICAL'], owner: 'Sok' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Include completed goals' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token',
      { priorities: ['CRITICAL'], owner: 'Sok', includeCompleted: false }));
    expect(currentLocation()).toBe('/vision-map/report?priority=CRITICAL&owner=Sok&completed=0');
    await userEvent.click(screen.getByRole('button', { name: 'Reset filters' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', {}));
  });

  it('rmFr13: the Details tab lists the rows with search and quick filters', async () => {
    vi.mocked(reportApi.getReportRows).mockResolvedValue([
      { goalId: 1, stepId: 4, taskId: 11, dream: 'Publish a study', goal: 'Collect the data', step: 'Run the survey', task: 'Print forms',
        issues: ['Blocked: Printer broken'], progress: 0, priority: 'HIGH', due: '2026-11-10', nextAction: 'Call', completed: false,
        blocked: true, overdue: true, dueSoon: false },
      { goalId: 1, stepId: 4, taskId: 12, dream: 'Publish a study', goal: 'Collect the data', step: 'Run the survey', task: 'Book room',
        issues: [], progress: 100, priority: 'LOW', due: null, nextAction: '', completed: true, blocked: false, overdue: false, dueSoon: false },
    ]);
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?tab=details', path: '/vision-map/report' });
    const details = await screen.findByRole('region', { name: 'Details' });
    expect(await within(details).findByRole('link', { name: 'Print forms' })).toHaveAttribute('href', '/tasks?stepId=4');
    expect(within(details).getByText('Blocked: Printer broken')).toBeInTheDocument();
    expect(reportApi.getReportRows).toHaveBeenCalledWith('token', {}, 'en');
    await userEvent.click(within(details).getByRole('button', { name: 'Overdue' }));
    expect(within(details).queryByText('Book room')).not.toBeInTheDocument();
    expect(within(details).getByText('1 of 2 row(s)')).toBeInTheDocument();
    await userEvent.click(within(details).getByRole('button', { name: 'Overdue' }));
    await userEvent.type(within(details).getByRole('searchbox', { name: 'Search the rows' }), 'book');
    expect(within(details).getByText('Book room')).toBeInTheDocument();
    expect(within(details).queryByText('Print forms')).not.toBeInTheDocument();
  });

  it('rmFr16: Save as report keeps the scope, period and filters and opens the saved draft', async () => {
    vi.mocked(savedReportApi.createSavedReport).mockResolvedValue({ id: 42 } as never);
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?dreamId=7&period=LAST_MONTH', path: '/vision-map/report' });
    await screen.findByRole('region', { name: 'This period' });
    await userEvent.click(screen.getByRole('button', { name: 'Save as report' }));
    await waitFor(() => expect(currentLocation()).toBe('/vision-map/reports/42'));
    expect(savedReportApi.createSavedReport).toHaveBeenCalledWith('token', { dreamId: 7, period: 'LAST_MONTH', language: 'en' });
  });

  it('rmFr32: compares with a final report on the same scope, kept in the URL, and shows what changed (RMC-12, RMC-13)', async () => {
    vi.mocked(savedReportApi.listSavedReports).mockResolvedValue([
      { id: 4, code: 'R-004', name: 'October', status: 'FINAL', scopeKind: 'DREAM', dreamId: 7, areaId: null, finalizedAt: '2026-10-31T09:00:00Z' },
      { id: 5, code: 'R-005', name: 'Other dream', status: 'FINAL', scopeKind: 'DREAM', dreamId: 9, areaId: null, finalizedAt: '2026-10-31T09:00:00Z' },
      { id: 6, code: 'R-006', name: 'Draft', status: 'DRAFT', scopeKind: 'DREAM', dreamId: 7, areaId: null },
    ] as never);
    const changes = Array.from({ length: 16 }, (_, index) => ({
      kind: 'TASK_COMPLETED' as const, itemType: 'TASK' as const, itemId: 100 + index, title: `Task ${index}`, context: 'Goal › Step',
      before: null, after: null, renamed: false,
    }));
    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report({
      scope: { kind: 'DREAM', dreamId: 7, label: 'Publish a study' },
      comparison: {
        reportId: 4, code: 'R-004', name: 'October', asOf: '2026-10-31',
        headline: { overallProgress: 12, totalTasks: 3, completedTasks: 9, overdueTasks: -2, blockedTasks: 0, openBlockers: 1, dueSoonTasks: 0 },
        goals: [
          { goalId: 1, isNew: false, healthBefore: 'AMBER', progressChange: 12, targetBefore: '2026-10-15', previousTitle: 'Collect data' },
          { goalId: 2, isNew: true },
        ],
        removed: [{ goalId: 9, title: 'Old goal', reason: 'ARCHIVED' }, { goalId: 10, title: 'Filtered goal', reason: null }],
        changes: [
          { kind: 'GOAL_WORSE', itemType: 'GOAL', itemId: 1, title: 'Collect the data', context: 'Publish a study', before: 'AMBER',
            after: 'RED', renamed: true },
          { kind: 'DUE_LATER', itemType: 'TASK', itemId: 11, title: 'Print forms', context: null, before: '2026-11-10', after: '2026-11-24',
            renamed: false },
          { kind: 'NEW_BLOCKER', itemType: 'OBSTACLE', itemId: 9, title: 'No budget', before: null, after: 'CRITICAL', renamed: false },
          ...changes,
        ],
        totalChanges: 25,
      },
    }));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?dreamId=7', path: '/vision-map/report' });

    const picker = await screen.findByRole('combobox', { name: 'Compare with' });
    expect(within(picker).getAllByRole('option').map((option) => option.textContent)).toEqual([
      "Don't compare", 'October (R-004, Oct 31, 2026)']);
    await userEvent.selectOptions(picker, '4');
    await waitFor(() => expect(currentLocation()).toContain('compareTo=4'));
    expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', { dreamId: 7, compareTo: 4 });

    // RM-FR-33: each tile shows its change, and says it in words.
    expect(screen.getByText('up 12 points')).toBeInTheDocument();
    expect(screen.getByText('2 fewer overdue tasks')).toBeInTheDocument();
    expect(screen.getByText('1 more open blockers')).toBeInTheDocument();
    expect(screen.getAllByText('no change since the compared report').length).toBeGreaterThan(0);
    // RM-FR-34: a Change column.
    const health = screen.getByRole('region', { name: 'Goal health' });
    expect(within(health).getByText('At risk → Off track · +12 points · Target Oct 15, 2026 → Nov 1, 2026 · (renamed)')).toBeInTheDocument();
    expect(within(health).getByText('New')).toBeInTheDocument();
    // RM-FR-35: up to 15 lines, most important first, and the rest on the Details tab.
    const changed = screen.getByRole('region', { name: 'What changed' });
    expect(within(changed).getByText('Since October (R-004), frozen Oct 31, 2026')).toBeInTheDocument();
    expect(within(changed).getAllByRole('listitem')).toHaveLength(15 + 2);
    expect(within(changed).getByText(/Collect the data \(renamed\) · At risk → Off track/)).toBeInTheDocument();
    expect(within(changed).getByText(/Print forms · Nov 10, 2026 → Nov 24, 2026/)).toBeInTheDocument();
    expect(within(changed).getByText(/No budget · Critical/)).toBeInTheDocument();
    expect(within(changed).getByText('Archived', { exact: false })).toBeInTheDocument();
    expect(within(changed).getByText('Filtered goal')).toBeInTheDocument();
    await userEvent.click(within(changed).getByRole('button', { name: 'and 10 more: see the Details tab' }));
    await waitFor(() => expect(currentLocation()).toContain('tab=details'));

    // Saving keeps the comparison; a new scope drops it.
    vi.mocked(savedReportApi.createSavedReport).mockResolvedValue({ id: 42 } as never);
    await userEvent.click(screen.getByRole('button', { name: 'Save as report' }));
    await waitFor(() => expect(savedReportApi.createSavedReport).toHaveBeenCalledWith('token',
      { dreamId: 7, compareTo: 4, compareToReportId: 4, language: 'en' }));
  });

  it('rmFr32: a new scope drops the comparison', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?dreamId=7&compareTo=4', path: '/vision-map/report' });
    await screen.findByRole('region', { name: 'This period' });
    expect(screen.getByRole('combobox', { name: 'Compare with' })).toBeDisabled();
    await userEvent.click(screen.getByRole('combobox', { name: 'Report on' }));
    await userEvent.click(screen.getByRole('option', { name: 'All dreams' }));
    await waitFor(() => expect(reportApi.getManagementReport).toHaveBeenLastCalledWith('token', {}));
  });

  it('shows why the report could not load', async () => {
    vi.mocked(reportApi.getManagementReport).mockRejectedValue(new Error('Dream not found'));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?dreamId=99', path: '/vision-map/report' });
    expect(await screen.findByText('Dream not found')).toBeInTheDocument();
  });
});
