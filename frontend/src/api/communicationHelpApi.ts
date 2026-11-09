import { apiClient } from './apiClient';
import type { CommunicationHelpAnswer, CommunicationHelpRequest, FollowUpDue, RankedMessage } from '../types/vision';

/** V15 (AH-FR-44 to AH-FR-66): one AI Communication helper. Nothing is saved or sent. */
export function communicationHelp(token: string, language: string, request: CommunicationHelpRequest) {
  return apiClient<CommunicationHelpAnswer>(`/ai/communication/help?language=${language}`, {
    method: 'POST', token, body: JSON.stringify(request),
  });
}

/** V15 (AH-FR-48): Sent or Followed Up messages past their follow-up date. No AI. */
export function getFollowUpDue(token: string) {
  return apiClient<FollowUpDue[]>('/communication-messages/follow-up-due', { token });
}

/** V15 (AH-FR-66): Draft messages in the rule order. No AI. */
export function getDraftPriority(token: string) {
  return apiClient<RankedMessage[]>('/communication-messages/draft-priority', { token });
}
