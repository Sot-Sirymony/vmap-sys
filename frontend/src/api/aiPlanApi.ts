import { apiClient } from './apiClient';
import type { GoalSuggestions, PlanCheck, SlippingStep, StepRescue, StepSuggestions } from '../types/vision';

// V9 Phase 5: the AI goal and plan builders. They only suggest; adding goes through goalApi and stepApi.

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
