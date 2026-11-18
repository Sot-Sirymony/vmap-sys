import { apiClient } from './apiClient';
import { fetchReportFile, type DeckOptions, type ReportFilter, type ReportFormat } from './reportApi';
import type { CommentaryDraft, ReportRow, SavedReport, SavedReportCommentary } from '../types/vision';

/**
 * V19 (BRD 19.0.0, RM-FR-16 to RM-FR-31): saved Management Reports. A final
 * report answers 409 to any change but a rename, and to delete.
 */

export type SavedReportSettings = ReportFilter & { name?: string; language?: string };

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

/** RM-FR-27: autosave of the commentary. */
export function saveReportCommentary(token: string, id: number, commentary: SavedReportCommentary) {
  return apiClient<SavedReport>(`/reports/saved/${id}/commentary`, { token, method: 'PUT', body: JSON.stringify(commentary) });
}

/** RM-FR-28: rule-based suggestions for the sections still empty. */
export function draftReportCommentary(token: string, id: number, language: string) {
  return apiClient<CommentaryDraft>(`/reports/saved/${id}/commentary-draft?language=${language}`, { token, method: 'POST' });
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
  const extra = format === 'pptx'
    ? `${deck.title?.trim() ? `&title=${encodeURIComponent(deck.title.trim())}` : ''}${deck.includeName === false ? '&includeName=false' : ''}`
    : '';
  return fetchReportFile(`/reports/saved/${id}.${format}?language=${language}${extra}`, token, format);
}
