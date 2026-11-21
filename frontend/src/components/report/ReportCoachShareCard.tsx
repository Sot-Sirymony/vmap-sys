import { useEffect, useState } from 'react';
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
import { UserCheck } from 'lucide-react';
import { getCoachShare, shareReportWithCoach, unshareReportWithCoach } from '../../api/savedReportApi';
import { tr } from '../../i18n/translate';
import type { CoachShareStatus, ReportSection } from '../../types/vision';
import { ALL_SECTIONS, SECTION_LABELS, reportDate } from '../../utils/managementReport';
import { Button } from '../common/Button';
import { DEFAULT_SHARE_SECTIONS } from './ReportShareCard';

/**
 * V19 (RM-FR-50, D-19-3): share a final report with the coach of the
 * participant's active cohort. The coach can read it, with only the sections
 * chosen, but never change it. Stopping removes access at once. onShared
 * tells the page, so archiving can warn that the coach will lose it (RM-FR-51).
 */
export function ReportCoachShareCard({ token, reportId, disabledReason, onShared, reloadKey }: Readonly<{
  token: string;
  reportId: number;
  /** Why the report cannot be shared (archived, or not verified), or empty. */
  disabledReason: string;
  onShared?: (shared: boolean) => void;
  reloadKey?: string;
}>) {
  const [status, setStatus] = useState<CoachShareStatus | null>(null);
  const [dialog, setDialog] = useState(false);
  const [sections, setSections] = useState<ReportSection[]>(DEFAULT_SHARE_SECTIONS);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    getCoachShare(token, reportId).then((next) => {
      if (active) setStatus(next);
    }).catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token, reportId, reloadKey]);

  useEffect(() => {
    if (status) onShared?.(status.shared);
  }, [status, onShared]);

  if (!status) return null;

  function openDialog() {
    setSections(status?.shared ? status.sections : DEFAULT_SHARE_SECTIONS);
    setError('');
    setDialog(true);
  }

  async function act(change: () => Promise<CoachShareStatus>) {
    setBusy(true);
    setError('');
    try {
      setStatus(await change());
      setDialog(false);
    } catch (actError) {
      setError(actError instanceof Error ? tr(actError.message) : tr('Unable to change sharing with your coach.'));
    } finally {
      setBusy(false);
    }
  }

  const coach = status.coachName ?? tr('your coach');
  const seen = [tr('Headline and period figures'), ...ALL_SECTIONS.filter((section) => sections.includes(section))
    .map((section) => tr(SECTION_LABELS[section]))];

  let action = null;
  if (status.available && status.shared) {
    action = <Button variant="secondary" disabled={busy} onClick={() => void act(() => unshareReportWithCoach(token, reportId))}>{tr('Stop sharing')}</Button>;
  } else if (status.available) {
    action = <Button onClick={openDialog} disabled={Boolean(disabledReason)}>{tr('Share with my coach')}</Button>;
  }

  return (
    <Card component="section" aria-label={tr('Share with my coach')}>
      <CardHeader avatar={<UserCheck aria-hidden="true" />} title={tr('Share with my coach')}
        titleTypographyProps={{ variant: 'subtitle1', component: 'h2', fontWeight: 600 }}
        subheader={tr('Your coach can read this report in the coaching area, but cannot change it.')}
        action={action} />
      <CardContent sx={{ pt: 0, display: 'grid', gap: 1 }}>
        {!status.available && <Typography variant="body2">{tr('Join a cohort with a coach to share a report with them.')}</Typography>}
        {status.available && status.shared && (
          <>
            <Typography variant="body2" role="status">
              {tr('Shared with {coach} since {date}.', { coach, date: reportDate((status.sharedAt ?? '').slice(0, 10)) })}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {status.sections.map((section) => tr(SECTION_LABELS[section])).join(', ')}
            </Typography>
          </>
        )}
        {status.available && !status.shared && disabledReason && <Alert severity="info">{disabledReason}</Alert>}
        {error && !dialog && <Alert severity="error">{error}</Alert>}
      </CardContent>

      <Dialog open={dialog} onClose={() => setDialog(false)} aria-labelledby="coach-share-title" fullWidth maxWidth="sm">
        <DialogTitle id="coach-share-title">{tr('Share this report with {coach}?', { coach })}</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2 }}>
          <Box component="fieldset" sx={{ border: 0, m: 0, p: 0, display: 'grid', gap: 0.5, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            <Typography component="legend" variant="body2" sx={{ mb: 0.5 }}>{tr('What your coach sees')}</Typography>
            {ALL_SECTIONS.map((section) => (
              <label className="checkbox-field" key={section}>
                <Checkbox checked={sections.includes(section)} onChange={(event) => setSections(ALL_SECTIONS.filter((value) =>
                  (value === section ? event.target.checked : sections.includes(value))))} />
                {tr(SECTION_LABELS[section])}
              </label>
            ))}
          </Box>
          <Alert severity="info" role="status">
            {tr('{coach} will see: {parts}. Read-only, until you stop sharing.', { coach, parts: seen.join(', ') })}
          </Alert>
          {error && <Alert severity="error">{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button variant="secondary" onClick={() => setDialog(false)}>{tr('Cancel')}</Button>
          <Button onClick={() => void act(() => shareReportWithCoach(token, reportId, sections))} disabled={busy}>
            {busy ? tr('Sharing…') : tr('Share')}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
}
