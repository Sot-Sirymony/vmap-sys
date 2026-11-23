import { useEffect, useMemo, useState } from 'react';
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
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { tr } from '../i18n/translate';
import { can } from '../utils/permissions';
import {
  SECURITY_CATALOGUE, SECURITY_GROUPS, type InVms, type SecurityFamily, type SecurityGroup, type SecurityItem,
} from '../utils/securityCatalogue';
import {
  changedItems, countAllowed, defaultState, mfaWarning, needsDeprecatedConfirm, setMany, setOne,
  type ChangeResult, type SecurityState,
} from '../utils/securityRules';
import { PageSection } from './PageSection';

type FamilyFilter = 'ALL' | SecurityFamily;
type StatusFilter = 'ALL' | 'ALLOWED' | 'BLOCKED';

const FAMILY_LABEL: Record<SecurityFamily, string> = { AUTHN: 'Authentication', AUTHZ: 'Authorization' };
const FAMILY_SHORT: Record<SecurityFamily, string> = { AUTHN: 'AuthN', AUTHZ: 'AuthZ' };
const IN_VMS_LABEL: Record<InVms, string> = { yes: 'In VMS today', partly: 'Partly in VMS', no: 'Not in VMS' };

/**
 * V24.0.0.0 (BRD "Security Access Control"): a front-end prototype where an
 * Admin chooses which security methods other users may use. Nothing is stored
 * and nothing is enforced; reloading the page resets it. Admin only (the page
 * is gated on "Manage roles", which only the Admin role holds, D-23-9).
 */
