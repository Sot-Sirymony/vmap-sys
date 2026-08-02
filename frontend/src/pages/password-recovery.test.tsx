import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';

const forgotPasswordMock = vi.fn();
const resetPasswordMock = vi.fn();

vi.mock('../api/authApi', () => ({
  forgotPassword: (...args: unknown[]) => forgotPasswordMock(...args),
  resetPassword: (...args: unknown[]) => resetPasswordMock(...args),
  login: vi.fn(),
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ token: null, user: null, setSession: vi.fn() }),
}));

const { ForgotPasswordPage } = await import('./ForgotPasswordPage');
const { ResetPasswordPage } = await import('./ResetPasswordPage');
const { LoginPage } = await import('./LoginPage');

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('requesting a reset link', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    forgotPasswordMock.mockResolvedValue(undefined);
  });

  it('sends the address to the API', async () => {
    const user = userEvent.setup();
    renderAt('/forgot-password');

    await user.type(screen.getByLabelText(/email/i), 'pat@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(forgotPasswordMock).toHaveBeenCalledWith({ email: 'pat@example.com' });
  });

  /**
   * The membership-oracle rule, enforced in the UI as well as the API. The
   * backend answers 204 for an unknown address exactly as for a known one, and
   * the wording here must not claim more than that — "we sent you an email"
   * would assert something the frontend cannot know, and would turn this form
   * into a way to discover which addresses are registered.
   */
  it('does not claim the address exists', async () => {
    const user = userEvent.setup();
    renderAt('/forgot-password');

    await user.type(screen.getByLabelText(/email/i), 'unknown@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    const confirmation = await screen.findByRole('alert');
    expect(confirmation).toHaveTextContent(/if .*unknown@example\.com.* has an account/i);
  });

  it('reports a transport failure', async () => {
    forgotPasswordMock.mockRejectedValue(new Error('Network unreachable'));
    const user = userEvent.setup();
    renderAt('/forgot-password');

    await user.type(screen.getByLabelText(/email/i), 'pat@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText('Network unreachable')).toBeInTheDocument();
  });
});

describe('redeeming a reset link', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    resetPasswordMock.mockResolvedValue(undefined);
  });

  async function submitNewPassword(
    user: ReturnType<typeof userEvent.setup>,
    next = 'ReplacementPass456',
    confirm = next,
  ) {
    await user.type(screen.getByLabelText(/^new password/i), next);
    await user.type(screen.getByLabelText(/confirm new password/i), confirm);
    await user.click(screen.getByRole('button', { name: /set new password/i }));
  }

  it('sends the token from the link with the new password', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc123');

    await submitNewPassword(user);

    expect(resetPasswordMock).toHaveBeenCalledWith({ token: 'abc123', newPassword: 'ReplacementPass456' });
  });

  /** The link is a credential; rendering it invites it into a screenshot. */
  it('never displays the token', () => {
    renderAt('/reset-password?token=super-secret-token');

    expect(screen.queryByText(/super-secret-token/)).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue(/super-secret-token/)).not.toBeInTheDocument();
  });

  it('says so when the link carries no token', () => {
    renderAt('/reset-password');

    expect(screen.getByRole('alert')).toHaveTextContent(/incomplete/i);
    expect(screen.queryByRole('button', { name: /set new password/i })).not.toBeInTheDocument();
  });

  it('refuses to submit when the confirmation does not match', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc123');

    await submitNewPassword(user, 'ReplacementPass456', 'ReplacementPass457');

    expect(await screen.findByText(/do not match/i)).toBeInTheDocument();
    expect(resetPasswordMock).not.toHaveBeenCalled();
  });

  it('hands over to sign-in and says the password changed', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc123');

    await submitNewPassword(user);

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/password updated/i);
  });

  /**
   * Redeeming the link proves access to the inbox. It must not also open a
   * session — the new password is the credential that should get them in.
   */
  it('does not sign the user in off the back of the link', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc123');

    await submitNewPassword(user);

    await screen.findByRole('heading', { name: /sign in/i });
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it.each([
    'This reset link has expired. Request a new one.',
    'This reset link has already been used. Request a new one.',
    'This reset link is not valid. Request a new one.',
  ])('surfaces the server refusing the token: %s', async (message) => {
    resetPasswordMock.mockRejectedValue(new Error(message));
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc123');

    await submitNewPassword(user);

    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});
