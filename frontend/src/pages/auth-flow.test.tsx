import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';

const registerMock = vi.fn();
const loginMock = vi.fn();
const setSessionMock = vi.fn();

vi.mock('../api/authApi', () => ({
  register: (...args: unknown[]) => registerMock(...args),
  login: (...args: unknown[]) => loginMock(...args),
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ token: null, appearance: null, setSession: setSessionMock }),
}));

const { RegisterPage } = await import('./RegisterPage');
const { LoginPage } = await import('./LoginPage');

/** Both auth pages plus a stand-in for the dashboard, so redirects are observable. */
function renderAuthRoutes(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<h1>Dashboard</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillRegistration(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/full name/i), 'Local Smoke');
  await user.type(screen.getByLabelText(/email/i), 'new.user@example.com');
  await user.type(screen.getByLabelText(/password/i), 'Password123');
  await user.click(screen.getByRole('button', { name: /create account/i }));
}

describe('registration hands over to sign-in', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    registerMock.mockResolvedValue({ token: 'a-token', email: 'new.user@example.com' });
  });

  /**
   * The regression this guards. Registering used to call setSession() with the
   * token the endpoint returns and replace straight to the dashboard, so a
   * successful sign-up produced no confirmation whatsoever — the form vanished
   * and the app appeared in its place.
   */
  it('confirms the account was created instead of silently opening the dashboard', async () => {
    const user = userEvent.setup();
    renderAuthRoutes('/register');

    await fillRegistration(user);

    const notice = await screen.findByRole('alert');
    expect(notice).toHaveTextContent(/account created/i);
    expect(notice).toHaveTextContent('new.user@example.com');
    expect(screen.queryByRole('heading', { name: 'Dashboard' })).not.toBeInTheDocument();
  });

  it('lands on the sign-in form, not the dashboard', async () => {
    const user = userEvent.setup();
    renderAuthRoutes('/register');

    await fillRegistration(user);

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });

  /**
   * The endpoint still returns a token; registering must not spend it. Creating
   * an account proves the account exists, not that whoever filled in the form is
   * entitled to a session on it.
   */
  it('does not open a session from the registration response', async () => {
    const user = userEvent.setup();
    renderAuthRoutes('/register');

    await fillRegistration(user);

    await screen.findByRole('heading', { name: /sign in/i });
    expect(setSessionMock).not.toHaveBeenCalled();
  });

  it('carries the new email over so it does not have to be retyped', async () => {
    const user = userEvent.setup();
    renderAuthRoutes('/register');

    await fillRegistration(user);

    await screen.findByRole('heading', { name: /sign in/i });
    expect(screen.getByLabelText(/email/i)).toHaveValue('new.user@example.com');
  });

  it('still reports a failed registration and stays on the form', async () => {
    registerMock.mockRejectedValue(new Error('Email is already registered.'));
    const user = userEvent.setup();
    renderAuthRoutes('/register');

    await fillRegistration(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('Email is already registered.');
    expect(screen.getByRole('heading', { name: /create account/i })).toBeInTheDocument();
  });
});

describe('sign-in page notice', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('shows nothing when reached directly', () => {
    renderAuthRoutes('/login');

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  /**
   * Otherwise a wrong password would render its error directly beneath a stale
   * "account created" success, which reads as though the failure were part of a
   * successful flow.
   */
  it('clears the success notice once sign-in is attempted', async () => {
    loginMock.mockRejectedValue(new Error('Invalid email or password.'));
    const user = userEvent.setup();
    renderAuthRoutes('/register');

    registerMock.mockResolvedValue({ token: 'a-token' });
    await fillRegistration(user);
    await screen.findByRole('heading', { name: /sign in/i });
    expect(await screen.findByRole('alert')).toHaveTextContent(/account created/i);

    await user.type(screen.getByLabelText(/password/i), 'wrong-password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid email or password.');
    });
    expect(screen.queryByText(/account created/i)).not.toBeInTheDocument();
  });
});
