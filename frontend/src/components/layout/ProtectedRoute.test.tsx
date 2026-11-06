import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthProvider } from '../../context/AuthContext';
import { ProtectedRoute } from './ProtectedRoute';

const USER = { id: 1, fullName: 'Test User', email: 'test@example.com', role: 'USER' };

describe('ProtectedRoute', () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('redirects anonymous users to login', () => {
    renderRoute();

    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });

  it('waits for a stored session to be renewed, then renders the child route', async () => {
    localStorage.setItem('visionMappingAuth', JSON.stringify({ refreshToken: 'refresh-1', user: USER }));
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      token: 'access-token', tokenType: 'Bearer', userId: 1, fullName: 'Test User', email: 'test@example.com',
      role: 'USER', refreshToken: 'refresh-2', expiresIn: 1800,
    }), { status: 200 }));

    renderRoute();

    // Not sent to the login page while the session is being renewed.
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
    expect(await screen.findByText('Protected Page')).toBeInTheDocument();
  });
});

function renderRoute() {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/protected" element={<div>Protected Page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}
