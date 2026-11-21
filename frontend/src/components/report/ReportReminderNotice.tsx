import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import { useNavigate } from 'react-router';
import { copySavedReport, getReportReminder } from '../../api/savedReportApi';
import { tr } from '../../i18n/translate';
import type { ReportReminderStatus } from '../../types/vision';
import { Button } from '../common/Button';

/**
 * V19 (RM-FR-49): the in-app report reminder. On the Dashboard it links to
 * the Reports page; on the Reports page it offers New report from this for
 * the latest final report. Shows nothing when no reminder is due.
 */
export function ReportReminderNotice({ token, onReportsPage = false }: Readonly<{ token: string | null; onReportsPage?: boolean }>) {
  const navigate = useNavigate();
  const [status, setStatus] = useState<ReportReminderStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return;
    let active = true;
    getReportReminder(token).then((next) => active && setStatus(next)).catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token]);

  if (!token || !status?.due) return null;

  async function startFromLatest() {
    if (!token || !status?.latestFinalId) return;
    setBusy(true);
    setError('');
    try {
      const copy = await copySavedReport(token, status.latestFinalId);
      navigate(`/vision-map/reports/${copy.id}`);
    } catch (copyError) {
      setError(copyError instanceof Error ? tr(copyError.message) : tr('Unable to start the report.'));
      setBusy(false);
    }
  }

  const message = status.cadence === 'MONTHLY' ? tr('It is time to prepare your monthly report.') : tr('It is time to prepare your weekly report.');
  let action = <Button variant="secondary" onClick={() => navigate('/vision-map/reports')}>{tr('Open Reports')}</Button>;
  if (onReportsPage) {
    action = status.latestFinalId
      ? (
        <Button variant="secondary" disabled={busy} onClick={() => void startFromLatest()}>
          {tr('New report from this')}
        </Button>
      )
      : <Button variant="secondary" onClick={() => navigate('/vision-map/report')}>{tr('Open the Management Report')}</Button>;
  }
  return (
    <Alert severity="info" role="status" action={action}>
      {message}
      {onReportsPage && status.latestFinalName && ` ${tr('Your latest final report: {name}.', { name: status.latestFinalName })}`}
      {error && <div>{error}</div>}
    </Alert>
  );
}
