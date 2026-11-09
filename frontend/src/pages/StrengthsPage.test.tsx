import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as chainAssessmentApi from '../api/chainAssessmentApi';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as strengthsApi from '../api/strengthsApi';
import * as workStyleProfileApi from '../api/workStyleProfileApi';
import { aDream, aGoal } from '../test/builders';
import { renderPage } from '../test/render';
import type { SelfAssessmentItem, StrengthsProfile } from '../types/vision';
import { StrengthsPage } from './StrengthsPage';

vi.mock('../api/chainAssessmentApi');
vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/strengthsApi');
vi.mock('../api/workStyleProfileApi');

function anItem(overrides: Partial<SelfAssessmentItem> = {}): SelfAssessmentItem {
  return {
    id: 1, dreamId: 2, kind: 'STRENGTH', description: 'Builds trust fast', evidence: 'Kept 9 of 10 clients',
    relevance: 'HIGH', source: 'MANUAL', outsideMismatch: false, archived: false,
    createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z', ...overrides,
  };
}

function aProfile(overrides: Partial<StrengthsProfile> = {}): StrengthsProfile {
  return {
    dreamId: 2, dreamTitle: 'Start a business', whatIBring: [], whatILack: [], recordedOnly: [], complete: false,
    uncoveredGaps: 0, learnInUse: 0, learnLimit: 2, lastSavedOn: null, comparedWith: null, retakeSuggested: false,
    changes: [], ...overrides,
  };
}

const gap = anItem({ id: 7, kind: 'WEAKNESS', description: 'Bookkeeping', evidence: 'Missed a filing' });

