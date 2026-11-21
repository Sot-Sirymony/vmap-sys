import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as cohortApi from '../api/cohortApi';
import { renderPage } from '../test/render';
import type { ManagementReport, SharedReport } from '../types/vision';
import { CoachSharedReportPage } from './CoachSharedReportPage';

vi.mock('../api/cohortApi');

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

const open = () => renderPage(<CoachSharedReportPage />, { role: 'COACH', route: '/coach/participants/11/reports/4',
  path: '/coach/participants/:userId/reports/:reportId' });

describe('CoachSharedReportPage (V19 RM-FR-50)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the shared report read-only, with no link expiry', async () => {
    vi.mocked(cohortApi.getCoachSharedReport).mockResolvedValue({ name: 'October progress', code: 'R-002', sharedBy: 'Chan', language: 'en',
      finalizedAt: '2026-11-01T09:00:00Z', expiresAt: null, sections: ['COMMENTARY'], report,
      commentary: { highlights: '- Venue booked', nothingNeeded: false } } as unknown as SharedReport);
    open();
    expect(await screen.findByRole('heading', { name: 'October progress', level: 2 })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(cohortApi.getCoachSharedReport).toHaveBeenCalledWith('token', 11, 4);
    expect(screen.getByText('Chan shared this report with you, read-only')).toBeInTheDocument();
    expect(screen.getByText('- Venue booked')).toBeInTheDocument();
    expect(screen.queryByText(/This link stops working/)).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('says plainly when the report is no longer shared', async () => {
    vi.mocked(cohortApi.getCoachSharedReport).mockRejectedValue(new Error('Report not found.'));
    open();
    expect(await screen.findByText('Report not found.')).toBeInTheDocument();
  });
});
