import { apiClient } from './apiClient';
import type {
  DreamSuggestions, GoalSuggestions, PlanCheck, SlippingStep, StepRescue, StepSuggestions, VisionAreaSuggestions,
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
