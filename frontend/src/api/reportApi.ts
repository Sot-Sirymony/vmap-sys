import { apiClient, authorizedFetch, reportSessionEnded } from './apiClient';
import type { ManagementReport, Priority, ReportHealth, ReportPeriodKind, ReportRow } from '../types/vision';

/**
 * V18 MR-FR-03: at most one of areaId and dreamId; neither means all dreams.
 * V19 RM-FR-06: a period preset, or CUSTOM with from and to (yyyy-MM-dd);
 * no period means this month. RM-FR-10: optional filters; empty means any,
 * and completed goals are included unless includeCompleted is false.
 */
export type ReportFilter = {
  areaId?: number;
  dreamId?: number;
  period?: ReportPeriodKind;
  from?: string;
  to?: string;
  priorities?: Priority[];
  owner?: string;
  healths?: ReportHealth[];
  includeCompleted?: boolean;
};

function query(filter: ReportFilter) {
  const params = new URLSearchParams();
  if (filter.dreamId) params.set('dreamId', String(filter.dreamId));
  else if (filter.areaId) params.set('areaId', String(filter.areaId));
  if (filter.period) params.set('period', filter.period);
  if (filter.period === 'CUSTOM' && filter.from && filter.to) {
    params.set('from', filter.from);
    params.set('to', filter.to);
  }
  if (filter.priorities?.length) params.set('priority', filter.priorities.join(','));
  if (filter.owner?.trim()) params.set('owner', filter.owner.trim());
  if (filter.healths?.length) params.set('health', filter.healths.join(','));
  if (filter.includeCompleted === false) params.set('includeCompleted', 'false');
  const text = params.toString();
  return text ? `?${text}` : '';
}

export function getManagementReport(token: string, filter: ReportFilter) {
  return apiClient<ManagementReport>(`/reports/management${query(filter)}`, { token });
}

/** V19 RM-FR-13: the Details tab's rows for the same scope, period and filters, issues in the given language. */
export async function getReportRows(token: string, filter: ReportFilter, language: string) {
  const separator = query(filter) ? '&' : '?';
  const result = await apiClient<{ rows: ReportRow[] }>(`/reports/management/rows${query(filter)}${separator}language=${language}`, { token });
  return result.rows;
}

/** V18 MR-FR-31: the deck's title (default "Progress report") and whether the title slide names the participant. */
export type DeckOptions = { title?: string; includeName?: boolean };

/** V18 MR-FR-15, MR-FR-23: the report's files. */
export type ReportFormat = 'xlsx' | 'pptx';

/**
 * Downloads the report as a file for the same scope as the Summary. The name
 * comes from the server's Content-Disposition, which is built from the scope,
 * never from free text.
 */
export async function downloadManagementReport(
  token: string,
  filter: ReportFilter,
  format: ReportFormat,
  language: string,
  deck: DeckOptions = {},
) {
  const separator = query(filter) ? '&' : '?';
  const extra = format === 'pptx'
    ? `${deck.title?.trim() ? `&title=${encodeURIComponent(deck.title.trim())}` : ''}${deck.includeName === false ? '&includeName=false' : ''}`
    : '';
  return fetchReportFile(`/reports/management.${format}${query(filter)}${separator}language=${language}${extra}`, token, format);
}

/**
 * Downloads a report file. The name comes from the server's
 * Content-Disposition, which is built from the scope or a cleaned report
 * name, never from raw free text.
 */
export async function fetchReportFile(path: string, token: string, format: ReportFormat) {
  const response = await authorizedFetch(path, { method: 'GET' }, token);
  if (!response.ok) {
    reportSessionEnded(response, token);
    let message = `Download failed with status ${response.status}`;
    try {
      const body = (await response.json()) as { message?: string };
      message = body.message ?? message;
    } catch {
      // Not JSON: keep the status message.
    }
    throw new Error(message);
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const fileName = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? `Management_Report.${format}`;
  return { blob: await response.blob(), fileName };
}
