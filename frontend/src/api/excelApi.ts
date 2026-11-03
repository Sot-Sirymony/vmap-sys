import { API_BASE_URL, apiClient, reportSessionEnded } from './apiClient';
import type { ExcelImportSummary } from '../types/vision';

export async function exportExcelWorkbook(token: string) {
  const response = await fetch(`${API_BASE_URL}/excel/export`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

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
