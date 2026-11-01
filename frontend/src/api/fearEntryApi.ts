import { apiClient } from './apiClient';
import type { FearEntry, FearEntryRequest } from '../types/vision';

export function listFearEntries(token: string) {
  return apiClient<FearEntry[]>('/fear-entries', { token });
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
