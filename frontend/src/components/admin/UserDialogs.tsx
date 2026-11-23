import { FormEvent, useState } from 'react';
import Alert from '@mui/material/Alert';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Modal } from '../common/Modal';
import { tr } from '../../i18n/translate';
import type {
  AdminRoleChangeInput,
  AdminUserCreateInput,
  AdminUserDetail,
  AdminUserSummary,
  AdminUserUpdateInput,
  CohortCoachChange,
  RoleInfo,
} from '../../types/admin';

/** V23 (UM-FR-29, UM-FR-31, UM-FR-36): the reason every protected change needs. */
export const MIN_REASON = 10;
export const MAX_REASON = 300;

export type CohortOption = { id: number; name: string };

/** Runs the save; shows its error under the form, and keeps what was typed. */
function useSubmit() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (caught) {
      // A closed "Verify it's you" is not an error; the dialog just stays open.
      if (!(caught instanceof Error && caught.name === 'StepUpCancelled')) {
        setError(caught instanceof Error ? caught.message : tr('Something went wrong. Please try again.'));
      }
    } finally {
      setBusy(false);
    }
  }
  return { busy, error, run };
}

function ReasonField({ value, onChange }: Readonly<{ value: string; onChange: (value: string) => void }>) {
  return (
    <TextField
      label={tr('Reason')}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      multiline
      minRows={2}
      fullWidth
      required
      helperText={tr('{min} to {max} characters. Kept in the security log.', { min: MIN_REASON, max: MAX_REASON })}
      slotProps={{ htmlInput: { maxLength: MAX_REASON } }}
    />
  );
}

function reasonOk(reason: string) {
  return reason.trim().length >= MIN_REASON;
}

function DialogButtons({ busy, label, variant = 'primary', disabled, onCancel }: Readonly<{
  busy: boolean; label: string; variant?: 'primary' | 'danger'; disabled?: boolean; onCancel: () => void;
}>) {
  return (
    <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
      <Button type="button" variant="secondary" onClick={onCancel} disabled={busy}>{tr('Cancel')}</Button>
      <Button type="submit" variant={variant} disabled={busy || disabled}>{busy ? tr('Saving...') : label}</Button>
    </Stack>
  );
}

function CohortSelect({ cohorts, value, onChange }: Readonly<{
  cohorts: CohortOption[]; value: number | null; onChange: (value: number | null) => void;
}>) {
  return (
    <TextField select label={tr('Cohort')} value={value ?? ''} fullWidth
      onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}>
      <MenuItem value="">{tr('No cohort')}</MenuItem>
      {cohorts.map((cohort) => <MenuItem key={cohort.id} value={cohort.id}>{cohort.name}</MenuItem>)}
    </TextField>
  );
}

/** UM-FR-22 to UM-FR-25: the person gets an invitation to choose their own password. */
export function AddUserDialog({ roles, cohorts, onSave, onClose }: Readonly<{
  roles: RoleInfo[];
  cohorts: CohortOption[] | null;
  onSave: (input: AdminUserCreateInput) => Promise<void>;
  onClose: () => void;
}>) {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('USER');
  const [cohortId, setCohortId] = useState<number | null>(null);
  const { busy, error, run } = useSubmit();
  const chosen = roles.find((item) => item.key === role);

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(() => onSave({ fullName: fullName.trim(), email: email.trim(), role, cohortId }));
  }

  return (
    <Modal title={tr('Add user')} onClose={onClose}>
      <form onSubmit={submit}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {tr('The person gets an email with a link to choose their own password. The link works once, for 7 days.')}
          </Typography>
          <TextField label={tr('Full name')} value={fullName} onChange={(event) => setFullName(event.target.value)}
            required fullWidth slotProps={{ htmlInput: { maxLength: 150 } }} />
          <TextField label={tr('Email')} type="email" value={email} onChange={(event) => setEmail(event.target.value)}
            required fullWidth slotProps={{ htmlInput: { maxLength: 180 } }} />
          <TextField select label={tr('Role')} value={role} onChange={(event) => setRole(event.target.value)} fullWidth>
            {roles.map((item) => <MenuItem key={item.key} value={item.key}>{tr(item.name)}</MenuItem>)}
          </TextField>
          {chosen?.privileged && (
            <Alert severity="info">
              {tr('This role can do more than use their own plan, so you will be asked for a code, and the person must turn on two-step sign-in.')}
            </Alert>
          )}
          {cohorts && <CohortSelect cohorts={cohorts} value={cohortId} onChange={setCohortId} />}
          {error && <ErrorMessage message={error} />}
          <DialogButtons busy={busy} label={tr('Send invitation')} onCancel={onClose}
            disabled={!fullName.trim() || !email.trim()} />
        </Stack>
      </form>
    </Modal>
  );
}

