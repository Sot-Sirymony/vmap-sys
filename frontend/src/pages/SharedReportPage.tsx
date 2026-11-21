import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useParams } from 'react-router';
import { downloadSharedReportPdf, getSharedReport } from '../api/sharedReportApi';
import { BrandMark } from '../components/common/BrandMark';
import { Button } from '../components/common/Button';
import { Loading } from '../components/common/Loading';
import { ReportCommentaryCard } from '../components/report/ReportCommentaryCard';
import { ReportDetailsTab } from '../components/report/ReportDetailsTab';
import { ReportSummary } from '../components/report/ReportSummary';
import { tr } from '../i18n/translate';
import type { SharedReport } from '../types/vision';
import { periodLabel, reportDate } from '../utils/managementReport';
import { saveBlob } from '../utils/saveBlob';

/**
 * RM-FR-47: keeps the shared page out of search engines and stops the
 * browser sending its address (which holds the token) to other sites, while
 * the page is open.
 */
function usePrivatePage() {
  useEffect(() => {
    const added = [
      { name: 'robots', content: 'noindex, nofollow' },
      { name: 'referrer', content: 'no-referrer' },
    ].map(({ name, content }) => {
      const meta = document.createElement('meta');
      meta.name = name;
      meta.content = content;
      document.head.appendChild(meta);
      return meta;
    });
    return () => added.forEach((meta) => meta.remove());
  }, []);
}

/**
 * V19 (BRD 19.0.0, RM-FR-47, RM-FR-48): a final report shared by a read-only
 * link, opened without an account. Only what the participant chose to share,
 * from the figures frozen on the day: no links into the plan. An expired,
 * stopped or unknown link says only that the report is no longer shared.
 */
export function SharedReportPage() {
  const shareToken = useParams().token ?? '';
  const [shared, setShared] = useState<SharedReport | null>(null);
  const [gone, setGone] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  usePrivatePage();

  useEffect(() => {
    getSharedReport(shareToken).then(setShared).catch(() => setGone(true));
  }, [shareToken]);

  async function downloadPdf() {
    setBusy(true);
    setError('');
    try {
      const { blob, fileName } = await downloadSharedReportPdf(shareToken);
      saveBlob(blob, fileName);
    } catch (downloadError) {
      setError(downloadError instanceof Error ? tr(downloadError.message) : tr('Unable to download the report.'));
    } finally {
      setBusy(false);
    }
  }

  const report = shared?.report;
  const scope = report?.scope.kind === 'ALL' || !report ? tr('All dreams') : report.scope.label ?? '';

  return (
    <Box component="main" sx={{ minHeight: '100vh', bgcolor: 'background.default', py: 4 }}>
      <Container maxWidth="lg">
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
          <BrandMark size={36} variant="color" />
          <Typography variant="h6" component="p">Vision Map</Typography>
        </Box>
        {gone && <Alert severity="info">{tr('This report is no longer shared.')}</Alert>}
        {!shared && !gone && <Loading />}
        {shared && report && (
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
              <Box sx={{ flex: 1, minWidth: 260 }}>
                <Typography variant="overline" color="text.secondary">
                  {tr('{name} shared this report with you', { name: shared.sharedBy })}
                </Typography>
                <Typography variant="h4" component="h1">{shared.name}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {`${shared.code} · ${scope} · ${periodLabel(report.period)}`}
                </Typography>
                {shared.finalizedAt && (
                  <Typography variant="body2" role="status">
                    {tr('Final · frozen {date}', { date: reportDate(shared.finalizedAt.slice(0, 10)) })}
                  </Typography>
                )}
              </Box>
              <Button onClick={() => void downloadPdf()} disabled={busy}>{busy ? tr('Preparing…') : tr('Download PDF')}</Button>
            </Box>
            {error && <Alert severity="error">{error}</Alert>}
            {shared.commentary && (
              <ReportCommentaryCard commentary={shared.commentary} editable={false}
                goals={report.goals.map((goal) => ({ goalId: goal.goalId, goalTitle: goal.goalTitle }))} />
            )}
            <ReportSummary report={report} filter={{}} shared />
            {shared.rows && (
              <Card component="section" aria-label={tr('Details')}>
                <CardHeader title={tr('Details')} titleTypographyProps={{ variant: 'subtitle1', component: 'h2', fontWeight: 600 }} />
                <CardContent sx={{ pt: 0 }}>
                  <ReportDetailsTab loadRows={() => Promise.resolve(shared.rows ?? [])} showDream={report.scope.kind !== 'DREAM'}
                    reloadKey={shareToken} noLinks />
                </CardContent>
              </Card>
            )}
            <Typography variant="caption" color="text.secondary">
              {tr('This link stops working on {date}.', { date: reportDate(shared.expiresAt.slice(0, 10)) })}
            </Typography>
          </Stack>
        )}
      </Container>
    </Box>
  );
}
