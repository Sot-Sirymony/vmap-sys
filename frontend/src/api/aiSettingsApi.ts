import { apiClient } from './apiClient';
import { stepUpHeader } from './stepUpApi';
import type { AssistantPage, AiSettings } from '../types/vision';

// V9 (AI-FR-24): admin-only AI settings. The key goes to the server once and never comes back.

export type AiSettingsInput = {
  enabled: boolean;
  model: string;
  monthlyBudgetUsd: number;
  dailyMessageLimit?: number;
  helpLines?: string;
  aiReminders?: boolean;
  coachingGuidelines?: string;
  // V11 (PB-FR-39): blank uses the main model for plan building too.
  planModel?: string;
  // V15 (AH-FR-57): pages where the page assistant and helpers are off for everyone.
  pagesOff?: AssistantPage[];
  // V31 v2 (DC-FR-66): blank removes the extra owner roles.
  ownerRoles?: string;
  // V32 (D-32-3): blank keeps the plan building or main model for Streamline.
  streamlineModel?: string;
};

export function getAiSettings(token: string) {
  return apiClient<AiSettings>('/admin/ai-settings', { token });
}

export function updateAiSettings(token: string, input: AiSettingsInput) {
  return apiClient<AiSettings>('/admin/ai-settings', { method: 'PUT', token, body: JSON.stringify(input) });
}

/** V20 SV-FR-07: a fresh "Verify it's you" code (stepUp) every time. */
export function saveAiApiKey(token: string, apiKey: string, stepUp?: number) {
  return apiClient<AiSettings>('/admin/ai-settings/api-key', { method: 'PUT', token, headers: stepUpHeader(stepUp), body: JSON.stringify({ apiKey }) });
}

export function removeAiApiKey(token: string, stepUp?: number) {
  return apiClient<AiSettings>('/admin/ai-settings/api-key', { method: 'DELETE', token, headers: stepUpHeader(stepUp) });
}

export function testAiConnection(token: string) {
  return apiClient<AiSettings>('/admin/ai-settings/test', { method: 'POST', token });
}
