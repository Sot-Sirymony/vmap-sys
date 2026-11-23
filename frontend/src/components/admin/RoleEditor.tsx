import { FormEvent, useEffect, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { listAdminUsers } from '../../api/adminUsersApi';
import { listCohorts } from '../../api/cohortApi';
import { tr } from '../../i18n/translate';
import type { AdminUserSummary, CohortCoachChange, PermissionInfo, RoleInfo, RoleInput } from '../../types/admin';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Modal } from '../common/Modal';
import { MAX_REASON, MIN_REASON } from './UserDialogs';

/** Mirrors the server (UM-FR-03): a permission brings the ones it needs. */
const NEEDS: Record<string, string[]> = {
  MANAGE_USERS: ['VIEW_USERS'],
  DELETE_USERS: ['MANAGE_USERS', 'VIEW_USERS'],
  RESET_TWO_FACTOR: ['VIEW_USERS'],
};

export function withNeeded(selected: string[], catalogue: PermissionInfo[]): string[] {
  const result = new Set(selected);
  catalogue.filter((permission) => permission.always).forEach((permission) => result.add(permission.key));
  let grew = true;
  while (grew) {
    grew = false;
    [...result].forEach((key) => (NEEDS[key] ?? []).forEach((needed) => {
      if (!result.has(needed)) {
        result.add(needed);
        grew = true;
      }
    }));
  }
  return catalogue.map((permission) => permission.key).filter((key) => result.has(key));
}

/** "adds Manage cohorts; removes View AI reports" (UM-FR-11). */
export function describeChange(before: string[], after: string[], catalogue: PermissionInfo[]): string {
  const label = (key: string) => tr(catalogue.find((permission) => permission.key === key)?.label ?? key);
  const added = after.filter((key) => !before.includes(key)).map(label);
  const removed = before.filter((key) => !after.includes(key)).map(label);
  const parts: string[] = [];
  if (added.length) parts.push(tr('adds {list}', { list: added.join(', ') }));
  if (removed.length) parts.push(tr('removes {list}', { list: removed.join(', ') }));
  return parts.join('; ');
}

type AffectedCohort = { id: number; name: string };

/**
 * V23 (BRD 23.0.0, UM-FR-08 to UM-FR-13): create a role, or change one. Admin and
 * Participant never reach here (the page offers no edit for them).
 */
