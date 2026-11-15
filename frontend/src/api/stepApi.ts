import { apiClient } from './apiClient';
import type { ArchiveImpact } from '../types/vision';
import type { GapStepRequest, GapStepResult, VisionStep, VisionStepRequest } from '../types/vision';

export function listSteps(token: string, includeArchived = false) {
  return apiClient<VisionStep[]>(`/steps?includeArchived=${includeArchived}`, { token });
}

export function createStep(token: string, request: VisionStepRequest) {
  return apiClient<VisionStep>('/steps', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateStep(token: string, id: number, request: VisionStepRequest) {
  return apiClient<VisionStep>(`/steps/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

// Status alone, so moving an older step that has no target date yet still works.
export function updateStepStatus(token: string, id: number, status: string) {
  return apiClient<VisionStep>(`/steps/${id}/status`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ status, manualOverride: false }),
  });
}

// Vision Map drag-and-drop: orderedIds is the full, reshuffled set of this
// goal's steps.
export function reorderSteps(token: string, goalId: number, orderedIds: number[]) {
  return apiClient<void>('/steps/reorder', {
    method: 'PATCH',
    token,
    body: JSON.stringify({ parentId: goalId, orderedIds }),
  });
}

// V17 BL-FR-21: removePartner also archives the partner a "find help" step created.
export function archiveStep(token: string, id: number, removePartner = false) {
  return apiClient<void>(`/steps/${id}${removePartner ? '?removePartner=true' : ''}`, {
    method: 'DELETE',
    token,
  });
}

export function restoreStep(token: string, id: number) {
  return apiClient<void>(`/steps/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function getStepArchiveImpact(token: string, id: number) {
  return apiClient<ArchiveImpact>(`/steps/${id}/archive-impact`, { token });
}

export function permanentlyDeleteStep(token: string, id: number) {
  return apiClient<void>(`/steps/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}

// V17 BL-FR-19: turns this step's gap into a "find help" step placed just before it.
export function createGapStep(token: string, id: number, request: GapStepRequest) {
  return apiClient<GapStepResult>(`/steps/${id}/gap-step`, {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}
