import { apiClient, fetchAllPages } from './apiClient';
import type { InnerBlocker, InnerBlockerAnswer, InnerBlockerCheckin, InnerBlockerRequest, InnerBlockerStatus } from '../types/vision';

/** V34 (BRD 34): the participant's own inner blockers. */
export type InnerBlockerFilter = {
  status?: InnerBlockerStatus;
  archived?: boolean;
  /** With includeAllDreams, also the blockers for all dreams (stage 3). */
  dreamId?: number;
  includeAllDreams?: boolean;
  /** The local day, so each linked habit's streak is filled in. */
  date?: string;
};

export function listInnerBlockers(token: string, filter: InnerBlockerFilter = {}) {
  const params = new URLSearchParams();
  if (filter.status) params.set('status', filter.status);
  if (filter.archived) params.set('archived', 'true');
  if (filter.dreamId !== undefined) params.set('dreamId', String(filter.dreamId));
  if (filter.includeAllDreams) params.set('includeAllDreams', 'true');
  if (filter.date) params.set('date', filter.date);
  const query = params.toString();
  return fetchAllPages<InnerBlocker>(`/inner-blockers${query ? `?${query}` : ''}`, token);
}

export function createInnerBlocker(token: string, request: InnerBlockerRequest) {
  return apiClient<InnerBlocker>('/inner-blockers', { method: 'POST', token, body: JSON.stringify(request) });
}

export function updateInnerBlocker(token: string, id: number, request: InnerBlockerRequest) {
  return apiClient<InnerBlocker>(`/inner-blockers/${id}`, { method: 'PUT', token, body: JSON.stringify(request) });
}

export function changeInnerBlockerStatus(token: string, id: number, status: InnerBlockerStatus, stopHabit: boolean) {
  return apiClient<InnerBlocker>(`/inner-blockers/${id}/status`, { method: 'PATCH', token, body: JSON.stringify({ status, stopHabit }) });
}

export function archiveInnerBlocker(token: string, id: number, stopHabit: boolean) {
  return apiClient<void>(`/inner-blockers/${id}/archive?stopHabit=${stopHabit}`, { method: 'POST', token });
}

export function restoreInnerBlocker(token: string, id: number) {
  return apiClient<void>(`/inner-blockers/${id}/restore`, { method: 'POST', token });
}

export function makeInnerBlockerHabit(token: string, id: number, date: string, name: string) {
  return apiClient<InnerBlocker>(`/inner-blockers/${id}/habit?date=${date}`, { method: 'POST', token, body: JSON.stringify({ name }) });
}

export function getInnerBlockerCheckins(token: string, reviewId: number) {
  return apiClient<{ checkins: InnerBlockerCheckin[] }>(`/reviews/${reviewId}/inner-blocker-checkins`, { token });
}

export function saveInnerBlockerCheckins(token: string, reviewId: number, checkins: { innerBlockerId: number; answer: InnerBlockerAnswer }[]) {
  return apiClient<{ checkins: InnerBlockerCheckin[] }>(`/reviews/${reviewId}/inner-blocker-checkins`, {
    method: 'PUT',
    token,
    body: JSON.stringify({ checkins }),
  });
}
