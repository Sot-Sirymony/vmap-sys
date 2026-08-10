import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { MoreVertical } from 'lucide-react';
import {
  archiveIssueReport,
  listAllIssueReports,
  listMyIssueReports,
  permanentlyDeleteIssueReport,
  restoreIssueReport,
  updateIssueReportStatus,
} from '../api/issueReportApi';
import { Button } from '../components/common/Button';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterPanel } from '../components/common/FilterPanel';
import { FilterSelect, optionsFromLabels } from '../components/common/FilterSelect';
import { Loading } from '../components/common/Loading';
import { Modal } from '../components/common/Modal';
import { ReportIssueModal } from '../components/common/ReportIssueModal';
import { StatusBadge } from '../components/common/StatusBadge';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import type { IssueReport, IssueReportStatus } from '../types/vision';
import { issueReportStatusLabels, reportTypeLabels, severityLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

const statuses: IssueReportStatus[] = ['OPEN', 'IN_REVIEW', 'PLANNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONT_FIX'];

type Scope = 'mine' | 'all';

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' });
}

/**
 * FR-38.3 / FR-38.4: the reporter tracks their own reports; an admin can switch
 * to the whole queue, filter it, and change a report's status with a resolution
 * note. Reporting itself happens through the header's "Report an issue" modal;
 * this page also surfaces one button that opens the same modal.
 */
