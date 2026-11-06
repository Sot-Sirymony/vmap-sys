import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import type { AuthResponse } from '../types/auth';

const STORAGE_KEY = 'visionMappingAuth';
const USER = { id: 1, fullName: 'Demo User', email: 'demo@example.com', role: 'USER' };

const response: AuthResponse = {
  token: 'access-token-1',
  tokenType: 'Bearer',
  userId: 1,
  fullName: 'Demo User',
  email: 'demo@example.com',
  role: 'USER',
  refreshToken: 'refresh-token-1',
  expiresIn: 1800,
};

function Harness({ remember }: { remember: boolean }) {
  const { setSession, logout, isAuthenticated, token } = useAuth();

  return (
    <>
      <button onClick={() => setSession(response, remember)}>sign in</button>
      <button onClick={logout}>sign out</button>
      <span>{isAuthenticated ? 'in' : 'out'}</span>
      <span data-testid="token">{token ?? 'none'}</span>
    </>
  );
}

function renderHarness(remember: boolean) {
  return render(
    <AuthProvider>
      <Harness remember={remember} />
    </AuthProvider>,
  );
}

/** Answers the auth endpoints; records every call. */
function mockServer(refreshStatus = 200) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.endsWith('/auth/refresh')) {
      return refreshStatus === 200
        ? new Response(JSON.stringify({ ...response, token: 'access-token-2', refreshToken: 'refresh-token-2' }), { status: 200 })
        : new Response(JSON.stringify({ message: 'Your session has ended.' }), { status: refreshStatus });
    }
    if (url.endsWith('/auth/logout')) {
      return new Response(null, { status: 204 });
    }
    return new Response('{}', { status: 200 });
  });
}

describe('AuthContext session storage (V10, SEC-07-05)', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('keeps only the refresh token on disk when remembered, never the access token', async () => {
    renderHarness(true);
    await userEvent.click(screen.getByText('sign in'));

    const stored = localStorage.getItem(STORAGE_KEY) ?? '';
    expect(stored).toContain('refresh-token-1');
    expect(stored).not.toContain('access-token-1');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(screen.getByText('in')).toBeInTheDocument();
    expect(screen.getByTestId('token')).toHaveTextContent('access-token-1');
  });

  it('keeps the session only for the tab when not remembered', async () => {
    renderHarness(false);
    await userEvent.click(screen.getByText('sign in'));

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(STORAGE_KEY)).toContain('refresh-token-1');
  });

  it('clears a remembered session when the user signs in without remembering', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ refreshToken: 'stale', user: USER }));
    mockServer(401);

    renderHarness(false);
    await userEvent.click(screen.getByText('sign in'));

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('signs out of both stores and ends the session on the server', async () => {
    const fetchMock = mockServer();
    renderHarness(true);
    await userEvent.click(screen.getByText('sign in'));
    await userEvent.click(screen.getByText('sign out'));

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(screen.getByText('out')).toBeInTheDocument();
    const logoutCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/auth/logout'));
    expect(logoutCall?.[1]?.body).toBe(JSON.stringify({ refreshToken: 'refresh-token-1' }));
  });

  it('restores a stored session by renewing it, and saves the new refresh token', async () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ refreshToken: 'refresh-token-1', user: USER }));
    mockServer();

    renderHarness(true);

    expect(await screen.findByText('in')).toBeInTheDocument();
    expect(screen.getByTestId('token')).toHaveTextContent('access-token-2');
    expect(sessionStorage.getItem(STORAGE_KEY)).toContain('refresh-token-2');
  });

  it('drops a session saved before V10, which held an access token instead', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: 'old-24-hour-token', user: USER }));

    renderHarness(true);

    expect(screen.getByText('out')).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('stores nothing for a response without a refresh token', async () => {
    const { refreshToken: _unused, ...withoutRefresh } = response;
    function OldShape() {
      const { setSession, isAuthenticated } = useAuth();
      return (
        <>
          <button onClick={() => setSession(withoutRefresh, true)}>sign in</button>
          <span>{isAuthenticated ? 'in' : 'out'}</span>
        </>
      );
    }
    render(<AuthProvider><OldShape /></AuthProvider>);
    await userEvent.click(screen.getByText('sign in'));

    await waitFor(() => expect(screen.getByText('in')).toBeInTheDocument());
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
