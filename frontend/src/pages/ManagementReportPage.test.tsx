import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as reportApi from '../api/reportApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { renderPage } from '../test/render';
import type { Dream, ManagementReport, VisionArea } from '../types/vision';
import { ManagementReportPage } from './ManagementReportPage';

vi.mock('../api/reportApi');
vi.mock('../api/dreamApi');
vi.mock('../api/visionAreaApi');

const report = (overrides: Partial<ManagementReport> = {}): ManagementReport => ({
  scope: { kind: 'ALL', areaId: null, dreamId: null, label: null },
  asOf: '2026-11-16',
  headline: {
    overallProgress: 41.5,
    goalsByHealth: { RED: 1, AMBER: 1, GREEN: 0, DONE: 1 },
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
      progress: 20, targetDate: '2026-11-01', overdueTasks: 3, blockedItems: 2, nextAction: 'Call the hall manager' },
    { goalId: 2, goalCode: 'G-002', goalTitle: 'Write the paper', dreamId: 7, dreamTitle: 'Publish a study', health: 'AMBER',
      progress: 45, targetDate: '2027-02-01', overdueTasks: 1, blockedItems: 1, nextAction: null },
    { goalId: 3, goalCode: 'G-003', goalTitle: 'Pick a topic', dreamId: 7, dreamTitle: 'Publish a study', health: 'DONE',
      progress: 100, targetDate: '2026-09-01', overdueTasks: 0, blockedItems: 0, nextAction: null },
  ],
  attention: [
    { kind: 'CRITICAL_BLOCKER', itemType: 'OBSTACLE', itemId: 9, title: 'No budget', context: 'Collect the data › Run the survey',
      stepId: 4, goalId: 1 },
    { kind: 'BLOCKED_TASK', itemType: 'TASK', itemId: 11, title: 'Print forms', context: 'Collect the data › Run the survey',
      stepId: 4, goalId: 1 },
  ],
  ...overrides,
});

const areas = [{ id: 3, name: 'Career', archived: false }] as VisionArea[];
const dreams = [{ id: 7, title: 'Publish a study', archived: false }, { id: 8, title: 'Old dream', archived: true }] as Dream[];

describe('ManagementReportPage (V18 MR-FR-01 to MR-FR-13)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(reportApi.getManagementReport).mockResolvedValue(report());
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue(areas);
    vi.mocked(dreamApi.listDreams).mockResolvedValue(dreams);
  });

  it('opens on the Summary tab for all dreams, with the scope and date (MRC-01)', async () => {
    renderPage(<ManagementReportPage />, { route: '/vision-map/report', path: '/vision-map/report' });
    expect(await screen.findByText(/All dreams · as of/)).toBeInTheDocument();
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
    expect(await screen.findByText(/Publish a study · as of/)).toBeInTheDocument();
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

  it('shows why the report could not load', async () => {
    vi.mocked(reportApi.getManagementReport).mockRejectedValue(new Error('Dream not found'));
    renderPage(<ManagementReportPage />, { route: '/vision-map/report?dreamId=99', path: '/vision-map/report' });
    expect(await screen.findByText('Dream not found')).toBeInTheDocument();
  });
});
