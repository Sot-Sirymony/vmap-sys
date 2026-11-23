import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as adminUsersApi from '../api/adminUsersApi';
import * as rolesApi from '../api/rolesApi';
import { renderPage } from '../test/render';
import type { AdminUserDetail, AdminUserSummary, RoleInfo } from '../types/admin';
import { UsersPage } from './UsersPage';

vi.mock('../api/adminUsersApi');
vi.mock('../api/rolesApi');
vi.mock('../api/cohortApi', () => ({ listCohorts: vi.fn().mockResolvedValue([]) }));
vi.mock('../api/stepUpApi', () => ({ getStepUpStatus: vi.fn().mockRejectedValue(new Error('off')), stepUpHeader: () => ({}) }));

const ALL = ['USE_OWN_PLAN', 'COACH_COHORTS', 'VIEW_USERS', 'MANAGE_USERS', 'DELETE_USERS', 'RESET_TWO_FACTOR', 'MANAGE_COHORTS',
  'MANAGE_MODULE_CONTENT', 'MANAGE_INTERFACE_TEXT', 'HANDLE_SUPPORT', 'MANAGE_AI_SETTINGS', 'VIEW_AI_REPORTS', 'VIEW_SECURITY_LOG',
  'MANAGE_ROLES'];

const roles: RoleInfo[] = [
  { key: 'USER', name: 'Participant', builtIn: true, editable: false, renameable: false, holders: 2, privileged: false, permissions: ['USE_OWN_PLAN'] },
  { key: 'COACH', name: 'Coach', builtIn: true, editable: true, renameable: false, holders: 1, privileged: true, permissions: ['USE_OWN_PLAN', 'COACH_COHORTS'] },
  { key: 'ADMIN', name: 'Admin', builtIn: true, editable: false, renameable: false, holders: 1, privileged: true, permissions: ALL },
];

function account(overrides: Partial<AdminUserSummary>): AdminUserSummary {
  return {
    id: 10, fullName: 'Sok Dara', email: 'dara@example.com', role: 'USER', roleName: 'Participant', status: 'ACTIVE',
    twoFactor: 'OFF', joinedAt: '2026-11-01T08:00:00Z', lastSignInAt: null, ...overrides,
  };
}

const dara = account({});
const blocked = account({ id: 11, fullName: 'Lim Nary', email: 'nary@example.com', status: 'DISABLED' });
const chief = account({ id: 12, fullName: 'Chief Admin', email: 'chief@example.com', role: 'ADMIN', roleName: 'Admin', twoFactor: 'ON' });

function detail(summary: AdminUserSummary, overrides: Partial<AdminUserDetail> = {}): AdminUserDetail {
  return {
    account: summary, createdBy: 'self sign-up', recordCounts: { visionAreas: 2, dreams: 3, goals: 4, steps: 5, tasks: 6 },
    recentEvents: [], invitationPending: false, coachedCohorts: [], ...overrides,
  };
}

const admin = { auth: { user: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'ADMIN', permissions: ALL } } };

