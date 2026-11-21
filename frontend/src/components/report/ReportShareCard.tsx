import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { Link2 } from 'lucide-react';
import { createReportShare, listReportShares, revokeReportShare } from '../../api/savedReportApi';
import { tr } from '../../i18n/translate';
import type { ReportSection, ReportShare } from '../../types/vision';
import { ALL_SECTIONS, SECTION_LABELS, reportDate } from '../../utils/managementReport';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Select } from '../common/Select';

/** RM-FR-46: the expiry choices, in days; 30 is the default. */
export const EXPIRY_DAYS = [7, 30, 90] as const;
/** RM-FR-47: what a reviewer sees unless the participant changes it. */
export const DEFAULT_SHARE_SECTIONS: ReportSection[] = ['COMMENTARY', 'GOAL_HEALTH', 'CHANGES'];

/** The address a reviewer opens. */
export function shareUrl(token: string) {
  return `${window.location.origin}/shared-report/${token}`;
}

function dayAfter(days: number) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function linkState(link: ReportShare) {
  if (link.revokedAt) return tr('Stopped');
  return link.open ? tr('Open') : tr('Expired');
}

/**
 * V19 (BRD 19.0.0, RM-FR-46 to RM-FR-48): read-only links to a final report.
 * Making one first shows exactly which sections the reviewer will see and
 * when the link ends, and asks to confirm; the link itself is shown once. Each
 * link shows its views and can be stopped at once. onOpenLinks reports how
 * many links are open, so archiving can warn that they will stop (RM-FR-51).
 */
