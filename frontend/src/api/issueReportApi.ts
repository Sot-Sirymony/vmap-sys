import { apiClient } from './apiClient';
import type {
  IssueReport,
  IssueReportRequest,
  IssueReportStatusUpdateRequest,
} from '../types/vision';

// FR-38.3: the caller's own reports.
export function listMyIssueReports(token: string, includeArchived = false) {
  return apiClient<IssueReport[]>(`/issue-reports?includeArchived=${includeArchived}`, { token });
}

// FR-38.4: admin triage queue, optionally filtered. Empty values mean "no filter".
export function listAllIssueReports(
  token: string,
  filters: { reportType?: string; status?: string; severity?: string } = {},
) {
  const params = new URLSearchParams();
  if (filters.reportType) params.set('reportType', filters.reportType);
  if (filters.status) params.set('status', filters.status);
  if (filters.severity) params.set('severity', filters.severity);
  const query = params.toString();
  return apiClient<IssueReport[]>(`/issue-reports/all${query ? `?${query}` : ''}`, { token });
}

export function createIssueReport(token: string, request: IssueReportRequest) {
  return apiClient<IssueReport>('/issue-reports', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateIssueReportStatus(
  token: string,
  id: number,
  request: IssueReportStatusUpdateRequest,
) {
  return apiClient<IssueReport>(`/issue-reports/${id}/status`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveIssueReport(token: string, id: number) {
  return apiClient<void>(`/issue-reports/${id}`, { method: 'DELETE', token });
}

export function restoreIssueReport(token: string, id: number) {
  return apiClient<void>(`/issue-reports/${id}/restore`, { method: 'POST', token });
}

export function permanentlyDeleteIssueReport(token: string, id: number) {
  return apiClient<void>(`/issue-reports/${id}/permanent`, { method: 'DELETE', token });
}
