import { act, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../api/apiClient';
import { ProtectedRoute } from '../components/layout/ProtectedRoute';
import { AuthProvider, tokenExpiresAt, useAuth } from './AuthContext';

const STORAGE_KEY = 'visionMappingAuth';
const USER = { id: 1, fullName: 'Demo User', email: 'demo@example.com', role: 'USER' };

/** An unsigned token with the given expiry; the app only reads the claim, never verifies it. */
function tokenExpiringAt(epochMs: number, name = 'access') {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'demo@example.com', exp: Math.floor(epochMs / 1000), name })}.signature`;
}

function storeSession(refreshToken = 'refresh-1') {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ refreshToken, user: USER }));
}

function authResponse(token: string, refreshToken: string) {
  return { token, tokenType: 'Bearer', userId: 1, fullName: 'Demo User', email: 'demo@example.com', role: 'USER', refreshToken, expiresIn: 1800 };
}

type Answer = { status: number; body?: unknown };

/**
 * A fake server. `refresh` answers each call to /auth/refresh in turn (the
 * last answer repeats); `tasks` answers GET /tasks by the token it was sent.
 */
function mockServer(options: { refresh: Answer[]; tasks?: (token: string | null) => Answer }) {
  let refreshCalls = 0;
  const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const json = (answer: Answer) => new Response(answer.body === undefined ? null : JSON.stringify(answer.body), { status: answer.status });
    if (url.endsWith('/auth/refresh')) {
      const answer = options.refresh[Math.min(refreshCalls, options.refresh.length - 1)];
      refreshCalls += 1;
      return json(answer);
    }
    if (url.endsWith('/auth/logout')) {
      return new Response(null, { status: 204 });
    }
    if (url.endsWith('/auth/login')) {
      return json({ status: 401, body: { message: 'Invalid email or password.' } });
    }
    if (url.endsWith('/tasks') && options.tasks) {
      const header = new Headers(init?.headers).get('Authorization');
      return json(options.tasks(header ? header.replace('Bearer ', '') : null));
    }
    return json({ status: 200, body: {} });
  });
  return { spy, refreshCalls: () => refreshCalls };
}

function LoginPage() {
  const state = useLocation().state as { notice?: string; from?: { pathname: string } } | null;
  return <div>Login Page {state?.notice} from {state?.from?.pathname}</div>;
}

function CurrentToken() {
  const { token } = useAuth();
  return <span data-testid="token">{token ?? 'none'}</span>;
}

function renderApp() {
  return render(
    <AuthProvider>
      <CurrentToken />
      <MemoryRouter initialEntries={['/tasks']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/tasks" element={<div>Tasks Page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('session renewal (V10, SEC-07-04)', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('reads the expiry from the token', () => {
    expect(tokenExpiresAt(tokenExpiringAt(Date.UTC(2030, 0, 1)))).toBe(Date.UTC(2030, 0, 1));
    expect(tokenExpiresAt('not-a-jwt')).toBeNull();
    expect(tokenExpiresAt(null)).toBeNull();
  });

  it('opens a stored session by renewing it first', async () => {
    const access = tokenExpiringAt(Date.now() + 1_800_000);
    mockServer({ refresh: [{ status: 200, body: authResponse(access, 'refresh-2') }] });
    storeSession();

    renderApp();

    expect(await screen.findByText('Tasks Page')).toBeInTheDocument();
    expect(screen.getByTestId('token')).toHaveTextContent(access);
  });

  it('sends the user to sign in, saying why, when the stored session has ended', async () => {
    mockServer({ refresh: [{ status: 401, body: { message: 'Your session has ended.' } }] });
    storeSession();

    renderApp();

    expect(await screen.findByText(/Login Page/)).toHaveTextContent('Your session has expired. Please sign in again.');
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('keeps the stored session when the server cannot be reached', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    storeSession();

    renderApp();

    expect(await screen.findByText(/Login Page/)).not.toHaveTextContent('expired');
    expect(localStorage.getItem(STORAGE_KEY)).toContain('refresh-1');
  });

  it('renews the access token shortly before it runs out', async () => {
    const first = tokenExpiringAt(Date.now() + 60_300, 'first');
    const second = tokenExpiringAt(Date.now() + 1_800_000, 'second');
    const server = mockServer({
      refresh: [
        { status: 200, body: authResponse(first, 'refresh-2') },
        { status: 200, body: authResponse(second, 'refresh-3') },
      ],
    });
    storeSession();

    renderApp();

    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent(second), { timeout: 3000 });
    expect(server.refreshCalls()).toBe(2);
    expect(screen.getByText('Tasks Page')).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).toContain('refresh-3');
  });

  it('answers a 401 by renewing once and retrying, without signing out', async () => {
    const first = tokenExpiringAt(Date.now() + 1_800_000, 'first');
    const second = tokenExpiringAt(Date.now() + 1_800_000, 'second');
    mockServer({
      refresh: [
        { status: 200, body: authResponse(first, 'refresh-2') },
        { status: 200, body: authResponse(second, 'refresh-3') },
      ],
      tasks: (token) => (token === second ? { status: 200, body: [{ id: 1 }] } : { status: 401, body: { message: 'Authentication required.' } }),
    });
    storeSession();
    renderApp();
    await screen.findByText('Tasks Page');

    let result: unknown;
    await act(async () => {
      result = await apiClient('/tasks', { token: first });
    });

    expect(result).toEqual([{ id: 1 }]);
    expect(screen.getByText('Tasks Page')).toBeInTheDocument();
  });

  it('shares one renewal between requests that fail together', async () => {
    const first = tokenExpiringAt(Date.now() + 1_800_000, 'first');
    const second = tokenExpiringAt(Date.now() + 1_800_000, 'second');
    const server = mockServer({
      refresh: [
        { status: 200, body: authResponse(first, 'refresh-2') },
        { status: 200, body: authResponse(second, 'refresh-3') },
      ],
      tasks: (token) => (token === second ? { status: 200, body: [] } : { status: 401 }),
    });
    storeSession();
    renderApp();
    await screen.findByText('Tasks Page');

    await act(async () => {
      await Promise.all([
        apiClient('/tasks?a', { token: first }).catch(() => undefined),
        apiClient('/tasks', { token: first }),
        apiClient('/tasks', { method: 'POST', token: first }),
      ]);
    });

    expect(server.refreshCalls()).toBe(2);
  });

  it('retries with the newer token when another tab renewed it a moment ago (409)', async () => {
    const first = tokenExpiringAt(Date.now() + 1_800_000, 'first');
    mockServer({
      refresh: [
        { status: 409, body: { message: 'Renewed in another tab.' } },
        { status: 200, body: authResponse(first, 'refresh-3') },
      ],
    });
    storeSession();

    renderApp();

    expect(await screen.findByText('Tasks Page', {}, { timeout: 2000 })).toBeInTheDocument();
  });

  it('signs out when renewing is refused too, but not on a failed sign-in', async () => {
    const first = tokenExpiringAt(Date.now() + 1_800_000, 'first');
    mockServer({
      refresh: [
        { status: 200, body: authResponse(first, 'refresh-2') },
        { status: 401, body: { message: 'Your session has ended.' } },
      ],
      tasks: () => ({ status: 401, body: { message: 'Authentication required.' } }),
    });
    storeSession();
    renderApp();
    await screen.findByText('Tasks Page');

    // No token: a wrong password at sign-in, not an ended session.
    await expect(apiClient('/auth/login', { method: 'POST' })).rejects.toThrow();
    expect(screen.getByText('Tasks Page')).toBeInTheDocument();

    await act(async () => {
      await expect(apiClient('/tasks', { token: first })).rejects.toThrow('Authentication required.');
    });
    expect(screen.getByText(/Login Page/)).toHaveTextContent('Your session has expired.');
    expect(screen.getByText(/Login Page/)).toHaveTextContent('from /tasks');
  });
});
