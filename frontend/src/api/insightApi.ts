import { apiClient } from './apiClient';
import type { Insight } from '../types/vision';

// FR-36.1: search the user's captured lessons. An empty query returns them all.
export function searchInsights(token: string, query: string) {
  return apiClient<Insight[]>(`/insights?query=${encodeURIComponent(query)}`, { token });
}
