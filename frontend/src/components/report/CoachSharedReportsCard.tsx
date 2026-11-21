import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { listCoachSharedReports } from '../../api/cohortApi';
import { tr } from '../../i18n/translate';
import type { CoachSharedReportSummary } from '../../types/vision';
import { reportDate } from '../../utils/managementReport';

/** V19 (RM-FR-50): the final reports a participant shared with their coach, newest share first. */
export function CoachSharedReportsCard({ token, userId }: Readonly<{ token: string; userId: number }>) {
  const [reports, setReports] = useState<CoachSharedReportSummary[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    listCoachSharedReports(token, userId)
      .then((list) => active && setReports(list))
      .catch((loadError: unknown) => active && setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load the shared reports.')));
    return () => {
      active = false;
    };
  }, [token, userId]);

  return (
    <Card component="section" aria-label={tr('Shared reports')}>
      <CardHeader title={tr('Shared reports')} subheader={tr('Final reports this participant chose to share with you, read-only')} />
      <CardContent sx={{ pt: 0 }}>
        {error && <Typography variant="body2" color="error">{error}</Typography>}
        {reports?.length === 0 && <Typography variant="body2">{tr('No reports shared with you yet.')}</Typography>}
        {reports && reports.length > 0 && (
          <Box component="ul" sx={{ m: 0, pl: 0, listStyle: 'none', display: 'grid', gap: 1 }}>
            {reports.map((report) => (
              <Box component="li" key={report.reportId}>
                <Link component={RouterLink} to={`/coach/participants/${userId}/reports/${report.reportId}`} sx={{ fontWeight: 600 }}>
                  {`${report.code} · ${report.name}`}
                </Link>
                <Typography variant="caption" color="text.secondary" component="p">
                  {[report.scopeLabel ?? tr('All dreams'),
                    report.finalizedAt ? tr('Final · frozen {date}', { date: reportDate(report.finalizedAt.slice(0, 10)) }) : '',
                    tr('shared {date}', { date: reportDate(report.sharedAt.slice(0, 10)) })].filter(Boolean).join(' · ')}
                </Typography>
              </Box>
            ))}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
