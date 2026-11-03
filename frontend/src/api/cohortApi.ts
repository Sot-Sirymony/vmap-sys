import { apiClient } from './apiClient';
import type {
  CoachComment,
  CoachCommentTarget,
  CoachParticipant,
  CoachParticipantDetail,
  Cohort,
  CohortMember,
  MyCohort,
  PitchReview,
} from '../types/vision';

// V9: the participant's own cohort (D-7, D-8).

export function getMyCohort(token: string) {
  return apiClient<MyCohort>('/cohort', { token });
}

export function joinCohort(token: string, joinCode: string) {
  return apiClient<MyCohort>('/cohort/join', { method: 'POST', token, body: JSON.stringify({ joinCode }) });
}

export function leaveCohort(token: string) {
  return apiClient<MyCohort>('/cohort/leave', { method: 'POST', token });
}

export function setJournalSharing(token: string, shareJournalsWithCoach: boolean) {
  return apiClient<MyCohort>('/cohort/sharing', { method: 'PUT', token, body: JSON.stringify({ shareJournalsWithCoach }) });
}

export function listMyCoachComments(token: string) {
  return apiClient<CoachComment[]>('/coach-comments', { token });
}

export function markCoachCommentRead(token: string, id: number) {
  return apiClient<CoachComment>(`/coach-comments/${id}/read`, { method: 'POST', token });
}

// V9: the coach workspace (D-6). The server checks every participant.

export function listCoachParticipants(token: string) {
  return apiClient<CoachParticipant[]>('/coach/participants', { token });
}

export function getCoachParticipant(token: string, userId: number) {
  return apiClient<CoachParticipantDetail>(`/coach/participants/${userId}`, { token });
}

export function addCoachComment(token: string, userId: number, targetType: CoachCommentTarget, targetId: number | null, body: string) {
  return apiClient<CoachComment>(`/coach/participants/${userId}/comments`, {
    method: 'POST',
    token,
    body: JSON.stringify({ targetType, targetId, body }),
  });
}

// V9 (v0.1 FR-11): the coach's 1 to 5 rating of the participant's pitch.
export function ratePitch(token: string, userId: number, rating: number, feedback: string) {
  return apiClient<PitchReview>(`/coach/participants/${userId}/pitch-rating`, {
    method: 'PUT',
    token,
    body: JSON.stringify({ rating, feedback }),
  });
}

// V9: cohort management (admin only).

export type CohortInput = { name: string; coachEmail: string; startDate: string | null };

export function listCohorts(token: string) {
  return apiClient<Cohort[]>('/admin/cohorts', { token });
}

export function createCohort(token: string, input: CohortInput) {
  return apiClient<Cohort>('/admin/cohorts', { method: 'POST', token, body: JSON.stringify(input) });
}

export function updateCohort(token: string, id: number, input: CohortInput) {
  return apiClient<Cohort>(`/admin/cohorts/${id}`, { method: 'PUT', token, body: JSON.stringify(input) });
}

export function newJoinCode(token: string, id: number) {
  return apiClient<Cohort>(`/admin/cohorts/${id}/join-code`, { method: 'POST', token });
}

export function setCohortArchived(token: string, id: number, archived: boolean) {
  return apiClient<Cohort>(`/admin/cohorts/${id}/${archived ? 'archive' : 'restore'}`, { method: 'POST', token });
}

export function listCohortMembers(token: string, id: number) {
  return apiClient<CohortMember[]>(`/admin/cohorts/${id}/members`, { token });
}

export function addCohortMember(token: string, id: number, email: string) {
  return apiClient<CohortMember>(`/admin/cohorts/${id}/members`, { method: 'POST', token, body: JSON.stringify({ email }) });
}

export function removeCohortMember(token: string, id: number, userId: number) {
  return apiClient<void>(`/admin/cohorts/${id}/members/${userId}`, { method: 'DELETE', token });
}
