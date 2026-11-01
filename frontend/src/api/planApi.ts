import { apiClient } from './apiClient';
import type { Milestone, MilestoneRequest, PlanStatus } from '../types/vision';

export function getPlanStatus(token: string, dreamId: number) {
  return apiClient<PlanStatus>(`/plan-status/${dreamId}`, { token });
}

export function listMilestones(token: string, dreamId: number) {
  return apiClient<Milestone[]>(`/milestones?dreamId=${dreamId}`, { token });
}

export function createMilestone(token: string, request: MilestoneRequest) {
  return apiClient<Milestone>('/milestones', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveMilestone(token: string, id: number) {
  return apiClient<void>(`/milestones/${id}`, {
    method: 'DELETE',
    token,
  });
}
