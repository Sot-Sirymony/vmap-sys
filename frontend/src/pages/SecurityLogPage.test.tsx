import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getSecurityLog } from '../api/securityLogApi';
import { SecurityLogPage } from './SecurityLogPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { role: 'ADMIN' } }) }));
vi.mock('../api/securityLogApi', () => ({ getSecurityLog: vi.fn() }));

const event = {
  id: 7, occurredAt: '2026-11-06T09:00:00Z', eventType: 'LOGIN_FAILED', outcome: 'DENIED' as const,
  actorId: 12, ipAddress: '203.0.113.9', targetType: null, targetId: null, detail: 'BadCredentialsException',
};

describe('SecurityLogPage (V10, SEC-09-05)', () => {
  beforeEach(() => {
    vi.mocked(getSecurityLog).mockReset();
    vi.mocked(getSecurityLog).mockResolvedValue({
      events: [event], page: 0, size: 50, totalEvents: 1, hasMore: false,
      recentAlerts: ['2026-11-06T09:01:00Z Many failed sign-ins for one account'],
    });
  });

  it('shows events and recent alerts', async () => {
    render(<SecurityLogPage />);

    expect(await screen.findByText('Login failed')).toBeInTheDocument();
    expect(screen.getByText('203.0.113.9')).toBeInTheDocument();
    expect(screen.getByText('BadCredentialsException')).toBeInTheDocument();
    expect(screen.getByText(/Many failed sign-ins for one account/)).toBeInTheDocument();
  });

  it('asks the server again when a filter changes', async () => {
    render(<SecurityLogPage />);
    await screen.findByText('Login failed');

    await userEvent.selectOptions(screen.getByLabelText('Outcome'), 'DENIED');

    await waitFor(() => expect(getSecurityLog).toHaveBeenLastCalledWith('token', { outcome: 'DENIED' }, 0));
  });
});