describe('StrengthsPage (V8.1 Strengths and Gaps)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dreamApi.listDreams).mockResolvedValue([
      aDream({ id: 1, title: 'Run a marathon' }),
      aDream({ id: 2, title: 'Start a business', letterRank: 'A' }),
    ]);
    vi.mocked(goalApi.listGoals).mockResolvedValue([aGoal({ id: 30, dreamId: 2, title: 'Find clients' })]);
    vi.mocked(workStyleProfileApi.getWorkStyleProfile).mockResolvedValue({
      dominant: 'DRIVER', secondary: null, paceFastScore: 8, paceDeliberateScore: 0, focusTaskScore: 8, focusPeopleScore: 0,
    });
    vi.mocked(chainAssessmentApi.getChainAssessments).mockResolvedValue({
      assessments: [{ id: 1, takenOn: '2026-11-01', settlingScore: 4, fearScore: 2, criticismScore: 4, visionScore: 4,
        knowledgeScore: 4, resourcesScore: 4, averageScore: 3.67 }],
      retakeSuggested: false,
    });
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile());
    vi.mocked(strengthsApi.listSelfAssessmentItems).mockResolvedValue([]);
    vi.mocked(strengthsApi.createSelfAssessmentItem).mockResolvedValue(anItem());
  });

  it('opens on the top-ranked dream and needs evidence before an item can be added', async () => {
    renderPage(<StrengthsPage />);
    expect(await screen.findByText('Your profile')).toBeInTheDocument();
    expect(strengthsApi.getStrengthsProfile).toHaveBeenCalledWith('token', 2);

    const add = screen.getByRole('button', { name: 'Add' });
    await userEvent.type(screen.getByRole('textbox', { name: /What do you bring/ }), 'Builds trust fast');
    expect(add).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox', { name: /Evidence: one real example/ }), 'Kept 9 of 10 clients');
    expect(add).toBeDisabled();
    await userEvent.click(screen.getByRole('combobox', { name: 'How much does it matter to this dream?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Matters a lot' }));
    expect(add).toBeEnabled();
    await userEvent.click(add);

    await waitFor(() => expect(strengthsApi.createSelfAssessmentItem).toHaveBeenCalledWith('token', expect.objectContaining({
      dreamId: 2, kind: 'STRENGTH', description: 'Builds trust fast', evidence: 'Kept 9 of 10 clients', relevance: 'HIGH', source: 'MANUAL',
    })));
  });

  it('suggests items from Work Style and low assessment scores without saving them', async () => {
    renderPage(<StrengthsPage />);
    const suggestions = within((await screen.findByText('Suggestions')).closest('.MuiCard-root') as HTMLElement);
    expect(suggestions.getByText('Fear of failing stops me from trying.')).toBeInTheDocument();
    expect(suggestions.getAllByRole('button', { name: 'Use this' })).toHaveLength(3);
    expect(strengthsApi.createSelfAssessmentItem).not.toHaveBeenCalled();

    await userEvent.click(suggestions.getAllByRole('button', { name: 'Use this' })[2]);
    expect(screen.getByRole('textbox', { name: /What do you lack/ })).toHaveValue('Fear of failing stops me from trying.');
    expect(strengthsApi.createSelfAssessmentItem).not.toHaveBeenCalled();
  });

  it('shows strengths before gaps and asks how an uncovered gap is covered', async () => {
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({
      whatIBring: [anItem()], whatILack: [gap], uncoveredGaps: 1,
    }));
    vi.mocked(strengthsApi.setGapCover).mockResolvedValue({ ...gap, coverMethod: 'ACCEPT' });
    renderPage(<StrengthsPage />);

    expect(await screen.findByText('1 gap(s) that matter a lot still need a way to cover them.')).toBeInTheDocument();
    const bring = screen.getByText('What I bring');
    const lack = screen.getByText('What I lack, and how each gap is covered');
    expect(bring.compareDocumentPosition(lack) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Choose how to cover it' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('radio', { name: /Accept it/ }));
    const save = within(dialog).getByRole('button', { name: 'Save' });
    expect(save).toBeDisabled();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Why does it not matter enough/ }), 'My accountant does it');
    await userEvent.click(save);
    await waitFor(() => expect(strengthsApi.setGapCover).toHaveBeenCalledWith('token', 7, { method: 'ACCEPT', note: 'My accountant does it' }));
  });

  it('turns off Learn once the limit is reached', async () => {
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({ whatILack: [gap], uncoveredGaps: 1, learnInUse: 2 }));
    renderPage(<StrengthsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Choose how to cover it' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('radio', { name: /Learn it myself/ })).toBeDisabled();
    expect(within(dialog).getByText('You are already learning 2 gaps. Cover this one another way.')).toBeInTheDocument();
  });

  it('asks whether to keep the partner a gap created when the gap is removed', async () => {
    const covered = { ...gap, coverMethod: 'PARTNER' as const, linkedPartnerId: 40 };
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({ whatILack: [covered] }));
    vi.mocked(strengthsApi.archiveSelfAssessmentItem).mockResolvedValue({ linkedItemRemoved: false, note: null });
    renderPage(<StrengthsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Remove Bookkeeping' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Do you also want to remove the partner it added?')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Keep it' }));
    await waitFor(() => expect(strengthsApi.archiveSelfAssessmentItem).toHaveBeenCalledWith('token', 7, false));
  });

  it('shows what changed since the earlier save', async () => {
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({
      whatILack: [gap], lastSavedOn: '2026-11-09', comparedWith: '2026-08-01',
      changes: [
        { type: 'ADDED', kind: 'WEAKNESS', description: 'Patience', relevanceAfter: 'LOW' },
        { type: 'CHANGED', kind: 'WEAKNESS', description: 'Bookkeeping', relevanceBefore: 'HIGH', relevanceAfter: 'HIGH', coverBefore: null, coverAfter: 'ACCEPT' },
      ],
    }));
    renderPage(<StrengthsPage />);
    expect(await screen.findByText('What changed since 2026-08-01')).toBeInTheDocument();
    expect(screen.getByText('Added: Patience')).toBeInTheDocument();
    expect(screen.getByText('Changed: Bookkeeping (Not covered → Accept it)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save a retake' })).toBeEnabled();
  });

  it('edits an item in the form and saves the change', async () => {
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({ whatIBring: [anItem()] }));
    vi.mocked(strengthsApi.updateSelfAssessmentItem).mockResolvedValue(anItem({ description: 'Builds trust quickly' }));
    renderPage(<StrengthsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Builds trust fast' }));
    expect(screen.getByText('Edit this item')).toBeInTheDocument();
    const description = screen.getByRole('textbox', { name: /What do you bring/ });
    await userEvent.clear(description);
    await userEvent.type(description, 'Builds trust quickly');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(strengthsApi.updateSelfAssessmentItem).toHaveBeenCalledWith('token', 1,
      expect.objectContaining({ description: 'Builds trust quickly', evidence: 'Kept 9 of 10 clients' })));
  });

  it('removes an item with nothing linked straight away, and shows a refusal from the server', async () => {
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({ recordedOnly: [anItem({ relevance: 'LOW' })] }));
    vi.mocked(strengthsApi.archiveSelfAssessmentItem).mockRejectedValue(new Error('Strength or gap not found: 1'));
    renderPage(<StrengthsPage />);

    expect(await screen.findByText('Recorded only')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove Builds trust fast' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(strengthsApi.archiveSelfAssessmentItem).toHaveBeenCalledWith('token', 1, false);
    expect(await screen.findByText('Strength or gap not found: 1')).toBeInTheDocument();
  });

  it('changes how a gap is covered, removing what it added and showing why something was kept', async () => {
    const covered = { ...gap, coverMethod: 'RESOURCE' as const, coverNote: 'A grant', linkedModuleNumber: 6 };
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({ whatILack: [covered] }));
    vi.mocked(strengthsApi.removeGapCover).mockResolvedValue({
      linkedItemRemoved: false,
      note: 'The exercise row was kept because it is the only one in that module. Edit it on the module page.',
    });
    renderPage(<StrengthsPage />);

    expect(await screen.findByText('Resource: A grant')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Change how Bookkeeping is covered'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Do you also want to remove the resource gap row it added?')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove it too' }));
    await waitFor(() => expect(strengthsApi.removeGapCover).toHaveBeenCalledWith('token', 7, true));
    expect(await screen.findByText(/The exercise row was kept/)).toBeInTheDocument();
  });

  it('saves the profile, flags a differing outside view and suggests a retake', async () => {
    vi.mocked(strengthsApi.getStrengthsProfile).mockResolvedValue(aProfile({
      whatIBring: [anItem({ outsideBy: 'My manager', outsideView: 'You rush', outsideMismatch: true })],
      complete: true, retakeSuggested: true, lastSavedOn: '2026-08-01',
    }));
    vi.mocked(strengthsApi.saveStrengthsProfile).mockResolvedValue(aProfile());
    renderPage(<StrengthsPage />);

    expect(await screen.findByText(/It has been 12 weeks or more since you saved this profile/)).toBeInTheDocument();
    expect(screen.getByText('Complete. Every gap that matters a lot has a way to cover it.')).toBeInTheDocument();
    expect(screen.getByText('My manager said: You rush')).toBeInTheDocument();
    expect(screen.getByText('Outside view differs')).toBeInTheDocument();
    expect(screen.getByText('Last saved 2026-08-01')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save a retake' }));
    await waitFor(() => expect(strengthsApi.saveStrengthsProfile).toHaveBeenCalledWith('token', 2));
  });

  it('lists removed items when asked and restores one', async () => {
    vi.mocked(strengthsApi.listSelfAssessmentItems).mockResolvedValue([anItem({ id: 9, description: 'Old habit', archived: true })]);
    vi.mocked(strengthsApi.restoreSelfAssessmentItem).mockResolvedValue(undefined);
    renderPage(<StrengthsPage />);

    await screen.findByText('Your profile');
    await userEvent.click(screen.getByRole('checkbox', { name: /archived/i }));
    await userEvent.click(await screen.findByRole('button', { name: 'Restore Old habit' }));
    await waitFor(() => expect(strengthsApi.restoreSelfAssessmentItem).toHaveBeenCalledWith('token', 9));
  });

  it('switches to another dream', async () => {
    renderPage(<StrengthsPage />);
    await screen.findByText('Your profile');
    await userEvent.click(screen.getByRole('combobox', { name: 'Dream' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Run a marathon' }));
    await waitFor(() => expect(strengthsApi.getStrengthsProfile).toHaveBeenCalledWith('token', 1));
  });

  it('starts from a dream when there is none, and says when the profile cannot load', async () => {
    vi.mocked(dreamApi.listDreams).mockResolvedValueOnce([]);
    const first = renderPage(<StrengthsPage />);
    expect(await screen.findByText('Strengths and gaps are judged against a dream.')).toBeInTheDocument();
    first.unmount();

    vi.mocked(strengthsApi.getStrengthsProfile).mockRejectedValue(new Error('Network down'));
    renderPage(<StrengthsPage />);
    expect(await screen.findByText('Network down')).toBeInTheDocument();
  });
});
