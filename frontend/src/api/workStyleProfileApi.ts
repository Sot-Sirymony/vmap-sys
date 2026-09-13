import { apiClient } from './apiClient';
import type { WorkStyleAssessmentRequest, WorkStyleProfile } from '../types/vision';

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
