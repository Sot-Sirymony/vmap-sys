import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as chainAssessmentApi from '../api/chainAssessmentApi';
import * as moduleApi from '../api/moduleApi';
import { currentLocation, renderPage } from '../test/render';
import type { LearningModuleSummary } from '../types/vision';
import { ModulesPage } from './ModulesPage';

vi.mock('../api/moduleApi');
vi.mock('../api/chainAssessmentApi');

const modules: LearningModuleSummary[] = [
  { number: 1, position: 1, stage: 'BREAK_CHAINS', title: 'Rewrite limiting beliefs', summary: 'Name what holds you back', status: 'COMPLETED' },
  { number: 2, position: 2, stage: 'BOOST', title: 'Find your partners', summary: 'Who can help', status: 'AVAILABLE' },
  { number: 3, position: 3, stage: 'LAUNCH', title: 'Launch your plan', summary: 'Start the first step', status: 'LOCKED' },
];

const withBaseline = { assessments: [{ id: 1 }] } as never;

describe('ModulesPage (FR-72)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(moduleApi.listModules).mockResolvedValue(modules);
    vi.mocked(chainAssessmentApi.getChainAssessments).mockResolvedValue(withBaseline);
  });

  it('lists the modules in order with their stage, state and overall progress', async () => {
    renderPage(<ModulesPage />);
    expect(await screen.findByText('Rewrite limiting beliefs')).toBeInTheDocument();
    expect(screen.getByText('1. Break barriers · Done')).toBeInTheDocument();
    expect(screen.getByText('2. Build momentum')).toBeInTheDocument();
    expect(screen.getByText('3. Launch · Locked')).toBeInTheDocument();
    expect(screen.getByText('1 of 3 done')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '33');
    expect(moduleApi.listModules).toHaveBeenCalledWith('token', 'en');
  });

  it('opens an unlocked module, but a locked one cannot be opened', async () => {
    renderPage(<ModulesPage />);
    await screen.findByText('Find your partners');
    expect(within(screen.getByText('Launch your plan').closest('.MuiCard-root') as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Find your partners/ }));
    expect(currentLocation()).toBe('/modules/2');
  });

  it('asks a participant with no baseline to take the assessment first', async () => {
    vi.mocked(chainAssessmentApi.getChainAssessments).mockResolvedValue({ assessments: [] } as never);
    renderPage(<ModulesPage />);
    expect(await screen.findByText('Take the assessment first, so you can measure how far you come.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Take it' }));
    expect(currentLocation()).toBe('/assessment');
  });

  it('gives an admin a way to edit the module text, and not a participant', async () => {
    const { unmount } = renderPage(<ModulesPage />);
    await screen.findByText('Find your partners');
    expect(screen.queryByRole('button', { name: 'Edit content' })).not.toBeInTheDocument();
    unmount();

    renderPage(<ModulesPage />, { role: 'ADMIN' });
    await userEvent.click(await screen.findByRole('button', { name: 'Edit content' }));
    expect(currentLocation()).toBe('/settings/module-content');
  });

  it('shows why the modules could not load, and tries again', async () => {
    vi.mocked(moduleApi.listModules).mockRejectedValueOnce(new Error('Server unavailable'));
    renderPage(<ModulesPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Retry|Try again/ }));
    expect(await screen.findByText('Rewrite limiting beliefs')).toBeInTheDocument();
  });
});
