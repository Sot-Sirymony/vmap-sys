import { render, screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';

export type TestRole = 'USER' | 'COACH' | 'ADMIN';

/** A signed-in user for tests, with every session action as a mock (COV-FR-07). */
export function signedIn(role: TestRole = 'USER', overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  return {
    token: 'token',
    user: { id: 1, fullName: 'Test Participant', email: 'participant@example.com', role },
    isAuthenticated: true,
    restoring: false,
    setSession: vi.fn(),
    logout: vi.fn(),
    sessionExpired: false,
    appearance: null,
    ...overrides,
  } as AuthContextValue;
}

/**
 * Where the app is now (path and query). Found even while a dialog hides the
 * rest of the page from assistive technology.
 */
export function currentLocation(): string | null {
  return screen.getByRole('status', { name: 'Current location', hidden: true }).textContent;
}

type RenderPageOptions = {
  /** Who is signed in; defaults to a participant. */
  role?: TestRole;
  /** Extra or replaced session values, for example a different user. */
  auth?: Partial<AuthContextValue>;
  /** The address the page opens at; defaults to "/". */
  route?: string;
  /** The route pattern, when the page reads URL parameters such as ":id". */
  path?: string;
  /** Navigation state the page reads, as a redirect from another page would set it. */
  state?: unknown;
};

/** Shows where the app has navigated to, for tests that check links and redirects. */
export function CurrentLocation() {
  const location = useLocation();
  return <output aria-label="Current location">{location.pathname + location.search}</output>;
}

/**
 * Renders a page the way the app does: signed in, inside a router and with the
 * real toast messages. Page tests only mock the API modules they use.
 */
export function renderPage(ui: ReactElement, { role = 'USER', auth = {}, route = '/', path, state }: RenderPageOptions = {}) {
  const value = signedIn(role, auth);
  const result = render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[state === undefined ? route : { pathname: route, state }]}>
        <ToastProvider>
          <Routes>
            <Route path={path ?? '*'} element={<>{ui}<CurrentLocation /></>} />
            {path && <Route path="*" element={<CurrentLocation />} />}
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
  return { ...result, auth: value };
}
