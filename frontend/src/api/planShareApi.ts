import { apiClient } from './apiClient';
import type { PlanShare, SharedPlan } from '../types/vision';

// V9 (v0.1 FR-10): read-only plan links for a partner or mentor.

export type PlanShareInput = { dreamId: number; partnerId: number | null; message: string; expiresInDays: number };

export function listPlanShares(token: string, dreamId: number) {
  return apiClient<PlanShare[]>(`/plan-shares?dreamId=${dreamId}`, { token });
}

export function createPlanShare(token: string, input: PlanShareInput) {
  return apiClient<PlanShare>('/plan-shares', { method: 'POST', token, body: JSON.stringify(input) });
}

export function revokePlanShare(token: string, id: number) {
  return apiClient<PlanShare>(`/plan-shares/${id}/revoke`, { method: 'POST', token });
}

/** Public: no token. The token in the link is the only key. */
export function getSharedPlan(shareToken: string) {
  return apiClient<SharedPlan>(`/shared-plans/${encodeURIComponent(shareToken)}`);
}

export function shareLink(shareToken: string) {
  return `${window.location.origin}/shared/${shareToken}`;
}