describe('UsersPage (V23 UM-FR-17 to UM-FR-22, UM-FR-35, UM-FR-39)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(rolesApi.listRoles).mockResolvedValue(roles);
    vi.mocked(adminUsersApi.listAdminUsers).mockResolvedValue({
      items: [dara, blocked, chief], page: 0, size: 25, totalElements: 3, hasNext: false,
      totalAccounts: 3, activeAccounts: 2, blockedAccounts: 1, countsByRole: { USER: 2, ADMIN: 1 },
    });
  });

  it('lists accounts with their counts, and searches as the person types', async () => {
    renderPage(<UsersPage />, admin);

    expect(await screen.findByRole('button', { name: 'Sok Dara' })).toBeInTheDocument();
    expect(screen.getByText('3 accounts')).toBeInTheDocument();
    expect(screen.getByText('1 blocked')).toBeInTheDocument();
    expect(screen.getByText('Participant: 2')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Search by name or email'), 'nary');
    await waitFor(() => expect(adminUsersApi.listAdminUsers).toHaveBeenLastCalledWith(
      'token', expect.objectContaining({ q: 'nary' }), 0, 25));
  });

  it('shows no Add user button to someone who may only view accounts', async () => {
    renderPage(<UsersPage />, { auth: { user: { id: 1, fullName: 'Viewer', email: 'v@example.com', role: 'AUDITOR', permissions: ['USE_OWN_PLAN', 'VIEW_USERS'] } } });

    await screen.findByRole('button', { name: 'Sok Dara' });
    expect(screen.queryByRole('button', { name: 'Add user' })).not.toBeInTheDocument();
  });

  it('adds a user by invitation', async () => {
    vi.mocked(adminUsersApi.createAdminUser).mockResolvedValue(detail(account({ id: 20, fullName: 'New Person', email: 'new@example.com' }),
      { invitationPending: true }));
    vi.mocked(adminUsersApi.getAdminUser).mockResolvedValue(detail(account({ id: 20, fullName: 'New Person', email: 'new@example.com' })));
    renderPage(<UsersPage />, admin);
    await screen.findByRole('button', { name: 'Sok Dara' });

    await userEvent.click(screen.getByRole('button', { name: 'Add user' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add user' });
    await userEvent.type(within(dialog).getByLabelText(/Full name/), 'New Person');
    await userEvent.type(within(dialog).getByLabelText(/Email/), 'new@example.com');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send invitation' }));

    await waitFor(() => expect(adminUsersApi.createAdminUser).toHaveBeenCalledWith('token',
      { fullName: 'New Person', email: 'new@example.com', role: 'USER', cohortId: null }, undefined));
    expect(await screen.findByText('Invitation sent to new@example.com.')).toBeInTheDocument();
  });

  it('deletes a blocked account only after its email is typed exactly', async () => {
    vi.mocked(adminUsersApi.getAdminUser).mockResolvedValue(detail(blocked, { coachedCohorts: [{ id: 5, name: 'Spring 2027' }] }));
    vi.mocked(adminUsersApi.deleteAdminUser).mockResolvedValue(undefined);
    renderPage(<UsersPage />, admin);

    await userEvent.click(await screen.findByRole('button', { name: 'Lim Nary' }));
    expect(await screen.findByRole('button', { name: 'Unblock' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete permanently' }));

    const dialog = await screen.findByRole('dialog', { name: 'Delete Lim Nary permanently' });
    expect(within(dialog).getByText('These cohorts will have no coach: Spring 2027')).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: 'Delete permanently' });
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Left the program in May');
    await userEvent.type(within(dialog).getByLabelText(/Type nary@example.com to confirm/), 'someone@example.com');
    expect(confirm).toBeDisabled();
    await userEvent.clear(within(dialog).getByLabelText(/Type nary@example.com to confirm/));
    await userEvent.type(within(dialog).getByLabelText(/Type nary@example.com to confirm/), 'nary@example.com');
    await userEvent.click(confirm);

    await waitFor(() => expect(adminUsersApi.deleteAdminUser).toHaveBeenCalledWith('token', 11, 'nary@example.com',
      'Left the program in May', undefined));
  });

  it('offers no delete for an active account, only blocking first', async () => {
    vi.mocked(adminUsersApi.getAdminUser).mockResolvedValue(detail(dara));
    renderPage(<UsersPage />, admin);

    await userEvent.click(await screen.findByRole('button', { name: 'Sok Dara' }));

    expect(await screen.findByRole('button', { name: 'Block' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete permanently' })).not.toBeInTheDocument();
    expect(screen.getByText('To delete this account, block it first.')).toBeInTheDocument();
  });

  it('hides role, block and delete on your own account', async () => {
    const me = account({ id: 1, fullName: 'Me', email: 'me@example.com', role: 'ADMIN', roleName: 'Admin' });
    vi.mocked(adminUsersApi.listAdminUsers).mockResolvedValue({
      items: [me], page: 0, size: 25, totalElements: 1, hasNext: false, totalAccounts: 1, activeAccounts: 1, blockedAccounts: 0,
      countsByRole: { ADMIN: 1 },
    });
    vi.mocked(adminUsersApi.getAdminUser).mockResolvedValue(detail(me));
    renderPage(<UsersPage />, admin);

    await userEvent.click(await screen.findByRole('button', { name: 'Me' }));

    expect(await screen.findByText('This is your account. You cannot change your own role, block or delete it.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Change role' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Block' })).not.toBeInTheDocument();
  });

  it('lets a user manager open an Admin but change nothing (D-23-9)', async () => {
    vi.mocked(adminUsersApi.getAdminUser).mockResolvedValue(detail(chief));
    renderPage(<UsersPage />, { auth: { user: { id: 2, fullName: 'Manager', email: 'm@example.com', role: 'COACH',
      permissions: ['USE_OWN_PLAN', 'COACH_COHORTS', 'VIEW_USERS', 'MANAGE_USERS'] } } });

    await userEvent.click(await screen.findByRole('button', { name: 'Chief Admin' }));

    expect(await screen.findByText('This account has permissions you do not have, so you cannot change it.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Block' })).not.toBeInTheDocument();
  });

  it('asks for a new coach for each cohort when a coach moves to a role that cannot coach', async () => {
    const coach = account({ id: 13, fullName: 'Coach Vanna', email: 'vanna@example.com', role: 'COACH', roleName: 'Coach' });
    vi.mocked(adminUsersApi.getAdminUser).mockResolvedValue(detail(coach, { coachedCohorts: [{ id: 5, name: 'Spring 2027' }] }));
    vi.mocked(adminUsersApi.changeAdminUserRole).mockResolvedValue(detail(account({ ...coach, role: 'USER', roleName: 'Participant' })));
    vi.mocked(adminUsersApi.listAdminUsers).mockResolvedValue({
      items: [coach], page: 0, size: 25, totalElements: 1, hasNext: false, totalAccounts: 1, activeAccounts: 1, blockedAccounts: 0,
      countsByRole: { COACH: 1 },
    });
    renderPage(<UsersPage />, admin);

    await userEvent.click(await screen.findByRole('button', { name: 'Coach Vanna' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Change role' }));
    const dialog = await screen.findByRole('dialog', { name: 'Change role for Coach Vanna' });
    expect(within(dialog).getByText(/coaches active cohorts and the new role cannot coach/)).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Moving to a participant seat');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Change role' }));

    await waitFor(() => expect(adminUsersApi.changeAdminUserRole).toHaveBeenCalledWith('token', 13,
      { role: 'USER', reason: 'Moving to a participant seat', cohortChanges: [{ cohortId: 5, newCoachId: null }] }, undefined));
  });
});
