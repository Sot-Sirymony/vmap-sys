import { apiClient } from './apiClient';
import type { DashboardSummary } from '../types/vision';

/**
 * @param visionAreaId scope every number to one area; omit for the whole
 *   portfolio. Scoped by the server, not the browser — what comes back are
 *   already sums, and a sum can't be re-filtered by area without the rows.
 */
export function getDashboardSummary(token: string, visionAreaId?: string) {
  const query = visionAreaId ? `?visionAreaId=${encodeURIComponent(visionAreaId)}` : '';
  return apiClient<DashboardSummary>(`/dashboard${query}`, { token });
}
