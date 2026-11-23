import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as securityAccessApi from '../api/securityAccessApi';
import { renderPage } from '../test/render';
import type { SecurityAccess } from '../types/securityAccess';
import { SecurityAccessPage } from './SecurityAccessPage';

vi.mock('../api/securityAccessApi');
vi.mock('../api/stepUpApi', () => ({ getStepUpStatus: vi.fn().mockRejectedValue(new Error('off')), stepUpHeader: () => ({}) }));

const admin = { auth: { user: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'ADMIN', permissions: ['USE_OWN_PLAN', 'MANAGE_ROLES', 'VIEW_SECURITY_LOG'] } } };
const viewer = { auth: { user: { id: 2, fullName: 'Coach', email: 'c@example.com', role: 'COACH', permissions: ['USE_OWN_PLAN', 'VIEW_SECURITY_LOG'] } } };
const participant = { auth: { user: { id: 3, fullName: 'P', email: 'p@example.com', role: 'USER', permissions: ['USE_OWN_PLAN'] } } };

const settings = (extra: Partial<SecurityAccess> = {}): SecurityAccess => ({
  passwordSignIn: true,
  googleSignIn: true,
  twoFactorScope: 'PRIVILEGED',
  googleAvailable: true,
  canChange: true,
  updatedAt: '2026-11-23T08:00:00Z',
  updatedBy: null,
  password: { accounts: 40, onlyWay: 30, openSessions: 12 },
  google: { accounts: 10, onlyWay: 4, openSessions: 3 },
  ...extra,
});

const toggle = (name: RegExp) => screen.getByRole('switch', { name });

describe('SecurityAccessPage (V24 BRD 24.0.0)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(securityAccessApi.getSecurityAccess).mockResolvedValue(settings());
  });

  it('refuses anyone without Manage roles or View security log, without calling the server', () => {
    renderPage(<SecurityAccessPage />, participant);
    expect(screen.getByText('Only an admin can open this page.')).toBeInTheDocument();
    expect(securityAccessApi.getSecurityAccess).not.toHaveBeenCalled();
  });

  it('SA-FR-02: shows the two switches, who each affects, and the two-step choice', async () => {
    renderPage(<SecurityAccessPage />, admin);
    expect(await screen.findByRole('switch', { name: 'Password sign-in: On' })).toBeChecked();
    expect(toggle(/^Google sign-in: On/)).toBeChecked();
    expect(screen.getByText('40 people use it')).toBeInTheDocument();
    expect(screen.getByText('30 can only sign in with a password')).toBeInTheDocument();
    expect(screen.getByText('4 can only sign in with Google')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /^Privileged roles/ })).toBeChecked();
    expect(screen.queryByText('View only')).not.toBeInTheDocument();
  });

  it('SA-FR-05, SA-FR-09: a change shows its effect, needs a reason, and saves through Verify it\'s you', async () => {
    const user = userEvent.setup();
    vi.mocked(securityAccessApi.updateSecurityAccess).mockResolvedValue(settings({ googleSignIn: false, updatedBy: 'Me' }));
    renderPage(<SecurityAccessPage />, admin);
    await user.click(await screen.findByRole('switch', { name: /^Google sign-in/ }));
    expect(screen.getByText(/4 people can only sign in with Google/)).toBeInTheDocument();

    await user.click(within(screen.getByRole('region', { name: 'Unsaved changes' })).getByRole('button', { name: 'Save changes' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('Turn Google sign-in off. 3 people signed in with Google will be signed out.')).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: 'Confirm and save' });
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Reason'), 'Google accounts are being retired');
    await user.click(confirm);

    await waitFor(() => expect(securityAccessApi.updateSecurityAccess).toHaveBeenCalledWith('token',
      { passwordSignIn: true, googleSignIn: false, twoFactorScope: 'PRIVILEGED', reason: 'Google accounts are being retired' }, undefined));
    expect(await screen.findByText(/Last changed by Me/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Unsaved changes' })).not.toBeInTheDocument();
  });

  it('shows the server refusal in the dialog', async () => {
    const user = userEvent.setup();
    vi.mocked(securityAccessApi.updateSecurityAccess).mockRejectedValue(new Error('4 people can only sign in with Google.'));
    renderPage(<SecurityAccessPage />, admin);
    await user.click(await screen.findByRole('radio', { name: /^Everyone/ }));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(/Require two-step sign-in for everyone/)).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Reason'), 'Raising the bar for all');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm and save' }));
    expect(await within(dialog).findByText('4 people can only sign in with Google.')).toBeInTheDocument();
  });

  it('D-24-6: both methods off cannot be saved; Discard puts the switches back', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await user.click(await screen.findByRole('switch', { name: /^Password sign-in/ }));
    await user.click(toggle(/^Google sign-in/));
    expect(screen.getByText('At least one sign-in method must stay on.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Discard' }));
    expect(toggle(/^Password sign-in/)).toBeChecked();
  });

  it('D-24-2: View security log reads only', async () => {
    vi.mocked(securityAccessApi.getSecurityAccess).mockResolvedValue(settings({ canChange: false, googleAvailable: false }));
    renderPage(<SecurityAccessPage />, viewer);
    expect(await screen.findByText('View only')).toBeInTheDocument();
    expect(toggle(/^Password sign-in/)).toBeDisabled();
    expect(screen.getByRole('radio', { name: /^Everyone/ })).toBeDisabled();
    expect(screen.getByText('Not set up on this server')).toBeInTheDocument();
  });

  it('SA-FR-04: the catalogue is a searchable reference with no switches', async () => {
    const user = userEvent.setup();
    renderPage(<SecurityAccessPage />, admin);
    await screen.findByRole('switch', { name: /^Password sign-in/ });
    await user.click(screen.getByRole('button', { name: 'Show the security methods reference' }));
    expect(screen.getAllByTestId('catalogue-item')).toHaveLength(57);
    expect(screen.getAllByRole('switch')).toHaveLength(2);
    await user.type(screen.getByLabelText('Search'), 'kerberos');
    expect(screen.getAllByTestId('catalogue-item')).toHaveLength(1);
    expect(screen.getByText('Not in VMS')).toBeInTheDocument();
  });
});
