import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../api/apiClient';
import { ProtectedRoute } from '../components/layout/ProtectedRoute';
import { AuthProvider, tokenExpiresAt } from './AuthContext';

const STORAGE_KEY = 'visionMappingAuth';

/** An unsigned token with the given expiry; the app only reads the claim, never verifies it. */
function tokenExpiringAt(epochMs: number) {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS384' })}.${encode({ sub: 'demo@example.com', exp: Math.floor(epochMs / 1000) })}.signature`;
}

function storeSession(token: string) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    token,
    user: { id: 1, fullName: 'Demo User', email: 'demo@example.com', role: 'USER' },
  }));
}

function LoginPage() {
  const state = useLocation().state as { notice?: string; from?: { pathname: string } } | null;
  return <div>Login Page {state?.notice} from {state?.from?.pathname}</div>;
}

function renderApp() {
  return render(
    <AuthProvider>
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

describe('an expired session', () => {
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

  it('is not restored when the app opens, and the login page says why', () => {
    storeSession(tokenExpiringAt(Date.now() - 60_000));
    renderApp();
    expect(screen.getByText(/Login Page/)).toHaveTextContent('Your session has expired. Please sign in again.');
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('signs out by itself when the token runs out, keeping the page to return to', () => {
    vi.useFakeTimers();
    storeSession(tokenExpiringAt(Date.now() + 5_000));
    renderApp();
    expect(screen.getByText('Tasks Page')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(5_000);
    });
    expect(screen.getByText(/Login Page/)).toHaveTextContent('Your session has expired.');
    expect(screen.getByText(/Login Page/)).toHaveTextContent('from /tasks');
  });

  it('signs out when the server refuses a signed-in request, but not on a failed sign-in', async () => {
    storeSession(tokenExpiringAt(Date.now() + 3_600_000));
    renderApp();
    const refused = () => Promise.resolve(new Response(JSON.stringify({ message: 'Authentication required.' }), { status: 401 }));
    vi.spyOn(globalThis, 'fetch').mockImplementation(refused);

    // No token: a wrong password at sign-in, not an ended session.
    await expect(apiClient('/auth/login', { method: 'POST' })).rejects.toThrow();
    expect(screen.getByText('Tasks Page')).toBeInTheDocument();

    await act(async () => {
      await expect(apiClient('/tasks', { token: 'old-token' })).rejects.toThrow('Authentication required.');
    });
    expect(screen.getByText(/Login Page/)).toHaveTextContent('Your session has expired.');
  });
});
