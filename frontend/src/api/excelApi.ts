import { apiClient, authorizedFetch, reportSessionEnded } from './apiClient';
import type { ExcelImportSummary } from '../types/vision';

export async function exportExcelWorkbook(token: string) {
  const response = await authorizedFetch('/excel/export', { method: 'POST' }, token);

  if (!response.ok) {
    reportSessionEnded(response, token);
    throw new Error(`Export failed with status ${response.status}`);
  }

  return response.blob();
}

export function importExcelWorkbook(token: string, file: File) {
  const body = new FormData();
  body.append('file', file);
  return apiClient<ExcelImportSummary>('/excel/import', {
    method: 'POST',
    token,
    body,
  });
}
