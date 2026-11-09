import { apiClient } from './apiClient';
import type { PageAssistantRequest, PageCheck, PageMetrics, PageProposals } from '../types/vision';

/** V15 (AH-FR-10): the area's rule checks; works without AI. */
export function checkPageArea(token: string, request: PageAssistantRequest) {
  return apiClient<PageCheck>('/ai/pages/assistant/check', { method: 'POST', token, body: JSON.stringify(request) });
}

/** V15 (AH-FR-06, AH-FR-07): checked change cards for the area; nothing is saved. */
export function proposePageChanges(token: string, language: string, request: PageAssistantRequest) {
  return apiClient<PageProposals>(`/ai/pages/assistant/proposals?language=${language}`, {
    method: 'POST', token, body: JSON.stringify(request),
  });
}

/** V15 (AH-FR-09): one card applied, as suggested (added) or after editing. Counts only. */
export function recordPageOutcome(token: string, requestId: number, added: number, edited: number) {
  return apiClient<void>(`/ai/pages/assistant/requests/${requestId}/outcome`, {
    method: 'POST', token, body: JSON.stringify({ added, edited }),
  });
}

/** V15 (AH-FR-56): per-feature metrics and baselines, for admins. */
export function getPageMetrics(token: string, days: number) {
  return apiClient<PageMetrics>(`/ai/pages/metrics?days=${days}`, { token });
}