export function SecurityAccessPage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [saved, setSaved] = useState<SecurityState>(() => defaultState());
  const [draft, setDraft] = useState<SecurityState>(() => defaultState());
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [family, setFamily] = useState<FamilyFilter>('ALL');
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const [confirmDeprecated, setConfirmDeprecated] = useState<SecurityItem | null>(null);
  const [showSummary, setShowSummary] = useState(false);

  const changes = useMemo(() => changedItems(saved, draft), [saved, draft]);
  const pending = changes.allowed.length + changes.blocked.length;

  // FR-15: closing or reloading the tab with unsaved changes asks first.
  useEffect(() => {
    if (pending === 0) return undefined;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [pending]);

  if (!can(user, 'MANAGE_ROLES')) {
    return (
      <PageSection title={tr('Security access')} subtitle={tr('Choose which security methods other users may use.')}>
        <ErrorMessage message={tr('Only an admin can open this page.')} />
      </PageSection>
    );
  }

  const apply = (result: ChangeResult) => {
    setDraft(result.state);
    setNotice(result.notice ? tr(result.notice.text, result.notice.values) : '');
  };

  const toggle = (item: SecurityItem, allowed: boolean) => {
    if (needsDeprecatedConfirm(item.id, allowed)) {
      setConfirmDeprecated(item);
      return;
    }
    apply(setOne(draft, item.id, allowed));
  };

  const query = search.trim().toLowerCase();
  const visible = (item: SecurityItem) =>
    (family === 'ALL' || item.family === family)
    && (status === 'ALL' || (status === 'ALLOWED') === Boolean(draft[item.id]))
    && (query === '' || [item.id, item.name, item.description].some((text) => text.toLowerCase().includes(query)));

  const authn = SECURITY_CATALOGUE.filter((item) => item.family === 'AUTHN');
  const authz = SECURITY_CATALOGUE.filter((item) => item.family === 'AUTHZ');
  const allIds = SECURITY_CATALOGUE.map((item) => item.id);

  const saveChanges = () => {
    setSaved(draft);
    setShowSummary(false);
    setNotice('');
    showToast(tr('Saved in this prototype only. Nothing is stored; reloading the page resets it.'), { duration: 6000 });
  };

  return (
    <PageSection
      title={tr('Security access')}
      subtitle={tr('Choose which security methods other users may use. Your own sign-in methods are not changed here.')}
      actions={<Chip color="warning" label={tr('Draft prototype')} />}
    >
      <Stack spacing={2} sx={{ pb: pending > 0 ? 10 : 0 }}>
        <Alert severity="info">
          {tr('This is a prototype for requirement discussion (BRD V24.0.0.0). Nothing is saved to the server and nothing is enforced at sign-in.')}
        </Alert>

        {/* Summary cards (FR-07) */}
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' } }}>
          <SummaryCard label={tr('Allowed in total')} value={countAllowed(draft, SECURITY_CATALOGUE)} total={SECURITY_CATALOGUE.length} />
          <SummaryCard label={tr('Authentication allowed')} value={countAllowed(draft, authn)} total={authn.length} />
          <SummaryCard label={tr('Authorization allowed')} value={countAllowed(draft, authz)} total={authz.length} />
        </Box>

        {notice && <Alert severity="error" onClose={() => setNotice('')}>{notice}</Alert>}
        {mfaWarning(draft) && (
          <Alert severity="warning">
            {tr('Multi-factor (MFA / 2FA) is allowed, but fewer than two "By factor" items are allowed. Allow at least two factors.')}
          </Alert>
        )}

        {/* Toolbar (FR-06, FR-08, FR-09, FR-14) */}
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} useFlexGap sx={{ alignItems: { md: 'center' }, flexWrap: 'wrap' }}>
          <TextField
            size="small"
            label={tr('Search')}
            placeholder={tr('Name, ID or description')}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            sx={{ minWidth: 220, flex: 1 }}
          />
          <TextField select size="small" label={tr('Family')} value={family} onChange={(event) => setFamily(event.target.value as FamilyFilter)} sx={{ minWidth: 160 }}>
            <MenuItem value="ALL">{tr('All')}</MenuItem>
            <MenuItem value="AUTHN">{tr('Authentication')}</MenuItem>
            <MenuItem value="AUTHZ">{tr('Authorization')}</MenuItem>
          </TextField>
          <TextField select size="small" label={tr('Status')} value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)} sx={{ minWidth: 140 }}>
            <MenuItem value="ALL">{tr('All')}</MenuItem>
            <MenuItem value="ALLOWED">{tr('Allowed')}</MenuItem>
            <MenuItem value="BLOCKED">{tr('Blocked')}</MenuItem>
          </TextField>
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <Button variant="secondary" onClick={() => apply(setMany(draft, allIds, true))}>{tr('Allow all')}</Button>
            <Button variant="secondary" onClick={() => apply(setMany(draft, allIds, false))}>{tr('Block all')}</Button>
            <Button variant="secondary" onClick={() => apply({ state: defaultState() })}>{tr('Reset to default')}</Button>
          </Stack>
        </Stack>

        {SECURITY_GROUPS.map((group) => {
          const items = SECURITY_CATALOGUE.filter((item) => item.group === group.name);
          const shown = items.filter(visible);
          if (shown.length === 0) return null;
          return (
            <GroupCard
              key={group.name}
              group={group}
              items={items}
              shown={shown}
              draft={draft}
              saved={saved}
              collapsed={Boolean(collapsed[group.name])}
              onCollapse={() => setCollapsed({ ...collapsed, [group.name]: !collapsed[group.name] })}
              onGroup={(allowed) => apply(setMany(draft, items.map((item) => item.id), allowed))}
              onToggle={toggle}
            />
          );
        })}
        {SECURITY_CATALOGUE.every((item) => !visible(item)) && (
          <Typography color="text.secondary">{tr('No security items match the search and filters.')}</Typography>
        )}
      </Stack>

      {/* Unsaved-changes bar (FR-11, FR-12) */}
      {pending > 0 && (
        <Paper
          elevation={6}
          role="region"
          aria-label={tr('Unsaved changes')}
          sx={{ position: 'fixed', bottom: 16, left: 16, right: 16, zIndex: (theme) => theme.zIndex.appBar, p: 1.5,
            display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', justifyContent: 'space-between' }}
        >
          <Typography>{tr('{count} unsaved change(s)', { count: pending })}</Typography>
          <Stack direction="row" spacing={1}>
            <Button variant="secondary" onClick={() => { setDraft(saved); setNotice(''); }}>{tr('Discard')}</Button>
            <Button onClick={() => setShowSummary(true)}>{tr('Save changes')}</Button>
          </Stack>
        </Paper>
      )}

      {/* Save summary (FR-13) */}
      {showSummary && (
        <Dialog open onClose={() => setShowSummary(false)} maxWidth="sm" fullWidth>
          <DialogTitle>{tr('Save these changes?')}</DialogTitle>
          <DialogContent>
            <ChangeList title={tr('Will be allowed')} items={changes.allowed} />
            <ChangeList title={tr('Will be blocked')} items={changes.blocked} />
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={saveChanges}>{tr('Confirm and save')}</Button>
            <Button variant="secondary" onClick={() => setShowSummary(false)}>{tr('Cancel')}</Button>
          </DialogActions>
        </Dialog>
      )}

      {/* Deprecated warning (BR-03) */}
      {confirmDeprecated && (
        <Dialog open onClose={() => setConfirmDeprecated(null)} maxWidth="xs" fullWidth>
          <DialogTitle>{tr('Allow a deprecated method?')}</DialogTitle>
          <DialogContent>
            <Typography>
              {tr('{name} is deprecated and no longer considered safe. Allow it only if you have no alternative.', { name: confirmDeprecated.name })}
            </Typography>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button variant="secondary" onClick={() => setConfirmDeprecated(null)}>{tr('Keep blocked')}</Button>
            <Button variant="danger" onClick={() => { apply(setOne(draft, confirmDeprecated.id, true)); setConfirmDeprecated(null); }}>
              {tr('Allow anyway')}
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </PageSection>
  );
}

function SummaryCard({ label, value, total }: { label: string; value: number; total: number }) {
  return (
    <Card>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        <Typography variant="h5" component="p">{tr('{value} of {total}', { value, total })}</Typography>
      </CardContent>
    </Card>
  );
}

type GroupCardProps = {
  group: SecurityGroup;
  items: SecurityItem[];
  shown: SecurityItem[];
  draft: SecurityState;
  saved: SecurityState;
  collapsed: boolean;
  onCollapse: () => void;
  onGroup: (allowed: boolean) => void;
  onToggle: (item: SecurityItem, allowed: boolean) => void;
};

function GroupCard({ group, items, shown, draft, saved, collapsed, onCollapse, onGroup, onToggle }: GroupCardProps) {
  const headingId = `security-group-${group.name.replace(/\W+/g, '-').toLowerCase()}`;
  return (
    <Card component="section" aria-labelledby={headingId}>
      <CardContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
            <IconButton
              size="small"
              onClick={onCollapse}
              aria-expanded={!collapsed}
              aria-label={collapsed ? tr('Expand {name}', { name: tr(group.name) }) : tr('Collapse {name}', { name: tr(group.name) })}
            >
              {collapsed ? <ChevronRight size={18} /> : <ChevronDown size={18} />}
            </IconButton>
            <Chip size="small" color={group.family === 'AUTHN' ? 'primary' : 'secondary'} label={FAMILY_SHORT[group.family]} />
            <Typography id={headingId} variant="h6" component="h2">{tr(group.name)}</Typography>
            <Typography variant="body2" color="text.secondary">
              {tr('{value} of {total} allowed', { value: countAllowed(draft, items), total: items.length })}
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1}>
            <Button variant="secondary" onClick={() => onGroup(true)}>{tr('Allow group')}</Button>
            <Button variant="secondary" onClick={() => onGroup(false)}>{tr('Block group')}</Button>
          </Stack>
        </Stack>
        <Collapse in={!collapsed}>
          <Stack divider={<Divider flexItem />} sx={{ mt: 1 }}>
            {shown.map((item) => (
              <ItemRow key={item.id} item={item} allowed={Boolean(draft[item.id])} unsaved={saved[item.id] !== draft[item.id]} onToggle={onToggle} />
            ))}
          </Stack>
        </Collapse>
      </CardContent>
    </Card>
  );
}

function ItemRow({ item, allowed, unsaved, onToggle }: { item: SecurityItem; allowed: boolean; unsaved: boolean; onToggle: GroupCardProps['onToggle'] }) {
  const statusText = allowed ? tr('Allowed') : tr('Blocked');
  return (
    <Stack direction="row" spacing={1.5} sx={{ py: 1, alignItems: 'flex-start' }}>
      <Switch
        checked={allowed}
        onChange={(event) => onToggle(item, event.target.checked)}
        slotProps={{ input: { 'aria-label': tr('{name}: {status}', { name: item.name, status: statusText }) } }}
      />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography sx={{ fontWeight: 600 }}>{item.name}</Typography>
          <Typography variant="caption" color="text.secondary">{item.id}</Typography>
          {item.tag && <Chip size="small" color={item.tag === 'Deprecated' ? 'error' : 'default'} variant="outlined" label={tr(item.tag)} />}
          <Chip size="small" variant="outlined" color={item.inVms === 'yes' ? 'success' : 'default'} label={tr(IN_VMS_LABEL[item.inVms])} />
          {unsaved && <Chip size="small" color="warning" label={tr('Unsaved')} />}
        </Stack>
        <Typography variant="body2" color="text.secondary">{item.description}</Typography>
      </Box>
      <Typography variant="body2" sx={{ minWidth: 64, textAlign: 'right', color: allowed ? 'success.main' : 'text.secondary' }}>
        {statusText}
      </Typography>
    </Stack>
  );
}

function ChangeList({ title, items }: { title: string; items: SecurityItem[] }) {
  if (items.length === 0) return null;
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2">{title} ({items.length})</Typography>
      <Box component="ul" sx={{ m: 0, pl: 3 }}>
        {items.map((item) => <li key={item.id}><Typography variant="body2">{item.id} · {item.name} ({tr(FAMILY_LABEL[item.family])})</Typography></li>)}
      </Box>
    </Box>
  );
}
