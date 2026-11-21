import { API_BASE_URL, apiClient } from './apiClient';
import type { SharedReport } from '../types/vision';

/**
 * V19 (BRD 19.0.0, RM-FR-47): a shared final report, opened without signing
 * in. Every failure is the same 404 ("This report is no longer shared").
 */
export function getSharedReport(shareToken: string) {
  return apiClient<SharedReport>(`/public/reports/${encodeURIComponent(shareToken)}`);
}

/** The shared report as a PDF, with only the shared sections. */
export async function downloadSharedReportPdf(shareToken: string) {
  const response = await fetch(`${API_BASE_URL}/public/reports/${encodeURIComponent(shareToken)}.pdf`, { referrerPolicy: 'no-referrer' });
  if (!response.ok) {
    throw new Error(response.status === 404 ? 'This report is no longer shared.' : `Download failed with status ${response.status}`);
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const fileName = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? 'Management_Report.pdf';
  return { blob: await response.blob(), fileName };
}
