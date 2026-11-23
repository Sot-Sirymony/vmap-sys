import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { X } from 'lucide-react';
import {
  blockAdminUser,
  changeAdminUserRole,
  deleteAdminUser,
  getAdminUser,
  resendAdminUserInvitation,
  sendAdminUserPasswordReset,
  unblockAdminUser,
  updateAdminUser,
} from '../../api/adminUsersApi';
import { resetTwoFactor } from '../../api/twoFactorApi';
import { useToast } from '../../context/ToastContext';
import { tr } from '../../i18n/translate';
import type { AdminUserDetail, AdminUserSummary, RoleInfo } from '../../types/admin';
import type { AuthUser } from '../../types/auth';
import { can, holdsAll } from '../../utils/permissions';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Loading } from '../common/Loading';
import { useStepUp } from '../step-up/useStepUp';
import {
  BlockDialog,
  ChangeRoleDialog,
  CohortOption,
  DeleteUserDialog,
  EditUserDialog,
  ReasonDialog,
  countLabel,
} from './UserDialogs';

type Open = 'edit' | 'role' | 'block' | 'unblock' | 'delete' | 'twoFactor' | null;

function when(iso?: string | null) {
  return iso ? new Date(iso).toLocaleString() : tr('Never');
}

/** The roles this viewer may give (D-23-9): every permission held, and the Admin role only by an Admin. */
export function giveableRoles(viewer: AuthUser | null, roles: RoleInfo[]) {
  return roles.filter((role) => holdsAll(viewer, role.permissions) && (role.key !== 'ADMIN' || viewer?.role === 'ADMIN'));
}

function Field({ label, value }: Readonly<{ label: string; value: React.ReactNode }>) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: '9rem 1fr', gap: 1, py: 0.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="body2" component="div">{value}</Typography>
    </Box>
  );
}

/**
 * V23 (BRD 23.0.0, UM-FR-19, UM-FR-20): one account and the actions the viewer
 * may take on it. Buttons appear only when allowed; the server checks again.
 */
