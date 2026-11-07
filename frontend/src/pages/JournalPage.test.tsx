import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dailyPlanApi from '../api/dailyPlanApi';
import * as fearEntryApi from '../api/fearEntryApi';
import { currentLocation, renderPage } from '../test/render';
import type { FearEntry } from '../types/vision';
import { JournalPage } from './JournalPage';

vi.mock('../api/fearEntryApi');
vi.mock('../api/dailyPlanApi');

const speaking: FearEntry = {
  id: 5, fear: 'Speaking at the conference', worstCase: 'I forget my words', likelihood: 'LOW', firstTest: 'Present to two colleagues',
  relatedDreamId: 3, archived: false, createdAt: '2026-11-02T09:00:00Z', updatedAt: '2026-11-02T09:00:00Z',
};
const oldFear: FearEntry = { ...speaking, id: 6, fear: 'Asking for a raise', relatedDreamId: null, archived: true };

const field = (name: RegExp) => screen.getByRole('textbox', { name });

describe('JournalPage (FR-65)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.scrollTo = vi.fn();
    vi.mocked(fearEntryApi.listFearEntries).mockResolvedValue([speaking]);
    vi.mocked(dailyPlanApi.listAttitudeCheckIns).mockResolvedValue([
      { id: 1, checkInDate: '2026-11-06', score: 4, note: 'Good focus' },
      { id: 2, checkInDate: '2026-11-05', score: 2 },
    ]);
    vi.mocked(fearEntryApi.createFearEntry).mockResolvedValue(speaking);
    vi.mocked(fearEntryApi.updateFearEntry).mockResolvedValue(speaking);
    vi.mocked(fearEntryApi.archiveFearEntry).mockResolvedValue(undefined as never);
    vi.mocked(fearEntryApi.restoreFearEntry).mockResolvedValue(undefined);
  });

  it('lists fears with their worst case and first test, and the attitude history', async () => {
    renderPage(<JournalPage />);
    expect(await screen.findByText('Speaking at the conference')).toBeInTheDocument();
    expect(screen.getByText('2026-11-02 · Low likelihood')).toBeInTheDocument();
    expect(screen.getByText('Worst case: I forget my words')).toBeInTheDocument();
    expect(screen.getByText('First test: Present to two colleagues')).toBeInTheDocument();
    expect(screen.getByText(/4 of 5 · Good focus/)).toBeInTheDocument();
    expect(screen.getByText(/^· 2 of 5$/)).toBeInTheDocument();
  });

  it('saves a new fear only once the fear, worst case and first test are filled', async () => {
    renderPage(<JournalPage />);
    await screen.findByText('Speaking at the conference');
    const save = screen.getByRole('button', { name: 'Save fear' });
    await userEvent.type(field(/What are you afraid of/), '  Launching the course  ');
    await userEvent.type(field(/worst that could happen/), 'Nobody signs up');
    expect(save).toBeDisabled();
    await userEvent.type(field(/low-risk first test/), 'Ask ten people');
    await userEvent.click(screen.getByRole('combobox', { name: 'How likely is that?' }));
    await userEvent.click(screen.getByRole('option', { name: 'High' }));
    await userEvent.click(save);

    await waitFor(() => expect(fearEntryApi.createFearEntry).toHaveBeenCalledWith('token', {
      fear: 'Launching the course', worstCase: 'Nobody signs up', likelihood: 'HIGH', firstTest: 'Ask ten people',
    }));
    await waitFor(() => expect(field(/What are you afraid of/)).toHaveValue(''));
  });

  it('edits a fear and keeps its dream link', async () => {
    renderPage(<JournalPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Speaking at the conference' }));
    expect(screen.getByText('Edit this fear')).toBeInTheDocument();
    expect(field(/What are you afraid of/)).toHaveValue('Speaking at the conference');
    await userEvent.clear(field(/low-risk first test/));
    await userEvent.type(field(/low-risk first test/), 'Record a practice talk');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(fearEntryApi.updateFearEntry).toHaveBeenCalledWith('token', 5, {
      fear: 'Speaking at the conference', worstCase: 'I forget my words', likelihood: 'LOW', firstTest: 'Record a practice talk', relatedDreamId: 3,
    }));
  });

  it('cancels an edit and clears the form', async () => {
    renderPage(<JournalPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Speaking at the conference' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByText('Write down a fear')).toBeInTheDocument();
    expect(field(/What are you afraid of/)).toHaveValue('');
  });

  it('removes a fear, and shows and restores removed ones', async () => {
    renderPage(<JournalPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Remove Speaking at the conference' }));
    expect(fearEntryApi.archiveFearEntry).toHaveBeenCalledWith('token', 5);

    vi.mocked(fearEntryApi.listFearEntries).mockResolvedValue([speaking, oldFear]);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show archived' }));
    await waitFor(() => expect(fearEntryApi.listFearEntries).toHaveBeenLastCalledWith('token', true));
    await userEvent.click(await screen.findByRole('button', { name: 'Restore Asking for a raise' }));
    expect(fearEntryApi.restoreFearEntry).toHaveBeenCalledWith('token', 6);
  });

  it('links to the criticism log on the Obstacles page', async () => {
    renderPage(<JournalPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Criticism log' }));
    expect(currentLocation()).toBe('/obstacles');
  });

  it('shows empty logs plainly', async () => {
    vi.mocked(fearEntryApi.listFearEntries).mockResolvedValue([]);
    vi.mocked(dailyPlanApi.listAttitudeCheckIns).mockResolvedValue([]);
    renderPage(<JournalPage />);
    expect(await screen.findByText('No fears written down yet.')).toBeInTheDocument();
    expect(screen.getByText('No check-ins yet.')).toBeInTheDocument();
  });

  it('shows why saving, removing, restoring or loading failed', async () => {
    vi.mocked(fearEntryApi.archiveFearEntry).mockRejectedValue(new Error('Could not remove'));
    vi.mocked(fearEntryApi.restoreFearEntry).mockRejectedValue(new Error('Could not restore'));
    vi.mocked(fearEntryApi.listFearEntries).mockResolvedValue([speaking, oldFear]);
    renderPage(<JournalPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Remove Speaking at the conference' }));
    expect(await screen.findByText('Could not remove')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Restore Asking for a raise' }));
    expect(await screen.findByText('Could not restore')).toBeInTheDocument();
  });

  it('shows why a fear could not be saved', async () => {
    vi.mocked(fearEntryApi.createFearEntry).mockRejectedValue(new Error('Fear is too long'));
    renderPage(<JournalPage />);
    await screen.findByText('Speaking at the conference');
    await userEvent.type(field(/What are you afraid of/), 'X');
    await userEvent.type(field(/worst that could happen/), 'Y');
    await userEvent.type(field(/low-risk first test/), 'Z');
    await userEvent.click(screen.getByRole('button', { name: 'Save fear' }));
    expect(await screen.findByText('Fear is too long')).toBeInTheDocument();
  });

  it('shows why the journal could not load', async () => {
    vi.mocked(fearEntryApi.listFearEntries).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<JournalPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });
});
