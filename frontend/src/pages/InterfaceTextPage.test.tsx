import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listUiTextOverrides, resetUiTextOverride, saveUiTextOverride } from '../api/uiTextApi';
import { InterfaceTextPage } from './InterfaceTextPage';

let role = 'ADMIN';
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { role } }) }));
vi.mock('../api/uiTextApi', () => ({
  listUiTextOverrides: vi.fn(),
  saveUiTextOverride: vi.fn(),
  resetUiTextOverride: vi.fn(),
}));

describe('InterfaceTextPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    role = 'ADMIN';
    vi.mocked(listUiTextOverrides).mockResolvedValue([]);
  });

  it('turns a non-admin away without loading anything', () => {
    role = 'USER';
    render(<InterfaceTextPage />);
    expect(screen.getByText('Only an administrator can edit interface text.')).toBeInTheDocument();
    expect(listUiTextOverrides).not.toHaveBeenCalled();
  });

  it('finds a string by its English text, saves a change, then resets it', async () => {
    const saved = { id: 7, language: 'km' as const, sourceText: 'Today', text: 'ថ្ងៃនេះរបស់ខ្ញុំ', updatedAt: '2026-11-02T00:00:00Z' };
    vi.mocked(saveUiTextOverride).mockResolvedValue(saved);
    vi.mocked(resetUiTextOverride).mockResolvedValue(undefined);
    render(<InterfaceTextPage />);

    await userEvent.click(await screen.findByRole('combobox', { name: 'Language' }));
    await userEvent.click(screen.getByRole('option', { name: 'ខ្មែរ (Khmer)' }));
    await waitFor(() => expect(listUiTextOverrides).toHaveBeenLastCalledWith('token', 'km'));
    // Pasted in one go: typing filters thousands of strings once per letter,
    // which made this test time out on CI with coverage on.
    await userEvent.click(await screen.findByPlaceholderText('Search text...'));
    await userEvent.paste('Today');
    const field = await screen.findByRole('textbox', { name: 'Text for "Today"' });
    expect(field).toHaveValue('ថ្ងៃនេះ');
    await userEvent.clear(field);
    await userEvent.click(field);
    await userEvent.paste('ថ្ងៃនេះរបស់ខ្ញុំ');
    const row = within(field.closest('.MuiCard-root') as HTMLElement);
    await userEvent.click(row.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(saveUiTextOverride).toHaveBeenCalledWith('token', 'km', 'Today', 'ថ្ងៃនេះរបស់ខ្ញុំ'));

    expect(await screen.findByText('Default: ថ្ងៃនេះ')).toBeInTheDocument();
    await userEvent.click(row.getByRole('button', { name: 'Reset to default' }));
    await waitFor(() => expect(resetUiTextOverride).toHaveBeenCalledWith('token', 7));
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Text for "Today"' })).toHaveValue('ថ្ងៃនេះ'));
  }, 30_000);
});
