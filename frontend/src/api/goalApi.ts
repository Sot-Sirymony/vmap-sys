import { apiClient } from './apiClient';
import type { ArchiveImpact } from '../types/vision';
import type { Goal, GoalRequest } from '../types/vision';

export function listGoals(token: string, includeArchived = false) {
  return apiClient<Goal[]>(`/goals?includeArchived=${includeArchived}`, { token });
}

export function createGoal(token: string, request: GoalRequest) {
  return apiClient<Goal>('/goals', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateGoal(token: string, id: number, request: GoalRequest) {
  return apiClient<Goal>(`/goals/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveGoal(token: string, id: number) {
  return apiClient<void>(`/goals/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function updateGoalStatus(token: string, id: number, status: string) {
  return apiClient<Goal>(`/goals/${id}/status`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ status, manualOverride: true }),
  });
}

export function restoreGoal(token: string, id: number) {
  return apiClient<void>(`/goals/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function getGoalArchiveImpact(token: string, id: number) {
  return apiClient<ArchiveImpact>(`/goals/${id}/archive-impact`, { token });
}

export function permanentlyDeleteGoal(token: string, id: number) {
  return apiClient<void>(`/goals/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
