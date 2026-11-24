import { apiClient } from './apiClient';
import type { MarkBlockedRequest, MarkBlockedResult } from '../types/vision';

/** V28 BA-FR-11, BA-FR-12: marks one open task or step as blocked and records an obstacle on it. No AI. */
export function markBlocked(token: string, request: MarkBlockedRequest) {
  return apiClient<MarkBlockedResult>('/blocks', { method: 'POST', token, body: JSON.stringify(request) });
}
