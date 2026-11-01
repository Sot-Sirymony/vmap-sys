import { apiClient } from './apiClient';
import type { ChainAssessment, ChainAssessmentRequest, ChainAssessmentSummary } from '../types/vision';

export function getChainAssessments(token: string) {
  return apiClient<ChainAssessmentSummary>('/chain-assessments', { token });
}

export function saveChainAssessment(token: string, request: ChainAssessmentRequest) {
  return apiClient<ChainAssessment>('/chain-assessments', {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}
