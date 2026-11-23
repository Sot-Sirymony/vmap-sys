import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as twoFactorApi from '../../api/twoFactorApi';
import { currentLocation, renderPage } from '../../test/render';
import { daysLeft, TwoFactorRequirementGate } from './TwoFactorRequirementGate';

vi.mock('../../api/twoFactorApi');

const status = (dueAt: string | null, enabled = false): twoFactorApi.TwoFactorStatus => ({
  enabled, dueAt, recoveryCodesLeft: 0, emailMethodAvailable: false, canTurnOff: false, trustedDevices: [],
});
const inDays = (days: number) => new Date(Date.now() + days * 86_400_000 - 60_000).toISOString();

/** V21 phase 4 (D-21-2, TF-FR-33, TF-FR-34; TFC-07): two-step sign-in for admins and coaches. */
describe('TwoFactorRequirementGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('before the date, offers "Set up now" or "Skip for now", and logs the skip', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(status(inDays(5)));
    vi.mocked(twoFactorApi.skipTwoFactorSetup).mockResolvedValue(undefined);
    renderPage(<TwoFactorRequirementGate />, { role: 'COACH', route: '/' });

    await userEvent.click(await screen.findByRole('button', { name: 'Skip for now (5 days left)' }));
    expect(twoFactorApi.skipTwoFactorSetup).toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(sessionStorage.getItem('vms.twoFactorSetupSkipped')).toBe('1');
  });

  it('"Set up now" opens the Security page', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(status(inDays(5)));
    renderPage(<TwoFactorRequirementGate />, { role: 'ADMIN', route: '/' });
    await userEvent.click(await screen.findByRole('button', { name: 'Set up now' }));
    expect(currentLocation()).toBe('/settings/security');
  });

  it('from the date, goes straight to the Security page', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(status(new Date(Date.now() - 1000).toISOString()));
    renderPage(<TwoFactorRequirementGate />, { role: 'COACH', route: '/dreams' });
    await waitFor(() => expect(currentLocation()).toBe('/settings/security'));
  });

  it('when the server refuses a call with TWO_FACTOR_SETUP_REQUIRED, opens the Security page', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(status(null, true));
    renderPage(<TwoFactorRequirementGate />, { role: 'COACH', route: '/dreams' });
    window.dispatchEvent(new Event('TWO_FACTOR_SETUP_REQUIRED'));
    await waitFor(() => expect(currentLocation()).toBe('/settings/security'));
  });

  it('asks nothing of participants without a date, or of staff who already use it', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(status(null));
    renderPage(<TwoFactorRequirementGate />, { role: 'USER', route: '/' });
    await waitFor(() => expect(twoFactorApi.getTwoFactorStatus).toHaveBeenCalled());
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(status(inDays(5), true));
    renderPage(<TwoFactorRequirementGate />, { role: 'ADMIN', route: '/' });
    await waitFor(() => expect(twoFactorApi.getTwoFactorStatus).toHaveBeenCalled());
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('V24 D-24-4: asks a participant with a date, in words for participants', async () => {
    vi.mocked(twoFactorApi.getTwoFactorStatus).mockResolvedValue(status(inDays(9)));
    renderPage(<TwoFactorRequirementGate />, { role: 'USER', route: '/' });
    expect(await screen.findByText('Every account now needs two-step sign-in. You can use an authenticator app or codes sent by email.'))
      .toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Skip for now (9 days left)' })).toBeInTheDocument();
  });

  it('counts whole days left, never below zero', () => {
    const now = Date.parse('2026-11-22T10:00:00Z');
    expect(daysLeft('2026-11-25T10:00:00Z', now)).toBe(3);
    expect(daysLeft('2026-11-22T11:00:00Z', now)).toBe(1);
    expect(daysLeft('2026-11-20T10:00:00Z', now)).toBe(0);
  });
});
