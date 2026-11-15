import { apiClient, authorizedFetch, reportSessionEnded } from './apiClient';
import type { ManagementReport } from '../types/vision';

/** V18 MR-FR-03: at most one of areaId and dreamId; neither means all dreams. */
export type ReportFilter = { areaId?: number; dreamId?: number };

function query(filter: ReportFilter) {
  if (filter.dreamId) return `?dreamId=${filter.dreamId}`;
  if (filter.areaId) return `?areaId=${filter.areaId}`;
  return '';
}

export function getManagementReport(token: string, filter: ReportFilter) {
  return apiClient<ManagementReport>(`/reports/management${query(filter)}`, { token });
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
  const response = await authorizedFetch(`/reports/management.${format}${query(filter)}${separator}language=${language}${extra}`, { method: 'GET' }, token);
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
