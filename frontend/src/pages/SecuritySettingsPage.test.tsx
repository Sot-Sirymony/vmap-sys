import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';

const changePasswordMock = vi.fn();

vi.mock('../api/authApi', () => ({
  changePassword: (...args: unknown[]) => changePasswordMock(...args),
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ token: 'a-token', user: { id: 1, email: 'pat@example.com', fullName: 'Pat', role: 'USER' } }),
}));

const { SecuritySettingsPage } = await import('./SecuritySettingsPage');

function renderPage() {
  return render(
    <MemoryRouter>
      <SecuritySettingsPage />
    </MemoryRouter>,
  );
}

type FormValues = { current?: string; next?: string; confirm?: string };

async function fillForm(user: ReturnType<typeof userEvent.setup>, values: FormValues = {}) {
  const current = values.current ?? 'CurrentPass123';
  const next = values.next ?? 'BrandNewPass456';
  // Defaults to a matching confirmation, so only the mismatch test has to say so.
  const confirm = values.confirm ?? next;

  await user.type(screen.getByLabelText(/current password/i), current);
  await user.type(screen.getByLabelText(/^new password/i), next);
  await user.type(screen.getByLabelText(/confirm new password/i), confirm);
  await user.click(screen.getByRole('button', { name: /change password/i }));
}

describe('SecuritySettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    changePasswordMock.mockResolvedValue(undefined);
  });

  it('sends the old and new password with the session token', async () => {
    const user = userEvent.setup();
    renderPage();

    await fillForm(user);

    await waitFor(() => {
      expect(changePasswordMock).toHaveBeenCalledWith(
        { currentPassword: 'CurrentPass123', newPassword: 'BrandNewPass456' },
        'a-token',
      );
    });
  });

  it('confirms the change', async () => {
    const user = userEvent.setup();
    renderPage();

    await fillForm(user);

    expect(await screen.findByText(/password changed/i)).toBeInTheDocument();
  });

  /**
   * The confirmation field exists to catch a typo before it becomes a password
   * nobody knows, so a mismatch must never reach the API.
   */
  it('refuses to submit when the confirmation does not match', async () => {
    const user = userEvent.setup();
    renderPage();

    await fillForm(user, { next: 'BrandNewPass456', confirm: 'BrandNewPass457' });

    expect(await screen.findByText(/do not match/i)).toBeInTheDocument();
    expect(changePasswordMock).not.toHaveBeenCalled();
  });

  /**
   * The whole point of the feature: the backend rejects a wrong current
   * password, and the user has to be told which field was wrong rather than
   * being shown a generic failure.
   */
  it('reports a wrong current password from the server', async () => {
    changePasswordMock.mockRejectedValue(new Error('Current password is incorrect.'));
    const user = userEvent.setup();
    renderPage();

    await fillForm(user);

    expect(await screen.findByText('Current password is incorrect.')).toBeInTheDocument();
    expect(screen.queryByText(/password changed/i)).not.toBeInTheDocument();
  });

  it('reports the server refusing an unchanged password', async () => {
    changePasswordMock.mockRejectedValue(new Error('New password must be different from the current password.'));
    const user = userEvent.setup();
    renderPage();

    await fillForm(user, { next: 'CurrentPass123' });

    expect(await screen.findByText(/must be different/i)).toBeInTheDocument();
  });

  /** So a new password is not left sitting in three fields on an unattended screen. */
  it('empties the fields after a successful change', async () => {
    const user = userEvent.setup();
    renderPage();

    await fillForm(user);
    await screen.findByText(/password changed/i);

    expect(screen.getByLabelText(/current password/i)).toHaveValue('');
    expect(screen.getByLabelText(/^new password/i)).toHaveValue('');
    expect(screen.getByLabelText(/confirm new password/i)).toHaveValue('');
  });

  it('keeps what was typed when the change fails, so it can be corrected', async () => {
    changePasswordMock.mockRejectedValue(new Error('Current password is incorrect.'));
    const user = userEvent.setup();
    renderPage();

    await fillForm(user);
    await screen.findByText('Current password is incorrect.');

    expect(screen.getByLabelText(/^new password/i)).toHaveValue('BrandNewPass456');
  });
});
