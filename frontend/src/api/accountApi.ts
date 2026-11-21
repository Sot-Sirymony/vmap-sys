import { apiClient, apiErrorFrom, authorizedFetch, reportSessionEnded } from './apiClient';
import { stepUpHeader } from './stepUpApi';
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
  options?: { reportReminder: ReportReminderCadence; reportReminderDay: number | null },
  /** V20 SV-FR-04: a new address needs the account code and a code sent to that address. */
  stepUp?: { account?: number; confirm?: number }) {
  return apiClient<ReminderSettings>('/account/reminders', {
    method: 'PUT',
    token,
    headers: { ...stepUpHeader(stepUp?.account), ...(stepUp?.confirm ? { 'X-Step-Up-Confirm': String(stepUp.confirm) } : {}) },
    body: JSON.stringify({ enabled, timeZone, reminderEmail, ...options }),
  });
}

export function sendTestReminder(token: string) {
  return apiClient<ReminderSettings>('/account/reminders/test', { method: 'POST', token });
}

/** V20 SV-FR-03: needs a verified "Verify it's you" code (stepUp) or the session's verified window. */
export async function exportAccountData(token: string, stepUp?: number) {
  const response = await authorizedFetch('/account/export', { headers: stepUpHeader(stepUp) }, token);
  if (!response.ok) {
    reportSessionEnded(response, token);
    throw await apiErrorFrom(response);
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

/** V20 SV-FR-02: needs a fresh "Verify it's you" code (stepUp). */
export function deleteAccount(token: string, password: string, stepUp?: number) {
  return apiClient<void>('/account/delete', {
    method: 'POST',
    headers: stepUpHeader(stepUp),
    token,
    body: JSON.stringify({ password }),
  });
}
