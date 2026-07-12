import { apiClient } from './apiClient';
import type { Obstacle, ObstacleRequest } from '../types/vision';

export function listObstacles(token: string, includeArchived = false) {
  return apiClient<Obstacle[]>(`/obstacles?includeArchived=${includeArchived}`, { token });
}

export function createObstacle(token: string, request: ObstacleRequest) {
  return apiClient<Obstacle>('/obstacles', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateObstacle(token: string, id: number, request: ObstacleRequest) {
  return apiClient<Obstacle>(`/obstacles/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveObstacle(token: string, id: number) {
  return apiClient<void>(`/obstacles/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function restoreObstacle(token: string, id: number) {
  return apiClient<void>(`/obstacles/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function permanentlyDeleteObstacle(token: string, id: number) {
  return apiClient<void>(`/obstacles/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
