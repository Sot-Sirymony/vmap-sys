import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { generateParticipantSummary, getParticipantSummary, rateSummary } from '../../api/aiCoachApi';
import { ParticipantSummaryCard } from './CoachSummaryCards';

vi.mock('../../api/aiCoachApi', () => ({
  getParticipantSummary: vi.fn(),
  generateParticipantSummary: vi.fn(),
  getCohortSummary: vi.fn(),
  generateCohortSummary: vi.fn(),
  rateSummary: vi.fn(),
}));

describe('ParticipantSummaryCard (AI-FR-18, AI-FR-19, AC-16)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('explains why there is no summary without the participant\'s consent', async () => {
    vi.mocked(getParticipantSummary).mockResolvedValue(undefined);
    render(<ParticipantSummaryCard token="token" userId={3} aiConsent={false} />);
    expect(await screen.findByText('This participant has not turned on AI consent, so AI cannot read their plan.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: "Make this week's summary" })).not.toBeInTheDocument();
  });

  it('makes the summary on request, shows the risk signals, and records the coach\'s verdict', async () => {
    vi.mocked(getParticipantSummary).mockResolvedValue(undefined);
    vi.mocked(generateParticipantSummary).mockResolvedValue({
      id: 8, weekStart: '2026-11-02', generatedAt: '2026-11-03T08:00:00Z', progress: 'Two steps done.', mood: null,
      risks: 'Overdue work is piling up.', suggestedAction: 'Ask about the bank.', riskSignals: ['3 items overdue.'],
    });
    vi.mocked(rateSummary).mockResolvedValue(undefined);
    render(<ParticipantSummaryCard token="token" userId={3} aiConsent />);

    await userEvent.click(await screen.findByRole('button', { name: "Make this week's summary" }));
    expect(generateParticipantSummary).toHaveBeenCalledWith('token', 3, 'en');
    expect(await screen.findByText('Two steps done.')).toBeInTheDocument();
    expect(screen.getByText('Journals are not shared, so there is no mood trend.')).toBeInTheDocument();
    expect(screen.getByText('• 3 items overdue.')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Yes'));
    expect(rateSummary).toHaveBeenCalledWith('token', 8, true);
  });
});