export function UserDetailPanel({ token, userId, viewer, roles, cohorts, coaches, onChanged, onClose }: Readonly<{
  token: string;
  userId: number;
  viewer: AuthUser | null;
  roles: RoleInfo[];
  cohorts: CohortOption[] | null;
  coaches: AdminUserSummary[];
  /** After a change; null when the account was deleted. */
  onChanged: (detail: AdminUserDetail | null) => void;
  onClose: () => void;
}>) {
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [error, setError] = useState('');
  const [open, setOpen] = useState<Open>(null);
  const stepUp = useStepUp(token);
  const { showToast } = useToast();

  const load = useCallback(async () => {
    setError('');
    try {
      setDetail(await getAdminUser(token, userId));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : tr('Could not load this account.'));
    }
  }, [token, userId]);

  useEffect(() => {
    void load();
  }, [load]);

  function changed(next: AdminUserDetail, message: string) {
    setDetail(next);
    setOpen(null);
    onChanged(next);
    showToast(message);
  }

  async function simpleAction(action: () => Promise<void>, message: string) {
    setError('');
    try {
      await action();
      showToast(message);
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : tr('Something went wrong. Please try again.'));
    }
  }

  const account = detail?.account;
  const self = Boolean(account && viewer && account.id === viewer.id);
  const targetPermissions = roles.find((role) => role.key === account?.role)?.permissions ?? [];
  const mayAct = Boolean(account) && holdsAll(viewer, targetPermissions);
  const manage = can(viewer, 'MANAGE_USERS') && mayAct;
  const active = account?.status === 'ACTIVE';

  return (
    <Drawer anchor="right" open onClose={onClose} slotProps={{ paper: { sx: { width: { xs: '100%', sm: 480 } } } }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', px: 2, py: 1.5 }}>
        <Typography variant="h6" component="h2">{account?.fullName ?? tr('Account')}</Typography>
        <IconButton aria-label={tr('Close')} onClick={onClose} size="small"><X size={18} /></IconButton>
      </Stack>
      <Divider />
      <Box sx={{ p: 2, overflowY: 'auto' }}>
        {error && <ErrorMessage message={error} />}
        {!detail || !account ? (!error && <Loading />) : (
          <Stack spacing={2}>
            <div>
              <Field label={tr('Email')} value={account.email} />
              <Field label={tr('Role')} value={tr(account.roleName)} />
              <Field label={tr('Status')} value={
                <Chip size="small" color={active ? 'success' : 'warning'} label={active ? tr('Active') : tr('Blocked')} />
              } />
              <Field label={tr('Cohort')} value={account.cohortName ?? '—'} />
              <Field label={tr('Two-step sign-in')} value={
                account.twoFactor === 'ON' ? tr('On')
                  : account.twoFactor === 'DUE' ? tr('Due by {date}', { date: when(account.twoFactorDueAt) }) : tr('Off')
              } />
              <Field label={tr('Created by')} value={tr(detail.createdBy)} />
              <Field label={tr('Joined')} value={when(account.joinedAt)} />
              <Field label={tr('Last sign-in')} value={when(account.lastSignInAt)} />
              {detail.roleChangedAt && (
                <Field label={tr('Role changed')} value={`${when(detail.roleChangedAt)}${detail.roleChangedBy ? ` · ${detail.roleChangedBy}` : ''}`} />
              )}
              {detail.blockedAt && (
                <Field label={tr('Blocked')} value={`${when(detail.blockedAt)}${detail.blockedBy ? ` · ${detail.blockedBy}` : ''}`} />
              )}
              {detail.invitationPending && (
                <Field label={tr('Invitation')} value={detail.invitationExpiresAt
                  ? tr('Not accepted yet; the link works until {date}', { date: when(detail.invitationExpiresAt) })
                  : tr('Not accepted; the link has expired')} />
              )}
            </div>

            <div>
              <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{tr('Records')}</Typography>
              <Typography variant="body2" color="text.secondary">
                {Object.entries(detail.recordCounts).map(([key, count]) => `${countLabel(key)}: ${count}`).join(' · ')}
              </Typography>
            </div>

            {self && <Alert severity="info">{tr('This is your account. You cannot change your own role, block or delete it.')}</Alert>}
            {!self && !mayAct && (
              <Alert severity="info">{tr('This account has permissions you do not have, so you cannot change it.')}</Alert>
            )}

            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
              {can(viewer, 'MANAGE_USERS') && (self || mayAct) && (
                <Button variant="secondary" onClick={() => setOpen('edit')}>{tr('Edit')}</Button>
              )}
              {manage && !self && <Button variant="secondary" onClick={() => setOpen('role')}>{tr('Change role')}</Button>}
              {manage && !self && active && detail.invitationPending && (
                <Button variant="secondary" onClick={() => void simpleAction(() => resendAdminUserInvitation(token, account.id),
                  tr('A new invitation was sent.'))}>{tr('Resend invitation')}</Button>
              )}
              {manage && !self && active && !detail.invitationPending && (
                <Button variant="secondary" onClick={() => void simpleAction(() => sendAdminUserPasswordReset(token, account.id),
                  tr('A password reset email was sent.'))}>{tr('Send password reset email')}</Button>
              )}
              {can(viewer, 'RESET_TWO_FACTOR') && !self && account.twoFactor === 'ON' && (
                <Button variant="secondary" onClick={() => setOpen('twoFactor')}>{tr('Reset two-step sign-in')}</Button>
              )}
              {manage && !self && active && <Button variant="danger" onClick={() => setOpen('block')}>{tr('Block')}</Button>}
              {manage && !self && !active && <Button onClick={() => setOpen('unblock')}>{tr('Unblock')}</Button>}
              {can(viewer, 'DELETE_USERS') && mayAct && !self && !active && (
                <Button variant="danger" onClick={() => setOpen('delete')}>{tr('Delete permanently')}</Button>
              )}
            </Stack>
            {can(viewer, 'DELETE_USERS') && mayAct && !self && active && (
              <Typography variant="caption" color="text.secondary">{tr('To delete this account, block it first.')}</Typography>
            )}

            <div>
              <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{tr('Recent security events')}</Typography>
              {detail.recentEvents.length === 0 ? (
                <Typography variant="body2" color="text.secondary">{tr('No events yet.')}</Typography>
              ) : detail.recentEvents.map((event) => (
                <Typography key={event.id} variant="body2" sx={{ py: 0.25 }}>
                  {new Date(event.occurredAt).toLocaleString()} · {event.eventType.toLowerCase().replaceAll('_', ' ')}
                  {event.outcome !== 'SUCCESS' ? ` (${event.outcome.toLowerCase()})` : ''}
                </Typography>
              ))}
            </div>
          </Stack>
        )}
      </Box>

      {detail && account && open === 'edit' && (
        <EditUserDialog account={account} cohorts={cohorts} onClose={() => setOpen(null)}
          onSave={async (input) => changed(await updateAdminUser(token, account.id, input), tr('Saved.'))} />
      )}
      {detail && account && open === 'role' && (
        <ChangeRoleDialog detail={detail} roles={giveableRoles(viewer, roles)} coaches={coaches} onClose={() => setOpen(null)}
          onSave={async (input) => changed(
            await stepUp.protect('ADMIN_ROLE_CHANGE', (code) => changeAdminUserRole(token, account.id, input, code)),
            tr('The role was changed.'),
          )} />
      )}
      {detail && account && (open === 'block' || open === 'unblock') && (
        <BlockDialog account={account} unblock={open === 'unblock'} onClose={() => setOpen(null)}
          onSave={async (reason) => changed(
            await stepUp.protect('ADMIN_USER_BLOCK', (code) => (open === 'unblock'
              ? unblockAdminUser(token, account.id, reason, code) : blockAdminUser(token, account.id, reason, code))),
            open === 'unblock' ? tr('The account was unblocked.') : tr('The account was blocked.'),
          )} />
      )}
      {detail && account && open === 'twoFactor' && (
        <ReasonDialog title={tr('Reset two-step sign-in for {name}', { name: account.fullName })}
          text={tr('The person signs in with their password only, and must set up two-step sign-in again.')}
          actionLabel={tr('Reset')} danger onClose={() => setOpen(null)}
          onSave={async (reason) => {
            await stepUp.protect('ADMIN_TWO_FACTOR_RESET', (code) => resetTwoFactor(token, account.id, reason, code));
            setOpen(null);
            showToast(tr('Two-step sign-in was reset.'));
            await load();
          }} />
      )}
      {detail && account && open === 'delete' && (
        <DeleteUserDialog detail={detail} onClose={() => setOpen(null)}
          onSave={async (confirmEmail, reason) => {
            await stepUp.protect('ADMIN_USER_DELETE', (code) => deleteAdminUser(token, account.id, confirmEmail, reason, code));
            setOpen(null);
            showToast(tr('The account was deleted.'));
            onChanged(null);
            onClose();
          }} />
      )}
      {stepUp.dialog}
    </Drawer>
  );
}