export function RoleEditor({ token, role, roles, catalogue, onSave, onClose }: Readonly<{
  token: string;
  /** null to create. */
  role: RoleInfo | null;
  roles: RoleInfo[];
  catalogue: PermissionInfo[];
  onSave: (input: RoleInput) => Promise<void>;
  onClose: () => void;
}>) {
  const [name, setName] = useState(role?.name ?? '');
  const [description, setDescription] = useState(role?.description ?? '');
  const [selected, setSelected] = useState<string[]>(withNeeded(role?.permissions ?? [], catalogue));
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [affected, setAffected] = useState<AffectedCohort[]>([]);
  const [coaches, setCoaches] = useState<AdminUserSummary[]>([]);
  const [handover, setHandover] = useState<Record<number, number | null>>({});

  const losesCoaching = Boolean(role?.permissions.includes('COACH_COHORTS') && !selected.includes('COACH_COHORTS'));
  const groups = useMemo(() => {
    const byGroup = new Map<string, PermissionInfo[]>();
    catalogue.filter((permission) => !permission.adminOnly).forEach((permission) => {
      byGroup.set(permission.group, [...(byGroup.get(permission.group) ?? []), permission]);
    });
    return [...byGroup.entries()];
  }, [catalogue]);

  // UM-FR-12: which active cohorts this role's holders coach, and who else could take them.
  useEffect(() => {
    if (!losesCoaching || !role) {
      setAffected([]);
      return;
    }
    let cancelled = false;
    const otherCoachingRoles = new Set(roles.filter((item) => item.key !== role.key && item.permissions.includes('COACH_COHORTS'))
      .map((item) => item.key));
    Promise.all([
      listAdminUsers(token, { role: role.key }, 0, 100),
      listCohorts(token),
      listAdminUsers(token, { status: 'ACTIVE', sort: 'name' }, 0, 100),
    ]).then(([holders, cohorts, active]) => {
      if (cancelled) return;
      const holderIds = new Set(holders.items.map((item) => item.id));
      setAffected(cohorts.filter((cohort) => !cohort.archived && cohort.coachId && holderIds.has(cohort.coachId))
        .map(({ id, name: cohortName }) => ({ id, name: cohortName })));
      setCoaches(active.items.filter((item) => otherCoachingRoles.has(item.role)));
    }).catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [losesCoaching, role, roles, token]);

  function toggle(key: string, on: boolean) {
    const next = on ? [...selected, key] : selected.filter((item) => item !== key && !(NEEDS[item] ?? []).includes(key));
    setSelected(withNeeded(next, catalogue));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    const cohortChanges: CohortCoachChange[] | undefined = affected.length
      ? affected.map((cohort) => ({ cohortId: cohort.id, newCoachId: handover[cohort.id] ?? null }))
      : undefined;
    try {
      await onSave({ name: name.trim(), description: description.trim(), permissions: selected, reason: reason.trim(), cohortChanges });
    } catch (caught) {
      if (!(caught instanceof Error && caught.name === 'StepUpCancelled')) {
        setError(caught instanceof Error ? caught.message : tr('Could not save the role.'));
      }
    } finally {
      setBusy(false);
    }
  }

  const change = role ? describeChange(role.permissions, selected, catalogue) : '';
  const privileged = selected.some((key) => key !== 'USE_OWN_PLAN');

  return (
    <Modal title={role ? tr('Edit role: {name}', { name: tr(role.name) }) : tr('New role')} onClose={onClose}>
      <form onSubmit={(event) => void submit(event)}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField label={tr('Name')} value={name} onChange={(event) => setName(event.target.value)} required fullWidth
            disabled={Boolean(role && !role.renameable)}
            helperText={role && !role.renameable ? tr('A built-in role keeps its name.') : tr('3 to 40 characters.')}
            slotProps={{ htmlInput: { maxLength: 40 } }} />
          <TextField label={tr('Description')} value={description} onChange={(event) => setDescription(event.target.value)}
            fullWidth disabled={Boolean(role && !role.renameable)} slotProps={{ htmlInput: { maxLength: 200 } }} />

          {groups.map(([group, permissions]) => (
            <div key={group}>
              <Typography variant="subtitle2">{tr(group)}</Typography>
              {permissions.map((permission) => (
                <FormControlLabel
                  key={permission.key}
                  sx={{ display: 'flex', alignItems: 'flex-start', mt: 0.5 }}
                  control={<Checkbox size="small" checked={selected.includes(permission.key)} disabled={permission.always}
                    onChange={(event) => toggle(permission.key, event.target.checked)} />}
                  label={
                    <span>
                      <Typography variant="body2" component="span">{tr(permission.label)}</Typography>
                      <Typography variant="caption" color="text.secondary" component="span" sx={{ display: 'block' }}>
                        {tr(permission.description)}
                      </Typography>
                    </span>
                  }
                />
              ))}
            </div>
          ))}

          {role && change && (
            <Alert severity="info">
              {tr('This change {change}. It affects {count} people, from their next request.', { change, count: role.holders })}
            </Alert>
          )}
          {privileged && (!role || !role.privileged) && (
            <Alert severity="info">{tr('People with this role must turn on two-step sign-in.')}</Alert>
          )}
          {affected.length > 0 && (
            <Stack spacing={1.5}>
              <Alert severity="warning">
                {tr('People with this role coach active cohorts. Choose a new coach, or none, for each.')}
              </Alert>
              {affected.map((cohort) => (
                <TextField key={cohort.id} select label={cohort.name} value={handover[cohort.id] ?? ''} fullWidth
                  onChange={(event) => setHandover({
                    ...handover, [cohort.id]: event.target.value === '' ? null : Number(event.target.value),
                  })}>
                  <MenuItem value="">{tr('No coach')}</MenuItem>
                  {coaches.map((coach) => <MenuItem key={coach.id} value={coach.id}>{coach.fullName}</MenuItem>)}
                </TextField>
              ))}
            </Stack>
          )}

          <TextField label={tr('Reason')} value={reason} onChange={(event) => setReason(event.target.value)} multiline minRows={2}
            required fullWidth slotProps={{ htmlInput: { maxLength: MAX_REASON } }}
            helperText={tr('{min} to {max} characters. Kept in the security log.', { min: MIN_REASON, max: MAX_REASON })} />
          {error && <ErrorMessage message={error} />}
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>{tr('Cancel')}</Button>
            <Button type="submit" disabled={busy || name.trim().length < 3 || reason.trim().length < MIN_REASON || (Boolean(role) && !change && name === role?.name && description === (role?.description ?? ''))}>
              {busy ? tr('Saving...') : role ? tr('Save role') : tr('Create role')}
            </Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}

/** UM-FR-14: a role with holders moves them to another role first, in the same action. */
export function DeleteRoleDialog({ role, roles, onSave, onClose }: Readonly<{
  role: RoleInfo;
  roles: RoleInfo[];
  onSave: (reason: string, moveTo?: string) => Promise<void>;
  onClose: () => void;
}>) {
  const targets = roles.filter((item) => item.key !== role.key && item.key !== 'ADMIN');
  const [moveTo, setMoveTo] = useState(role.holders > 0 ? 'USER' : '');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onSave(reason.trim(), role.holders > 0 ? moveTo : undefined);
    } catch (caught) {
      if (!(caught instanceof Error && caught.name === 'StepUpCancelled')) {
        setError(caught instanceof Error ? caught.message : tr('Could not delete the role.'));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={tr('Delete role: {name}', { name: role.name })} onClose={onClose}>
      <form onSubmit={(event) => void submit(event)}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {role.holders > 0 ? (
            <>
              <Typography variant="body2">
                {tr('{count} people have this role. Choose the role they move to.', { count: role.holders })}
              </Typography>
              <TextField select label={tr('Move them to')} value={moveTo} onChange={(event) => setMoveTo(event.target.value)} fullWidth>
                {targets.map((item) => <MenuItem key={item.key} value={item.key}>{tr(item.name)}</MenuItem>)}
              </TextField>
            </>
          ) : (
            <Typography variant="body2">{tr('Nobody has this role.')}</Typography>
          )}
          <TextField label={tr('Reason')} value={reason} onChange={(event) => setReason(event.target.value)} multiline minRows={2}
            required fullWidth slotProps={{ htmlInput: { maxLength: MAX_REASON } }}
            helperText={tr('{min} to {max} characters. Kept in the security log.', { min: MIN_REASON, max: MAX_REASON })} />
          {error && <ErrorMessage message={error} />}
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>{tr('Cancel')}</Button>
            <Button type="submit" variant="danger" disabled={busy || reason.trim().length < MIN_REASON}>
              {busy ? tr('Saving...') : tr('Delete role')}
            </Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}
