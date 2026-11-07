import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as cohortApi from '../api/cohortApi';
import { setCurrentLanguage } from '../i18n/translate';
import { renderPage } from '../test/render';
import type { Cohort } from '../types/vision';
import { CohortsPage } from './CohortsPage';

vi.mock('../api/cohortApi');

const spring: Cohort = {
  id: 1, name: 'Spring 2027', joinCode: 'ABC123', coachId: 7, coachName: 'Coach Dara', coachEmail: 'dara@example.com',
  startDate: '2027-03-01', archived: false, memberCount: 2, aiEnabled: true,
};
const pilot: Cohort = { id: 2, name: 'Pilot', joinCode: 'ZZZ999', archived: true, memberCount: 0, aiEnabled: false };

/** The card holding one cohort. */
const cohortCard = (name: string) => screen.getByText(name).closest('.MuiCard-root') as HTMLElement;

describe('CohortsPage (V9 FR-01, FR-20; BR-14)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(cohortApi.listCohorts).mockResolvedValue([spring, pilot]);
    vi.mocked(cohortApi.listCohortMembers).mockResolvedValue([
      { userId: 11, fullName: 'Sok Chan', email: 'chan@example.com' },
      { userId: 12, fullName: 'Lim Nary', email: 'nary@example.com' },
    ]);
  });
  afterEach(() => setCurrentLanguage('en'));

  it.each(['USER', 'COACH'] as const)('tells a %s that only an administrator can manage cohorts, and loads nothing', (role) => {
    renderPage(<CohortsPage />, { role });
    expect(screen.getByText('Only an administrator can manage cohorts.')).toBeInTheDocument();
    expect(cohortApi.listCohorts).not.toHaveBeenCalled();
  });

  it('lists each cohort with its coach, start date, members, join code and AI setting', async () => {
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    const card = cohortCard('Spring 2027');
    expect(within(card).getByText('Coach: Coach Dara · Starts: 2027-03-01 · 2 member(s)')).toBeInTheDocument();
    expect(within(card).getByText('ABC123')).toBeInTheDocument();
    expect(within(card).getByRole('switch', { name: 'AI help is on for this cohort' })).toBeChecked();

    const pilotCard = cohortCard('Pilot');
    expect(within(pilotCard).getByText('No coach yet · 0 member(s)')).toBeInTheDocument();
    expect(within(pilotCard).getByText('Archived')).toBeInTheDocument();
    expect(within(pilotCard).getByRole('button', { name: 'Restore' })).toBeInTheDocument();
  });

  it('creates a cohort with a coach, then reloads the list and clears the form', async () => {
    vi.mocked(cohortApi.createCohort).mockResolvedValue({ ...spring, id: 3, name: 'Autumn 2027' });
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    const create = screen.getByRole('button', { name: 'Create cohort' });
    expect(create).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox', { name: 'Cohort name' }), 'Autumn 2027');
    await userEvent.type(screen.getByRole('textbox', { name: 'Coach email' }), 'dara@example.com');
    await userEvent.type(screen.getByLabelText('Start date'), '2027-09-01');
    await userEvent.click(create);

    expect(cohortApi.createCohort).toHaveBeenCalledWith('token', { name: 'Autumn 2027', coachEmail: 'dara@example.com', startDate: '2027-09-01' });
    await waitFor(() => expect(cohortApi.listCohorts).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('textbox', { name: 'Cohort name' })).toHaveValue('');
  });

  it('shows why a cohort could not be created, for example a coach who has not registered', async () => {
    vi.mocked(cohortApi.createCohort).mockRejectedValue(new Error('No account with that email.'));
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    await userEvent.type(screen.getByRole('textbox', { name: 'Cohort name' }), 'Autumn');
    await userEvent.click(screen.getByRole('button', { name: 'Create cohort' }));
    expect(await screen.findByText('No account with that email.')).toBeInTheDocument();
  });

  it('edits a cohort in place', async () => {
    vi.mocked(cohortApi.updateCohort).mockResolvedValue({ ...spring, name: 'Spring 2027 (Phnom Penh)' });
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    const card = cohortCard('Spring 2027');
    await userEvent.click(within(card).getByRole('button', { name: 'Edit' }));
    const name = within(card).getByRole('textbox', { name: 'Cohort name' });
    expect(name).toHaveValue('Spring 2027');
    expect(within(card).getByRole('textbox', { name: 'Coach email' })).toHaveValue('dara@example.com');
    await userEvent.type(name, ' (Phnom Penh)');
    await userEvent.click(within(card).getByRole('button', { name: 'Save changes' }));

    expect(cohortApi.updateCohort).toHaveBeenCalledWith('token', 1, { name: 'Spring 2027 (Phnom Penh)', coachEmail: 'dara@example.com', startDate: '2027-03-01' });
    expect(await screen.findByText('Spring 2027 (Phnom Penh)')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();
  });

  it('turns AI help off for one cohort', async () => {
    vi.mocked(cohortApi.updateCohort).mockResolvedValue({ ...spring, aiEnabled: false });
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    await userEvent.click(within(cohortCard('Spring 2027')).getByRole('switch', { name: 'AI help is on for this cohort' }));
    expect(cohortApi.updateCohort).toHaveBeenCalledWith('token', 1, expect.objectContaining({ name: 'Spring 2027', aiEnabled: false }));
    expect(await within(cohortCard('Spring 2027')).findByRole('switch', { name: 'AI help is off for this cohort' })).not.toBeChecked();
  });

  it('issues a new join code and archives or restores a cohort', async () => {
    vi.mocked(cohortApi.newJoinCode).mockResolvedValue({ ...spring, joinCode: 'NEW777' });
    vi.mocked(cohortApi.setCohortArchived).mockImplementation(async (_token, id, archived) => ({ ...(id === 1 ? spring : pilot), archived }));
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    await userEvent.click(within(cohortCard('Spring 2027')).getByRole('button', { name: 'New join code' }));
    expect(await screen.findByText('NEW777')).toBeInTheDocument();

    await userEvent.click(within(cohortCard('Spring 2027')).getByRole('button', { name: 'Archive' }));
    expect(cohortApi.setCohortArchived).toHaveBeenCalledWith('token', 1, true);
    await userEvent.click(within(cohortCard('Pilot')).getByRole('button', { name: 'Restore' }));
    expect(cohortApi.setCohortArchived).toHaveBeenCalledWith('token', 2, false);
  });

  it('shows the members, adds one by email and removes one', async () => {
    vi.mocked(cohortApi.addCohortMember).mockResolvedValue({ userId: 13, fullName: 'Kim Sreyneang', email: 'srey@example.com' });
    vi.mocked(cohortApi.removeCohortMember).mockResolvedValue(undefined);
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    const card = cohortCard('Spring 2027');
    await userEvent.click(within(card).getByRole('button', { name: 'Members' }));
    expect(await within(card).findByText('Sok Chan · chan@example.com')).toBeInTheDocument();

    await userEvent.type(within(card).getByRole('textbox', { name: 'Add by email' }), ' srey@example.com ');
    await userEvent.click(within(card).getByRole('button', { name: 'Add' }));
    expect(cohortApi.addCohortMember).toHaveBeenCalledWith('token', 1, 'srey@example.com');
    await waitFor(() => expect(cohortApi.listCohorts).toHaveBeenCalledTimes(2));

    await userEvent.click(within(card).getByRole('button', { name: 'Remove Lim Nary' }));
    expect(cohortApi.removeCohortMember).toHaveBeenCalledWith('token', 1, 12);

    await userEvent.click(within(card).getByRole('button', { name: 'Hide members' }));
    expect(within(card).queryByText('Sok Chan · chan@example.com')).not.toBeInTheDocument();
  });

  it('says when a cohort has no members, and why adding one failed', async () => {
    vi.mocked(cohortApi.listCohortMembers).mockResolvedValue([]);
    vi.mocked(cohortApi.addCohortMember).mockRejectedValue(new Error('That person is already in another cohort.'));
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    const card = cohortCard('Spring 2027');
    await userEvent.click(within(card).getByRole('button', { name: 'Members' }));
    expect(await within(card).findByText('No members yet.')).toBeInTheDocument();
    await userEvent.type(within(card).getByRole('textbox', { name: 'Add by email' }), 'chan@example.com');
    await userEvent.click(within(card).getByRole('button', { name: 'Add' }));
    expect(await within(card).findByText('That person is already in another cohort.')).toBeInTheDocument();
  });

  it('shows why the cohorts could not load', async () => {
    vi.mocked(cohortApi.listCohorts).mockRejectedValue(new Error('Server unavailable'));
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });

  it('shows the page in Khmer (COV-NFR-08)', async () => {
    setCurrentLanguage('km');
    renderPage(<CohortsPage />, { role: 'ADMIN' });
    await screen.findByText('Spring 2027');
    expect(screen.getByRole('button', { name: 'បង្កើតក្រុមសិក្សា' })).toBeInTheDocument();
    expect(within(cohortCard('Spring 2027')).getByRole('button', { name: 'លេខកូដចូលរួមថ្មី' })).toBeInTheDocument();
  });
});
