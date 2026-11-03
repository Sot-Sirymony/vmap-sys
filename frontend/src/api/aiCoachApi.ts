import { apiClient } from './apiClient';
import type { AiCohortSummary, AiParticipantSummary, CoachCommentTarget } from '../types/vision';

// V9 Phase 8: the coach co-pilot. Summaries are kept for the week; GET never calls Claude.

export function getParticipantSummary(token: string, userId: number) {
  return apiClient<AiParticipantSummary | undefined>(`/coach/participants/${userId}/ai-summary`, { token });
}

export function generateParticipantSummary(token: string, userId: number, language: string) {
  return apiClient<AiParticipantSummary>(`/coach/participants/${userId}/ai-summary?language=${language}`, { method: 'POST', token });
}

export function getCohortSummary(token: string, cohortId: number) {
  return apiClient<AiCohortSummary | undefined>(`/coach/cohorts/${cohortId}/ai-summary`, { token });
}

export function generateCohortSummary(token: string, cohortId: number, language: string) {
  return apiClient<AiCohortSummary>(`/coach/cohorts/${cohortId}/ai-summary?language=${language}`, { method: 'POST', token });
}

export function rateSummary(token: string, summaryId: number, accurate: boolean) {
  return apiClient<void>(`/coach/ai-summaries/${summaryId}/rating`, { method: 'PUT', token, body: JSON.stringify({ accurate }) });
}

export function draftCoachFeedback(token: string, userId: number, targetType: CoachCommentTarget, targetId: number | null, language: string) {
  return apiClient<{ message?: string | null }>(`/coach/participants/${userId}/feedback-draft`, {
    method: 'POST',
    token,
    body: JSON.stringify({ targetType, targetId, language }),
  });
}
