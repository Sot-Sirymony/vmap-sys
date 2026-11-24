import { apiClient } from './apiClient';
import type {
  RepeatCadence,
  StreamlineCandidate,
  StreamlineItemUpdate,
  StreamlineListItem,
  StreamlineReview,
  StreamlineSimplify,
  StreamlineSuggestions,
  StreamlineVerdict,
  RequirementSource,
  StreamlineRepeatRule,
} from '../types/streamline';

// V27 (BRD 27): the Streamline review API.
const base = '/streamline-reviews';

function send<T>(token: string, path: string, method: string, body?: unknown) {
  return apiClient<T>(path, { method, token, body: body === undefined ? undefined : JSON.stringify(body) });
}

export function listStreamlineReviews(token: string) {
  return apiClient<StreamlineListItem[]>(base, { token });
}

export function listStreamlineCandidates(token: string) {
  return apiClient<StreamlineCandidate[]>(`${base}/candidates`, { token });
}

export function startStreamlineReview(token: string, anchor: { goalId?: number; stepId?: number }) {
  return send<StreamlineReview>(token, base, 'POST', anchor);
}

export function getStreamlineReview(token: string, id: number) {
  return apiClient<StreamlineReview>(`${base}/${id}`, { token });
}

export function updateStreamlineItem(token: string, id: number, itemId: number, update: StreamlineItemUpdate) {
  return send<StreamlineReview>(token, `${base}/${id}/items/${itemId}`, 'PUT', update);
}

export function bulkQuestionStreamline(
  token: string,
  id: number,
  body: { itemIds: number[]; requirementOwner?: string; requirementSource?: RequirementSource; whyNeeded?: string; verdict?: StreamlineVerdict },
) {
  return send<StreamlineReview>(token, `${base}/${id}/items/bulk`, 'PUT', body);
}

export function deleteStreamlineItem(token: string, id: number, itemId: number, confirmEmpty = false) {
  return send<StreamlineReview>(token, `${base}/${id}/items/${itemId}/delete`, 'POST', { confirmEmpty });
}

export function restoreStreamlineItem(token: string, id: number, itemId: number) {
  return send<StreamlineReview>(token, `${base}/${id}/items/${itemId}/restore`, 'POST');
}

export function mergeStreamlineItem(token: string, id: number, itemId: number, targetItemId: number, confirmEmpty = false) {
  return send<StreamlineReview>(token, `${base}/${id}/items/${itemId}/merge`, 'POST', { targetItemId, confirmEmpty });
}

export function simplifyStreamlineItem(token: string, id: number, itemId: number, body: StreamlineSimplify) {
  return send<StreamlineReview>(token, `${base}/${id}/items/${itemId}/simplify`, 'PUT', body);
}

export function repeatStreamlineItem(token: string, id: number, itemId: number, cadence: RepeatCadence) {
  return send<StreamlineReview>(token, `${base}/${id}/items/${itemId}/repeat`, 'POST', { cadence });
}

export function setStreamlineTarget(token: string, id: number, targetCycleDays: number) {
  return send<StreamlineReview>(token, `${base}/${id}/target`, 'PUT', { targetCycleDays });
}

export function completeStreamlineStage(token: string, id: number, stage: number) {
  return send<StreamlineReview>(token, `${base}/${id}/stages/${stage}/complete`, 'POST');
}

export function skipStreamlineStage(token: string, id: number, stage: number, reason: string) {
  return send<StreamlineReview>(token, `${base}/${id}/stages/${stage}/not-needed`, 'POST', { reason });
}

export function abandonStreamlineReview(token: string, id: number) {
  return send<StreamlineReview>(token, `${base}/${id}/abandon`, 'POST');
}

export function suggestStreamline(token: string, id: number, stage: number, language: string) {
  return send<StreamlineSuggestions>(token, `${base}/${id}/stages/${stage}/suggestions?language=${language}`, 'POST');
}

/** D-27-10: a coach's read-only view of a participant's reviews. */
export function listParticipantStreamlineReviews(token: string, participantId: number) {
  return apiClient<StreamlineListItem[]>(`/coach/participants/${participantId}/streamline-reviews`, { token });
}

export function getParticipantStreamlineReview(token: string, participantId: number, reviewId: number) {
  return apiClient<StreamlineReview>(`/coach/participants/${participantId}/streamline-reviews/${reviewId}`, { token });
}

/** SR-FR-09: repeating task rules. */
export function listTaskRepeatRules(token: string) {
  return apiClient<StreamlineRepeatRule[]>('/task-repeat-rules', { token });
}

export function updateTaskRepeatRule(token: string, id: number, body: { cadence?: RepeatCadence; active?: boolean }) {
  return send<StreamlineRepeatRule>(token, `/task-repeat-rules/${id}`, 'PUT', body);
}

export function stopTaskRepeatRule(token: string, id: number) {
  return send<void>(token, `/task-repeat-rules/${id}`, 'DELETE');
}
