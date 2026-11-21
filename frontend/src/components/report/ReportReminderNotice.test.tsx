import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as savedReportApi from '../../api/savedReportApi';
import { currentLocation, renderPage } from '../../test/render';
import type { SavedReport } from '../../types/vision';
import { ReportReminderNotice } from './ReportReminderNotice';

vi.mock('../../api/savedReportApi');

const due = { due: true, cadence: 'WEEKLY', dueOn: '2026-11-20', latestFinalId: 4, latestFinalName: 'October progress' } as const;

describe('ReportReminderNotice (V19 RM-FR-49)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows nothing when no reminder is due', async () => {
    vi.mocked(savedReportApi.getReportReminder).mockResolvedValue({ due: false, cadence: 'WEEKLY' });
    renderPage(<ReportReminderNotice token="token" />);
    await waitFor(() => expect(savedReportApi.getReportReminder).toHaveBeenCalledWith('token'));
    expect(screen.queryByText(/It is time to prepare/)).not.toBeInTheDocument();
  });

  it('links to the Reports page from the Dashboard', async () => {
    vi.mocked(savedReportApi.getReportReminder).mockResolvedValue(due);
    renderPage(<ReportReminderNotice token="token" />);
    expect(await screen.findByText('It is time to prepare your weekly report.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open Reports' }));
    expect(currentLocation()).toBe('/vision-map/reports');
  });

  it('starts the next report from the latest final one on the Reports page', async () => {
    vi.mocked(savedReportApi.getReportReminder).mockResolvedValue({ ...due, cadence: 'MONTHLY' });
    vi.mocked(savedReportApi.copySavedReport).mockResolvedValue({ id: 9 } as SavedReport);
    renderPage(<ReportReminderNotice token="token" onReportsPage />);
    expect(await screen.findByText(/It is time to prepare your monthly report\. Your latest final report: October progress\./))
      .toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New report from this' }));
    expect(savedReportApi.copySavedReport).toHaveBeenCalledWith('token', 4);
    await waitFor(() => expect(currentLocation()).toBe('/vision-map/reports/9'));
  });

  it('says why the next report could not start, and opens the Management Report when there is no final report yet', async () => {
    vi.mocked(savedReportApi.getReportReminder).mockResolvedValue(due);
    vi.mocked(savedReportApi.copySavedReport).mockRejectedValue(new Error('Report not found.'));
    const { unmount } = renderPage(<ReportReminderNotice token="token" onReportsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New report from this' }));
    expect(await screen.findByText('Report not found.')).toBeInTheDocument();
    unmount();

    vi.mocked(savedReportApi.getReportReminder).mockResolvedValue({ due: true, cadence: 'WEEKLY', latestFinalId: null });
    renderPage(<ReportReminderNotice token="token" onReportsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Open the Management Report' }));
    expect(currentLocation()).toBe('/vision-map/report');
  });
});
