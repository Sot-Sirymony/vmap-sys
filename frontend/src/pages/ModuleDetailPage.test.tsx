import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { completeModule, getModule } from '../api/moduleApi';
import type { LearningModuleDetail } from '../types/vision';
import { ModuleDetailPage } from './ModuleDetailPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../api/moduleApi', () => ({ getModule: vi.fn(), completeModule: vi.fn() }));

const base: LearningModuleDetail = {
  number: 1, position: 2, stage: 'BREAK_CHAINS', title: 'Rewrite Limiting Beliefs', summary: 'Summary', guidance: 'Guidance',
  exerciseInstructions: 'Instructions', linkPath: null, fields: [], rows: [], requirementMessage: null, status: 'AVAILABLE', completedAt: null,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/modules/1']}>
      <Routes><Route path="/modules/:moduleNumber" element={<ModuleDetailPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe('ModuleDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('saves a form exercise with the rows the user typed', async () => {
    const fields = [{ key: 'belief', label: 'Limiting belief', type: 'text' }];
    vi.mocked(getModule).mockResolvedValue({ ...base, fields });
    vi.mocked(completeModule).mockResolvedValue({ ...base, fields, status: 'COMPLETED' });
    renderPage();

    await userEvent.type(await screen.findByLabelText('Limiting belief'), 'Too late');
    await userEvent.click(screen.getByRole('button', { name: 'Save exercise' }));

    await waitFor(() => expect(completeModule).toHaveBeenCalledWith('token', 1, [{ belief: 'Too late' }], 'en'));
    expect(await screen.findByText(/Saved/)).toBeInTheDocument();
  });

  it('blocks a data-proven module and says what is missing', async () => {
    vi.mocked(getModule).mockResolvedValue({ ...base, linkPath: '/journal', requirementMessage: 'Log at least one fear first.' });
    renderPage();

    expect(await screen.findByText('Log at least one fear first.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark as done' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Go there' })).toBeEnabled();
  });

  it('shows why a locked module cannot open', async () => {
    vi.mocked(getModule).mockRejectedValue(new Error('Finish the earlier modules to unlock this one.'));
    renderPage();

    expect(await screen.findByText('Finish the earlier modules to unlock this one.')).toBeInTheDocument();
  });
});
