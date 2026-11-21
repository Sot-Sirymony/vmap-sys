import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadSharedReportPdf, getSharedReport } from '../api/sharedReportApi';
import type { ManagementReport, SharedReport } from '../types/vision';
import { saveBlob } from '../utils/saveBlob';
import { SharedReportPage } from './SharedReportPage';

vi.mock('../api/sharedReportApi', () => ({ getSharedReport: vi.fn(), downloadSharedReportPdf: vi.fn() }));
vi.mock('../utils/saveBlob', () => ({ saveBlob: vi.fn() }));

const report = {
  scope: { kind: 'DREAM', areaId: null, dreamId: 7, label: 'Publish a study' },
  asOf: '2026-11-30',
  period: { kind: 'THIS_MONTH', from: '2026-11-01', to: '2026-11-30', statusAsOfToday: false, closedBeforeRecording: false },
  periodFigures: { tasksCompleted: 4, progressGained: 12, blockersOpened: 0, blockersClosed: 1, tasksBecameOverdue: 0 },
  headline: { overallProgress: 46, goalsByHealth: { RED: 0, AMBER: 0, DONE_OPEN_WORK: 0, GREEN: 1, DONE: 0 }, totalTasks: 3,
    completedTasks: 2, overdueTasks: 2, blockedTasks: 1, openBlockers: 0, dueSoonTasks: 0 },
  tasksByStatus: { NOT_STARTED: 1, IN_PROGRESS: 0, WAITING: 0, BLOCKED: 0, COMPLETED: 2, PAUSED: 0 },
  openTasksByPriority: [],
  blockersByKind: {},
  unplacedObstacles: 2,
  goals: [{ goalId: 5, goalCode: 'G-005', goalTitle: 'Collect the data', dreamId: 7, dreamTitle: 'Publish a study', health: 'GREEN',
    progress: 46, targetDate: null, overdueTasks: 0, blockedItems: 0, nextAction: 'Call', reasons: [{ code: 'ON_TRACK' }] }],
  attention: [],
  trend: [],
  filters: { priorities: [], owner: null, healths: [], includeCompleted: true },
  owners: [],
  comparison: null,
} as ManagementReport;

const shared = (overrides: Partial<SharedReport> = {}): SharedReport => ({
  name: 'November progress', code: 'R-001', sharedBy: 'Dara', language: 'en', finalizedAt: '2026-11-30T09:00:00Z',
  expiresAt: '2026-12-30T09:00:00Z', sections: ['COMMENTARY', 'GOAL_HEALTH', 'DETAILS'], report,
  commentary: { highlights: '- Booked the hall', risks: null, decisionsNeeded: null, nothingNeeded: false, nextFocus: null,
    goalNotes: { 5: 'Going well' } },
  rows: [{ goalId: 5, stepId: 9, taskId: 11, dream: 'Publish a study', goal: 'Collect the data', step: 'Field work', task: 'Print forms',
    issues: [], progress: 0, priority: 'HIGH', due: '2026-12-05', nextAction: '', completed: false, blocked: false, overdue: false,
    dueSoon: true }],
  ...overrides,
});

const page = () => render(
  <MemoryRouter initialEntries={['/shared-report/abc123']}>
    <Routes><Route path="/shared-report/:token" element={<SharedReportPage />} /></Routes>
  </MemoryRouter>,
);

describe('SharedReportPage (V19 RM-FR-47)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows only what was shared, from the frozen report, with no links into the plan (RMC-18)', async () => {
    vi.mocked(getSharedReport).mockResolvedValue(shared());
    page();
    expect(await screen.findByRole('heading', { level: 1, name: 'November progress' })).toBeInTheDocument();
    expect(getSharedReport).toHaveBeenCalledWith('abc123');
    expect(screen.getByText('Dara shared this report with you')).toBeInTheDocument();
    expect(screen.getByText('Final · frozen Nov 30, 2026')).toBeInTheDocument();
    expect(screen.getByText('- Booked the hall')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Goal health' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Needs attention' })).not.toBeInTheDocument();
    expect(screen.queryByText(/not on a step yet/)).not.toBeInTheDocument();
    const details = screen.getByRole('region', { name: 'Details' });
    expect(await within(details).findByText('Print forms')).toBeInTheDocument();
    // RM-FR-47: no links into the plan anywhere on the page.
    expect(screen.queryAllByRole('link')).toHaveLength(0);
    expect(screen.getByText('This link stops working on Dec 30, 2026.')).toBeInTheDocument();
    // Kept out of search engines, and the address is not sent on.
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex, nofollow');
    expect(document.head.querySelector('meta[name="referrer"]')?.getAttribute('content')).toBe('no-referrer');
  });

  it('downloads the PDF, and leaves out goal health when it was not shared', async () => {
    vi.mocked(getSharedReport).mockResolvedValue(shared({ sections: ['CHANGES'], commentary: null, rows: null,
      report: { ...report, goals: [] } }));
    const blob = new Blob(['%PDF']);
    vi.mocked(downloadSharedReportPdf).mockResolvedValueOnce({ blob, fileName: 'November_progress_2026-11-30.pdf' })
      .mockRejectedValueOnce(new Error('This report is no longer shared.'));
    page();
    await userEvent.click(await screen.findByRole('button', { name: 'Download PDF' }));
    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'November_progress_2026-11-30.pdf'));
    expect(screen.queryByRole('region', { name: 'Goal health' })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Commentary' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Download PDF' }));
    expect(await screen.findByText('This report is no longer shared.')).toBeInTheDocument();
  });

  it('says only that the report is no longer shared when the link has ended (RMC-18)', async () => {
    vi.mocked(getSharedReport).mockRejectedValue(new Error('This report is no longer shared.'));
    const { unmount } = page();
    expect(await screen.findByText('This report is no longer shared.')).toBeInTheDocument();
    unmount();
    expect(document.head.querySelector('meta[name="robots"]')).toBeNull();
  });
});
