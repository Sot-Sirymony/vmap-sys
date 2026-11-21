import { useEffect, useMemo, useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import { FileBarChart, Lock, PencilLine } from 'lucide-react';
import { Link, useNavigate } from 'react-router';
import { ReportReminderNotice } from '../components/report/ReportReminderNotice';
import { listSavedReports } from '../api/savedReportApi';
import { Button } from '../components/common/Button';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { useAuth } from '../context/AuthContext';
import type { SavedReport } from '../types/vision';
import { PERIOD_LABELS, reportDate } from '../utils/managementReport';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

type StatusFilter = 'ALL' | 'DRAFT' | 'FINAL';

/** "This month" for a draft's moving preset, else its dates. */
function periodText(report: SavedReport) {
  if (report.periodFrom && report.periodTo) {
    return tr('{from} to {to}', { from: reportDate(report.periodFrom), to: reportDate(report.periodTo) });
  }
  return report.periodKind === 'CUSTOM' ? '—' : tr(PERIOD_LABELS[report.periodKind]);
}

/** RM-FR-19: Draft or Final, with an icon and words, never colour alone. */
export function ReportStatusChip({ status }: Readonly<{ status: SavedReport['status'] }>) {
  return status === 'FINAL'
    ? <Chip size="small" icon={<Lock size={14} aria-hidden="true" />} label={tr('Final')} color="primary" />
    : <Chip size="small" icon={<PencilLine size={14} aria-hidden="true" />} label={tr('Draft')} variant="outlined" />;
}

/**
 * V19 RM-FR-17: every saved report, newest change first. Filter by status,
 * search by name or code, and show archived reports when asked.
 */
export function SavedReportsPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [reports, setReports] = useState<SavedReport[] | null>(null);
  const [error, setError] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (!token) return;
    let active = true;
    setError('');
    listSavedReports(token, showArchived)
      .then((data) => active && setReports(data))
      .catch((loadError) => active && setError(loadError instanceof Error ? loadError.message : tr('Unable to load the reports.')));
    return () => {
      active = false;
    };
  }, [token, showArchived]);

  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (reports ?? [])
      .filter((report) => status === 'ALL' || report.status === status)
      .filter((report) => !needle || report.name.toLowerCase().includes(needle) || report.code.toLowerCase().includes(needle));
  }, [reports, status, search]);

  const columns: DataTableColumn<SavedReport>[] = [
    { key: 'name', label: tr('Name'), sortValue: (row) => row.name,
      render: (row) => <Link to={`/vision-map/reports/${row.id}`}>{row.name}</Link> },
    { key: 'code', label: tr('Code'), sortValue: (row) => row.code, render: (row) => row.code },
    { key: 'scope', label: tr('Report on'), sortValue: (row) => row.scopeLabel ?? '',
      render: (row) => (row.scopeKind === 'ALL' ? tr('All dreams') : row.scopeLabel ?? '—') },
    { key: 'period', label: tr('Period'), render: periodText },
    { key: 'status', label: tr('Status'), sortValue: (row) => row.status,
      render: (row) => (
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
          <ReportStatusChip status={row.status} />
          {row.archived && <Chip size="small" label={tr('Archived')} />}
        </Box>
      ) },
    { key: 'compared', label: tr('Compared with'), render: (row) => {
      const source = reports?.find((other) => other.id === row.compareToReportId);
      return source ? <Link to={`/vision-map/reports/${source.id}`}>{source.code}</Link> : '—';
    } },
    { key: 'updated', label: tr('Last changed'), sortValue: (row) => row.updatedAt,
      render: (row) => reportDate(row.updatedAt.slice(0, 10)) },
    { key: 'finalized', label: tr('Finalised on'), sortValue: (row) => row.finalizedAt ?? '',
      render: (row) => (row.finalizedAt ? reportDate(row.finalizedAt.slice(0, 10)) : '—') },
  ];

  return (
    <PageSection
      title={tr('Saved reports')}
      subtitle={tr('Reports you saved from the Management Report. Final ones keep their figures.')}
      actions={<Button onClick={() => navigate('/vision-map/report')}>{tr('Open the Management Report')}</Button>}
    >
      <ReportReminderNotice token={token} onReportsPage />
      {error && <ErrorMessage message={error} />}
      {reports === null && !error && <Loading />}
      {reports !== null && reports.length === 0 && !showArchived ? (
        <EmptyState headline={tr('No saved reports yet')} icon={FileBarChart}
          action={<Button onClick={() => navigate('/vision-map/report')}>{tr('Open the Management Report')}</Button>}>
          {tr('Open the Management Report, choose what it covers, and press Save as report.')}
        </EmptyState>
      ) : reports !== null && (
        <Card>
          <CardContent sx={{ display: 'grid', gap: 1.5 }}>
            <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-end', flexWrap: 'wrap' }}>
              <Box sx={{ minWidth: 220, flex: '1 1 220px', maxWidth: 340 }}>
                <Input type="search" aria-label={tr('Search reports')} placeholder={tr('Search reports')} value={search}
                  onChange={(event) => setSearch(event.target.value)} />
              </Box>
              <label>
                {tr('Status')}
                <FormControl size="small" sx={{ minWidth: 140, display: 'flex' }}>
                  <Select SelectDisplayProps={{ 'aria-label': tr('Status') }} value={status}
                    onChange={(event) => setStatus(event.target.value as StatusFilter)}>
                    <MenuItem value="ALL">{tr('All')}</MenuItem>
                    <MenuItem value="DRAFT">{tr('Draft')}</MenuItem>
                    <MenuItem value="FINAL">{tr('Final')}</MenuItem>
                  </Select>
                </FormControl>
              </label>
              <ShowArchivedToggle checked={showArchived} onToggle={() => setShowArchived((current) => !current)} />
            </Box>
            <DataTable rows={visible} columns={columns} emptyMessage={tr('No reports match.')} defaultSortKey="updated"
              defaultSortDirection="desc" defaultRowsPerPage={25} />
          </CardContent>
        </Card>
      )}
    </PageSection>
  );
}
