import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as authApi from '../api/authApi';
import { setCurrentLanguage } from '../i18n/translate';
import { currentLocation, renderPage } from '../test/render';
import { LoginPage } from './LoginPage';

vi.mock('../api/authApi');

const REMEMBERED_EMAIL_KEY = 'visionMappingRememberedEmail';
const session = { token: 'new-token', user: { id: 1, fullName: 'Sok Chan', email: 'chan@example.com', role: 'USER' } };

const email = () => screen.getByLabelText('Email address');
const password = () => screen.getByLabelText('Password');

/** BRD 12 CAC-06, CLAUDE.md "Testing Requirements": login form validation. */
describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(authApi.login).mockResolvedValue(session as never);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('requires an email address and a password before signing in', async () => {
    renderPage(<LoginPage />, { route: '/login' });
    expect(email()).toBeRequired();
    expect(email()).toHaveAttribute('type', 'email');
    expect(password()).toBeRequired();

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(authApi.login).not.toHaveBeenCalled();

    await userEvent.type(email(), 'not-an-email');
    await userEvent.type(password(), 'Password123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(email()).toBeInvalid();
    expect(authApi.login).not.toHaveBeenCalled();
  });

  it('signs in, remembers only the email, and opens the page the person was sent from', async () => {
    const { auth } = renderPage(<LoginPage />, { route: '/login', state: { from: { pathname: '/tasks' } } });
    await userEvent.type(email(), 'chan@example.com');
    await userEvent.type(password(), 'Password123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(authApi.login).toHaveBeenCalledWith({ email: 'chan@example.com', password: 'Password123', rememberMe: true });
    await waitFor(() => expect(auth.setSession).toHaveBeenCalledWith(session, true));
    expect(currentLocation()).toBe('/tasks');
    expect(localStorage.getItem(REMEMBERED_EMAIL_KEY)).toBe('chan@example.com');
    // The password is never written to the browser's storage.
    expect(JSON.stringify({ ...localStorage })).not.toContain('Password123');
  });

  it('forgets the email when the person chooses not to be remembered', async () => {
    localStorage.setItem(REMEMBERED_EMAIL_KEY, 'chan@example.com');
    const { auth } = renderPage(<LoginPage />, { route: '/login' });
    expect(email()).toHaveValue('chan@example.com');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Remember me on this device' }));
    await userEvent.type(password(), 'Password123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(auth.setSession).toHaveBeenCalledWith(session, false));
    expect(localStorage.getItem(REMEMBERED_EMAIL_KEY)).toBeNull();
    expect(currentLocation()).toBe('/');
  });

  it('shows why sign-in failed and stays on the form, without opening a session', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('Invalid email or password.'));
    const { auth } = renderPage(<LoginPage />, { route: '/login' });
    await userEvent.type(email(), 'chan@example.com');
    await userEvent.type(password(), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument();
    expect(auth.setSession).not.toHaveBeenCalled();
    expect(currentLocation()).toBe('/login');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  it('explains that a session ended, and clears the notice on the next attempt', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('Invalid email or password.'));
    renderPage(<LoginPage />, { route: '/login', state: { notice: 'Your session ended. Sign in again.', sessionExpired: true } });
    expect(screen.getByText('Your session ended. Sign in again.')).toBeInTheDocument();
    await userEvent.type(email(), 'chan@example.com');
    await userEvent.type(password(), 'Password123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument();
    expect(screen.queryByText('Your session ended. Sign in again.')).not.toBeInTheDocument();
  });

  it('offers Google sign-in, password recovery and registration', async () => {
    renderPage(<LoginPage />, { route: '/login' });
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password');
    expect(screen.getByRole('link', { name: 'Create one' })).toHaveAttribute('href', '/register');
    await userEvent.click(screen.getByRole('button', { name: /Google/ }));
    expect(currentLocation()).toBe('/oauth/google');
  });

  it('shows the form in Khmer (COV-NFR-08)', () => {
    setCurrentLanguage('km');
    renderPage(<LoginPage />, { route: '/login' });
    expect(screen.getByRole('button', { name: 'ចូល' })).toBeInTheDocument();
    expect(screen.getByLabelText('អាសយដ្ឋានអ៊ីមែល')).toBeRequired();
  });
});
