import { apiClient } from './apiClient';
import type {
  DreamSuggestions, GoalRequest, GoalSuggestions, PlanAddResult, PlanCheck, PlanMetrics, SlippingStep, StepRescue,
  StepSuggestions, VisionAreaSuggestions, VisionStepRequest,
} from '../types/vision';

// V9 Phase 5: the AI vision area, dream, goal and plan builders. They only suggest; adding goes through the normal APIs.

export function suggestVisionAreas(token: string, language: string) {
  return apiClient<VisionAreaSuggestions>(`/ai/plan/vision-area-suggestions?language=${language}`, { method: 'POST', token });
}

export function suggestDreams(token: string, visionAreaId: number, language: string, stretch = false) {
  return apiClient<DreamSuggestions>(
    `/ai/plan/vision-areas/${visionAreaId}/dream-suggestions?stretch=${stretch}&language=${language}`,
    { method: 'POST', token },
  );
}

export function suggestGoals(token: string, dreamId: number, language: string, stretch = false) {
  return apiClient<GoalSuggestions>(`/ai/plan/dreams/${dreamId}/goal-suggestions?stretch=${stretch}&language=${language}`, {
    method: 'POST',
    token,
  });
}

export function suggestSteps(token: string, goalId: number, language: string) {
  return apiClient<StepSuggestions>(`/ai/plan/goals/${goalId}/step-suggestions?language=${language}`, { method: 'POST', token });
}

export function checkPlan(token: string, dreamId: number, language: string) {
  return apiClient<PlanCheck>(`/ai/plan/dreams/${dreamId}/check?language=${language}`, { method: 'POST', token });
}

export function listSlippingSteps(token: string) {
  return apiClient<SlippingStep[]>('/ai/plan/slipping-steps', { token });
}

export function rescueStep(token: string, stepId: number, language: string) {
  return apiClient<StepRescue>(`/ai/plan/steps/${stepId}/rescue?language=${language}`, { method: 'POST', token });
}

// V11 Phase 1: what happens to suggestions after they are shown.

/** PB-FR-34: 1 thumbs up, -1 thumbs down, 0 clears. */
export function ratePlanAnswer(token: string, requestId: number, rating: number) {
  return apiClient<void>(`/ai/plan/requests/${requestId}/rating`, { method: 'PUT', token, body: JSON.stringify({ rating }) });
}

/** PB-FR-35: counts only, for suggestions saved through the normal create endpoints. */
export function recordPlanOutcome(token: string, requestId: number, added: number, edited: number) {
  return apiClient<void>(`/ai/plan/requests/${requestId}/outcome`, { method: 'POST', token, body: JSON.stringify({ added, edited }) });
}

/** PB-FR-31: everything kept, saved all together or not at all. */
export function addPlan(token: string, body: {
  requestId?: number | null;
  goals?: { goal: GoalRequest; edited: boolean }[];
  steps?: { step: VisionStepRequest; edited: boolean }[];
}) {
  return apiClient<PlanAddResult>('/ai/plan/add', { method: 'POST', token, body: JSON.stringify(body) });
}

/** PB-FR-32: archives what one add saved. */
export function undoPlan(token: string, body: { requestId?: number | null; goalIds?: number[]; stepIds?: number[] }) {
  return apiClient<void>('/ai/plan/undo', { method: 'POST', token, body: JSON.stringify(body) });
}

/** PB-FR-36: admins only. */
export function getPlanMetrics(token: string, days = 30) {
  return apiClient<PlanMetrics>(`/admin/ai/plan-metrics?days=${days}`, { token });
}
