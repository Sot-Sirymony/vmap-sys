import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { getTwoFactorReport, resetTwoFactor, type TwoFactorReportRow } from '../api/twoFactorApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { isStepUpCancelled, useStepUp } from '../components/step-up/useStepUp';
import { useAuth } from '../context/AuthContext';
import { formatDate } from '../i18n/formatDate';
import { tr } from '../i18n/translate';
import { PageSection } from './PageSection';

/**
 * V21 (TF-FR-28, TF-FR-36, D-21-5): admins see who has two-step sign-in, and
 * can reset it for someone who lost their device and their recovery codes,
 * after checking it is really them outside the app.
 */
export function TwoFactorAdminPage() {
  const { token, user } = useAuth();
  const [rows, setRows] = useState<TwoFactorReportRow[]>([]);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');
  const stepUp = useStepUp(token);

  const load = useCallback(() => {
    getTwoFactorReport(token)
      .then(setRows)
      .catch((caught: unknown) => setError(caught instanceof Error ? tr(caught.message) : tr('Unable to load the report.')));
  }, [token]);

  useEffect(load, [load]);

  async function reset(row: TwoFactorReportRow) {
    const reason = window.prompt(tr('Why are you resetting two-step sign-in for {name}? Say how you checked it is them.', { name: row.fullName }));
    if (!reason || !reason.trim()) return;
    setError('');
    setDone('');
    try {
      await stepUp.protect('ADMIN_TWO_FACTOR_RESET', (code) => resetTwoFactor(token, row.userId, reason.trim(), code));
      setDone(tr('Two-step sign-in was reset for {name}. They were signed out everywhere and emailed.', { name: row.fullName }));
      load();
    } catch (caught) {
      if (!isStepUpCancelled(caught)) {
        setError(caught instanceof Error ? tr(caught.message) : tr('Could not reset two-step sign-in.'));
      }
    }
  }

  function state(row: TwoFactorReportRow) {
    if (row.enabled) return row.method === 'EMAIL' ? tr('On (email code)') : tr('On (authenticator app)');
    if (row.overdue) return tr('Off, overdue');
    return row.dueAt ? tr('Off, due {date}', { date: formatDate(new Date(row.dueAt), { year: true }) }) : tr('Off');
  }

  return (
    <PageSection title={tr('Two-step sign-in')} subtitle={tr('Who uses it, and resetting it after a lost device')}>
      <Card>
        <CardContent>
          {error && <ErrorMessage message={error} />}
          {done && <Alert severity="success">{done}</Alert>}
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {tr('Reset only after confirming the person outside the app, for example by calling a number you already have.')}
          </Typography>
          <Table size="small" aria-label={tr('Two-step sign-in by person')}>
            <TableHead>
              <TableRow>
                <TableCell>{tr('Name')}</TableCell>
                <TableCell>{tr('Role')}</TableCell>
                <TableCell>{tr('Two-step sign-in')}</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.userId}>
                  <TableCell>{row.fullName}<Typography variant="caption" sx={{ display: 'block' }}>{row.email}</Typography></TableCell>
                  <TableCell>{tr(row.role)}</TableCell>
                  <TableCell>{state(row)}</TableCell>
                  <TableCell>
                    {row.enabled && row.userId !== user?.id && (
                      <Button variant="secondary" onClick={() => void reset(row)}
                        aria-label={tr('Reset two-step sign-in for {name}', { name: row.fullName })}>{tr('Reset')}</Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      {stepUp.dialog}
    </PageSection>
  );
}
