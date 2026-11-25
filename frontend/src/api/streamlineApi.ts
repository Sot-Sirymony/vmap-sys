import { apiClient } from './apiClient';
import type {
  RepeatCadence, RequirementSource, StreamlineAdvice, StreamlineBatchAction, StreamlineBatchResult, StreamlineBatchView, StreamlineDraftAll, StreamlineDrafts, StreamlineUndoResult, StreamlineCandidate, StreamlineItemUpdate, StreamlineListItem, StreamlinePicks, StreamlineRepeatRule, StreamlineReview, StreamlineSimplify, StreamlineSuggestions, StreamlineVerdict,
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

/** V32 SF-FR-01: the saved suggestions for a stage, or null when none still fit. Uses no AI. */
export async function getSavedStreamlineSuggestions(token: string, id: number, stage: number) {
  const answer = await apiClient<StreamlineSuggestions | undefined>(`${base}/${id}/stages/${stage}/suggestions`, { token });
  return answer ?? null;
}

/** V32 D-32-1: removes a dismissed card from the saved set. */
export function dismissStreamlineSuggestion(token: string, id: number, stage: number,
  card: { itemId: number; action: string; targetItemId: number | null }) {
  return send<void>(token, `${base}/${id}/stages/${stage}/suggestions/dismiss`, 'POST',
    { itemId: card.itemId, action: card.action, targetItemId: card.targetItemId });
}

/** V32 SF-FR-08 (D-32-7): one AI request drafting the current stage and every later one. */
export function draftAllStreamline(token: string, id: number, language: string) {
  return send<StreamlineDraftAll>(token, `${base}/${id}/draft-all?language=${language}`, 'POST');
}

/** V32 (D-32-7, D-32-8): saved drafts for the current and later stages, and whether Draft all stages can run. */
export function getStreamlineDrafts(token: string, id: number) {
  return apiClient<StreamlineDrafts>(`${base}/${id}/drafts`, { token });
}

/** V32 SF-FR-05 (D-32-4): Apply selected or Apply all as one batch; stops at the first failure. */
export function applyStreamlineBatch(token: string, id: number, stage: number, actions: StreamlineBatchAction[]) {
  return send<StreamlineBatchResult>(token, `${base}/${id}/apply-batch`, 'POST', { stage, actions });
}

/** V32 SF-FR-06 (D-32-5): undo a whole batch. */
export function undoStreamlineBatch(token: string, id: number, batchId: number) {
  return send<StreamlineUndoResult>(token, `${base}/${id}/batches/${batchId}/undo`, 'POST');
}

/** V32 SF-FR-07: the latest batches on a stage, newest first. */
export function listStreamlineBatches(token: string, id: number, stage: number) {
  return apiClient<StreamlineBatchView[]>(`${base}/${id}/batches?stage=${stage}`, { token });
}

/** V32 SF-FR-15 (D-32-12): dismissed suggestions, saved so they stay dismissed on every device. */
export function dismissStreamlineCandidates(token: string, dismissals: { anchorType: 'GOAL' | 'STEP'; anchorId: number; rule: string }[]) {
  return send<unknown>(token, `${base}/candidate-dismissals`, 'POST', { dismissals });
}

/** V30 SA-FR-01: AI-ranked goals and steps to streamline. Suggestions only. */
export function pickStreamlineCandidates(token: string, language: string) {
  return send<StreamlinePicks>(token, `${base}/ai-candidates?language=${language}`, 'POST');
}

/** V30 SA-FR-07: advice on a completed review. */
export function adviseStreamlineSummary(token: string, id: number, language: string) {
  return send<StreamlineAdvice>(token, `${base}/${id}/summary-advice?language=${language}`, 'POST');
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
