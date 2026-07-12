import { apiClient } from './apiClient';
import type { ArchiveImpact } from '../types/vision';
import type { VisionArea, VisionAreaRequest } from '../types/vision';

export function listVisionAreas(token: string, includeArchived = false) {
  return apiClient<VisionArea[]>(`/vision-areas?includeArchived=${includeArchived}`, { token });
}

export function createVisionArea(token: string, request: VisionAreaRequest) {
  return apiClient<VisionArea>('/vision-areas', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateVisionArea(token: string, id: number, request: VisionAreaRequest) {
  return apiClient<VisionArea>(`/vision-areas/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveVisionArea(token: string, id: number) {
  return apiClient<void>(`/vision-areas/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function restoreVisionArea(token: string, id: number) {
  return apiClient<void>(`/vision-areas/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function getVisionAreaArchiveImpact(token: string, id: number) {
  return apiClient<ArchiveImpact>(`/vision-areas/${id}/archive-impact`, { token });
}

export function permanentlyDeleteVisionArea(token: string, id: number) {
  return apiClient<void>(`/vision-areas/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
