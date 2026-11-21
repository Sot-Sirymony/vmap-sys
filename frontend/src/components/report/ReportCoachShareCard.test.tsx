import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as savedReportApi from '../../api/savedReportApi';
import { renderPage } from '../../test/render';
import { ReportCoachShareCard } from './ReportCoachShareCard';

vi.mock('../../api/savedReportApi');

const notShared = { available: true, coachName: 'Dara', shared: false, sections: [] };
const shared = { available: true, coachName: 'Dara', shared: true, sections: ['COMMENTARY' as const], sharedAt: '2026-11-20T09:00:00Z' };

describe('ReportCoachShareCard (V19 RM-FR-50)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shares with the coach after showing what they will see, then stops at once', async () => {
    vi.mocked(savedReportApi.getCoachShare).mockResolvedValue(notShared);
    vi.mocked(savedReportApi.shareReportWithCoach).mockResolvedValue(shared);
    vi.mocked(savedReportApi.unshareReportWithCoach).mockResolvedValue(notShared);
    const onShared = vi.fn();
    renderPage(<ReportCoachShareCard token="token" reportId={3} disabledReason="" onShared={onShared} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Share with my coach' }));
    const dialog = screen.getByRole('dialog', { name: 'Share this report with Dara?' });
    expect(within(dialog).getByText(/Dara will see: Headline and period figures, Commentary, Goal health, What changed\./)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Goal health' }));
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'What changed' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Share' }));
    expect(savedReportApi.shareReportWithCoach).toHaveBeenCalledWith('token', 3, ['COMMENTARY']);
    expect(await screen.findByText(/Shared with Dara since/)).toBeInTheDocument();
    expect(onShared).toHaveBeenLastCalledWith(true);

    await userEvent.click(await screen.findByRole('button', { name: 'Stop sharing' }));
    expect(savedReportApi.unshareReportWithCoach).toHaveBeenCalledWith('token', 3);
    expect(await screen.findByRole('button', { name: 'Share with my coach' })).toBeInTheDocument();
    await waitFor(() => expect(onShared).toHaveBeenLastCalledWith(false));
  });

  it('explains when there is no coach, when the report cannot be shared, and when sharing fails', async () => {
    vi.mocked(savedReportApi.getCoachShare).mockResolvedValue({ available: false, shared: false, sections: [] });
    const { unmount } = renderPage(<ReportCoachShareCard token="token" reportId={3} disabledReason="" />);
    expect(await screen.findByText('Join a cohort with a coach to share a report with them.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Share with my coach' })).not.toBeInTheDocument();
    unmount();

    vi.mocked(savedReportApi.getCoachShare).mockResolvedValue(notShared);
    const archived = renderPage(<ReportCoachShareCard token="token" reportId={3} disabledReason="Restore this report before you share it." />);
    expect(await screen.findByText('Restore this report before you share it.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Share with my coach' })).toBeDisabled();
    archived.unmount();

    vi.mocked(savedReportApi.shareReportWithCoach).mockRejectedValue(new Error('Only a final report can be shared. Finalise it first.'));
    renderPage(<ReportCoachShareCard token="token" reportId={3} disabledReason="" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Share with my coach' }));
    await userEvent.click(screen.getByRole('button', { name: 'Share' }));
    expect(await screen.findByText('Only a final report can be shared. Finalise it first.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByText('Only a final report can be shared. Finalise it first.')).toBeInTheDocument();
  });
});
