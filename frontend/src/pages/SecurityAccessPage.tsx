import { useCallback, useEffect, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { getSecurityAccess, updateSecurityAccess } from '../api/securityAccessApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { StepUpMailWarning } from '../components/step-up/StepUpMailWarning';
import { useStepUp } from '../components/step-up/useStepUp';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { formatDate } from '../i18n/formatDate';
import { tr } from '../i18n/translate';
import type { MethodUsage, SecurityAccess, TwoFactorScope } from '../types/securityAccess';
import { can } from '../utils/permissions';
import { SECURITY_CATALOGUE, SECURITY_GROUPS, type InVms, type SecurityFamily, type SecurityItem } from '../utils/securityCatalogue';
import { PageSection } from './PageSection';

type Draft = { passwordSignIn: boolean; googleSignIn: boolean; twoFactorScope: TwoFactorScope };
type FamilyFilter = 'ALL' | SecurityFamily;

const MIN_REASON = 10;
const FAMILY_SHORT: Record<SecurityFamily, string> = { AUTHN: 'AuthN', AUTHZ: 'AuthZ' };
const IN_VMS_LABEL: Record<InVms, string> = { yes: 'In VMS today', partly: 'Partly in VMS', no: 'Not in VMS' };

function draftOf(settings: SecurityAccess): Draft {
  return { passwordSignIn: settings.passwordSignIn, googleSignIn: settings.googleSignIn, twoFactorScope: settings.twoFactorScope };
}

/** What a draft changes, in plain words, with its consequences (SA-FR-09). */
function describeChanges(saved: SecurityAccess, draft: Draft): string[] {
  const lines: string[] = [];
  if (saved.passwordSignIn !== draft.passwordSignIn) {
    lines.push(draft.passwordSignIn ? tr('Turn password sign-in on.')
      : tr('Turn password sign-in off. {count} people signed in with a password will be signed out.', { count: saved.password.openSessions }));
  }
  if (saved.googleSignIn !== draft.googleSignIn) {
    lines.push(draft.googleSignIn ? tr('Turn Google sign-in on.')
      : tr('Turn Google sign-in off. {count} people signed in with Google will be signed out.', { count: saved.google.openSessions }));
  }
  if (saved.twoFactorScope !== draft.twoFactorScope) {
    lines.push(draft.twoFactorScope === 'EVERYONE'
      ? tr('Require two-step sign-in for everyone. People without it get 14 days to set it up.')
      : tr('Require two-step sign-in for privileged roles only. Participants who have not set it up no longer need to.'));
  }
  return lines;
}

/**
 * V24 (BRD 24.0.0): the sign-in methods an Admin allows for everyone else
 * (SA-FR-01 to SA-FR-09), and the security catalogue from the 24.0.0.0
 * prototype as a read-only reference (SA-FR-04). Admins change; roles with
 * "View security log" read (D-24-2). Admins are never affected (D-24-5).
 */
export function SecurityAccessPage() {
  const { token, user } = useAuth();
  const { showToast } = useToast();
  const stepUp = useStepUp(token);
  const [settings, setSettings] = useState<SecurityAccess | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const allowed = can(user, 'MANAGE_ROLES') || can(user, 'VIEW_SECURITY_LOG');

  const load = useCallback(async () => {
    if (!token || !allowed) return;
    setLoading(true);
    setError('');
    try {
      const next = await getSecurityAccess(token);
      setSettings(next);
      setDraft(draftOf(next));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : tr('Could not load the sign-in methods.'));
    } finally {
      setLoading(false);
    }
  }, [token, allowed]);

  useEffect(() => {
    void load();
  }, [load]);

  const changes = useMemo(() => (settings && draft ? describeChanges(settings, draft) : []), [settings, draft]);

  // Closing or reloading the tab with unsaved changes asks first.
  useEffect(() => {
    if (changes.length === 0) return undefined;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [changes.length]);

  const subtitle = tr('Choose how everyone except admins may sign in. Admins can always sign in with a password.');
  if (!allowed) {
    return (
      <PageSection title={tr('Security access')} subtitle={subtitle}>
        <ErrorMessage message={tr('Only an admin can open this page.')} />
      </PageSection>
    );
  }

  const readOnly = !settings?.canChange;
  const bothOff = draft !== null && !draft.passwordSignIn && !draft.googleSignIn;

  return (
    <PageSection title={tr('Security access')} subtitle={subtitle}
      actions={readOnly && settings ? <Chip label={tr('View only')} /> : undefined}>
      <StepUpMailWarning token={token} />
      <Stack spacing={2} sx={{ pb: changes.length > 0 ? 10 : 0 }}>
        {error && <ErrorMessage message={error} />}
        {loading && !settings ? <Loading /> : settings && draft && (
          <>
            <Card component="section" aria-labelledby="sign-in-methods-heading">
              <CardContent>
                <Typography id="sign-in-methods-heading" variant="h6" component="h2">{tr('Sign-in methods')}</Typography>
                <Stack divider={<Divider flexItem />} sx={{ mt: 1 }}>
                  <MethodRow
                    label={tr('Password sign-in')}
                    description={tr('Email address and password.')}
                    checked={draft.passwordSignIn}
                    disabled={readOnly}
                    usage={settings.password}
                    onlyWayText={tr('{count} can only sign in with a password', { count: settings.password.onlyWay })}
                    onChange={(value) => setDraft({ ...draft, passwordSignIn: value })}
                  />
                  <MethodRow
                    label={tr('Google sign-in')}
                    description={tr('Sign in with a Google account.')}
                    checked={draft.googleSignIn}
                    disabled={readOnly}
                    usage={settings.google}
                    onlyWayText={tr('{count} can only sign in with Google', { count: settings.google.onlyWay })}
                    note={settings.googleAvailable ? undefined : tr('Not set up on this server')}
                    onChange={(value) => setDraft({ ...draft, googleSignIn: value })}
                  />
                </Stack>
                {bothOff && <Alert severity="error" sx={{ mt: 1 }}>{tr('At least one sign-in method must stay on.')}</Alert>}
                {settings.passwordSignIn && !draft.passwordSignIn && settings.password.onlyWay > 0 && (
                  <Alert severity="warning" sx={{ mt: 1 }}>
                    {tr('{count} people can only sign in with a password. Each must link Google from their Account page first, or the change will be refused.',
                      { count: settings.password.onlyWay })}
                  </Alert>
                )}
                {settings.googleSignIn && !draft.googleSignIn && settings.google.onlyWay > 0 && (
                  <Alert severity="warning" sx={{ mt: 1 }}>
                    {tr('{count} people can only sign in with Google. Send each a password reset from the Users page first, or the change will be refused.',
                      { count: settings.google.onlyWay })}
                  </Alert>
                )}
              </CardContent>
            </Card>

            <Card component="section" aria-labelledby="two-step-heading">
              <CardContent>
                <Typography id="two-step-heading" variant="h6" component="h2">{tr('Two-step sign-in required for')}</Typography>
                <RadioGroup
                  value={draft.twoFactorScope}
                  onChange={(event) => setDraft({ ...draft, twoFactorScope: event.target.value as TwoFactorScope })}
                >
                  <FormControlLabel value="PRIVILEGED" disabled={readOnly} control={<Radio />}
                    label={tr('Privileged roles: coaches, admins and roles with admin permissions')} />
                  <FormControlLabel value="EVERYONE" disabled={readOnly} control={<Radio />}
                    label={tr('Everyone: people without it get 14 days to set it up')} />
                </RadioGroup>
              </CardContent>
            </Card>

            {settings.updatedBy && (
              <Typography variant="body2" color="text.secondary">
                {tr('Last changed by {name} on {date}.', { name: settings.updatedBy, date: formatDate(new Date(settings.updatedAt), { year: true }) })}
              </Typography>
            )}
          </>
        )}

        <CatalogueReference />
      </Stack>

      {settings && draft && changes.length > 0 && (
        <Paper
          elevation={6}
          role="region"
          aria-label={tr('Unsaved changes')}
          sx={{ position: 'fixed', bottom: 16, left: 16, right: 16, zIndex: (theme) => theme.zIndex.appBar, p: 1.5,
            display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', justifyContent: 'space-between' }}
        >
          <Typography>{tr('{count} unsaved change(s)', { count: changes.length })}</Typography>
          <Stack direction="row" spacing={1}>
            <Button variant="secondary" onClick={() => setDraft(draftOf(settings))}>{tr('Discard')}</Button>
            <Button onClick={() => setSaving(true)} disabled={bothOff}>{tr('Save changes')}</Button>
          </Stack>
        </Paper>
      )}

      {saving && token && settings && draft && (
        <SaveDialog
          changes={changes}
          onClose={() => setSaving(false)}
          onSave={async (reason) => {
            const saved = await stepUp.protect('ADMIN_SECURITY_ACCESS', (code) => updateSecurityAccess(token, { ...draft, reason }, code));
            setSettings(saved);
            setDraft(draftOf(saved));
            setSaving(false);
            showToast(tr('Sign-in methods saved.'));
          }}
        />
      )}
      {stepUp.dialog}
    </PageSection>
  );
}

type MethodRowProps = {
  label: string;
  description: string;
  checked: boolean;
  disabled: boolean;
  usage: MethodUsage;
  onlyWayText: string;
  note?: string;
  onChange: (value: boolean) => void;
};

function MethodRow({ label, description, checked, disabled, usage, onlyWayText, note, onChange }: MethodRowProps) {
  const status = checked ? tr('On') : tr('Off');
  return (
    <Stack direction="row" spacing={1.5} sx={{ py: 1.5, alignItems: 'flex-start' }}>
      <Switch
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        slotProps={{ input: { 'aria-label': tr('{name}: {status}', { name: label, status }) } }}
      />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography sx={{ fontWeight: 600 }}>{label}</Typography>
          {note && <Chip size="small" color="warning" variant="outlined" label={note} />}
        </Stack>
        <Typography variant="body2" color="text.secondary">{description}</Typography>
        <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap', mt: 0.75 }}>
          <Chip size="small" variant="outlined" label={tr('{count} people use it', { count: usage.accounts })} />
          <Chip size="small" variant="outlined" label={onlyWayText} />
          <Chip size="small" variant="outlined" label={tr('{count} signed in now', { count: usage.openSessions })} />
        </Stack>
      </Box>
      <Typography variant="body2" sx={{ minWidth: 40, textAlign: 'right', color: checked ? 'success.main' : 'text.secondary' }}>
        {status}
      </Typography>
    </Stack>
  );
}

function SaveDialog({ changes, onClose, onSave }: { changes: string[]; onClose: () => void; onSave: (reason: string) => Promise<void> }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const short = reason.trim().length < MIN_REASON;

  const submit = async () => {
    setBusy(true);
    setError('');
    try {
      await onSave(reason.trim());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : tr('Could not save the sign-in methods.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{tr('Save these changes?')}</DialogTitle>
      <DialogContent>
        <Box component="ul" sx={{ mt: 0, pl: 3 }}>
          {changes.map((line) => <li key={line}><Typography variant="body2">{line}</Typography></li>)}
        </Box>
        <TextField
          label={tr('Reason')}
          helperText={tr('At least {count} characters. Kept in the security log.', { count: MIN_REASON })}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          fullWidth
          multiline
          minRows={2}
          slotProps={{ htmlInput: { maxLength: 300 } }}
        />
        {error && <Box sx={{ mt: 2 }}><ErrorMessage message={error} /></Box>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={() => void submit()} disabled={short || busy}>{busy ? tr('Saving...') : tr('Confirm and save')}</Button>
        <Button variant="secondary" onClick={onClose}>{tr('Cancel')}</Button>
      </DialogActions>
    </Dialog>
  );
}

/** SA-FR-04: the 24.0.0.0 catalogue, for reference only; methods VMS lacks have no switch (D-24-1). */
function CatalogueReference() {
  const [search, setSearch] = useState('');
  const [family, setFamily] = useState<FamilyFilter>('ALL');
  const [open, setOpen] = useState(false);
  const query = search.trim().toLowerCase();
  const visible = (item: SecurityItem) => (family === 'ALL' || item.family === family)
    && (query === '' || [item.id, item.name, item.description].some((text) => text.toLowerCase().includes(query)));

  return (
    <Card component="section" aria-labelledby="catalogue-heading">
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <IconButton size="small" onClick={() => setOpen(!open)} aria-expanded={open}
            aria-label={open ? tr('Hide the security methods reference') : tr('Show the security methods reference')}>
            {open ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
          </IconButton>
          <Typography id="catalogue-heading" variant="h6" component="h2">{tr('Security methods reference')}</Typography>
        </Stack>
        <Typography variant="body2" color="text.secondary">
          {tr('Common authentication and authorization methods, and whether VMS has them. Only the methods above can be switched.')}
        </Typography>
        <Collapse in={open}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ my: 2 }}>
            <TextField size="small" label={tr('Search')} placeholder={tr('Name, ID or description')} value={search}
              onChange={(event) => setSearch(event.target.value)} sx={{ flex: 1 }} />
            <TextField select size="small" label={tr('Family')} value={family}
              onChange={(event) => setFamily(event.target.value as FamilyFilter)} sx={{ minWidth: 180 }}>
              <MenuItem value="ALL">{tr('All')}</MenuItem>
              <MenuItem value="AUTHN">{tr('Authentication')}</MenuItem>
              <MenuItem value="AUTHZ">{tr('Authorization')}</MenuItem>
            </TextField>
          </Stack>
          {SECURITY_GROUPS.map((group) => {
            const items = SECURITY_CATALOGUE.filter((item) => item.group === group.name && visible(item));
            if (items.length === 0) return null;
            return (
              <Box key={group.name} sx={{ mb: 2 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <Chip size="small" color={group.family === 'AUTHN' ? 'primary' : 'secondary'} label={FAMILY_SHORT[group.family]} />
                  <Typography variant="subtitle1" component="h3">{tr(group.name)}</Typography>
                </Stack>
                <Stack divider={<Divider flexItem />}>
                  {items.map((item) => (
                    <Box key={item.id} sx={{ py: 0.75 }} data-testid="catalogue-item">
                      <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                        <Typography sx={{ fontWeight: 600 }}>{item.name}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.id}</Typography>
                        {item.tag && <Chip size="small" variant="outlined" color={item.tag === 'Deprecated' ? 'error' : 'default'} label={tr(item.tag)} />}
                        <Chip size="small" variant="outlined" color={item.inVms === 'yes' ? 'success' : 'default'} label={tr(IN_VMS_LABEL[item.inVms])} />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">{item.description}</Typography>
                    </Box>
                  ))}
                </Stack>
              </Box>
            );
          })}
        </Collapse>
      </CardContent>
    </Card>
  );
}
