import { apiClient } from './apiClient';
import { fetchReportFile, fileOptions, type DeckOptions, type ReportFilter, type ReportFormat } from './reportApi';
import type {
  CommentaryDraft, ReportExport, ReportReminderStatus, ReportRow, ReportSection, ReportShare, SavedReport, SavedReportCommentary,
} from '../types/vision';

/**
 * V19 (BRD 19.0.0, RM-FR-16 to RM-FR-31): saved Management Reports. A final
 * report answers 409 to any change but a rename, and to delete.
 */

/** compareToReportId: a final report on the same scope; null or left out for the latest one (RM-FR-32). */
export type SavedReportSettings = ReportFilter & { name?: string; language?: string; compareToReportId?: number | null };

/** RM-FR-17: newest change first; archived ones only when asked for. */
export function listSavedReports(token: string, includeArchived = false) {
  return apiClient<SavedReport[]>(`/reports/saved${includeArchived ? '?includeArchived=true' : ''}`, { token });
}

function body(settings: SavedReportSettings) {
  return {
    name: settings.name,
    areaId: settings.areaId,
    dreamId: settings.dreamId,
    period: settings.period,
    from: settings.period === 'CUSTOM' ? settings.from : undefined,
    to: settings.period === 'CUSTOM' ? settings.to : undefined,
    priorities: settings.priorities ?? [],
    owner: settings.owner,
    healths: settings.healths ?? [],
    includeCompleted: settings.includeCompleted ?? true,
    language: settings.language,
    compareToReportId: settings.compareToReportId ?? undefined,
  };
}

/** RM-FR-16: Save as report, from the scope, period and filters in use. */
export function createSavedReport(token: string, settings: SavedReportSettings) {
  return apiClient<SavedReport>('/reports/saved', { token, method: 'POST', body: JSON.stringify(body(settings)) });
}

export function getSavedReport(token: string, id: number) {
  return apiClient<SavedReport>(`/reports/saved/${id}`, { token });
}

/** RM-FR-18, RM-FR-23: a draft's settings, or a final report's name only. */
export function updateSavedReport(token: string, id: number, settings: SavedReportSettings) {
  return apiClient<SavedReport>(`/reports/saved/${id}`, { token, method: 'PUT', body: JSON.stringify(body(settings)) });
}

/** RM-FR-23: rename, the one change a final report allows. */
export function renameSavedReport(token: string, id: number, name: string) {
  return apiClient<SavedReport>(`/reports/saved/${id}`, { token, method: 'PUT', body: JSON.stringify({ name }) });
}

/** RM-FR-39: the sections the report's files include, remembered for the next download; an empty list means all. */
export function saveReportSections(token: string, id: number, sections: ReportSection[]) {
  return apiClient<SavedReport>(`/reports/saved/${id}/sections`, { token, method: 'PUT', body: JSON.stringify({ sections }) });
}

/** RM-FR-27: autosave of the commentary. */
export function saveReportCommentary(token: string, id: number, commentary: SavedReportCommentary) {
  return apiClient<SavedReport>(`/reports/saved/${id}/commentary`, { token, method: 'PUT', body: JSON.stringify(commentary) });
}

/** RM-FR-28 rule-based, or RM-FR-29 AI, suggestions for the sections still empty. */
export function draftReportCommentary(token: string, id: number, language: string, mode: 'RULES' | 'AI' = 'RULES') {
  return apiClient<CommentaryDraft>(`/reports/saved/${id}/commentary-draft?language=${language}&mode=${mode}`, { token, method: 'POST' });
}

/** RM-FR-49: whether the report reminder is due, and the latest final report. */
export function getReportReminder(token: string) {
  return apiClient<ReportReminderStatus>('/reports/saved/reminder', { token });
}

/** RM-FR-19: freeze the figures. */
export function finalizeSavedReport(token: string, id: number, language: string) {
  return apiClient<SavedReport>(`/reports/saved/${id}/finalize?language=${language}`, { token, method: 'POST' });
}

/** RM-FR-21: a new draft for the next period, from this report. */
export function copySavedReport(token: string, id: number) {
  return apiClient<SavedReport>(`/reports/saved/${id}/copy`, { token, method: 'POST' });
}

export function archiveSavedReport(token: string, id: number) {
  return apiClient<SavedReport>(`/reports/saved/${id}/archive`, { token, method: 'PATCH' });
}

export function restoreSavedReport(token: string, id: number) {
  return apiClient<SavedReport>(`/reports/saved/${id}/restore`, { token, method: 'PATCH' });
}

/** RM-FR-22: drafts only. */
export function deleteSavedReport(token: string, id: number) {
  return apiClient<void>(`/reports/saved/${id}`, { token, method: 'DELETE' });
}

export async function getSavedReportRows(token: string, id: number, language: string) {
  const result = await apiClient<{ rows: ReportRow[] }>(`/reports/saved/${id}/rows?language=${language}`, { token });
  return result.rows;
}

/** RM-FR-38: files from a saved report; a final report's come from its snapshot. */
export function downloadSavedReport(token: string, id: number, format: ReportFormat, language: string, deck: DeckOptions = {}) {
  return fetchReportFile(`/reports/saved/${id}.${format}?language=${language}${fileOptions(format, deck)}`, token, format);
}

/** RM-FR-25: the report's downloads, newest first. */
export function listReportExports(token: string, id: number) {
  return apiClient<ReportExport[]>(`/reports/saved/${id}/exports`, { token });
}

/** RM-FR-46: the report's share links. */
export function listReportShares(token: string, id: number) {
  return apiClient<ReportShare[]>(`/reports/saved/${id}/shares`, { token });
}

/** RM-FR-46, RM-FR-48: a read-only link to a final report; the answer holds the token, shown only this once. */
export function createReportShare(token: string, id: number, expiresInDays: number, sections: ReportSection[]) {
  return apiClient<ReportShare>(`/reports/saved/${id}/shares`, {
    token, method: 'POST', body: JSON.stringify({ expiresInDays, sections }),
  });
}

/** RM-FR-46: stop a link at once. */
export function revokeReportShare(token: string, id: number, shareId: number) {
  return apiClient<ReportShare>(`/reports/saved/${id}/shares/${shareId}/revoke`, { token, method: 'PATCH' });
}
