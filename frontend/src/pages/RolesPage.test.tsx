import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as rolesApi from '../api/rolesApi';
import { renderPage } from '../test/render';
import type { PermissionInfo, RoleInfo } from '../types/admin';
import { RolesPage } from './RolesPage';

vi.mock('../api/rolesApi');
vi.mock('../api/adminUsersApi', () => ({ listAdminUsers: vi.fn() }));
vi.mock('../api/cohortApi', () => ({ listCohorts: vi.fn() }));
vi.mock('../api/stepUpApi', () => ({ getStepUpStatus: vi.fn().mockRejectedValue(new Error('off')), stepUpHeader: () => ({}) }));

const permission = (key: string, group: string, label: string, extra: Partial<PermissionInfo> = {}): PermissionInfo => ({
  key, group, label, description: `${label} description`, adminOnly: false, always: false, ...extra,
});

const catalogue: PermissionInfo[] = [
  permission('USE_OWN_PLAN', 'Own plan', 'Use own plan', { always: true }),
  permission('COACH_COHORTS', 'Coaching', 'Coach cohorts'),
  permission('VIEW_USERS', 'People', 'View users'),
  permission('MANAGE_USERS', 'People', 'Manage users'),
  permission('MANAGE_COHORTS', 'Program', 'Manage cohorts'),
  permission('MANAGE_ROLES', 'Roles', 'Manage roles', { adminOnly: true }),
];

const roles: RoleInfo[] = [
  { key: 'USER', name: 'Participant', builtIn: true, editable: false, renameable: false, holders: 4, privileged: false, permissions: ['USE_OWN_PLAN'] },
  { key: 'COACH', name: 'Coach', builtIn: true, editable: true, renameable: false, holders: 2, privileged: true, permissions: ['USE_OWN_PLAN', 'COACH_COHORTS'] },
  { key: 'ASSISTANT', name: 'Program assistant', builtIn: false, editable: true, renameable: true, holders: 0, privileged: true,
    permissions: ['USE_OWN_PLAN', 'MANAGE_COHORTS'] },
];

const ALL = catalogue.map((item) => item.key);
const admin = { auth: { user: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'ADMIN', permissions: ALL } } };

describe('RolesPage (V23 UM-FR-08 to UM-FR-16)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(rolesApi.listRoles).mockResolvedValue(roles);
    vi.mocked(rolesApi.listPermissions).mockResolvedValue(catalogue);
  });

  it('lists each role with what it allows; locked roles offer no edit', async () => {
    renderPage(<RolesPage />, admin);

    const participant = (await screen.findByRole('heading', { name: 'Participant' })).closest('.MuiCard-root') as HTMLElement;
    expect(within(participant).getByText('This role cannot be changed.')).toBeInTheDocument();
    expect(within(participant).queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    const coach = screen.getByRole('heading', { name: 'Coach' }).closest('.MuiCard-root') as HTMLElement;
    expect(within(coach).getByText('Coach cohorts')).toBeInTheDocument();
    expect(within(coach).getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    expect(within(coach).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    const assistant = screen.getByRole('heading', { name: 'Program assistant' }).closest('.MuiCard-root') as HTMLElement;
    expect(within(assistant).getByRole('button', { name: 'Delete' })).toBeInTheDocument();
  });

  it('shows nothing to change to someone who may only read roles', async () => {
    renderPage(<RolesPage />, { auth: { user: { id: 2, fullName: 'Viewer', email: 'v@example.com', role: 'AUDITOR', permissions: ['USE_OWN_PLAN', 'VIEW_USERS'] } } });

    await screen.findByRole('heading', { name: 'Coach' });
    expect(screen.queryByRole('button', { name: 'New role' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('creates a role; a permission brings the ones it needs, and Manage roles is never offered', async () => {
    vi.mocked(rolesApi.createRole).mockResolvedValue({ ...roles[2], key: 'USER_HELPER', name: 'User helper' });
    renderPage(<RolesPage />, admin);
    await screen.findByRole('heading', { name: 'Coach' });

    await userEvent.click(screen.getByRole('button', { name: 'New role' }));
    const dialog = await screen.findByRole('dialog', { name: 'New role' });
    expect(within(dialog).queryByText('Manage roles')).not.toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/^Name/), 'User helper');
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /^Manage users/ }));
    expect(within(dialog).getByRole('checkbox', { name: /^View users/ })).toBeChecked();
    expect(within(dialog).getByText('People with this role must turn on two-step sign-in.')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Helps with sign-up week');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create role' }));

    await waitFor(() => expect(rolesApi.createRole).toHaveBeenCalledWith('token', {
      name: 'User helper', description: '', permissions: ['USE_OWN_PLAN', 'VIEW_USERS', 'MANAGE_USERS'],
      reason: 'Helps with sign-up week', cohortChanges: undefined,
    }, undefined));
  });

  it('says what an edit changes and how many people it affects', async () => {
    renderPage(<RolesPage />, admin);
    const coach = (await screen.findByRole('heading', { name: 'Coach' })).closest('.MuiCard-root') as HTMLElement;

    await userEvent.click(within(coach).getByRole('button', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit role: Coach' });
    expect(within(dialog).getByLabelText(/^Name/)).toBeDisabled();
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /^Manage cohorts/ }));

    expect(within(dialog).getByText('This change adds Manage cohorts. It affects 2 people, from their next request.')).toBeInTheDocument();
  });

  it('moves the holders of a deleted role in the same action', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue([roles[0], roles[1], { ...roles[2], holders: 3 }]);
    vi.mocked(rolesApi.deleteRole).mockResolvedValue(undefined);
    renderPage(<RolesPage />, admin);
    const assistant = (await screen.findByRole('heading', { name: 'Program assistant' })).closest('.MuiCard-root') as HTMLElement;

    await userEvent.click(within(assistant).getByRole('button', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete role: Program assistant' });
    expect(within(dialog).getByText('3 people have this role. Choose the role they move to.')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Sign-up week is over');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete role' }));

    await waitFor(() => expect(rolesApi.deleteRole).toHaveBeenCalledWith('token', 'ASSISTANT', 'Sign-up week is over', 'USER', undefined));
  });
});
