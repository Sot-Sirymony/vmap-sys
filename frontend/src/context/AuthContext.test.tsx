import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import type { AuthResponse } from '../types/auth';

const STORAGE_KEY = 'visionMappingAuth';

const response: AuthResponse = {
  token: 'jwt-token',
  tokenType: 'Bearer',
  userId: 1,
  fullName: 'Demo User',
  email: 'demo@example.com',
  role: 'USER',
};

function Harness({ remember }: { remember: boolean }) {
  const { setSession, logout, isAuthenticated } = useAuth();

  return (
    <>
      <button onClick={() => setSession(response, remember)}>sign in</button>
      <button onClick={logout}>sign out</button>
      <span>{isAuthenticated ? 'in' : 'out'}</span>
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

describe('AuthContext session storage', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('keeps the session across a browser restart when remembered', async () => {
    renderHarness(true);
    await userEvent.click(screen.getByText('sign in'));

    expect(localStorage.getItem(STORAGE_KEY)).toContain('jwt-token');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(screen.getByText('in')).toBeInTheDocument();
  });

  it('does not leave the token on disk when not remembered', async () => {
    renderHarness(false);
    await userEvent.click(screen.getByText('sign in'));

    // The whole point of unchecking the box: nothing survives the browser closing.
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(STORAGE_KEY)).toContain('jwt-token');
  });

  it('clears a remembered session when the user signs in without remembering', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: 'stale', user: null }));

    renderHarness(false);
    await userEvent.click(screen.getByText('sign in'));

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('logs out of both stores', async () => {
    renderHarness(true);
    await userEvent.click(screen.getByText('sign in'));
    await userEvent.click(screen.getByText('sign out'));

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(screen.getByText('out')).toBeInTheDocument();
  });

  it('restores a session stored in either place', () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token: 'jwt-token', user: null }));

    renderHarness(true);

    expect(screen.getByText('in')).toBeInTheDocument();
  });
});
