import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as savedReportApi from '../api/savedReportApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { currentLocation, renderPage } from '../test/render';
import type { ManagementReport, SavedReport } from '../types/vision';
import { SavedReportPage, filterOf } from './SavedReportPage';

vi.mock('../api/savedReportApi');
vi.mock('../api/dreamApi');
vi.mock('../api/visionAreaApi');

const summary = {
  scope: { kind: 'DREAM', dreamId: 7, label: 'Publish a study' },
  asOf: '2026-11-17',
  period: { kind: 'THIS_MONTH', from: '2026-11-01', to: '2026-11-17', statusAsOfToday: false, closedBeforeRecording: false },
  periodFigures: { tasksCompleted: 2, progressGained: 5, blockersOpened: 0, blockersClosed: 1, tasksBecameOverdue: 0 },
  headline: { overallProgress: 40, goalsByHealth: { RED: 0, AMBER: 0, DONE_OPEN_WORK: 0, GREEN: 1, DONE: 0 }, totalTasks: 3,
    completedTasks: 2, overdueTasks: 0, blockedTasks: 0, openBlockers: 0, dueSoonTasks: 0 },
  tasksByStatus: { NOT_STARTED: 1, IN_PROGRESS: 0, WAITING: 0, BLOCKED: 0, COMPLETED: 2, PAUSED: 0 },
  openTasksByPriority: [],
  blockersByKind: {},
  unplacedObstacles: 0,
  goals: [{ goalId: 5, goalCode: 'G-005', goalTitle: 'Collect the data', dreamId: 7, dreamTitle: 'Publish a study', health: 'GREEN',
    progress: 40, targetDate: null, overdueTasks: 0, blockedItems: 0, nextAction: 'Call', reasons: [{ code: 'ON_TRACK' }] }],
  attention: [],
  trend: [],
  filters: { priorities: [], owner: null, healths: [], includeCompleted: true },
  owners: ['Dara'],
} as ManagementReport;

const saved = (overrides: Partial<SavedReport> = {}): SavedReport => ({
  id: 3, code: 'R-003', name: 'November progress', status: 'DRAFT', archived: false, scopeKind: 'DREAM', areaId: null, dreamId: 7,
  scopeLabel: 'Publish a study', periodKind: 'THIS_MONTH', periodFrom: null, periodTo: null,
  filters: { priorities: [], owner: null, healths: [], includeCompleted: true },
  commentary: { highlights: null, risks: '- Venue may cancel', decisionsNeeded: null, nothingNeeded: false, nextFocus: null, goalNotes: {} },
  language: 'en', compareToReportId: null, sections: [], finalizedAt: null, createdAt: '2026-11-17T08:00:00Z', updatedAt: '2026-11-17T08:00:00Z',
  verified: null, report: summary, ...overrides,
});

function open() {
  renderPage(<SavedReportPage />, { route: '/vision-map/reports/3', path: '/vision-map/reports/:id' });
}

