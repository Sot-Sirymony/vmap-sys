import { type FormEvent, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import {
  type ApprovedResourceInput,
  createApprovedResource,
  deleteApprovedResource,
  listApprovedResources,
  updateApprovedResource,
} from '../../api/aiAdminApi';
import { tr } from '../../i18n/translate';
import type { ApprovedResource } from '../../types/vision';
import { Button } from '../common/Button';
import { safeHttpUrl } from '../../utils/safeUrl';

const KINDS = ['COURSE', 'BOOK', 'TOOL', 'ORGANISATION', 'FUNDING', 'PERSON', 'OTHER'];
const KIND_LABELS: Record<string, string> = {
  COURSE: 'Course', BOOK: 'Book', TOOL: 'Tool', ORGANISATION: 'Organisation', FUNDING: 'Funding', PERSON: 'Person to ask', OTHER: 'Other',
};
const EMPTY: ApprovedResourceInput = { kind: 'COURSE', name: '', url: '', description: '', active: true };

/**
 * V9 (AI-FR-26): resources an admin has checked. Resource ideas use these
 * first, and only these may show a link; anything else stays "check before use".
 */
export function ApprovedResourcesCard({ token }: { token: string }) {
  const [resources, setResources] = useState<ApprovedResource[]>([]);
  const [input, setInput] = useState<ApprovedResourceInput>(EMPTY);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listApprovedResources(token).then(setResources).catch(() => setResources([]));
  }, [token]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
      setResources(await listApprovedResources(token));
    } catch (actionError) {
      setError(actionError instanceof Error ? tr(actionError.message) : tr('Unable to save this.'));
    } finally {
      setBusy(false);
    }
  }

  function handleAdd(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      await createApprovedResource(token, { ...input, name: input.name.trim(), url: input.url.trim() });
      setInput(EMPTY);
    });
  }

  const asInput = (resource: ApprovedResource): ApprovedResourceInput => ({
    kind: resource.kind, name: resource.name, url: resource.url ?? '', description: resource.description ?? '', active: resource.active,
  });

  return (
    <Card>
      <CardHeader
        title={tr('Approved resources')}
        subheader={tr('Resources you have checked. AI resource ideas use these first, and only these can show a link.')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          {resources.length === 0 && <Typography variant="body2" color="text.secondary">{tr('No approved resources yet.')}</Typography>}
          {resources.map((resource) => (
            <Box key={resource.id} sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', opacity: resource.active ? 1 : 0.6 }}>
              <Chip size="small" label={tr(KIND_LABELS[resource.kind] ?? 'Other')} />
              {safeHttpUrl(resource.url)
                ? <Link href={safeHttpUrl(resource.url)} target="_blank" rel="noopener noreferrer" sx={{ fontWeight: 600 }}>{resource.name}</Link>
                : <Typography variant="body2" sx={{ fontWeight: 600 }}>{resource.name}</Typography>}
              {resource.description && <Typography variant="caption" color="text.secondary">{resource.description}</Typography>}
              <FormControlLabel
                control={<Switch size="small" checked={resource.active} disabled={busy}
                  onChange={(event) => void run(async () => { await updateApprovedResource(token, resource.id, { ...asInput(resource), active: event.target.checked }); })} />}
                label={tr('Offered')}
              />
              <Button variant="secondary" disabled={busy}
                onClick={() => { if (window.confirm(tr('Remove {name} from the approved list?', { name: resource.name }))) { void run(() => deleteApprovedResource(token, resource.id)); } }}>
                {tr('Remove')}
              </Button>
            </Box>
          ))}
          <Box component="form" onSubmit={handleAdd} sx={{ display: 'grid', gap: 1, gridTemplateColumns: { xs: '1fr', sm: '160px 1fr' } }}>
            <Select size="small" value={input.kind} onChange={(event) => setInput({ ...input, kind: event.target.value })}
              SelectDisplayProps={{ 'aria-label': tr('Kind') }}>
              {KINDS.map((kind) => <MenuItem key={kind} value={kind}>{tr(KIND_LABELS[kind])}</MenuItem>)}
            </Select>
            <TextField size="small" label={tr('Name')} value={input.name} required onChange={(event) => setInput({ ...input, name: event.target.value })}
              slotProps={{ htmlInput: { maxLength: 200 } }} />
            <TextField size="small" label={tr('Link (optional)')} placeholder="https://" value={input.url}
              onChange={(event) => setInput({ ...input, url: event.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />
            <TextField size="small" label={tr('What it is for (optional)')} value={input.description}
              onChange={(event) => setInput({ ...input, description: event.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />
            <Box><Button type="submit" disabled={busy || !input.name.trim()}>{tr('Add resource')}</Button></Box>
          </Box>
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </CardContent>
    </Card>
  );
}