/** UM-FR-27: name and cohort; the login email cannot change here. */
export function EditUserDialog({ account, cohorts, onSave, onClose }: Readonly<{
  account: AdminUserSummary;
  cohorts: CohortOption[] | null;
  onSave: (input: AdminUserUpdateInput) => Promise<void>;
  onClose: () => void;
}>) {
  const [fullName, setFullName] = useState(account.fullName);
  const [cohortId, setCohortId] = useState<number | null>(account.cohortId ?? null);
  const { busy, error, run } = useSubmit();
  // Keep the current cohort selectable even when it is archived and so not in the list.
  const options = cohorts && account.cohortId && !cohorts.some((cohort) => cohort.id === account.cohortId)
    ? [...cohorts, { id: account.cohortId, name: account.cohortName ?? String(account.cohortId) }]
    : cohorts;

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(() => onSave({ fullName: fullName.trim(), cohortId }));
  }

  return (
    <Modal title={tr('Edit {name}', { name: account.fullName })} onClose={onClose}>
      <form onSubmit={submit}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField label={tr('Full name')} value={fullName} onChange={(event) => setFullName(event.target.value)}
            required fullWidth slotProps={{ htmlInput: { maxLength: 150 } }} />
          <TextField label={tr('Email')} value={account.email} disabled fullWidth
            helperText={tr('The email is how the person signs in, so it cannot be changed here.')} />
          {options && <CohortSelect cohorts={options} value={cohortId} onChange={setCohortId} />}
          {error && <ErrorMessage message={error} />}
          <DialogButtons busy={busy} label={tr('Save')} onCancel={onClose} disabled={!fullName.trim()} />
        </Stack>
      </form>
    </Modal>
  );
}

/** UM-FR-29, UM-FR-30: a new role, a reason, and a new coach for each cohort when the new role cannot coach. */
export function ChangeRoleDialog({ detail, roles, coaches, onSave, onClose }: Readonly<{
  detail: AdminUserDetail;
  roles: RoleInfo[];
  coaches: AdminUserSummary[];
  onSave: (input: AdminRoleChangeInput) => Promise<void>;
  onClose: () => void;
}>) {
  const account = detail.account;
  const choices = roles.filter((role) => role.key !== account.role);
  const [role, setRole] = useState(choices[0]?.key ?? '');
  const [reason, setReason] = useState('');
  const [handover, setHandover] = useState<Record<number, number | null>>({});
  const { busy, error, run } = useSubmit();
  const chosen = roles.find((item) => item.key === role);
  const needsHandover = Boolean(chosen && !chosen.permissions.includes('COACH_COHORTS') && detail.coachedCohorts.length > 0);
  const otherCoaches = coaches.filter((coach) => coach.id !== account.id);

  function submit(event: FormEvent) {
    event.preventDefault();
    const cohortChanges: CohortCoachChange[] | undefined = needsHandover
      ? detail.coachedCohorts.map((cohort) => ({ cohortId: cohort.id, newCoachId: handover[cohort.id] ?? null }))
      : undefined;
    void run(() => onSave({ role, reason: reason.trim(), cohortChanges }));
  }

  return (
    <Modal title={tr('Change role for {name}', { name: account.fullName })} onClose={onClose}>
      <form onSubmit={submit}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2">{tr('Current role: {role}', { role: tr(account.roleName) })}</Typography>
          {choices.length === 0 ? (
            <Alert severity="info">{tr('There is no other role you can give.')}</Alert>
          ) : (
            <TextField select label={tr('New role')} value={role} onChange={(event) => setRole(event.target.value)} fullWidth>
              {choices.map((item) => <MenuItem key={item.key} value={item.key}>{tr(item.name)}</MenuItem>)}
            </TextField>
          )}
          {chosen?.privileged && (
            <Alert severity="info">{tr('The person must turn on two-step sign-in within the grace period.')}</Alert>
          )}
          {needsHandover && (
            <Stack spacing={1.5}>
              <Alert severity="warning">
                {tr('This person coaches active cohorts and the new role cannot coach. Choose a new coach, or none, for each.')}
              </Alert>
              {detail.coachedCohorts.map((cohort) => (
                <TextField key={cohort.id} select label={cohort.name} value={handover[cohort.id] ?? ''} fullWidth
                  onChange={(event) => setHandover({
                    ...handover, [cohort.id]: event.target.value === '' ? null : Number(event.target.value),
                  })}>
                  <MenuItem value="">{tr('No coach')}</MenuItem>
                  {otherCoaches.map((coach) => <MenuItem key={coach.id} value={coach.id}>{coach.fullName}</MenuItem>)}
                </TextField>
              ))}
            </Stack>
          )}
          <ReasonField value={reason} onChange={setReason} />
          {error && <ErrorMessage message={error} />}
          <DialogButtons busy={busy} label={tr('Change role')} onCancel={onClose} disabled={!role || !reasonOk(reason)} />
        </Stack>
      </form>
    </Modal>
  );
}

