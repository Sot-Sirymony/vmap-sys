import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as insightApi from '../api/insightApi';
import { setCurrentLanguage } from '../i18n/translate';
import { currentLocation, renderPage } from '../test/render';
import type { Insight } from '../types/vision';
import { InsightsPage } from './InsightsPage';

vi.mock('../api/insightApi');

const fromReview: Insight = {
  source: 'REVIEW', sourceId: 4, sourceTitle: 'WEEKLY', kind: 'LESSON_LEARNED', date: '2026-11-01',
  content: 'Block mornings for writing.',
} as Insight;
const fromObstacle: Insight = {
  source: 'OBSTACLE', sourceId: 9, sourceTitle: 'No time on weekdays', kind: 'ROOT_CAUSE',
  content: 'Too many meetings.',
} as Insight;

describe('InsightsPage (FR-36.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(insightApi.searchInsights).mockResolvedValue([fromReview, fromObstacle]);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('lists lessons from reviews and obstacles, each with its kind and source', async () => {
    renderPage(<InsightsPage />);
    expect(await screen.findByText('Block mornings for writing.')).toBeInTheDocument();
    expect(screen.getByText('Lesson learned')).toBeInTheDocument();
    expect(screen.getByText('Root cause')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Weekly review' })).toHaveAttribute('href', '/reviews');
    expect(screen.getByRole('link', { name: 'Obstacle: No time on weekdays' })).toHaveAttribute('href', '/obstacles');
    expect(screen.getByText(/2026/)).toBeInTheDocument();
    expect(insightApi.searchInsights).toHaveBeenCalledWith('token', '');
  });

  it('goes back to the source record to edit it', async () => {
    renderPage(<InsightsPage />);
    await userEvent.click(await screen.findByRole('link', { name: 'Obstacle: No time on weekdays' }));
    expect(currentLocation()).toBe('/obstacles');
  });

  it('searches the lessons, and says when nothing matches', async () => {
    renderPage(<InsightsPage />);
    await screen.findByText('Block mornings for writing.');
    vi.mocked(insightApi.searchInsights).mockResolvedValue([]);
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search insights' }), 'budget');
    await waitFor(() => expect(insightApi.searchInsights).toHaveBeenLastCalledWith('token', 'budget'));
    expect(await screen.findByText('No insights match your search.')).toBeInTheDocument();
  });

  it('explains where lessons come from when there are none yet', async () => {
    vi.mocked(insightApi.searchInsights).mockResolvedValue([]);
    renderPage(<InsightsPage />);
    expect(await screen.findByText(/No lessons captured yet/)).toBeInTheDocument();
  });

  it('shows why the lessons could not load', async () => {
    vi.mocked(insightApi.searchInsights).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<InsightsPage />);
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });

  it('shows the kinds in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<InsightsPage />);
    expect(await screen.findByText('Block mornings for writing.')).toBeInTheDocument();
    expect(screen.getByText('មេរៀនដែលបានរៀន')).toBeInTheDocument();
    expect(screen.getByText('មូលហេតុដើម')).toBeInTheDocument();
    vi.mocked(insightApi.searchInsights).mockResolvedValue([]);
    await userEvent.type(screen.getByRole('searchbox'), 'x');
    expect(await screen.findByText('គ្មានការយល់ដឹងត្រូវនឹងការស្វែងរករបស់អ្នកទេ។')).toBeInTheDocument();
  });
});
