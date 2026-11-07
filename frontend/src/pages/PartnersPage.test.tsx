import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as idealPartnerProfileApi from '../api/idealPartnerProfileApi';
import * as partnerApi from '../api/partnerApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { answerEmptyLists, emptyPage, pageOf } from '../test/apiMocks';
import { aDream, aGoal, aPartner, aStep, aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { PartnersPage } from './PartnersPage';

vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/idealPartnerProfileApi');
vi.mock('../api/partnerApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');

const mentor = aPartner({ id: 1, code: 'P-001', name: 'Dr. Mentor', role: 'Research lead', organization: 'NIPH', supportType: 'MENTOR', status: 'TO_CONTACT', relatedDreamId: 1 });
const funder = aPartner({ id: 2, code: 'P-002', name: 'Health Fund', supportType: 'FINANCIAL', status: 'CONTACTED' });
const coder = aPartner({ id: 3, code: 'P-003', name: 'Sok Dara', supportType: 'TECHNICAL', status: 'ACTIVE', vettedAt: '2026-10-01' });

const row = (name: string) => screen.getByText(name, { selector: 'td, td *' }).closest('tr') as HTMLElement;

async function chooseOption(dialog: HTMLElement, field: string, option: string) {
  await userEvent.click(within(dialog).getByRole('combobox', { name: field }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
}

// Long form pages: each test may take several seconds on a busy CI runner (COV-NFR-05).
describe('PartnersPage (CLAUDE.md page 8)', { timeout: 30000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([dreamApi, goalApi, idealPartnerProfileApi, stepApi, taskApi, visionAreaApi]);
    vi.mocked(partnerApi.listPartners).mockResolvedValue(pageOf([mentor, funder, coder]));
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1, name: 'Career' })]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([aDream({ id: 1, title: 'Become a researcher' })]);
    vi.mocked(goalApi.listGoals).mockResolvedValue([aGoal({ id: 4, title: 'Prepare one concept note' })]);
    vi.mocked(stepApi.listSteps).mockResolvedValue([aStep({ id: 7, title: 'Search literature' })]);
    vi.mocked(taskApi.listTasks).mockResolvedValue([aTask({ id: 9, title: 'Search PubMed' })]);
    vi.mocked(partnerApi.createPartner).mockResolvedValue(mentor);
    vi.mocked(partnerApi.updatePartner).mockResolvedValue(mentor);
    vi.mocked(partnerApi.archivePartner).mockResolvedValue(undefined as never);
  });

  it('lists partners with their support type and status, and counts them by status', async () => {
    renderPage(<PartnersPage />);
    expect(await screen.findByText('Dr. Mentor', { selector: 'td, td *' })).toBeInTheDocument();
    expect(within(row('Dr. Mentor')).getByText('Mentor')).toBeInTheDocument();
    expect(within(row('Dr. Mentor')).getByText('TO CONTACT')).toBeInTheDocument();
    expect(within(row('Health Fund')).getByText('Financial')).toBeInTheDocument();
    expect(partnerApi.listPartners).toHaveBeenCalledWith('token', 0, 10, false, 'id,desc', '', { supportType: '', status: '', dreamId: '' });
    expect(screen.getByRole('button', { name: /1 to contact/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /1 active/ })).toBeInTheDocument();
  });

  it('adds a partner linked to a dream, goal, step and task, with support type and contact status', async () => {
    renderPage(<PartnersPage />);
    await screen.findByText('Dr. Mentor', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'Create partner' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Name/ }), 'Prof. Chan');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Email/ }), 'chan@example.com');
    await chooseOption(dialog, 'Support Type', 'Expert');
    await chooseOption(dialog, 'Status', 'Contacted');
    await chooseOption(dialog, 'Offer Type', 'Recognition');
    await chooseOption(dialog, 'Vision Area', 'Career');
    await chooseOption(dialog, 'Dream', 'Become a researcher');
    await chooseOption(dialog, 'Goal', 'Prepare one concept note');
    await chooseOption(dialog, 'Step', 'Search literature');
    await chooseOption(dialog, 'Task', 'Search PubMed');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create partner' }).at(-1)!);

    await waitFor(() => expect(partnerApi.createPartner).toHaveBeenCalledWith('token', expect.objectContaining({
      name: 'Prof. Chan', email: 'chan@example.com', phone: undefined, supportType: 'EXPERT', status: 'CONTACTED', offerType: 'RECOGNITION',
      relatedVisionAreaId: 1, relatedDreamId: 1, relatedGoalId: 4, relatedStepId: 7, relatedTaskId: 9,
    })));
  });

  it('opens a new partner already linked to a dream and goal from a link, then cleans the address', async () => {
    renderPage(<PartnersPage />, { route: '/partners?create=partner&relatedDreamId=1&relatedGoalId=4' });
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Dream' })).toHaveTextContent('Become a researcher'));
    expect(within(dialog).getByRole('combobox', { name: 'Goal' })).toHaveTextContent('Prepare one concept note');
    expect(currentLocation()).toBe('/partners');
  });

  it('asks about integrity before a financial or technical partner first becomes Active, and needs a reason if anything is flagged', async () => {
    renderPage(<PartnersPage />);
    await screen.findByText('Health Fund', { selector: 'td, td *' });
    await userEvent.click(within(row('Health Fund')).getByRole('button', { name: 'Partner actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Partner' });
    expect(within(dialog).queryByText('Before you move this partner to Active…')).not.toBeInTheDocument();
    await chooseOption(dialog, 'Status', 'Active');
    expect(within(dialog).getByText('Before you move this partner to Active…')).toBeInTheDocument();

    const honesty = within(dialog).getByRole('group', { name: /honesty or trustworthiness/ });
    await userEvent.click(within(honesty).getByRole('button', { name: 'Flag it' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText(/Record why you are proceeding anyway/)).toBeInTheDocument();
    expect(partnerApi.updatePartner).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByRole('textbox', { name: /Why proceed anyway/ }), 'Known through my supervisor');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(partnerApi.updatePartner).toHaveBeenCalledWith('token', 2, expect.objectContaining({
      status: 'ACTIVE', flagDishonesty: true, riskOverrideNote: 'Known through my supervisor',
    })));
  });

  it('does not ask again for a partner who was already vetted', async () => {
    renderPage(<PartnersPage />);
    await screen.findByText('Sok Dara', { selector: 'td, td *' });
    await userEvent.click(within(row('Sok Dara')).getByRole('button', { name: 'Partner actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Partner' });
    expect(within(dialog).getByRole('combobox', { name: 'Status' })).toHaveTextContent('Active');
    expect(within(dialog).queryByText('Before you move this partner to Active…')).not.toBeInTheDocument();
  });

  it('moves a partner on the board, and opens the integrity check instead for an unvetted funder', async () => {
    renderPage(<PartnersPage />);
    await screen.findByText('Dr. Mentor', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));

    const mentorCard = screen.getByText('Dr. Mentor').closest('article') as HTMLElement;
    expect(within(mentorCard).getByText('P-001 · Research lead · NIPH')).toBeInTheDocument();
    await userEvent.click(within(mentorCard).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Contacted' }));
    await waitFor(() => expect(partnerApi.updatePartner).toHaveBeenCalledWith('token', 1, expect.objectContaining({ name: 'Dr. Mentor', status: 'CONTACTED', relatedDreamId: 1 })));

    vi.mocked(partnerApi.updatePartner).mockClear();
    const funderCard = screen.getByText('Health Fund').closest('article') as HTMLElement;
    await userEvent.click(within(funderCard).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Active' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Partner' });
    expect(within(dialog).getByText('Before you move this partner to Active…')).toBeInTheDocument();
    expect(partnerApi.updatePartner).not.toHaveBeenCalled();
  });

  it('shows why a board move failed', async () => {
    vi.mocked(partnerApi.updatePartner).mockRejectedValue(new Error('Partner was changed elsewhere'));
    renderPage(<PartnersPage />);
    await screen.findByText('Dr. Mentor', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Dr. Mentor').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Waiting' }));
    expect(await screen.findByText('Partner was changed elsewhere')).toBeInTheDocument();
  });

  it('archives a partner and opens its details', async () => {
    renderPage(<PartnersPage />);
    await screen.findByText('Dr. Mentor', { selector: 'td, td *' });
    await userEvent.click(within(row('Dr. Mentor')).getByRole('button', { name: 'Partner actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(partnerApi.archivePartner).toHaveBeenCalledWith('token', 1));
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();

    await userEvent.click(within(row('Dr. Mentor')).getByRole('button', { name: 'Partner actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'View details' }));
    expect(currentLocation()).toBe('/partners/1');
  });

  it('filters on the server by status, support type and search', async () => {
    renderPage(<PartnersPage />, { route: '/partners?status=ACTIVE' });
    await screen.findByText('Dr. Mentor', { selector: 'td, td *' });
    expect(partnerApi.listPartners).toHaveBeenLastCalledWith('token', 0, 10, false, 'id,desc', '', { supportType: '', status: 'ACTIVE', dreamId: '' });

    await userEvent.click(within(screen.getByText('Support Type', { selector: 'label' })).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Financial' }));
    await waitFor(() => expect(partnerApi.listPartners).toHaveBeenLastCalledWith('token', 0, 10, false, 'id,desc', '', expect.objectContaining({ supportType: 'FINANCIAL' })));

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search partners' }), 'fund');
    await waitFor(() => expect(partnerApi.listPartners).toHaveBeenLastCalledWith('token', 0, 10, false, 'id,desc', 'fund', expect.anything()));
  });

  it('filters by status from the summary chips', async () => {
    renderPage(<PartnersPage />, { route: '/partners' });
    await screen.findByText('Dr. Mentor', { selector: 'td, td *' });
    await userEvent.click(screen.getByRole('button', { name: /1 to contact/ }));
    await waitFor(() => expect(currentLocation()).toBe('/partners?status=TO_CONTACT'));
  });

  it('says when no partners match the filters', async () => {
    vi.mocked(partnerApi.listPartners).mockResolvedValue(emptyPage());
    renderPage(<PartnersPage />, { route: '/partners?status=DECLINED' });
    expect(await screen.findByText('No partners match these filters.')).toBeInTheDocument();
  });

  it('invites a first partner when there are none', async () => {
    vi.mocked(partnerApi.listPartners).mockResolvedValue(emptyPage());
    renderPage(<PartnersPage />);
    expect(await screen.findByText('No partners yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add your first partner' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('shows the partner profiles that steps still need', async () => {
    vi.mocked(idealPartnerProfileApi.listIdealPartnerProfiles).mockResolvedValue([
      { id: 1, stepId: 7, requiredExperience: 'Systematic reviews', characterTraits: 'Patient', motivation: 'Co-authorship', offerInReturn: 'Credit', archived: false },
      { id: 2, stepId: 99, archived: false },
    ]);
    renderPage(<PartnersPage />);
    expect(await screen.findByText('Ideal partner profiles')).toBeInTheDocument();
    expect(screen.getByText('Search literature')).toBeInTheDocument();
    expect(screen.getByText('Experience: Systematic reviews')).toBeInTheDocument();
    expect(screen.getByText('Traits: Patient')).toBeInTheDocument();
    expect(screen.getByText('Motivation: Co-authorship')).toBeInTheDocument();
    expect(screen.getByText('In return: Credit')).toBeInTheDocument();
    expect(screen.getByText('Step #99')).toBeInTheDocument();
  });
});
