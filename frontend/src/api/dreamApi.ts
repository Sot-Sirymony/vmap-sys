import { apiClient } from './apiClient';
import type { ArchiveImpact } from '../types/vision';
import type { Dream, DreamCommitmentRequest, DreamRequest } from '../types/vision';

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

// FR-69: sign or replace this dream's commitment.
export function commitToDream(token: string, id: number, request: DreamCommitmentRequest) {
  return apiClient<Dream>(`/dreams/${id}/commitment`, {
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

// Drag-and-drop reorder: orderedIds is the full, reshuffled set of this
// vision area's dreams.
export function reorderDreams(token: string, visionAreaId: number, orderedIds: number[]) {
  return apiClient<void>('/dreams/reorder', {
    method: 'PATCH',
    token,
    body: JSON.stringify({ parentId: visionAreaId, orderedIds }),
  });
}

/** V29 AR-FR-04: with a reason, the archive endpoint saves it; without one, archiving works as before. */
export function archiveDream(token: string, id: number, reason?: string) {
  if (reason?.trim()) {
    return apiClient<void>(`/dreams/${id}/archive`, { method: 'POST', token, body: JSON.stringify({ reason: reason.trim() }) });
  }
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

// FR-59.4: backs the completion-time contribution nudge.
export function dreamHasLinkedPartner(token: string, id: number) {
  return apiClient<{ linked: boolean }>(`/dreams/${id}/has-linked-partner`, { token }).then((answer) => answer.linked);
}
