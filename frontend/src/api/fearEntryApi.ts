import { apiClient } from './apiClient';
import type { FearEntry, FearEntryRequest } from '../types/vision';

export function listFearEntries(token: string, includeArchived = false) {
  return apiClient<FearEntry[]>(`/fear-entries?includeArchived=${includeArchived}`, { token });
}

export function createFearEntry(token: string, request: FearEntryRequest) {
  return apiClient<FearEntry>('/fear-entries', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveFearEntry(token: string, id: number) {
  return apiClient<void>(`/fear-entries/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function updateFearEntry(token: string, id: number, request: FearEntryRequest) {
  return apiClient<FearEntry>(`/fear-entries/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function restoreFearEntry(token: string, id: number) {
  return apiClient<void>(`/fear-entries/${id}/restore`, {
    method: 'POST',
    token,
  });
}
