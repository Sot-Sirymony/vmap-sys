import { useCallback, useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { createAdminUser, listAdminUsers } from '../api/adminUsersApi';
import { listCohorts } from '../api/cohortApi';
import { listRoles } from '../api/rolesApi';
import { AddUserDialog, type CohortOption } from '../components/admin/UserDialogs';
import { UserDetailPanel, giveableRoles } from '../components/admin/UserDetailPanel';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { Select } from '../components/common/Select';
import { StepUpMailWarning } from '../components/step-up/StepUpMailWarning';
import { useStepUp } from '../components/step-up/useStepUp';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { tr } from '../i18n/translate';
import type { AdminUserFilter, AdminUserPage, AdminUserSummary, RoleInfo } from '../types/admin';
import { can } from '../utils/permissions';
import { PageSection } from './PageSection';

const PAGE_SIZES = [10, 25, 50, 100];

function shortDate(iso?: string | null) {
  return iso ? new Date(iso).toLocaleDateString() : '—';
}

function twoFactorLabel(user: AdminUserSummary) {
  if (user.twoFactor === 'ON') return tr('On');
  if (user.twoFactor === 'DUE') return tr('Due by {date}', { date: shortDate(user.twoFactorDueAt) });
  return tr('Off');
}

/**
 * V23 (BRD 23.0.0, UM-FR-17, UM-FR-18, UM-FR-22): every account, searchable and
 * filtered, with a detail panel for the actions the viewer may take.
 */
export function UsersPage() {
  const { token, user: viewer } = useAuth();
  const { showToast } = useToast();
  const stepUp = useStepUp(token);
  const [search, setSearch] = useState('');
  const query = useDebouncedValue(search, 300);
  const [filter, setFilter] = useState<AdminUserFilter>({ sort: 'joined' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [result, setResult] = useState<AdminUserPage | null>(null);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [cohorts, setCohorts] = useState<CohortOption[] | null>(null);
  const [coaches, setCoaches] = useState<AdminUserSummary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      setResult(await listAdminUsers(token, { ...filter, q: query }, page, size));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : tr('Could not load the accounts.'));
    } finally {
      setLoading(false);
    }
  }, [token, filter, query, page, size]);

  useEffect(() => {
    void load();
  }, [load]);

  // Roles name the filter and the dialogs; cohorts only for someone who manages them.
  useEffect(() => {
    if (!token) return;
    listRoles(token).then(setRoles).catch(() => setRoles([]));
    if (can(viewer, 'MANAGE_COHORTS')) {
      listCohorts(token)
        .then((list) => setCohorts(list.filter((cohort) => !cohort.archived).map(({ id, name }) => ({ id, name }))))
        .catch(() => setCohorts(null));
    }
  }, [token, viewer]);

  // Possible new coaches for a cohort handover: active accounts whose role can coach.
  useEffect(() => {
    if (!token || !can(viewer, 'MANAGE_USERS') || roles.length === 0) return;
    const coaching = new Set(roles.filter((role) => role.permissions.includes('COACH_COHORTS')).map((role) => role.key));
    listAdminUsers(token, { status: 'ACTIVE', sort: 'name' }, 0, 100)
      .then((list) => setCoaches(list.users.content.filter((item) => coaching.has(item.role))))
      .catch(() => setCoaches([]));
  }, [token, viewer, roles]);

  function update(next: Partial<AdminUserFilter>) {
    setFilter((current) => ({ ...current, ...next }));
    setPage(0);
  }

  const roleName = (key: string) => tr(roles.find((role) => role.key === key)?.name ?? key);

  return (
    <PageSection title={tr('Users')} subtitle={tr('Every account. Account details only: nobody here sees what people wrote in their plans.')}>
      <StepUpMailWarning token={token} />
      <Stack spacing={2}>
        {result && (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <Chip label={tr('{count} accounts', { count: result.totalAccounts })} />
            <Chip color="success" variant="outlined" label={tr('{count} active', { count: result.activeAccounts })} />
            <Chip color="warning" variant="outlined" label={tr('{count} blocked', { count: result.blockedAccounts })} />
            {Object.entries(result.countsByRole).map(([key, count]) => (
              <Chip key={key} variant="outlined" label={`${roleName(key)}: ${count}`} />
            ))}
          </Stack>
        )}

        <Card>
          <CardContent>
            <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr 1fr', md: '2fr 1fr 1fr 1fr 1fr' } }}>
              <Input aria-label={tr('Search by name or email')} placeholder={tr('Search by name or email')} value={search}
                onChange={(event) => {
                  setSearch(event.target.value);
                  setPage(0);
                }} />
              <Select aria-label={tr('Role')} value={filter.role ?? ''} onChange={(event) => update({ role: event.target.value || undefined })}>
                <option value="">{tr('All roles')}</option>
                {roles.map((role) => <option key={role.key} value={role.key}>{tr(role.name)}</option>)}
              </Select>
              <Select aria-label={tr('Status')} value={filter.status ?? ''} onChange={(event) => update({ status: event.target.value || undefined })}>
                <option value="">{tr('All statuses')}</option>
                <option value="ACTIVE">{tr('Active')}</option>
                <option value="DISABLED">{tr('Blocked')}</option>
              </Select>
              <Select aria-label={tr('Two-step sign-in')} value={filter.twoFactor ?? ''}
                onChange={(event) => update({ twoFactor: event.target.value || undefined })}>
                <option value="">{tr('Any two-step state')}</option>
                <option value="ON">{tr('On')}</option>
                <option value="DUE">{tr('Due')}</option>
                <option value="OFF">{tr('Off')}</option>
              </Select>
              <Select aria-label={tr('Sort')} value={filter.sort ?? 'joined'}
                onChange={(event) => update({ sort: event.target.value as AdminUserFilter['sort'] })}>
                <option value="joined">{tr('Newest first')}</option>
                <option value="name">{tr('Name')}</option>
                <option value="lastSignIn">{tr('Last sign-in')}</option>
              </Select>
              {cohorts && cohorts.length > 0 && (
                <Select aria-label={tr('Cohort')} value={filter.cohortId ?? ''}
                  onChange={(event) => update({ cohortId: event.target.value ? Number(event.target.value) : undefined })}>
                  <option value="">{tr('All cohorts')}</option>
                  {cohorts.map((cohort) => <option key={cohort.id} value={cohort.id}>{cohort.name}</option>)}
                </Select>
              )}
            </Box>
          </CardContent>
        </Card>

        {can(viewer, 'MANAGE_USERS') && (
          <Box>
            <Button onClick={() => setAdding(true)} disabled={roles.length === 0}>{tr('Add user')}</Button>
          </Box>
        )}

        {error && <ErrorMessage message={error} />}
        {loading && !result ? <Loading variant="table" /> : result && (
          <Card>
            <TableContainer>
              <Table size="small" aria-label={tr('Accounts')}>
                <TableHead>
                  <TableRow>
                    <TableCell>{tr('Name')}</TableCell>
                    <TableCell>{tr('Email')}</TableCell>
                    <TableCell>{tr('Role')}</TableCell>
                    <TableCell>{tr('Status')}</TableCell>
                    <TableCell>{tr('Cohort')}</TableCell>
                    <TableCell>{tr('Two-step sign-in')}</TableCell>
                    <TableCell>{tr('Joined')}</TableCell>
                    <TableCell>{tr('Last sign-in')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {result.users.content.length === 0 && (
                    <TableRow><TableCell colSpan={8}>{tr('No accounts match these filters.')}</TableCell></TableRow>
                  )}
                  {result.users.content.map((item) => (
                    <TableRow key={item.id} hover sx={{ cursor: 'pointer' }} onClick={() => setSelected(item.id)}>
                      <TableCell>
                        <Link component="button" type="button" underline="hover" color="inherit" sx={{ fontWeight: 500 }}
                          onClick={(event) => {
                            event.stopPropagation();
                            setSelected(item.id);
                          }}>
                          {item.fullName}
                        </Link>
                      </TableCell>
                      <TableCell>{item.email}</TableCell>
                      <TableCell>{tr(item.roleName)}</TableCell>
                      <TableCell>
                        <Chip size="small" color={item.status === 'ACTIVE' ? 'success' : 'warning'}
                          label={item.status === 'ACTIVE' ? tr('Active') : tr('Blocked')} />
                      </TableCell>
                      <TableCell>{item.cohortName ?? '—'}</TableCell>
                      <TableCell>{twoFactorLabel(item)}</TableCell>
                      <TableCell>{shortDate(item.joinedAt)}</TableCell>
                      <TableCell>{item.lastSignInAt ? shortDate(item.lastSignInAt) : tr('Never')}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={result.users.totalElements}
              page={page}
              rowsPerPage={size}
              rowsPerPageOptions={PAGE_SIZES}
              onPageChange={(_, next) => setPage(next)}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
              labelRowsPerPage={tr('Rows per page')}
            />
          </Card>
        )}
        {result && (
          <Typography variant="caption" color="text.secondary">
            {tr('Select an account to see its details and actions.')}
          </Typography>
        )}
      </Stack>

      {adding && token && (
        <AddUserDialog roles={giveableRoles(viewer, roles)} cohorts={cohorts} onClose={() => setAdding(false)}
          onSave={async (input) => {
            const created = await stepUp.protect('ADMIN_ROLE_CHANGE', (code) => createAdminUser(token, input, code));
            setAdding(false);
            showToast(tr('Invitation sent to {email}.', { email: created.account.email }));
            await load();
            setSelected(created.account.id);
          }} />
      )}
      {selected !== null && token && (
        <UserDetailPanel token={token} userId={selected} viewer={viewer} roles={roles} cohorts={cohorts} coaches={coaches}
          onClose={() => setSelected(null)}
          onChanged={() => void load()} />
      )}
      {stepUp.dialog}
    </PageSection>
  );
}
