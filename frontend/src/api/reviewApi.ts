import { apiClient } from './apiClient';
import type { Review, ReviewRequest } from '../types/vision';

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