export function IssueReportsPage() {
  const { token, user } = useAuth();
  const { showToast } = useToast();
  const isAdmin = user?.role === 'ADMIN';

  const [scope, setScope] = useState<Scope>('mine');
  const [reports, setReports] = useState<IssueReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reportOpen, setReportOpen] = useState(false);

  const [filterType, setFilterType] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterSeverity, setFilterSeverity] = useState('');

  const [detail, setDetail] = useState<IssueReport | null>(null);
  const [triageTarget, setTriageTarget] = useState<IssueReport | null>(null);
  const [confirm, setConfirm] = useState<{ message: string; danger?: boolean; onConfirm: () => void } | null>(null);

  const reload = useCallback(() => {
    if (!token) {
      return;
    }
    setLoading(true);
    const request = scope === 'all'
      ? listAllIssueReports(token, { reportType: filterType, status: filterStatus, severity: filterSeverity })
      : listMyIssueReports(token, true);
    request
      .then((data) => {
        setReports(data);
        setError('');
      })
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : 'Unable to load reports.'))
      .finally(() => setLoading(false));
  }, [token, scope, filterType, filterStatus, filterSeverity]);

  useEffect(() => {
    reload();
  }, [reload]);

  async function runAction(action: Promise<unknown>, message: string) {
    try {
      await action;
      showToast(message);
      reload();
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : 'Action failed.');
    }
  }

  const columns: DataTableColumn<IssueReport>[] = [
    { key: 'code', label: 'Code', sortValue: (report) => report.code, render: (report) => report.code },
    {
      key: 'reportType',
      label: 'Type',
      sortValue: (report) => reportTypeLabels[report.reportType],
      render: (report) => reportTypeLabels[report.reportType],
    },
    {
      key: 'title',
      label: 'Title',
      sortValue: (report) => report.title,
      sx: { fontWeight: 500 },
      render: (report) => (
        <Link component="button" type="button" underline="hover" sx={{ textAlign: 'left', fontWeight: 500 }} onClick={() => setDetail(report)}>
          {report.title}
        </Link>
      ),
    },
    ...(isAdmin && scope === 'all'
      ? [{
          key: 'reporter',
          label: 'Reporter',
          sortValue: (report: IssueReport) => report.reporterName,
          render: (report: IssueReport) => report.reporterName,
        }]
      : []),
    {
      key: 'severity',
      label: 'Severity',
      sortValue: (report) => report.severity ?? '',
      render: (report) => (report.severity ? <StatusBadge status={report.severity} /> : '-'),
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (report) => report.status,
      render: (report) => <StatusBadge status={report.status} />,
    },
    {
      key: 'createdAt',
      label: 'Reported',
      sortValue: (report) => report.createdAt,
      render: (report) => <span title={report.createdAt}>{formatDateTime(report.createdAt)}</span>,
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (report) => (
        <ReportActions
          report={report}
          isAdmin={isAdmin}
          onView={() => setDetail(report)}
          onTriage={() => setTriageTarget(report)}
          onArchive={() => setConfirm({
            message: 'Archive this report? You can bring it back later with the archive filter.',
            onConfirm: () => void runAction(archiveIssueReport(token!, report.id), 'Archived.'),
          })}
          onRestore={() => void runAction(restoreIssueReport(token!, report.id), 'Restored.')}
          onDelete={() => setConfirm({
            message: 'Permanently delete this report? This cannot be undone.',
            danger: true,
            onConfirm: () => void runAction(permanentlyDeleteIssueReport(token!, report.id), 'Deleted.'),
          })}
        />
      ),
    },
  ];

  const openCount = useMemo(
    () => reports.filter((report) => report.status === 'OPEN').length,
    [reports],
  );

  return (
    <PageSection
      title="Issue Reports"
      subtitle={isAdmin
        ? 'Track your reports, or switch to the full queue to triage what others have raised.'
        : 'Bugs and improvements you’ve raised, and where each one stands.'}
      actions={<Button type="button" onClick={() => setReportOpen(true)}>Report an issue</Button>}
    >
      {isAdmin && (
        <ToggleButtonGroup
          size="small"
          exclusive
          value={scope}
          onChange={(_event, next: Scope | null) => next && setScope(next)}
          sx={{ mb: 1 }}
        >
          <ToggleButton value="mine">My reports</ToggleButton>
          <ToggleButton value="all">All reports{openCount > 0 && scope === 'all' ? ` · ${openCount} open` : ''}</ToggleButton>
        </ToggleButtonGroup>
      )}

      {isAdmin && scope === 'all' && (
        <FilterPanel activeCount={[filterType, filterStatus, filterSeverity].filter(Boolean).length}>
          <FilterSelect label="Type" value={filterType} onChange={setFilterType} options={optionsFromLabels(reportTypeLabels)} />
          <FilterSelect label="Status" value={filterStatus} onChange={setFilterStatus} options={optionsFromLabels(issueReportStatusLabels)} />
          <FilterSelect label="Severity" value={filterSeverity} onChange={setFilterSeverity} options={optionsFromLabels(severityLabels)} />
        </FilterPanel>
      )}

      {loading && <Loading variant="table" />}
      {error && <ErrorMessage message={error} onRetry={reload} />}

      {!loading && !error && reports.length === 0 && (
        <EmptyState>
          {scope === 'all'
            ? 'No reports match these filters.'
            : 'You haven’t raised any reports yet. Use "Report an issue" when something looks off.'}
        </EmptyState>
      )}

      {!loading && !error && reports.length > 0 && (
        <Card>
          <CardContent>
            <DataTable
              rows={reports}
              columns={columns}
              emptyMessage="No reports."
              defaultSortKey="createdAt"
              defaultSortDirection="desc"
              rowClassName={(report) => (report.archived ? 'row-archived' : '')}
            />
          </CardContent>
        </Card>
      )}

      {reportOpen && (
        <ReportIssueModal onClose={() => setReportOpen(false)} onSubmitted={reload} />
      )}
      {detail && <ReportDetailModal report={detail} onClose={() => setDetail(null)} />}
      {triageTarget && (
        <TriageModal
          report={triageTarget}
          onClose={() => setTriageTarget(null)}
          onSaved={() => { setTriageTarget(null); reload(); }}
        />
      )}
      {confirm && (
        <ConfirmDialog
          title="Please confirm"
          message={confirm.message}
          confirmLabel="Confirm"
          danger={confirm.danger}
          onConfirm={() => { const run = confirm.onConfirm; setConfirm(null); run(); }}
          onClose={() => setConfirm(null)}
        />
      )}
    </PageSection>
  );
}

