import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { tr } from '../../i18n/translate';
import type { SharedReport } from '../../types/vision';
import { periodLabel, reportDate } from '../../utils/managementReport';
import { ReportCommentaryCard } from './ReportCommentaryCard';
import { ReportDetailsTab } from './ReportDetailsTab';
import { ReportSummary } from './ReportSummary';

/**
 * V19 (RM-FR-47, RM-FR-50): a shared final report, read-only, with only the
 * sections the participant chose and no links into the plan. Used by the
 * public share link and by the coach view.
 */
export function SharedReportView({ shared, reloadKey, action, error, sharedLine, titleLevel = 'h1' }: Readonly<{
  shared: SharedReport;
  /** Keeps the Details table's rows apart between reports. */
  reloadKey: string;
  /** A button beside the title, such as Download PDF. */
  action?: ReactNode;
  error?: string;
  /** The line above the title; "{name} shared this report with you" when left out. */
  sharedLine?: string;
  /** h2 inside a page that already has its own h1. */
  titleLevel?: 'h1' | 'h2';
}>) {
  const { report } = shared;
  const scope = report.scope.kind === 'ALL' ? tr('All dreams') : report.scope.label ?? '';
  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <Box sx={{ flex: 1, minWidth: 260 }}>
          <Typography variant="overline" color="text.secondary">
            {sharedLine ?? tr('{name} shared this report with you', { name: shared.sharedBy })}
          </Typography>
          <Typography variant="h4" component={titleLevel}>{shared.name}</Typography>
          <Typography variant="body2" color="text.secondary">
            {`${shared.code} · ${scope} · ${periodLabel(report.period)}`}
          </Typography>
          {shared.finalizedAt && (
            <Typography variant="body2" role="status">
              {tr('Final · frozen {date}', { date: reportDate(shared.finalizedAt.slice(0, 10)) })}
            </Typography>
          )}
        </Box>
        {action}
      </Box>
      {error && <Alert severity="error">{error}</Alert>}
      {shared.commentary && (
        <ReportCommentaryCard commentary={shared.commentary} editable={false}
          goals={report.goals.map((goal) => ({ goalId: goal.goalId, goalTitle: goal.goalTitle }))} />
      )}
      <ReportSummary report={report} filter={{}} shared />
      {shared.rows && (
        // The Details table is its own "Details" card.
        <ReportDetailsTab loadRows={() => Promise.resolve(shared.rows ?? [])} showDream={report.scope.kind !== 'DREAM'}
          reloadKey={reloadKey} noLinks />
      )}
      {shared.expiresAt && (
        <Typography variant="caption" color="text.secondary">
          {tr('This link stops working on {date}.', { date: reportDate(shared.expiresAt.slice(0, 10)) })}
        </Typography>
      )}
    </Stack>
  );
}
