import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { commitToDream } from '../../api/dreamApi';
import { listPartners } from '../../api/partnerApi';
import { createPlanShare, listPlanShares, revokePlanShare, shareLink } from '../../api/planShareApi';
import { pageOf } from '../../test/apiMocks';
import { aDream, aPartner } from '../../test/builders';
import { renderPage } from '../../test/render';
import type { PlanShare } from '../../types/vision';
import { DreamCommitmentCard } from './DreamCommitmentCard';
import { PlanShareCard } from './PlanShareCard';

vi.mock('../../api/dreamApi');
vi.mock('../../api/partnerApi');
vi.mock('../../api/planShareApi');

describe('DreamCommitmentCard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('asks for the why first, because the commitment rests on it', () => {
    renderPage(<DreamCommitmentCard dream={aDream({ whyImportant: '' })} token="token" defaultName="Sok Dara" onChanged={vi.fn()} />);
    expect(screen.getByText('Edit this dream and write why it matters to you. Your commitment rests on that reason.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign commitment' })).not.toBeInTheDocument();
  });

  it('signs a commitment with the first action, start date and name', async () => {
    vi.mocked(commitToDream).mockResolvedValue(aDream());
    const onChanged = vi.fn().mockResolvedValue(undefined);
    renderPage(<DreamCommitmentCard dream={aDream({ id: 4, whyImportant: 'Help my community' })} token="token" defaultName="Sok Dara" onChanged={onChanged} />);
    expect(screen.getByText('Why it matters: Help my community')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /^Signed by/ })).toHaveValue('Sok Dara');
    await userEvent.type(screen.getByRole('textbox', { name: /^First action/ }), '  Read three papers  ');
    const start = screen.getByLabelText(/^Start date/);
    await userEvent.clear(start);
    await userEvent.type(start, '2026-11-10');
    await userEvent.click(screen.getByRole('button', { name: 'Sign commitment' }));
    expect(commitToDream).toHaveBeenCalledWith('token', 4, { signedName: 'Sok Dara', startDate: '2026-11-10', firstAction: 'Read three papers' });
    expect(onChanged).toHaveBeenCalled();
  });

  it('shows a signed commitment, lets it be updated, and shows why saving failed', async () => {
    vi.mocked(commitToDream).mockRejectedValue(new Error('Start date is too far away.'));
    const dream = aDream({
      whyImportant: 'Help my community', committedAt: '2026-11-01T09:00:00Z', commitmentSignedName: 'Sok Dara',
      commitmentStartDate: '2026-11-03', commitmentFirstAction: 'Read three papers',
    } as never);
    renderPage(<DreamCommitmentCard dream={dream} token="token" defaultName="Sok Dara" onChanged={vi.fn()} />);
    expect(screen.getByText(/Signed by Sok Dara on 2026-11-01/)).toBeInTheDocument();
    expect(screen.getByText('First action: Read three papers')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Update' }));
    expect(screen.getByRole('textbox', { name: /^First action/ })).toHaveValue('Read three papers');
    await userEvent.click(screen.getByRole('button', { name: 'Sign commitment' }));
    expect(await screen.findByText('Start date is too far away.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByText('First action: Read three papers')).toBeInTheDocument();
  });
});

describe('PlanShareCard (V9 share with a partner)', () => {
  const open: PlanShare = {
    id: 1, dreamId: 4, dreamTitle: 'Become a researcher', partnerName: 'Dr. Mentor', message: 'Feedback on my plan',
    createdAt: '2026-11-01T00:00:00Z', expiresAt: '2027-01-30T00:00:00Z', open: true, viewCount: 2,
  };
  const stopped: PlanShare = { ...open, id: 2, partnerName: null, open: false, revokedAt: '2026-11-02T00:00:00Z', viewCount: 0 };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listPartners).mockResolvedValue(pageOf([aPartner({ id: 7, name: 'Dr. Mentor' })]));
    vi.mocked(shareLink).mockImplementation((shareToken: string) => `https://vmap.example/shared/${shareToken}`);
    vi.mocked(revokePlanShare).mockResolvedValue({ ...open, open: false });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('lists links with who they are for and whether they are open, and stops an open one', async () => {
    vi.mocked(listPlanShares).mockResolvedValue([open, stopped]);
    renderPage(<PlanShareCard dream={aDream({ id: 4 })} token="token" />);
    expect(await screen.findByText('Dr. Mentor')).toBeInTheDocument();
    expect(screen.getByText('Open')).toBeInTheDocument();
    expect(screen.getByText('Stopped')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: /Stop/ })[0]);
    expect(revokePlanShare).toHaveBeenCalledWith('token', 1);
  });

  it('creates a read-only link for a partner and shows it once to copy', async () => {
    vi.mocked(listPlanShares).mockResolvedValue([]);
    vi.mocked(createPlanShare).mockResolvedValue({ ...open, id: 3, token: 'abc123' });
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    renderPage(<PlanShareCard dream={aDream({ id: 4 })} token="token" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Create a share link' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Who is it for?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Dr. Mentor' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'What you are asking them for' }), 'Feedback on my plan');
    await userEvent.click(screen.getByRole('button', { name: 'Create link' }));
    expect(createPlanShare).toHaveBeenCalledWith('token', expect.objectContaining({ dreamId: 4, partnerId: 7, message: 'Feedback on my plan' }));

    expect(await screen.findByText('Copy this link now. It is shown only once.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Share link' })).toHaveValue('https://vmap.example/shared/abc123');
    await userEvent.click(screen.getByRole('button', { name: 'Copy link' }));
    expect(writeText).toHaveBeenCalledWith('https://vmap.example/shared/abc123');
    expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument();
  });

  it('shows why a link could not be created or listed, and can be cancelled', async () => {
    vi.mocked(listPlanShares).mockRejectedValueOnce(new Error('Sharing is turned off.')).mockResolvedValue([]);
    vi.mocked(createPlanShare).mockRejectedValue(new Error('Too many open links.'));
    renderPage(<PlanShareCard dream={aDream({ id: 4 })} token="token" />);
    expect(await screen.findByText('Sharing is turned off.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Create a share link' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create link' }));
    expect(await screen.findByText('Too many open links.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Create link' })).not.toBeInTheDocument());
  });
});
