import { render as renderPlain, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { deleteAccount, getReminderSettings, sendTestReminder, updateReminderSettings } from '../api/accountApi';
import { AccountSettingsPage } from './AccountSettingsPage';

const logout = vi.fn();
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', logout }) }));
vi.mock('../api/aiApi', () => ({ getAiStatus: vi.fn().mockResolvedValue({ available: false, reason: 'OFF', consentGiven: false, dailyLimit: 20, messagesLeftToday: 20 }), setAiConsent: vi.fn() }));
vi.mock('../api/accountApi', () => ({
  deleteAccount: vi.fn(),
  exportAccountData: vi.fn(),
  getReminderSettings: vi.fn(),
  updateReminderSettings: vi.fn(),
  sendTestReminder: vi.fn(),
}));

const LOGIN = 'me@example.com';

const render = (page: ReactElement) => renderPlain(<MemoryRouter>{page}</MemoryRouter>);

describe('AccountSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getReminderSettings).mockResolvedValue({ enabled: false, timeZone: null, emailConfigured: false, sendHour: 8, reminderEmail: null, sendsTo: LOGIN });
  });

  it('says plainly when no mail server can send reminders', async () => {
    render(<AccountSettingsPage />);
    expect(await screen.findByText(/no reminder emails can be sent yet/)).toBeInTheDocument();
  });

  it('saves reminders with the browser time zone', async () => {
    vi.mocked(updateReminderSettings).mockResolvedValue({ enabled: true, timeZone: 'UTC', emailConfigured: true, sendHour: 8, reminderEmail: null, sendsTo: LOGIN });
    render(<AccountSettingsPage />);

    await userEvent.click(await screen.findByRole('switch', { name: 'Email me a daily reminder' }));

    await waitFor(() => expect(updateReminderSettings).toHaveBeenCalledWith('token', true, expect.any(String), ''));
  });

  it('saves a separate reminder address, then sends a test email to it', async () => {
    vi.mocked(getReminderSettings).mockResolvedValue({ enabled: true, timeZone: 'UTC', emailConfigured: true, sendHour: 8, reminderEmail: null, sendsTo: LOGIN });
    const chosen = { enabled: true, timeZone: 'UTC', emailConfigured: true, sendHour: 8, reminderEmail: 'inbox@example.org', sendsTo: 'inbox@example.org' };
    vi.mocked(updateReminderSettings).mockResolvedValue(chosen);
    vi.mocked(sendTestReminder).mockResolvedValue(chosen);
    render(<AccountSettingsPage />);

    expect(await screen.findByText(`Leave blank to use your login email. Reminders now go to ${LOGIN}.`)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Send reminders to'), ' inbox@example.org ');
    expect(screen.getByRole('button', { name: 'Send test email' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Save email' }));
    await waitFor(() => expect(updateReminderSettings).toHaveBeenCalledWith('token', true, expect.any(String), 'inbox@example.org'));
    expect(await screen.findByText('Reminder email saved.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Send test email' }));
    expect(await screen.findByText('Test email sent to inbox@example.org. Check your inbox and spam folder.')).toBeInTheDocument();
  });

  it('keeps the test button off when no mail server is set up', async () => {
    render(<AccountSettingsPage />);
    expect(await screen.findByRole('button', { name: 'Send test email' })).toBeDisabled();
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
