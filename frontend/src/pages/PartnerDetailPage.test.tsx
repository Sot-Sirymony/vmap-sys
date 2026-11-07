import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as communicationApi from '../api/communicationApi';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as partnerApi from '../api/partnerApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { setCurrentLanguage } from '../i18n/translate';
import { answerEmptyLists, emptyPage, pageOf } from '../test/apiMocks';
import { aDream, aGoal, aPartner, aStep, aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import type { CommunicationMessage, Partner } from '../types/vision';
import { PartnerDetailPage } from './PartnerDetailPage';

vi.mock('../api/communicationApi');
vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/partnerApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');

const open = (partner: Partner) => {
  vi.mocked(partnerApi.getPartner).mockResolvedValue(partner);
  return renderPage(<PartnerDetailPage />, { route: `/partners/${partner.id}`, path: '/partners/:partnerId' });
};

/** The value shown next to a label in a profile or vetting list. */
const valueOf = (label: string) => screen.getByText(label).nextElementSibling;

const currentStage = () => screen.getAllByText(/^(Identify|Contact|Recruit|Utilize|Done)$/)
  .find((element) => getComputedStyle(element).fontWeight === '700')?.textContent;

describe('PartnerDetailPage (FR-15.4, FR-50.3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    answerEmptyLists([dreamApi, goalApi, stepApi, taskApi, visionAreaApi]);
    vi.mocked(communicationApi.listCommunicationMessages).mockResolvedValue(emptyPage());
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1, name: 'Career' })]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([aDream({ id: 2, title: 'Become a researcher' })]);
    vi.mocked(goalApi.listGoals).mockResolvedValue([aGoal({ id: 3, title: 'Prepare one concept note' })]);
    vi.mocked(stepApi.listSteps).mockResolvedValue([aStep({ id: 4, title: 'Search literature' })]);
    vi.mocked(taskApi.listTasks).mockResolvedValue([aTask({ id: 5, stepId: 4, title: 'Search PubMed' })]);
  });
  afterEach(() => setCurrentLanguage('en'));

  it('shows the profile, with a dash for anything not recorded', async () => {
    open(aPartner({ id: 1, code: 'P-001', name: 'Dr. Mentor', role: 'Research lead', email: 'mentor@example.com', supportType: 'MENTOR', offerType: 'RECOGNITION', workStyleType: 'PLANNER' }));
    expect(await screen.findByRole('heading', { name: 'Dr. Mentor (P-001)' })).toBeInTheDocument();
    expect(valueOf('Role')).toHaveTextContent('Research lead');
    expect(valueOf('Email')).toHaveTextContent('mentor@example.com');
    expect(valueOf('Phone')).toHaveTextContent('-');
    expect(valueOf('Support Type')).toHaveTextContent('Mentor');
    expect(valueOf('Offer Type')).toHaveTextContent('Recognition');
    expect(valueOf('Work Style')).toHaveTextContent('Planner');
    expect(communicationApi.listCommunicationMessages).toHaveBeenCalledWith('token', 0, 100, false, 'id,desc', '', { partnerId: '1', status: '' });
  });

  it('links to every work item the partner helps with', async () => {
    open(aPartner({ relatedVisionAreaId: 1, relatedDreamId: 2, relatedGoalId: 3, relatedStepId: 4, relatedTaskId: 5 }));
    expect(await screen.findByRole('link', { name: 'Career' })).toHaveAttribute('href', '/dreams?visionAreaId=1');
    expect(screen.getByRole('link', { name: 'Become a researcher' })).toHaveAttribute('href', '/dreams/2');
    expect(screen.getByRole('link', { name: 'Prepare one concept note' })).toHaveAttribute('href', '/steps?goalId=3');
    expect(screen.getByRole('link', { name: 'Search literature' })).toHaveAttribute('href', '/tasks?stepId=4');
    expect(screen.getByRole('link', { name: 'Search PubMed' })).toHaveAttribute('href', '/tasks?stepId=4');
  });

  it('says when the partner is not linked to any work', async () => {
    open(aPartner());
    expect(await screen.findByText('Not linked to any work item yet.')).toBeInTheDocument();
  });

  it.each([
    ['TO_CONTACT', {}, 'Identify'],
    ['CONTACTED', {}, 'Contact'],
    ['AGREED', {}, 'Recruit'],
    ['ACTIVE', {}, 'Recruit'],
    ['ACTIVE', { relatedDreamId: 2 }, 'Utilize'],
    ['COMPLETED', {}, 'Done'],
  ] as const)('places a %s partner %o at the %s stage', async (status, links, stage) => {
    open(aPartner({ status, ...links }));
    await screen.findByText('Recruitment stage');
    expect(currentStage()).toBe(stage);
  });

  it('shows the integrity vetting record for a financial partner, with flagged concerns and the reason to proceed', async () => {
    open(aPartner({ supportType: 'FINANCIAL', vettedAt: '2026-10-01T08:00:00Z', flagGossip: true, flagAnger: true, riskOverrideNote: 'Known for ten years' }));
    expect(await screen.findByText('Integrity vetting')).toBeInTheDocument();
    expect(valueOf('Vetted')).not.toHaveTextContent('Not yet vetted');
    expect(valueOf('Concerns flagged')).toHaveTextContent(/volatile anger.*; .*private information/);
    expect(valueOf('Why proceeded anyway')).toHaveTextContent('Known for ten years');
  });

  it('shows a technical partner as not yet vetted with nothing flagged, and no vetting for a mentor', async () => {
    const { unmount } = open(aPartner({ supportType: 'TECHNICAL' }));
    expect(await screen.findByText('Integrity vetting')).toBeInTheDocument();
    expect(valueOf('Vetted')).toHaveTextContent('Not yet vetted');
    expect(valueOf('Concerns flagged')).toHaveTextContent('None');
    expect(screen.queryByText('Why proceeded anyway')).not.toBeInTheDocument();
    unmount();

    open(aPartner({ supportType: 'MENTOR' }));
    await screen.findByText('Recruitment stage');
    expect(screen.queryByText('Integrity vetting')).not.toBeInTheDocument();
  });

  it('lists every message prepared for the partner', async () => {
    vi.mocked(communicationApi.listCommunicationMessages).mockResolvedValue(pageOf([
      { id: 1, subject: 'Request for guidance', audience: 'Mentor', status: 'SENT', followUpDate: '2026-11-20' },
      { id: 2, subject: '', audience: '', status: 'DRAFT' },
    ] as CommunicationMessage[]));
    open(aPartner());
    const table = await screen.findByRole('region', { name: 'Messages to this partner' });
    const [, first, second] = within(table).getAllByRole('row');
    expect(within(first).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['Request for guidance', 'Mentor', 'SENT', '2026-11-20']);
    expect(within(second).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['(No subject)', '-', 'DRAFT', '-']);
  });

  it('points to the Communication Builder when there are no messages', async () => {
    open(aPartner());
    expect(await screen.findByRole('link', { name: 'Draft one in the Communication Builder.' })).toHaveAttribute('href', '/communication');
  });

  it('offers a way back when the partner cannot be loaded', async () => {
    vi.mocked(partnerApi.getPartner).mockRejectedValue(new Error('Partner not found'));
    renderPage(<PartnerDetailPage />, { route: '/partners/99', path: '/partners/:partnerId' });
    expect(await screen.findByText('Partner not found')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Back to partners' }));
    expect(currentLocation()).toBe('/partners');
  });

  it('shows the recruitment stages in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    open(aPartner({ status: 'TO_CONTACT' }));
    await screen.findByRole('heading', { name: /Dr\. Mentor/ });
    expect(screen.getByText('ដំណាក់កាលជ្រើសរើស')).toBeInTheDocument();
    expect(screen.getByText('កំណត់')).toBeInTheDocument();
    expect(screen.getByText('ការងារដែលភ្ជាប់')).toBeInTheDocument();
  });
});
