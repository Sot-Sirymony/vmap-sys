import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthProvider } from '../../context/AuthContext';
import { ProtectedRoute } from './ProtectedRoute';

describe('ProtectedRoute', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('redirects anonymous users to login', () => {
    renderRoute();

    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });

  it('renders child route for authenticated users', () => {
    localStorage.setItem('visionMappingAuth', JSON.stringify({
      token: 'test-token',
      user: { id: 1, fullName: 'Test User', email: 'test@example.com', role: 'USER' },
    }));

    renderRoute();

    expect(screen.getByText('Protected Page')).toBeInTheDocument();
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
