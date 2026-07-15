import { apiClient } from './apiClient';
import type { DashboardSummary } from '../types/vision';

/**
 * @param visionAreaId scope every number to one area; omit for the whole
 *   portfolio. Scoped by the server, not the browser — what comes back are
 *   already sums, and a sum can't be re-filtered by area without the rows.
 */
/** `from`/`to` (yyyy-MM-dd) window the two time-based tiles; omit for the current month. */
export function getDashboardSummary(token: string, visionAreaId?: string, from?: string, to?: string) {
  const params = new URLSearchParams();
  if (visionAreaId) {
    params.set('visionAreaId', visionAreaId);
  }
  if (from) {
    params.set('from', from);
  }
  if (to) {
    params.set('to', to);
  }
  const query = params.toString();
  return apiClient<DashboardSummary>(`/dashboard${query ? `?${query}` : ''}`, { token });
}
