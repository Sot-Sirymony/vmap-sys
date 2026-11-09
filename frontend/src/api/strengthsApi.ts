import { apiClient } from './apiClient';
import type {
  GapCoverRemoval,
  GapCoverRequest,
  SelfAssessmentItem,
  SelfAssessmentItemRequest,
  StrengthsProfile,
} from '../types/vision';

// V8.1 FR-79 to FR-84: the Strengths and Gaps profile.

export function listSelfAssessmentItems(token: string, dreamId: number, includeArchived = false) {
  return apiClient<SelfAssessmentItem[]>(`/self-assessment-items?dreamId=${dreamId}&includeArchived=${includeArchived}`, { token });
}

export function createSelfAssessmentItem(token: string, request: SelfAssessmentItemRequest) {
  return apiClient<SelfAssessmentItem>('/self-assessment-items', { method: 'POST', token, body: JSON.stringify(request) });
}

export function updateSelfAssessmentItem(token: string, id: number, request: SelfAssessmentItemRequest) {
  return apiClient<SelfAssessmentItem>(`/self-assessment-items/${id}`, { method: 'PUT', token, body: JSON.stringify(request) });
}

export function setGapCover(token: string, id: number, request: GapCoverRequest) {
  return apiClient<SelfAssessmentItem>(`/self-assessment-items/${id}/cover`, { method: 'PUT', token, body: JSON.stringify(request) });
}

export function removeGapCover(token: string, id: number, removeLinked: boolean) {
  return apiClient<GapCoverRemoval>(`/self-assessment-items/${id}/cover?removeLinked=${removeLinked}`, { method: 'DELETE', token });
}

export function archiveSelfAssessmentItem(token: string, id: number, removeLinked: boolean) {
  return apiClient<GapCoverRemoval>(`/self-assessment-items/${id}?removeLinked=${removeLinked}`, { method: 'DELETE', token });
}

export function restoreSelfAssessmentItem(token: string, id: number) {
  return apiClient<void>(`/self-assessment-items/${id}/restore`, { method: 'POST', token });
}

export function getStrengthsProfile(token: string, dreamId: number) {
  return apiClient<StrengthsProfile>(`/strengths-profiles/${dreamId}`, { token });
}

export function saveStrengthsProfile(token: string, dreamId: number) {
  return apiClient<StrengthsProfile>(`/strengths-profiles/${dreamId}/snapshots`, { method: 'POST', token });
}
