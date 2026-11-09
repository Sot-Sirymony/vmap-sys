import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { LoggedFailure } from '../../hooks/useClientErrorLog';
import { useI18n } from '../../i18n/I18nContext';

type PageProblemsPanelProps = {
  failures: LoggedFailure[];
};

/** What a status most likely means, in words a participant can act on. */
function likelyCause(failure: LoggedFailure, t: ReturnType<typeof useI18n>['t']) {
  if (failure.status === 0) {
    return t('The server could not be reached. Check your connection, or the server may be restarting.');
  }
  if (failure.status === 404 && failure.message?.startsWith('No endpoint')) {
    return t('The server is older than this page. A server update has probably not finished deploying.');
  }
  if (failure.status >= 500) {
    return t('The server ran into a problem. Quote the reference when you report it.');
  }
  return '';
}

function timeOf(iso: string) {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}

/**
 * Diagnostics: the requests on this page that failed, newest first. Shown only
 * when there is something to show, so a working page stays uncluttered.
 */
export function PageProblemsPanel({ failures }: PageProblemsPanelProps) {
  const { t } = useI18n();
  if (failures.length === 0) {
    return null;
  }
  const anyUnsent = failures.some((failure) => !failure.sent);

  return (
    <Card component="section" aria-label={t('Problems on this page')}>
      <CardHeader
        title={t('Problems on this page')}
        subheader={t('Requests on this page that failed. Admins can see these too, so they can fix the cause.')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          {anyUnsent && (
            <Alert severity="warning">
              {t('Some problems could not be sent to the server. They are kept here until you close the app.')}
            </Alert>
          )}
          {failures.map((failure) => {
            const cause = likelyCause(failure, t);
            return (
              <Box key={failure.key} sx={{ borderLeft: 3, borderColor: failure.status >= 500 || failure.status === 0 ? 'error.main' : 'warning.main', pl: 1.5 }}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Chip size="small" color={failure.status >= 500 || failure.status === 0 ? 'error' : 'warning'} label={failure.status === 0 ? t('No connection') : failure.status} />
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
                    {failure.method} {failure.path}
                  </Typography>
                  {!failure.sent && <Chip size="small" variant="outlined" label={t('Not sent')} />}
                </Box>
                {failure.message && <Typography variant="body2" sx={{ mt: 0.5 }}>{failure.message}</Typography>}
                {cause && <Typography variant="body2" color="text.secondary">{cause}</Typography>}
                <Typography variant="caption" color="text.secondary">
                  {timeOf(failure.occurredAt)}
                  {failure.reference ? ` · ${t('Reference: {reference}', { reference: failure.reference })}` : ''}
                </Typography>
              </Box>
            );
          })}
        </Stack>
      </CardContent>
    </Card>
  );
}
