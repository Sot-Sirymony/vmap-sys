import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { deleteAccount, getReminderSettings, updateReminderSettings } from '../api/accountApi';
import { AccountSettingsPage } from './AccountSettingsPage';

const logout = vi.fn();
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', logout }) }));
vi.mock('../api/accountApi', () => ({
  deleteAccount: vi.fn(),
  exportAccountData: vi.fn(),
  getReminderSettings: vi.fn(),
  updateReminderSettings: vi.fn(),
}));

describe('AccountSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getReminderSettings).mockResolvedValue({ enabled: false, timeZone: null, emailConfigured: false, sendHour: 8 });
  });

  it('says plainly when no mail server can send reminders', async () => {
    render(<AccountSettingsPage />);
    expect(await screen.findByText(/no reminder emails can be sent yet/)).toBeInTheDocument();
  });

  it('saves reminders with the browser time zone', async () => {
    vi.mocked(updateReminderSettings).mockResolvedValue({ enabled: true, timeZone: 'UTC', emailConfigured: true, sendHour: 8 });
    render(<AccountSettingsPage />);

    await userEvent.click(await screen.findByRole('switch', { name: 'Email me a daily reminder' }));

    await waitFor(() => expect(updateReminderSettings).toHaveBeenCalledWith('token', true, expect.any(String)));
  });

  it('keeps delete disabled until the password and the word are both given, then signs out', async () => {
    vi.mocked(deleteAccount).mockResolvedValue(undefined);
    render(<AccountSettingsPage />);
    const button = screen.getByRole('button', { name: 'Permanently delete my account' });
    expect(button).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Your password'), 'secret');
    await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'delete');
    expect(button).toBeDisabled();
    await userEvent.clear(screen.getByLabelText('Type DELETE to confirm'));
    await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'DELETE');
    await userEvent.click(button);

    await waitFor(() => expect(deleteAccount).toHaveBeenCalledWith('token', 'secret'));
    await waitFor(() => expect(logout).toHaveBeenCalled());
  });

  it('stays signed in and shows the reason when deletion is refused', async () => {
    vi.mocked(deleteAccount).mockRejectedValue(new Error('That password is not correct.'));
    render(<AccountSettingsPage />);

    await userEvent.type(screen.getByLabelText('Your password'), 'wrong');
    await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'DELETE');
    await userEvent.click(screen.getByRole('button', { name: 'Permanently delete my account' }));

    expect(await screen.findByText('That password is not correct.')).toBeInTheDocument();
    expect(logout).not.toHaveBeenCalled();
  });
});
