import type { ReportFilter } from '../../api/reportApi';
import type { SavedReport } from '../../types/vision';
import { Select } from '../common/Select';
import { tr } from '../../i18n/translate';
import { reportDate } from '../../utils/managementReport';

/** RM-FR-32: only final reports on the same scope can be compared with. */
export function comparableReports(reports: SavedReport[], filter: ReportFilter, excludeId?: number) {
  const kind = filter.dreamId ? 'DREAM' : filter.areaId ? 'AREA' : 'ALL';
  return reports
    .filter((report) => report.status === 'FINAL' && report.id !== excludeId && report.scopeKind === kind)
    .filter((report) => (report.dreamId ?? undefined) === (kind === 'DREAM' ? filter.dreamId : undefined))
    .filter((report) => (report.areaId ?? undefined) === (kind === 'AREA' ? filter.areaId : undefined))
    .sort((first, second) => (second.finalizedAt ?? '').localeCompare(first.finalizedAt ?? ''));
}

/**
 * V19 RM-FR-32: choose the earlier final report to compare with. The empty
 * choice is "Don't compare" on the live report and "The latest final report"
 * on a draft.
 */
export function ReportComparePicker({ reports, value, onChange, emptyLabel }: Readonly<{
  reports: SavedReport[];
  value?: number | null;
  onChange: (reportId: number | undefined) => void;
  emptyLabel: string;
}>) {
  return (
    <label>
      {tr('Compare with')}
      <Select value={value ? String(value) : ''} onChange={(event) => onChange(event.target.value ? Number(event.target.value) : undefined)}
        disabled={reports.length === 0}>
        <option value="">{reports.length === 0 ? tr('No final report on this scope yet') : emptyLabel}</option>
        {reports.map((report) => (
          <option key={report.id} value={report.id}>
            {`${report.name} (${report.code}${report.finalizedAt ? `, ${reportDate(report.finalizedAt.slice(0, 10))}` : ''})`}
          </option>
        ))}
      </Select>
    </label>
  );
}