export function ReportShareCard({ token, reportId, disabledReason, onOpenLinks, reloadKey }: Readonly<{
  token: string;
  reportId: number;
  /** Why a new link cannot be made (archived, or not verified), or empty. */
  disabledReason: string;
  onOpenLinks?: (count: number) => void;
  reloadKey?: string;
}>) {
  const [links, setLinks] = useState<ReportShare[]>([]);
  const [error, setError] = useState('');
  const [dialog, setDialog] = useState(false);
  const [days, setDays] = useState<number>(30);
  const [sections, setSections] = useState<ReportSection[]>(DEFAULT_SHARE_SECTIONS);
  const [created, setCreated] = useState<ReportShare | null>(null);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    try {
      const list = await listReportShares(token, reportId);
      setLinks(list);
      onOpenLinks?.(list.filter((link) => link.open).length);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : tr('Unable to load the share links.'));
    }
  }, [token, reportId, onOpenLinks]);

  useEffect(() => {
    void load();
  }, [load, reloadKey]);

  function openDialog() {
    setDays(30);
    setSections(DEFAULT_SHARE_SECTIONS);
    setCreated(null);
    setCopied(false);
    setError('');
    setDialog(true);
  }

  async function create() {
    setBusy(true);
    setError('');
    try {
      setCreated(await createReportShare(token, reportId, days, sections));
      await load();
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : tr('Unable to share the report.'));
    } finally {
      setBusy(false);
    }
  }

  async function revoke(link: ReportShare) {
    setError('');
    try {
      await revokeReportShare(token, reportId, link.id);
      await load();
    } catch (revokeError) {
      setError(revokeError instanceof Error ? revokeError.message : tr('Unable to stop the link.'));
    }
  }

  async function copy(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  const seen = [tr('Headline and period figures'), ...ALL_SECTIONS.filter((section) => sections.includes(section))
    .map((section) => tr(SECTION_LABELS[section]))];

  return (
    <Card component="section" aria-label={tr('Share')}>
      <CardHeader avatar={<Link2 aria-hidden="true" />} title={tr('Share')}
        titleTypographyProps={{ variant: 'subtitle1', component: 'h2', fontWeight: 600 }}
        subheader={tr('A read-only link anyone with the address can open without signing in. Stop it at any time.')}
        action={<Button onClick={openDialog} disabled={Boolean(disabledReason)}>{tr('Create share link')}</Button>} />
      <CardContent sx={{ pt: 0, display: 'grid', gap: 1.5 }}>
        {disabledReason && <Alert severity="info">{disabledReason}</Alert>}
        {error && !dialog && <Alert severity="error">{error}</Alert>}
        {links.length === 0 ? (
          <Typography variant="body2">{tr('No share links yet.')}</Typography>
        ) : (
          <Box component="ul" sx={{ m: 0, pl: 0, listStyle: 'none', display: 'grid', gap: 1 }}>
            {links.map((link) => (
              <Box component="li" key={link.id}
                sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', borderBottom: 1, borderColor: 'divider', pb: 1 }}>
                <Box sx={{ flex: 1, minWidth: 240 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {`${linkState(link)} · ${tr('made {date}', { date: reportDate(link.createdAt.slice(0, 10)) })}`}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" component="p">
                    {tr('Ends {date} · {count} view(s)', { date: reportDate(link.expiresAt.slice(0, 10)), count: link.viewCount })}
                    {link.lastViewedAt ? ` · ${tr('last viewed {date}', { date: reportDate(link.lastViewedAt.slice(0, 10)) })}` : ''}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" component="p">
                    {link.sections.map((section) => tr(SECTION_LABELS[section])).join(', ')}
                  </Typography>
                </Box>
                {link.open && (
                  <Button variant="secondary" onClick={() => void revoke(link)} aria-label={tr('Stop link made {date}', {
                    date: reportDate(link.createdAt.slice(0, 10)),
                  })}>{tr('Stop')}</Button>
                )}
              </Box>
            ))}
          </Box>
        )}
      </CardContent>

      <Dialog open={dialog} onClose={() => setDialog(false)} aria-labelledby="share-dialog-title" fullWidth maxWidth="sm">
        <DialogTitle id="share-dialog-title">{created ? tr('Link ready') : tr('Share this report?')}</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2 }}>
          {created?.token ? (
            <>
              <Typography variant="body2">{tr('Copy the link now. For your security it is not shown again.')}</Typography>
              <Input aria-label={tr('Share link')} value={shareUrl(created.token)} readOnly onFocus={(event) => event.target.select()} />
              {copied && <Typography variant="body2" role="status">{tr('Copied.')}</Typography>}
            </>
          ) : (
            <>
              <label>
                {tr('Link lasts')}
                <Select value={String(days)} onChange={(event) => setDays(Number(event.target.value))}>
                  {EXPIRY_DAYS.map((value) => <option key={value} value={value}>{tr('{count} days', { count: value })}</option>)}
                </Select>
              </label>
              <Box component="fieldset" sx={{ border: 0, m: 0, p: 0, display: 'grid', gap: 0.5, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                <Typography component="legend" variant="body2" sx={{ mb: 0.5 }}>{tr('What the reviewer sees')}</Typography>
                {ALL_SECTIONS.map((section) => (
                  <label className="checkbox-field" key={section}>
                    <Checkbox checked={sections.includes(section)} onChange={(event) => setSections(ALL_SECTIONS.filter((value) =>
                      (value === section ? event.target.checked : sections.includes(value))))} />
                    {tr(SECTION_LABELS[section])}
                  </label>
                ))}
              </Box>
              <Alert severity="info" role="status">
                {tr('The reviewer will see: {parts}. The link stops working on {date}.', {
                  parts: seen.join(', '), date: reportDate(dayAfter(days)),
                })}
              </Alert>
              {error && <Alert severity="error">{error}</Alert>}
            </>
          )}
        </DialogContent>
        <DialogActions>
          {created?.token ? (
            <>
              <Button variant="secondary" onClick={() => void copy(shareUrl(created.token ?? ''))}>{tr('Copy link')}</Button>
              <Button onClick={() => setDialog(false)}>{tr('Done')}</Button>
            </>
          ) : (
            <>
              <Button variant="secondary" onClick={() => setDialog(false)}>{tr('Cancel')}</Button>
              <Button onClick={() => void create()} disabled={busy}>{busy ? tr('Sharing…') : tr('Create link')}</Button>
            </>
          )}
        </DialogActions>
      </Dialog>
    </Card>
  );
}
