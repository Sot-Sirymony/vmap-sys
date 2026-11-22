import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/apiClient';
import * as twoFactorApi from '../api/twoFactorApi';
import { currentLocation, renderPage } from '../test/render';
import type { TwoFactorChallenge } from '../types/auth';
import { TwoFactorChallengePage } from './TwoFactorChallengePage';

vi.mock('../api/twoFactorApi');

const totp: TwoFactorChallenge = {
  status: 'TWO_FACTOR_REQUIRED', challengeToken: 'challenge-1', method: 'TOTP', emailUnavailable: false,
  expiresAt: '2026-11-22T10:05:00Z',
};
const session = { token: 'access', refreshToken: 'refresh', userId: 1, fullName: 'Ana', email: 'ana@example.com', role: 'USER' };

/** V21 phase 3 (TF-FR-12 to TF-FR-17, TF-FR-21, TF-FR-27): the second step of signing in. */
describe('TwoFactorChallengePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('signs in with the authenticator code, remembers the device when asked, and opens the page from before', async () => {
    vi.mocked(twoFactorApi.verifySignIn).mockResolvedValue({ ...session, deviceToken: 'device-1' } as never);
    const { auth } = renderPage(<TwoFactorChallengePage />, {
      route: '/sign-in/two-factor', state: { challenge: totp, remember: false, from: '/tasks' },
    });

    expect(screen.getByText(/Open your authenticator app/)).toBeInTheDocument();
    const code = screen.getByLabelText('6-digit code');
    expect(code).toHaveAttribute('autocomplete', 'one-time-code');
    expect(code).toHaveAttribute('inputmode', 'numeric');
    await userEvent.type(code, '123456');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Remember this device for 30 days' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await waitFor(() => expect(auth.setSession).toHaveBeenCalledWith({ ...session, deviceToken: 'device-1' }, false));
    expect(twoFactorApi.verifySignIn).toHaveBeenCalledWith({
      challengeToken: 'challenge-1', code: '123456', recoveryCode: undefined, rememberDevice: true,
    });
    expect(localStorage.getItem('vms.deviceToken')).toBe('device-1');
    expect(currentLocation()).toBe('/tasks');
  });

  it('shows the tries left for a wrong code, and switches to a recovery code', async () => {
    vi.mocked(twoFactorApi.verifySignIn)
      .mockRejectedValueOnce(new ApiError('That code is not right. 4 tries left.', 400, 'TWO_FACTOR_WRONG_CODE', 4))
      .mockResolvedValueOnce(session as never);
    renderPage(<TwoFactorChallengePage />, { route: '/sign-in/two-factor', state: { challenge: totp } });

    await userEvent.type(screen.getByLabelText('6-digit code'), '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByText('That code is not right. 4 tries left.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Use a recovery code' }));
    await userEvent.type(screen.getByLabelText('Recovery code'), 'ABCDE-FGHJK');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await waitFor(() => expect(twoFactorApi.verifySignIn).toHaveBeenLastCalledWith({
      challengeToken: 'challenge-1', code: undefined, recoveryCode: 'ABCDE-FGHJK', rememberDevice: false,
    }));
  });

  it('sends the person back to sign in when the step has ended', async () => {
    vi.mocked(twoFactorApi.verifySignIn).mockRejectedValue(new ApiError('This sign-in step has ended.', 410, 'TWO_FACTOR_ENDED'));
    renderPage(<TwoFactorChallengePage />, { route: '/sign-in/two-factor', state: { challenge: totp } });
    await userEvent.type(screen.getByLabelText('6-digit code'), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await waitFor(() => expect(currentLocation()).toBe('/login'));
  });

  it('for the email method shows where the code went, and offers a recovery code when email is down', () => {
    renderPage(<TwoFactorChallengePage />, {
      route: '/sign-in/two-factor',
      state: { challenge: { ...totp, method: 'EMAIL', sentTo: 'a***@e***.com', emailUnavailable: true } },
    });
    expect(screen.getByText(/We could not email a code right now/)).toBeInTheDocument();
    expect(screen.getByLabelText('Recovery code')).toBeInTheDocument();
  });

  it('explains the options for a lost device', async () => {
    renderPage(<TwoFactorChallengePage />, { route: '/sign-in/two-factor', state: { challenge: totp } });
    await userEvent.click(screen.getByRole('button', { name: 'Lost your device?' }));
    expect(screen.getByText('Use one of the recovery codes you saved.')).toBeInTheDocument();
  });

  it('opened directly, it points back to sign in', () => {
    renderPage(<TwoFactorChallengePage />, { route: '/sign-in/two-factor' });
    expect(screen.getByRole('link', { name: 'Go to sign in' })).toHaveAttribute('href', '/login');
  });
});
