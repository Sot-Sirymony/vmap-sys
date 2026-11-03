import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiSettings, saveAiApiKey } from '../api/aiSettingsApi';
import type { AiSettings } from '../types/vision';
import { AiSettingsPage } from './AiSettingsPage';

let role = 'ADMIN';
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { role } }) }));
vi.mock('../api/aiSettingsApi', () => ({
  getAiSettings: vi.fn(),
  saveAiApiKey: vi.fn(),
  removeAiApiKey: vi.fn(),
  testAiConnection: vi.fn(),
  updateAiSettings: vi.fn(),
}));

const base: AiSettings = {
  enabled: false,
  model: 'claude-opus-5-5',
  models: [{ id: 'claude-opus-5-5', label: 'Claude Opus 5.5 (recommended)' }],
  monthlyBudgetUsd: 20,
  keySource: 'NONE',
  keyReadable: true,
  dailyMessageLimit: 20,
  helpLines: 'Call 117',
  spendThisMonthUsd: 0,
  requestsThisMonth: 0,
};

describe('AiSettingsPage (AI-FR-24)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    role = 'ADMIN';
  });

  it('turns away someone who is not an admin without loading anything', () => {
    role = 'USER';
    render(<AiSettingsPage />);
    expect(screen.getByText('Only an administrator can change AI settings.')).toBeInTheDocument();
    expect(getAiSettings).not.toHaveBeenCalled();
  });

  it('saves a key, clears the field and shows only the last four characters', async () => {
    vi.mocked(getAiSettings).mockResolvedValue(base);
    vi.mocked(saveAiApiKey).mockResolvedValue({ ...base, keySource: 'DATABASE', apiKeyLast4: 'wxyz' });
    render(<AiSettingsPage />);

    const field = await screen.findByLabelText('New API key');
    expect(field).toHaveAttribute('type', 'password');
    await userEvent.type(field, 'sk-ant-api03-abcdefghijklmnopwxyz');
    await userEvent.click(screen.getByRole('button', { name: 'Save key' }));

    expect(saveAiApiKey).toHaveBeenCalledWith('token', 'sk-ant-api03-abcdefghijklmnopwxyz');
    expect(await screen.findByText('sk-ant-…wxyz')).toBeInTheDocument();
    expect(field).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Replace key' })).toBeInTheDocument();
  });
});