describe('SavedReportPage (V19 RM-FR-16 to RM-FR-31)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(savedReportApi.getSavedReport).mockResolvedValue(saved());
    vi.mocked(savedReportApi.listSavedReports).mockResolvedValue([]);
    vi.mocked(savedReportApi.listReportShares).mockResolvedValue([]);
    vi.mocked(savedReportApi.listReportExports).mockResolvedValue([]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([{ id: 7, title: 'Publish a study', archived: false }] as never);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([]);
    vi.mocked(savedReportApi.saveReportCommentary).mockImplementation(async (_token, _id, commentary) => saved({ commentary }));
  });

  it('shows a draft with live figures, editable commentary and settings, and saves the commentary on leaving a field (RM-FR-27)', async () => {
    open();
    expect(await screen.findByText('Draft · figures update until you finalise')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Report on' })).toBeInTheDocument();
    const commentary = screen.getByRole('region', { name: 'Commentary' });
    const highlights = within(commentary).getByLabelText('Highlights');
    await userEvent.type(highlights, '- Survey done');
    await userEvent.tab();
    await waitFor(() => expect(savedReportApi.saveReportCommentary).toHaveBeenCalledWith('token', 3,
      expect.objectContaining({ highlights: '- Survey done', risks: '- Venue may cancel' })));
    expect(await within(commentary).findByText('Saved')).toBeInTheDocument();
  });

  it('fills only empty sections with suggestions, marked until edited (RM-FR-28)', async () => {
    vi.mocked(savedReportApi.draftReportCommentary).mockResolvedValue({ highlights: '- Completed 2 task(s)', risks: null, nextFocus: '- Call' });
    open();
    const commentary = await screen.findByRole('region', { name: 'Commentary' });
    await userEvent.click(within(commentary).getByRole('button', { name: 'Suggest a draft' }));
    expect(await within(commentary).findByDisplayValue('- Completed 2 task(s)')).toBeInTheDocument();
    expect(within(commentary).getByDisplayValue('- Venue may cancel')).toBeInTheDocument();
    expect(within(commentary).getAllByText('Suggested')).toHaveLength(2);
    await userEvent.type(within(commentary).getByLabelText('Highlights'), '!');
    expect(within(commentary).getAllByText('Suggested')).toHaveLength(1);
  });

  it('finalises after confirmation, then shows the frozen report read-only (RM-FR-19, RM-FR-20)', async () => {
    vi.mocked(savedReportApi.finalizeSavedReport).mockResolvedValue(saved({ status: 'FINAL', finalizedAt: '2026-11-17T09:00:00Z',
      periodFrom: '2026-11-01', periodTo: '2026-11-17', verified: true }));
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'Finalise' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(/Figures will be frozen as of/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Finalise' }));
    expect(await screen.findByText(/Final · frozen/)).toBeInTheDocument();
    expect(savedReportApi.finalizeSavedReport).toHaveBeenCalledWith('token', 3, 'en');
    expect(screen.queryByRole('combobox', { name: 'Report on' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete draft' })).not.toBeInTheDocument();
    const commentary = screen.getByRole('region', { name: 'Commentary' });
    expect(within(commentary).getByText('- Venue may cancel')).toBeInTheDocument();
    expect(within(commentary).queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('warns when a final report fails its check, and can still be renamed', async () => {
    vi.mocked(savedReportApi.getSavedReport).mockResolvedValue(saved({ status: 'FINAL', finalizedAt: '2026-11-17T09:00:00Z', verified: false,
      commentary: { nothingNeeded: true, goalNotes: { 5: 'On schedule' } } }));
    vi.mocked(savedReportApi.renameSavedReport).mockResolvedValue(saved({ status: 'FINAL', name: 'Q4 review' }));
    open();
    expect(await screen.findByText('This report could not be verified. Its stored figures may have been changed.')).toBeInTheDocument();
    expect(screen.getByText('Nothing needed this period.')).toBeInTheDocument();
    expect(screen.getByText('On schedule')).toBeInTheDocument();
    const name = screen.getByRole('textbox', { name: 'Report name' });
    await userEvent.clear(name);
    await userEvent.type(name, 'Q4 review');
    await userEvent.click(screen.getByRole('button', { name: 'Rename' }));
    await waitFor(() => expect(savedReportApi.renameSavedReport).toHaveBeenCalledWith('token', 3, 'Q4 review'));
  });

  it('changes a draft\'s settings, makes a new report from it, archives it and deletes it', async () => {
    vi.mocked(savedReportApi.updateSavedReport).mockResolvedValue(saved({ periodKind: 'LAST_MONTH' }));
    vi.mocked(savedReportApi.copySavedReport).mockResolvedValue(saved({ id: 9 }));
    vi.mocked(savedReportApi.archiveSavedReport).mockResolvedValue(saved({ archived: true }));
    vi.mocked(savedReportApi.deleteSavedReport).mockResolvedValue(undefined);
    open();
    await userEvent.click(await screen.findByRole('combobox', { name: 'Period' }));
    await userEvent.click(screen.getByRole('option', { name: 'Last month' }));
    await waitFor(() => expect(savedReportApi.updateSavedReport).toHaveBeenCalledWith('token', 3,
      expect.objectContaining({ dreamId: 7, period: 'LAST_MONTH', name: 'November progress' })));

    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(await screen.findByRole('button', { name: 'Restore' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Delete draft' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete draft' }));
    await waitFor(() => expect(currentLocation()).toBe('/vision-map/reports'));
  });

  it('rmFr32: picks the report a draft is compared with, and drops it for a new scope; says how to get one (RM-FR-36)', async () => {
    vi.mocked(savedReportApi.listSavedReports).mockResolvedValue([
      saved({ id: 1, code: 'R-001', name: 'September', status: 'FINAL', finalizedAt: '2026-09-30T09:00:00Z' }),
      saved({ id: 2, code: 'R-002', name: 'October', status: 'FINAL', finalizedAt: '2026-10-31T09:00:00Z' }),
      saved({ id: 3, code: 'R-003', name: 'Itself', status: 'FINAL' }),
      saved({ id: 4, code: 'R-004', name: 'All dreams', status: 'FINAL', scopeKind: 'ALL', dreamId: null }),
    ]);
    vi.mocked(savedReportApi.updateSavedReport).mockResolvedValue(saved({ compareToReportId: 1 }));
    open();
    expect(await screen.findByText('Finalise this report to compare the next one with it.')).toBeInTheDocument();
    const picker = await screen.findByRole('combobox', { name: 'Compare with' });
    await waitFor(() => expect(within(picker).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'The latest final report', 'October (R-002, Oct 31, 2026)', 'September (R-001, Sep 30, 2026)']));
    await userEvent.selectOptions(picker, '1');
    await waitFor(() => expect(savedReportApi.updateSavedReport).toHaveBeenCalledWith('token', 3,
      expect.objectContaining({ dreamId: 7, compareToReportId: 1 })));
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Compare with' })).toHaveValue('1'));

    await userEvent.click(screen.getByRole('combobox', { name: 'Report on' }));
    await userEvent.click(screen.getByRole('option', { name: 'All dreams' }));
    await waitFor(() => expect(savedReportApi.updateSavedReport).toHaveBeenLastCalledWith('token', 3,
      expect.objectContaining({ compareToReportId: null })));
  });

  it('rmFr39: remembers the sections chosen for the files, and starts a final report in its own language (RM-FR-44)', async () => {
    vi.mocked(savedReportApi.getSavedReport).mockResolvedValue(saved({ status: 'FINAL', finalizedAt: '2026-11-17T09:00:00Z',
      language: 'km', sections: ['SUMMARY', 'DETAILS'] }));
    vi.mocked(savedReportApi.saveReportSections).mockResolvedValue(saved());
    renderPage(<SavedReportPage />, { route: '/vision-map/reports/3?tab=export', path: '/vision-map/reports/:id' });
    const include = await screen.findByRole('region', { name: 'What the files include' });
    expect(within(include).getByRole('combobox', { name: 'File language' })).toHaveValue('km');
    expect(within(include).getByRole('checkbox', { name: 'Summary' })).toBeChecked();
    expect(within(include).getByRole('checkbox', { name: 'Blockers' })).not.toBeChecked();
    await userEvent.click(within(include).getByRole('checkbox', { name: 'Blockers' }));
    expect(savedReportApi.saveReportSections).toHaveBeenCalledWith('token', 3, ['SUMMARY', 'DETAILS', 'BLOCKERS']);
    expect(screen.getByText(/These files show the figures frozen on/)).toBeInTheDocument();
  });

  it('rmFr46: shares a final report after showing what the reviewer sees, shows the link once, and stops it (RM-FR-48)', async () => {
    const final = saved({ status: 'FINAL', finalizedAt: '2026-11-17T09:00:00Z', verified: true });
    vi.mocked(savedReportApi.getSavedReport).mockResolvedValue(final);
    const open = { id: 21, sections: ['COMMENTARY', 'GOAL_HEALTH', 'CHANGES'] as const, createdAt: '2026-11-18T09:00:00Z',
      expiresAt: '2026-12-18T09:00:00Z', revokedAt: null, open: true, viewCount: 3, lastViewedAt: '2026-11-19T09:00:00Z' };
    vi.mocked(savedReportApi.listReportShares).mockResolvedValueOnce([]).mockResolvedValue([{ ...open, sections: [...open.sections] }]);
    vi.mocked(savedReportApi.createReportShare).mockResolvedValue({ ...open, sections: ['COMMENTARY', 'GOAL_HEALTH', 'CHANGES', 'DETAILS'],
      viewCount: 0, lastViewedAt: null, token: 'tok123' });
    vi.mocked(savedReportApi.revokeReportShare).mockResolvedValue({ ...open, sections: [], open: false, revokedAt: '2026-11-20T09:00:00Z' });
    renderPage(<SavedReportPage />, { route: '/vision-map/reports/3', path: '/vision-map/reports/:id' });

    const card = await screen.findByRole('region', { name: 'Share' });
    expect(within(card).getByText('No share links yet.')).toBeInTheDocument();
    await userEvent.click(within(card).getByRole('button', { name: 'Create share link' }));
    const dialog = screen.getByRole('dialog', { name: 'Share this report?' });
    await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: 'Link lasts' }), '90');
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Details / Report rows' }));
    expect(within(dialog).getByRole('status')).toHaveTextContent(
      'The reviewer will see: Headline and period figures, Commentary, Goal health, Details / Report rows, What changed.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create link' }));
    expect(savedReportApi.createReportShare).toHaveBeenCalledWith('token', 3, 90, ['COMMENTARY', 'GOAL_HEALTH', 'DETAILS', 'CHANGES']);
    const ready = await screen.findByRole('dialog', { name: 'Link ready' });
    expect(within(ready).getByRole('textbox', { name: 'Share link' })).toHaveValue(`${window.location.origin}/shared-report/tok123`);
    await userEvent.click(within(ready).getByRole('button', { name: 'Done' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    expect(await within(card).findByText(/3 view\(s\) · last viewed Nov 19, 2026/)).toBeInTheDocument();
    await userEvent.click(within(card).getByRole('button', { name: 'Stop link made Nov 18, 2026' }));
    expect(savedReportApi.revokeReportShare).toHaveBeenCalledWith('token', 3, 21);

    // RM-FR-51: archiving warns that open links stop.
    vi.mocked(savedReportApi.archiveSavedReport).mockResolvedValue({ ...final, archived: true });
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    const confirm = screen.getByRole('dialog', { name: 'Archive this report?' });
    expect(confirm).toHaveTextContent('Its 1 open share link(s) will stop working at once.');
    await userEvent.click(within(confirm).getByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(savedReportApi.archiveSavedReport).toHaveBeenCalledWith('token', 3));
    expect(await screen.findByText('Restore this report before you share it.')).toBeInTheDocument();
  });

  it('rmc18: a draft has no Share card; an unverified final report cannot be shared; downloads are listed (RM-FR-25)', async () => {
    vi.mocked(savedReportApi.listReportExports).mockResolvedValue([
      { id: 2, format: 'pdf', language: 'km', createdAt: '2026-11-18T09:30:00Z' },
      { id: 1, format: 'xlsx', language: 'en', createdAt: '2026-11-17T08:00:00Z' },
    ]);
    const { unmount } = renderPage(<SavedReportPage />, { route: '/vision-map/reports/3?tab=export', path: '/vision-map/reports/:id' });
    const history = await screen.findByRole('region', { name: 'Download history' });
    expect(within(history).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      expect.stringMatching(/^PDF · ខ្មែរ · Nov 18, 2026/), expect.stringMatching(/^Excel · English · Nov 17, 2026/)]);
    expect(screen.queryByRole('region', { name: 'Share' })).not.toBeInTheDocument();
    unmount();

    vi.mocked(savedReportApi.getSavedReport).mockResolvedValue(saved({ status: 'FINAL', finalizedAt: '2026-11-17T09:00:00Z', verified: false }));
    open();
    const card = await screen.findByRole('region', { name: 'Share' });
    expect(within(card).getByRole('button', { name: 'Create share link' })).toBeDisabled();
    expect(within(card).getByText('This report could not be verified, so it cannot be shared.')).toBeInTheDocument();
  });

  it('opens the new report made from this one (RM-FR-21)', async () => {
    vi.mocked(savedReportApi.copySavedReport).mockResolvedValue(saved({ id: 9 }));
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'New report from this' }));
    await waitFor(() => expect(currentLocation()).toBe('/vision-map/reports/9'));
  });

  it('shows why a report could not open, and maps saved settings back to the controls', async () => {
    vi.mocked(savedReportApi.getSavedReport).mockRejectedValue(new Error('Report not found.'));
    open();
    expect(await screen.findByText('Report not found.')).toBeInTheDocument();
    expect(filterOf(saved({ dreamId: null, areaId: 2, periodKind: 'CUSTOM', periodFrom: '2026-11-01', periodTo: '2026-11-05',
      filters: { priorities: ['HIGH'], owner: 'Dara', healths: ['RED'], includeCompleted: false } })))
      .toEqual({ areaId: 2, period: 'CUSTOM', from: '2026-11-01', to: '2026-11-05', priorities: ['HIGH'], owner: 'Dara', healths: ['RED'],
        includeCompleted: false });
  });
});
