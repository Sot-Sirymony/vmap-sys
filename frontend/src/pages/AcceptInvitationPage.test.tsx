import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as authApi from '../api/authApi';
import * as securityAccessApi from '../api/securityAccessApi';
import { resetOfferedSignInMethods } from '../hooks/useOfferedSignInMethods';
import { ApiError } from '../api/apiClient';
import { currentLocation, renderPage } from '../test/render';
import { AcceptInvitationPage } from './AcceptInvitationPage';

vi.mock('../api/authApi');
vi.mock('../api/securityAccessApi');

const PASSWORD = 'Quiet-River-Stone-77';

describe('AcceptInvitationPage (V23 UM-FR-24)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetOfferedSignInMethods();
    vi.mocked(securityAccessApi.getOfferedSignInMethods).mockResolvedValue({ password: true, google: true });
    vi.mocked(authApi.checkInvitation).mockResolvedValue({ fullName: 'Sokha', email: 'sokha@example.com', expiresAt: '2026-11-30T00:00:00Z' });
  });

  it('greets the person, and signs them in once they choose a password', async () => {
    const setSession = vi.fn();
    vi.mocked(authApi.acceptInvitation).mockResolvedValue({
      token: 'access', refreshToken: 'refresh', userId: 5, fullName: 'Sokha', email: 'sokha@example.com', role: 'USER',
    } as never);
    renderPage(<AcceptInvitationPage />, { route: '/invitation?token=abc', auth: { setSession, token: null, user: null } });

    expect(await screen.findByText('Choose a password for sokha@example.com. Only you will know it.')).toBeInTheDocument();
    expect(authApi.checkInvitation).toHaveBeenCalledWith('abc');
    await userEvent.type(screen.getByLabelText('Password'), PASSWORD);
    await userEvent.type(screen.getByLabelText('Confirm password'), PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Save password and sign in' }));

    await waitFor(() => expect(authApi.acceptInvitation).toHaveBeenCalledWith('abc', PASSWORD, true));
    // The session opens after the answer arrives; under a busy test run that is a moment later.
    await waitFor(() => expect(setSession).toHaveBeenCalled());
    await waitFor(() => expect(currentLocation()).toBe('/'));
  });

  it('refuses passwords that do not match', async () => {
    renderPage(<AcceptInvitationPage />, { route: '/invitation?token=abc', auth: { token: null, user: null } });

    await userEvent.type(await screen.findByLabelText('Password'), PASSWORD);
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Something-else-1');
    await userEvent.click(screen.getByRole('button', { name: 'Save password and sign in' }));

    expect(screen.getByText('The passwords do not match.')).toBeInTheDocument();
    expect(authApi.acceptInvitation).not.toHaveBeenCalled();
  });

  it('says so when the link was used, replaced or has expired', async () => {
    vi.mocked(authApi.checkInvitation).mockRejectedValue(new ApiError('This invitation link was used, replaced or has expired.', 410));
    renderPage(<AcceptInvitationPage />, { route: '/invitation?token=old', auth: { token: null, user: null } });

    expect(await screen.findByText('This invitation link was used, replaced or has expired.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
  });

  it('explains a link with no token', () => {
    renderPage(<AcceptInvitationPage />, { route: '/invitation', auth: { token: null, user: null } });

    expect(screen.getByText('This invitation link is incomplete. Open the link from your email again.')).toBeInTheDocument();
    expect(authApi.checkInvitation).not.toHaveBeenCalled();
  });

  it('V24 SA-FR-11: with password sign-in off, the invitation is accepted with Google', async () => {
    vi.mocked(securityAccessApi.getOfferedSignInMethods).mockResolvedValue({ password: false, google: true });
    renderPage(<AcceptInvitationPage />, { route: '/invitation?token=abc', auth: { token: null, user: null } });
    expect(await screen.findByText('Password sign-in is turned off. Accept your invitation with Google, using sokha@example.com.'))
      .toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Google/ }));
    await waitFor(() => expect(currentLocation()).toBe('/oauth/google'));
  });
});