/** A change that needs only a reason, such as resetting two-step sign-in. */
export function ReasonDialog({ title, text, actionLabel, danger, onSave, onClose }: Readonly<{
  title: string;
  text: string;
  actionLabel: string;
  danger?: boolean;
  onSave: (reason: string) => Promise<void>;
  onClose: () => void;
}>) {
  const [reason, setReason] = useState('');
  const { busy, error, run } = useSubmit();

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(() => onSave(reason.trim()));
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={submit}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2">{text}</Typography>
          <ReasonField value={reason} onChange={setReason} />
          {error && <ErrorMessage message={error} />}
          <DialogButtons busy={busy} label={actionLabel} variant={danger ? 'danger' : 'primary'}
            onCancel={onClose} disabled={!reasonOk(reason)} />
        </Stack>
      </form>
    </Modal>
  );
}

/** UM-FR-31, UM-FR-33: block or unblock, with a reason the person is not told (Q-04). */
export function BlockDialog({ account, unblock, onSave, onClose }: Readonly<{
  account: AdminUserSummary;
  unblock: boolean;
  onSave: (reason: string) => Promise<void>;
  onClose: () => void;
}>) {
  return (
    <ReasonDialog
      title={unblock ? tr('Unblock {name}', { name: account.fullName }) : tr('Block {name}', { name: account.fullName })}
      text={unblock
        ? tr('The person can sign in again. Their old sessions stay closed.')
        : tr('The person is signed out everywhere and cannot sign in. Their share links stop working. Their data is kept. They get an email that does not include the reason.')}
      actionLabel={unblock ? tr('Unblock') : tr('Block')}
      danger={!unblock}
      onSave={onSave}
      onClose={onClose}
    />
  );
}

/** Plain names for the record counts the server sends. */
const COUNT_LABELS: Record<string, string> = {
  visionAreas: 'Vision areas', dreams: 'Dreams', goals: 'Goals', steps: 'Steps', tasks: 'Tasks',
};

export function countLabel(key: string) {
  return tr(COUNT_LABELS[key] ?? key);
}

/** UM-FR-35 to UM-FR-38: only for a blocked account; the email typed exactly, a reason, a fresh code. */
export function DeleteUserDialog({ detail, onSave, onClose }: Readonly<{
  detail: AdminUserDetail;
  onSave: (confirmEmail: string, reason: string) => Promise<void>;
  onClose: () => void;
}>) {
  const account = detail.account;
  const [confirmEmail, setConfirmEmail] = useState('');
  const [reason, setReason] = useState('');
  const { busy, error, run } = useSubmit();
  const matches = confirmEmail.trim().toLowerCase() === account.email.toLowerCase();

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(() => onSave(confirmEmail.trim(), reason.trim()));
  }

  return (
    <Modal title={tr('Delete {name} permanently', { name: account.fullName })} onClose={onClose}>
      <form onSubmit={submit}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="error">{tr('This cannot be undone.')}</Alert>
          <div>
            <Typography variant="subtitle2">{tr('Erased')}</Typography>
            <Typography variant="body2">
              {Object.entries(detail.recordCounts).map(([key, count]) => `${countLabel(key)}: ${count}`).join(' · ')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {tr('Also erased: journals, reviews, partners, messages, reports and every other record the person owns.')}
            </Typography>
          </div>
          <div>
            <Typography variant="subtitle2">{tr('Kept without the person\'s name')}</Typography>
            <Typography variant="body2" color="text.secondary">
              {tr('AI usage costs, and security log entries with the account number only.')}
            </Typography>
          </div>
          {detail.coachedCohorts.length > 0 && (
            <Alert severity="warning">
              {tr('These cohorts will have no coach: {names}', { names: detail.coachedCohorts.map((cohort) => cohort.name).join(', ') })}
            </Alert>
          )}
          <TextField label={tr('Type {email} to confirm', { email: account.email })} value={confirmEmail}
            onChange={(event) => setConfirmEmail(event.target.value)} fullWidth required autoComplete="off" />
          <ReasonField value={reason} onChange={setReason} />
          {error && <ErrorMessage message={error} />}
          <DialogButtons busy={busy} label={tr('Delete permanently')} variant="danger" onCancel={onClose}
            disabled={!matches || !reasonOk(reason)} />
        </Stack>
      </form>
    </Modal>
  );
}
