import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { getSecurityLog, type SecurityEvent, type SecurityLogFilter } from '../api/securityLogApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { Select } from '../components/common/Select';
import { useAuth } from '../context/AuthContext';
import { tr } from '../i18n/translate';
import { PageSection } from './PageSection';

/** Kept in step with SecurityEventType on the server. */
const EVENT_TYPES = [
  'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGIN_LOCKED', 'LOGGED_OUT', 'REGISTERED', 'PASSWORD_CHANGED',
  'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED', 'REFRESH_TOKEN_REUSED', 'INVALID_TOKEN',
  'ACCESS_DENIED', 'NOT_OWNER', 'RATE_LIMITED', 'ROLE_CHANGED', 'COACH_VIEWED_PARTICIPANT',
  'SHARE_LINK_CREATED', 'SHARE_LINK_OPENED', 'SHARE_LINK_REVOKED', 'EXCEL_IMPORTED', 'EXCEL_EXPORTED',
  'ACCOUNT_EXPORTED', 'ACCOUNT_DELETED', 'AI_SETTINGS_CHANGED', 'AI_KEY_CHANGED', 'AI_KEY_REMOVED',
  'APPROVED_RESOURCE_CHANGED', 'MODULE_CONTENT_CHANGED', 'INTERFACE_TEXT_CHANGED', 'COHORT_CHANGED',
  'COHORT_MEMBER_CHANGED', 'ISSUE_REPORT_TRIAGED',
];

const OUTCOME_COLOR = { SUCCESS: 'success', FAILURE: 'warning', DENIED: 'error' } as const;

function label(type: string) {
  const words = type.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * V10 (SEC-09-05): admins read the security log — sign-ins, refused requests,
 * coach access, share links, imports, exports and admin changes. Read-only.
 */
export function SecurityLogPage() {
  const { token } = useAuth();
  const [filter, setFilter] = useState<SecurityLogFilter>({});
  const [events, setEvents] = useState<SecurityEvent[]>([]);
  const [alerts, setAlerts] = useState<string[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async (nextPage: number, append: boolean) => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      const log = await getSecurityLog(token, filter, nextPage);
      setEvents((current) => (append ? [...current, ...log.events] : log.events));
      setAlerts(log.recentAlerts);
      setTotal(log.totalEvents);
      setPage(log.page);
      setHasMore(log.hasMore);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : tr('Could not load the security log.'));
    } finally {
      setLoading(false);
    }
  }, [token, filter]);

  useEffect(() => {
    void load(0, false);
  }, [load]);

  return (
    <PageSection title={tr('Security log')} subtitle={tr('Sign-ins, refused requests and admin changes. Kept for 12 months and cannot be edited.')}>
      <Stack spacing={2}>
        {alerts.length > 0 && (
          <Alert severity="warning">
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{tr('Recent alerts')}</Typography>
            {alerts.slice(0, 5).map((alert) => (
              <Typography key={alert} variant="body2">{alert}</Typography>
            ))}
          </Alert>
        )}

        <Card>
          <CardContent>
            <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr 1fr' } }}>
              <Select aria-label={tr('Event')} value={filter.type ?? ''} onChange={(event) => setFilter({ ...filter, type: event.target.value || undefined })}>
                <option value="">{tr('All events')}</option>
                {EVENT_TYPES.map((type) => <option key={type} value={type}>{tr(label(type))}</option>)}
              </Select>
              <Select aria-label={tr('Outcome')} value={filter.outcome ?? ''} onChange={(event) => setFilter({ ...filter, outcome: event.target.value || undefined })}>
                <option value="">{tr('All outcomes')}</option>
                <option value="SUCCESS">{tr('Success')}</option>
                <option value="FAILURE">{tr('Failure')}</option>
                <option value="DENIED">{tr('Denied')}</option>
              </Select>
              <Input
                aria-label={tr('Account number')}
                placeholder={tr('Account number')}
                inputMode="numeric"
                value={filter.actorId ?? ''}
                onChange={(event) => setFilter({ ...filter, actorId: event.target.value.replace(/\D/g, '') || undefined })}
              />
            </Box>
          </CardContent>
        </Card>

        {error && <ErrorMessage message={error} />}
        {loading && events.length === 0 ? <Loading variant="table" /> : (
          <Card>
            <TableContainer>
              <Table size="small" aria-label={tr('Security events')}>
                <TableHead>
                  <TableRow>
                    <TableCell>{tr('When')}</TableCell>
                    <TableCell>{tr('Event')}</TableCell>
                    <TableCell>{tr('Outcome')}</TableCell>
                    <TableCell>{tr('Account')}</TableCell>
                    <TableCell>{tr('Network')}</TableCell>
                    <TableCell>{tr('Target')}</TableCell>
                    <TableCell>{tr('Detail')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {events.length === 0 && (
                    <TableRow><TableCell colSpan={7}>{tr('No events match these filters.')}</TableCell></TableRow>
                  )}
                  {events.map((event) => (
                    <TableRow key={event.id}>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(event.occurredAt).toLocaleString()}</TableCell>
                      <TableCell>{tr(label(event.eventType))}</TableCell>
                      <TableCell><Chip size="small" color={OUTCOME_COLOR[event.outcome]} label={tr(label(event.outcome))} /></TableCell>
                      <TableCell>{event.actorId ?? '—'}</TableCell>
                      <TableCell>{event.ipAddress ?? '—'}</TableCell>
                      <TableCell>{event.targetType ? `${event.targetType}${event.targetId ? ` #${event.targetId}` : ''}` : '—'}</TableCell>
                      <TableCell>{event.detail ?? ''}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        )}

        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            {tr('Showing')} {events.length} {tr('of')} {total}
          </Typography>
          {hasMore && (
            <Button type="button" onClick={() => void load(page + 1, true)} disabled={loading}>
              {tr('Load more')}
            </Button>
          )}
        </Stack>
      </Stack>
    </PageSection>
  );
}
