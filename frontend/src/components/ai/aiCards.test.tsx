import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApprovedResource, deleteApprovedResource, listApprovedResources, updateApprovedResource } from '../../api/aiAdminApi';
import { getAiStatus } from '../../api/aiApi';
import { explainAssessment } from '../../api/aiModuleApi';
import { renderPage } from '../../test/render';
import type { AiStatus, ApprovedResource } from '../../types/vision';
import { ApprovedResourcesCard } from './ApprovedResourcesCard';
import { AssessmentExplainCard } from './AssessmentExplainCard';

vi.mock('../../api/aiAdminApi');
vi.mock('../../api/aiApi');
vi.mock('../../api/aiModuleApi');

const status = (reason: AiStatus['reason']): AiStatus => ({ available: reason === 'OK', reason, consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 });

describe('AssessmentExplainCard (V9)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('is hidden when AI is turned off', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(status('OFF'));
    renderPage(<AssessmentExplainCard token="token" />);
    await waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    expect(screen.queryByText('What does my result mean?')).not.toBeInTheDocument();
  });

  it('explains the result as an AI suggestion and links the module to focus on next', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(status('OK'));
    vi.mocked(explainAssessment).mockResolvedValue({
      explanation: 'Your planning is strong; follow-through is lower.', focusModule: 6, focusTitle: 'Daily execution', reason: 'Lowest score',
    });
    renderPage(<AssessmentExplainCard token="token" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Explain my result' }));
    expect(explainAssessment).toHaveBeenCalledWith('token', 'en');
    expect(await screen.findByText('Your planning is strong; follow-through is lower.')).toBeInTheDocument();
    expect(screen.getByText('AI suggestion')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Daily execution' })).toHaveAttribute('href', '/modules/6');
    expect(screen.getByText(/· Lowest score/)).toBeInTheDocument();
  });

  it('shows why the explanation could not be made', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(status('NO_CONSENT'));
    vi.mocked(explainAssessment).mockRejectedValue(new Error('Daily AI limit reached.'));
    renderPage(<AssessmentExplainCard token="token" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Explain my result' }));
    expect(await screen.findByText('Daily AI limit reached.')).toBeInTheDocument();
  });
});

describe('ApprovedResourcesCard (AI-FR-26)', () => {
  const course: ApprovedResource = { id: 1, kind: 'COURSE', name: 'Research methods 101', url: 'https://example.org/course', description: 'Free course', active: true };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(createApprovedResource).mockResolvedValue(course);
    vi.mocked(updateApprovedResource).mockResolvedValue(course);
    vi.mocked(deleteApprovedResource).mockResolvedValue(undefined);
  });

  afterEach(() => vi.restoreAllMocks());

  it('lists approved resources and adds a new one', async () => {
    vi.mocked(listApprovedResources).mockResolvedValueOnce([]).mockResolvedValue([course]);
    renderPage(<ApprovedResourcesCard token="token" />, { role: 'ADMIN' });
    expect(await screen.findByText('No approved resources yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add resource' })).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), 'Research methods 101');
    await userEvent.type(screen.getByRole('textbox', { name: 'Link (optional)' }), 'https://example.org/course');
    await userEvent.click(screen.getByRole('button', { name: 'Add resource' }));
    expect(createApprovedResource).toHaveBeenCalledWith('token', expect.objectContaining({ name: 'Research methods 101', url: 'https://example.org/course' }));
    expect(await screen.findByText('Research methods 101')).toBeInTheDocument();
  });

  it('turns a resource off and on, and removes it only after confirming', async () => {
    vi.mocked(listApprovedResources).mockResolvedValue([course]);
    renderPage(<ApprovedResourcesCard token="token" />, { role: 'ADMIN' });
    await userEvent.click(await screen.findByRole('switch', { name: 'Offered' }));
    expect(updateApprovedResource).toHaveBeenCalledWith('token', 1, expect.objectContaining({ active: false }));

    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(deleteApprovedResource).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(confirm).toHaveBeenLastCalledWith('Remove Research methods 101 from the approved list?');
    await waitFor(() => expect(deleteApprovedResource).toHaveBeenCalledWith('token', 1));
  });

  it('shows why a change could not be saved', async () => {
    vi.mocked(listApprovedResources).mockResolvedValue([]);
    vi.mocked(createApprovedResource).mockRejectedValue(new Error('That link is not a web address.'));
    renderPage(<ApprovedResourcesCard token="token" />, { role: 'ADMIN' });
    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), 'Bad link');
    await userEvent.click(screen.getByRole('button', { name: 'Add resource' }));
    expect(await screen.findByText('That link is not a web address.')).toBeInTheDocument();
  });
});
