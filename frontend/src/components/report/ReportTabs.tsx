import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import { FileBarChart } from 'lucide-react';
import { useNavigate } from 'react-router';
import type { DeckOptions, ReportFilter, ReportFormat } from '../../api/reportApi';
import type { ManagementReport, ReportRow } from '../../types/vision';
import { Button } from '../common/Button';
import { EmptyState } from '../common/EmptyState';
import { ReportDetailsTab } from './ReportDetailsTab';
import { ReportExportTab } from './ReportExportTab';
import { ReportSummary } from './ReportSummary';
import { tr } from '../../i18n/translate';

export type ReportTab = 'summary' | 'details' | 'export';

export function reportTabFrom(value: string | null): ReportTab {
  return value === 'export' || value === 'details' ? value : 'summary';
}

/**
 * V18 MR-FR-02, V19 RM-FR-13: the report's Summary, Details and Export tabs,
 * shared by the live report and saved reports. The Summary can show content
 * above it, such as a saved report's commentary.
 */
export function ReportTabs({ report, filter, tab, onTabChange, loadRows, reloadKey, token, download, exportNote, aboveSummary }: Readonly<{
  report: ManagementReport | null;
  filter: ReportFilter;
  tab: ReportTab;
  onTabChange: (tab: ReportTab) => void;
  loadRows: (language: string) => Promise<ReportRow[]>;
  reloadKey: string;
  token: string;
  download?: (format: ReportFormat, language: string, deck: DeckOptions) => Promise<{ blob: Blob; fileName: string }>;
  exportNote?: string;
  aboveSummary?: ReactNode;
}>) {
  const navigate = useNavigate();
  const empty = report !== null && report.goals.length === 0;
  const emptyState = (
    // MR-FR-05.
    <EmptyState
      headline={tr('Nothing to report yet')}
      icon={FileBarChart}
      action={<Button onClick={() => navigate('/vision-map')}>{tr('Open the Vision Map')}</Button>}
    >
      {tr('Add goals, steps and tasks to a dream, and the report fills in from them.')}
    </EmptyState>
  );

  return (
    <>
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} onChange={(_event, value: string) => onTabChange(reportTabFrom(value))} aria-label={tr('Report views')}>
          <Tab value="summary" label={tr('Summary')} id="report-tab-summary" aria-controls="report-panel-summary" />
          <Tab value="details" label={tr('Details')} id="report-tab-details" aria-controls="report-panel-details" />
          <Tab value="export" label={tr('Export')} id="report-tab-export" aria-controls="report-panel-export" />
        </Tabs>
      </Box>

      <Box role="tabpanel" id="report-panel-summary" aria-labelledby="report-tab-summary" hidden={tab !== 'summary'}>
        {tab === 'summary' && (
          <Stack spacing={2}>
            {aboveSummary}
            {empty && emptyState}
            {report && !empty && <ReportSummary report={report} filter={filter} />}
          </Stack>
        )}
      </Box>
      <Box role="tabpanel" id="report-panel-details" aria-labelledby="report-tab-details" hidden={tab !== 'details'}>
        {tab === 'details' && (empty ? emptyState : report && (
          <ReportDetailsTab loadRows={loadRows} showDream={report.scope.kind !== 'DREAM'} reloadKey={reloadKey} />
        ))}
      </Box>
      <Box role="tabpanel" id="report-panel-export" aria-labelledby="report-tab-export" hidden={tab !== 'export'}>
        {tab === 'export' && (
          <ReportExportTab token={token} filter={filter} download={download} note={exportNote}
            disabledReason={empty ? tr('Add goals to a dream first; there is nothing to export yet.') : ''} />
        )}
      </Box>
    </>
  );
}