type ReportActionsProps = {
  report: IssueReport;
  isAdmin: boolean;
  onView: () => void;
  onTriage: () => void;
  onArchive: () => void;
  onRestore: () => void;
  onDelete: () => void;
};

function ReportActions({ report, isAdmin, onView, onTriage, onArchive, onRestore, onDelete }: ReportActionsProps) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const close = () => setAnchorEl(null);

  return (
    <>
      <IconButton size="small" aria-label="Report actions" onClick={(event) => setAnchorEl(event.currentTarget)}>
        <MoreVertical size={16} />
      </IconButton>
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={close}>
        <MenuItem onClick={() => { close(); onView(); }}>View details</MenuItem>
        {isAdmin && <MenuItem onClick={() => { close(); onTriage(); }}>Change status</MenuItem>}
        {!report.archived && <MenuItem onClick={() => { close(); onArchive(); }}>Archive</MenuItem>}
        {report.archived && <MenuItem onClick={() => { close(); onRestore(); }}>Restore</MenuItem>}
        {report.archived && (
          <MenuItem sx={{ color: 'error.main' }} onClick={() => { close(); onDelete(); }}>Delete permanently</MenuItem>
        )}
      </Menu>
    </>
  );
}

function ReportDetailModal({ report, onClose }: { report: IssueReport; onClose: () => void }) {
  return (
    <Modal title={`${report.code} · ${reportTypeLabels[report.reportType]}`} onClose={onClose}>
      <Stack spacing={1.25}>
        <Typography variant="h6">{report.title}</Typography>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
          <StatusBadge status={report.status} />
          {report.severity && <StatusBadge status={report.severity} />}
        </Stack>
        {report.description && (
          <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>{report.description}</Typography>
        )}
        <DetailRow label="Reported by" value={`${report.reporterName} (${report.reporterEmail})`} />
        <DetailRow label="On page" value={report.contextRoute || '—'} />
        <DetailRow label="App version" value={report.appVersion || '—'} />
        <DetailRow label="Reported" value={formatDateTime(report.createdAt)} />
        {report.resolutionNote && <DetailRow label="Resolution" value={report.resolutionNote} />}
      </Stack>
    </Modal>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <Typography variant="body2" color="text.secondary">
      <strong>{label}:</strong> {value}
    </Typography>
  );
}

function TriageModal({ report, onClose, onSaved }: { report: IssueReport; onClose: () => void; onSaved: () => void }) {
  const { token } = useAuth();
  const { showToast } = useToast();
  const [status, setStatus] = useState<IssueReportStatus>(report.status);
  const [resolutionNote, setResolutionNote] = useState(report.resolutionNote ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  // BR-32: a bug moved to Resolved must carry a note; surface it before the save.
  const noteRequired = report.reportType === 'BUG' && status === 'RESOLVED';

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      await updateIssueReportStatus(token, report.id, { status, resolutionNote: resolutionNote || undefined });
      showToast('Status updated.');
      onSaved();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Unable to update status.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title={`Triage ${report.code}`} onClose={onClose}>
      <form className="form-grid" onSubmit={handleSubmit}>
        {error && <ErrorMessage message={error} />}
        <label>
          Status
          <FormControl fullWidth size="small">
            <Select
              SelectDisplayProps={{ 'aria-label': 'Status' }}
              value={status}
              onChange={(event) => setStatus(event.target.value as IssueReportStatus)}
            >
              {statuses.map((value) => (
                <MenuItem value={value} key={value}>{issueReportStatusLabels[value]}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </label>
        <label className="field-full">
          Resolution note
          <Textarea
            value={resolutionNote}
            onChange={(event) => setResolutionNote(event.target.value)}
            required={noteRequired}
          />
          {noteRequired && (
            <span className="field-hint">A resolved bug must record how it was resolved (BR-32).</span>
          )}
        </label>
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }} className="field-full">
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save'}</Button>
        </Stack>
      </form>
    </Modal>
  );
}
