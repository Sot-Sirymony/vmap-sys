import { apiClient } from './apiClient';
import type { ArchiveImpact } from '../types/vision';
import type { VisionStep, VisionStepRequest } from '../types/vision';

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

export function archiveStep(token: string, id: number) {
  return apiClient<void>(`/steps/${id}`, {
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
