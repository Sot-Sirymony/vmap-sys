import { apiClient } from './apiClient';
import type { GoalSynergyLink, GoalSynergyLinkRequest } from '../types/vision';

// FR-35: a goal's synergy links, read from either side.
export function listGoalSynergyLinks(token: string, goalId: number) {
  return apiClient<GoalSynergyLink[]>(`/goals/${goalId}/synergy-links`, { token });
}

export function createGoalSynergyLink(token: string, goalId: number, request: GoalSynergyLinkRequest) {
  return apiClient<GoalSynergyLink>(`/goals/${goalId}/synergy-links`, {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function deleteGoalSynergyLink(token: string, linkId: number) {
  return apiClient<void>(`/goals/synergy-links/${linkId}`, { method: 'DELETE', token });
}
