import { apiClient } from './apiClient';
import type { NextStep, OutreachDraft, PitchAudience, PitchFeedback, ResourceIdea, WeeklyReviewDraft } from '../types/vision';

// V9 Phase 6: AI for daily use. Suggestions only; saving goes through the normal endpoints.

export function suggestNextStep(token: string, minutes: number, language: string) {
  return apiClient<NextStep>(`/ai/daily/next-step?minutes=${minutes}&language=${language}`, { method: 'POST', token });
}

export function draftWeeklyReview(token: string, language: string) {
  return apiClient<WeeklyReviewDraft>(`/ai/daily/weekly-review-draft?language=${language}`, { method: 'POST', token });
}

export function getPitchFeedback(token: string, audience: PitchAudience, pitch: string, language: string) {
  return apiClient<PitchFeedback>('/ai/daily/pitch-feedback', {
    method: 'POST',
    token,
    body: JSON.stringify({ audience, pitch, language }),
  });
}

export type OutreachInput = { partnerId: number | null; relatedDreamId: number | null; purpose: string; request: string };

export function draftOutreach(token: string, input: OutreachInput, language: string) {
  return apiClient<OutreachDraft>('/ai/daily/outreach-draft', {
    method: 'POST',
    token,
    body: JSON.stringify({ ...input, language }),
  });
}

export function getResourceIdeas(token: string, goalId: number, language: string) {
  return apiClient<{ resources: ResourceIdea[] }>(`/ai/daily/goals/${goalId}/resource-ideas?language=${language}`, {
    method: 'POST',
    token,
  });
}
