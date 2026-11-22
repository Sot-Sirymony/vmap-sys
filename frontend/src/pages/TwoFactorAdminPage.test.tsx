import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as twoFactorApi from '../api/twoFactorApi';
import { renderPage } from '../test/render';
import { TwoFactorAdminPage } from './TwoFactorAdminPage';

vi.mock('../api/twoFactorApi');

const rows: twoFactorApi.TwoFactorReportRow[] = [
  { userId: 2, fullName: 'Dara Coach', email: 'dara@example.com', role: 'COACH', enabled: true, method: 'TOTP', dueAt: null, overdue: false },
  { userId: 3, fullName: 'Vanna Coach', email: 'vanna@example.com', role: 'COACH', enabled: false, dueAt: '2026-11-01T00:00:00Z', overdue: true },
];

/** V21 phase 4 (TF-FR-28, TF-FR-36; TFC-09): the admin two-step sign-in page. */
describe('TwoFactorAdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(twoFactorApi.getTwoFactorReport).mockResolvedValue(rows);
  });

  it('shows who uses it and who is overdue', async () => {
    renderPage(<TwoFactorAdminPage />, { role: 'ADMIN' });
    expect(await screen.findByText('On (authenticator app)')).toBeInTheDocument();
    expect(screen.getByText('Off, overdue')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reset two-step sign-in for Vanna Coach' })).not.toBeInTheDocument();
  });

  it('resets with a reason after asking for it', async () => {
    vi.mocked(twoFactorApi.resetTwoFactor).mockResolvedValue(undefined);
    vi.spyOn(window, 'prompt').mockReturnValue('Lost phone; called her on the number on file');
    renderPage(<TwoFactorAdminPage />, { role: 'ADMIN' });
    await userEvent.click(await screen.findByRole('button', { name: 'Reset two-step sign-in for Dara Coach' }));
    await waitFor(() => expect(twoFactorApi.resetTwoFactor).toHaveBeenCalledWith(expect.anything(), 2,
      'Lost phone; called her on the number on file', undefined));
    expect(await screen.findByText(/was reset for Dara Coach/)).toBeInTheDocument();
  });

  it('does nothing without a reason', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('  ');
    renderPage(<TwoFactorAdminPage />, { role: 'ADMIN' });
    await userEvent.click(await screen.findByRole('button', { name: 'Reset two-step sign-in for Dara Coach' }));
    expect(twoFactorApi.resetTwoFactor).not.toHaveBeenCalled();
  });
});
