import { apiClient } from './apiClient';
import type { ArchiveImpact } from '../types/vision';
import type { Dream, DreamRequest } from '../types/vision';

export function listDreams(token: string, includeArchived = false) {
  return apiClient<Dream[]>(`/dreams?includeArchived=${includeArchived}`, { token });
}

export function createDream(token: string, request: DreamRequest) {
  return apiClient<Dream>('/dreams', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateDream(token: string, id: number, request: DreamRequest) {
  return apiClient<Dream>(`/dreams/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveDream(token: string, id: number) {
  return apiClient<void>(`/dreams/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function restoreDream(token: string, id: number) {
  return apiClient<void>(`/dreams/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function getDreamArchiveImpact(token: string, id: number) {
  return apiClient<ArchiveImpact>(`/dreams/${id}/archive-impact`, { token });
}

export function permanentlyDeleteDream(token: string, id: number) {
  return apiClient<void>(`/dreams/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
