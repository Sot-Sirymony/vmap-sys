import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as savedReportApi from '../api/savedReportApi';
import { renderPage } from '../test/render';
import type { SavedReport } from '../types/vision';
import { SavedReportsPage } from './SavedReportsPage';

vi.mock('../api/savedReportApi');

const base = {
  archived: false, scopeKind: 'DREAM', areaId: null, dreamId: 7, scopeLabel: 'Publish a study', periodKind: 'THIS_MONTH',
  periodFrom: null, periodTo: null, filters: { priorities: [], owner: null, healths: [], includeCompleted: true },
  commentary: { nothingNeeded: false, goalNotes: {} }, language: 'en', compareToReportId: null, finalizedAt: null,
  createdAt: '2026-11-17T08:00:00Z', updatedAt: '2026-11-17T08:00:00Z', verified: null, report: null,
} as const;

const reports = [
  { ...base, id: 1, code: 'R-001', name: 'October progress', status: 'FINAL', periodKind: 'LAST_MONTH', periodFrom: '2026-10-01',
    periodTo: '2026-10-31', finalizedAt: '2026-11-01T09:00:00Z' },
  { ...base, id: 2, code: 'R-002', name: 'November draft', status: 'DRAFT', compareToReportId: 1 },
] as unknown as SavedReport[];

describe('SavedReportsPage (V19 RM-FR-17)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(savedReportApi.listSavedReports).mockResolvedValue(reports);
  });

  it('lists reports with their status and comparison, and filters by status and name', async () => {
    renderPage(<SavedReportsPage />, { route: '/vision-map/reports' });
    const draftRow = (await screen.findByRole('link', { name: 'November draft' })).closest('tr') as HTMLElement;
    expect(within(draftRow).getByText('Draft')).toBeInTheDocument();
    expect(within(draftRow).getByRole('link', { name: 'R-001' })).toHaveAttribute('href', '/vision-map/reports/1');
    expect(screen.getByRole('link', { name: 'November draft' })).toHaveAttribute('href', '/vision-map/reports/2');

    await userEvent.click(screen.getByRole('combobox', { name: 'Status' }));
    await userEvent.click(screen.getByRole('option', { name: 'Final' }));
    expect(screen.queryByRole('link', { name: 'November draft' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'October progress' })).toBeInTheDocument();

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search reports' }), 'zzz');
    expect(screen.getByText('No reports match.')).toBeInTheDocument();
  });

  it('asks for archived reports when shown, and explains how to start when there are none', async () => {
    renderPage(<SavedReportsPage />, { route: '/vision-map/reports' });
    await screen.findByRole('link', { name: 'November draft' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show archived' }));
    await waitFor(() => expect(savedReportApi.listSavedReports).toHaveBeenLastCalledWith('token', true));
  });

  it('explains how to save the first report', async () => {
    vi.mocked(savedReportApi.listSavedReports).mockResolvedValue([]);
    renderPage(<SavedReportsPage />, { route: '/vision-map/reports' });
    expect(await screen.findByText('No saved reports yet')).toBeInTheDocument();
  });
});
