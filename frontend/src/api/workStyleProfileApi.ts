import { apiClient } from './apiClient';
import type { WorkStyleAssessmentRequest, WorkStyleBalance, WorkStyleHelpAnswer, WorkStyleHelpMode, WorkStyleProfile } from '../types/vision';

/**
 * FR-49: the user's work-style profile, stored per account. Neither call
 * takes a user id — the backend resolves the caller from the token (BR-38).
 */
export function getWorkStyleProfile(token: string) {
  return apiClient<WorkStyleProfile>('/work-style-profile', { token });
}

/** FR-49.1: retaking overwrites the prior result outright — a full replace. */
export function submitWorkStyleAssessment(token: string, request: WorkStyleAssessmentRequest) {
  return apiClient<WorkStyleProfile>('/work-style-profile', {
    token,
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

/** V15 (AH-FR-76): turns "Let AI helpers use my work style" on or off. */
export function setWorkStyleAiUse(token: string, enabled: boolean) {
  return apiClient<WorkStyleProfile>('/work-style-profile/ai-use', { token, method: 'PUT', body: JSON.stringify({ enabled }) });
}

/** V15 (AH-FR-73): partners by style and the complementary styles still missing. No AI. */
export function getPartnerBalance(token: string) {
  return apiClient<WorkStyleBalance>('/work-style-profile/partner-balance', { token });
}

/** V15 (AH-FR-70, AH-FR-71, AH-FR-73, AH-FR-75): one AI Work Style helper. Nothing is saved. */
export function workStyleHelp(token: string, language: string, request: { mode: WorkStyleHelpMode; partnerId?: number; partnerNotes?: string }) {
  return apiClient<WorkStyleHelpAnswer>(`/ai/work-style/help?language=${language}`, { token, method: 'POST', body: JSON.stringify(request) });
}
