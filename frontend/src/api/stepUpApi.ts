import { apiClient } from './apiClient';
import type { StepUpAction, StepUpChallenge, StepUpStatus, StepUpVerified } from '../types/vision';

/**
 * V20 (BRD 20.0.0, SV-FR-10 to SV-FR-16): "Verify it's you" codes. A code is
 * emailed for one action; once verified, its id goes to the protected action
 * in the X-Step-Up header.
 */
/** The header that carries a verified code to a protected action; empty without one. */
export function stepUpHeader(stepUp?: number): Record<string, string> {
  return stepUp ? { 'X-Step-Up': String(stepUp) } : {};
}

export function requestStepUpCode(token: string, action: StepUpAction, language: string, target?: string) {
  return apiClient<StepUpChallenge>(`/account/step-up?language=${language}`, {
    method: 'POST',
    token,
    body: JSON.stringify(target ? { action, target } : { action }),
  });
}

/** V20 SV-FR-34: for the admin warning when codes cannot be sent. */
export function getStepUpStatus(token: string) {
  return apiClient<StepUpStatus>('/admin/step-up', { token });
}

export function verifyStepUpCode(token: string, challengeId: number, code: string) {
  return apiClient<StepUpVerified>(`/account/step-up/${challengeId}/verify`, {
    method: 'POST',
    token,
    body: JSON.stringify({ code }),
  });
}
