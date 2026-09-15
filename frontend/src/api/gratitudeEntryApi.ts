import { apiClient } from './apiClient';
import type { GratitudeEntry, GratitudeEntryRequest } from '../types/vision';

export function listGratitudeEntries(token: string, includeArchived = false) {
  return apiClient<GratitudeEntry[]>(`/gratitude-entries?includeArchived=${includeArchived}`, { token });
}

export function createGratitudeEntry(token: string, request: GratitudeEntryRequest) {
  return apiClient<GratitudeEntry>('/gratitude-entries', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateGratitudeEntry(token: string, id: number, request: GratitudeEntryRequest) {
  return apiClient<GratitudeEntry>(`/gratitude-entries/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveGratitudeEntry(token: string, id: number) {
  return apiClient<void>(`/gratitude-entries/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function restoreGratitudeEntry(token: string, id: number) {
  return apiClient<void>(`/gratitude-entries/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function permanentlyDeleteGratitudeEntry(token: string, id: number) {
  return apiClient<void>(`/gratitude-entries/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
