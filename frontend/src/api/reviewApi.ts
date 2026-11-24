import { apiClient } from './apiClient';
import type { DueReview, Review, ReviewRequest, ReviewRhythm, ReviewRhythmChoice } from '../types/vision';

export function listReviews(token: string, includeArchived = false) {
  return apiClient<Review[]>(`/reviews?includeArchived=${includeArchived}`, { token });
}

export function createReview(token: string, request: ReviewRequest) {
  return apiClient<Review>('/reviews', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateReview(token: string, id: number, request: ReviewRequest) {
  return apiClient<Review>(`/reviews/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveReview(token: string, id: number) {
  return apiClient<void>(`/reviews/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function restoreReview(token: string, id: number) {
  return apiClient<void>(`/reviews/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function permanentlyDeleteReview(token: string, id: number) {
  return apiClient<void>(`/reviews/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}

// V31 v1 step 3 (DC-FR-48 to DC-FR-50): a dream's review rhythm and the reviews it makes due.

/** The dream's rhythm, or null when it has none (the server answers 204). */
export async function getReviewRhythm(token: string, dreamId: number) {
  return (await apiClient<ReviewRhythm | undefined>(`/dreams/${dreamId}/review-rhythm`, { token })) ?? null;
}

export function saveReviewRhythm(token: string, dreamId: number, rhythm: ReviewRhythmChoice) {
  return apiClient<ReviewRhythm>(`/dreams/${dreamId}/review-rhythm`, { method: 'PUT', token, body: JSON.stringify(rhythm) });
}

export function listDueReviews(token: string) {
  return apiClient<DueReview[]>('/reviews/due', { token });
}
