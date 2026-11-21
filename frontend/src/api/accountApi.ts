import { apiClient, authorizedFetch, reportSessionEnded } from './apiClient';
import type { ProgressMetrics, ReminderSettings, ReportReminderCadence } from '../types/vision';

export function getProgressMetrics(token: string, date: string) {
  return apiClient<ProgressMetrics>(`/progress-metrics?date=${date}`, { token });
}

export function getReminderSettings(token: string) {
  return apiClient<ReminderSettings>('/account/reminders', { token });
}

/**
 * A blank reminderEmail sends reminders to the login email. Without a
 * report reminder (V19 RM-FR-49), the saved one is kept as it was.
 */
export function updateReminderSettings(token: string, enabled: boolean, timeZone: string, reminderEmail = '',
  options?: { reportReminder: ReportReminderCadence; reportReminderDay: number | null }) {
  return apiClient<ReminderSettings>('/account/reminders', {
    method: 'PUT',
    token,
    body: JSON.stringify({ enabled, timeZone, reminderEmail, ...options }),
  });
}

export function sendTestReminder(token: string) {
  return apiClient<ReminderSettings>('/account/reminders/test', { method: 'POST', token });
}

export async function exportAccountData(token: string) {
  const response = await authorizedFetch('/account/export', {}, token);
  if (!response.ok) {
    reportSessionEnded(response, token);
    throw new Error(`Export failed with status ${response.status}`);
  }
  return response.blob();
}

export async function downloadPlanPdf(token: string, dreamId: number) {
  const response = await authorizedFetch(`/dreams/${dreamId}/plan.pdf`, {}, token);
  if (!response.ok) {
    reportSessionEnded(response, token);
    throw new Error(`Download failed with status ${response.status}`);
  }
  return response.blob();
}

export function deleteAccount(token: string, password: string) {
  return apiClient<void>('/account/delete', {
    method: 'POST',
    token,
    body: JSON.stringify({ password }),
  });
}
